package com.example.temitest

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.robotemi.sdk.NlpResult
import com.robotemi.sdk.Robot
import com.robotemi.sdk.SttLanguage
import com.robotemi.sdk.TtsRequest
import com.robotemi.sdk.face.ContactModel
import com.robotemi.sdk.face.OnFaceRecognizedListener
import com.robotemi.sdk.listeners.OnBeWithMeStatusChangedListener
import com.robotemi.sdk.listeners.OnDetectionStateChangedListener
import com.robotemi.sdk.listeners.OnGoToLocationStatusChangedListener
import com.robotemi.sdk.listeners.OnRobotDragStateChangedListener
import com.robotemi.sdk.listeners.OnRobotLiftedListener
import com.robotemi.sdk.listeners.OnRobotReadyListener
import com.robotemi.sdk.permission.OnRequestPermissionResultListener
import com.robotemi.sdk.permission.Permission
import com.robotemi.sdk.voice.WakeupOrigin
import com.example.temitest.ui.preview.TemiPreview
import com.example.temitest.ui.theme.TemiTestTheme

/** One entry per PNG in res/drawable, with the screen background shown behind it. */
enum class Face(@param:DrawableRes val res: Int, val background: Color) {
    CHEEKY(R.drawable.face_cheeky, Color(0xFFFFCAF5)),
    HAPPY(R.drawable.face_happy, Color(0xFFFFAE55)),
    INQUISITIVE(R.drawable.face_inquisitive, Color(0xFFB4FFA9)),
    NERDY(R.drawable.face_nerdy, Color(0xFF5AFBFF)),
    SURPRISE(R.drawable.face_surprise, Color(0xFFFFE75C)),
    UNSURE(R.drawable.face_unsure, Color(0xFF57BFFF)),
}

class MainActivity : ComponentActivity(),
    OnRobotReadyListener,
    Robot.WakeupWordListener,
    Robot.AsrListener,
    Robot.NlpListener,
    Robot.TtsListener,
    Robot.ConversationViewAttachesListener,
    OnDetectionStateChangedListener,
    OnBeWithMeStatusChangedListener,
    OnGoToLocationStatusChangedListener,
    OnRobotLiftedListener,
    OnRobotDragStateChangedListener,
    OnFaceRecognizedListener,
    OnRequestPermissionResultListener {

    private val robot by lazy { Robot.getInstance() }

    // ---------- Face state ----------

    /** The face shown when nothing is happening. */
    private val idleFace = Face.CHEEKY

    /** Compose watches this; changing it redraws the screen. */
    private var face by mutableStateOf(idleFace)

    /** Set while following, so "back to idle" doesn't override that face. */
    private var busyFace: Face? = null

    private val handler = Handler(Looper.getMainLooper())
    private val backToIdle = Runnable { face = busyFace ?: idleFace }

    /**
     * Show a face. If [holdMs] > 0 it goes back to idle (or the busy face) after that long.
     * If [holdMs] is 0 it stays until something else changes it.
     */
    private fun showFace(newFace: Face, holdMs: Long = 0) {
        runOnUiThread {
            handler.removeCallbacks(backToIdle)
            face = newFace
            if (holdMs > 0) handler.postDelayed(backToIdle, holdMs)
        }
    }

    private fun backToIdleNow() = showFace(busyFace ?: idleFace)

    // ---------- Lifecycle ----------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TemiTestTheme {
                EmotionFaceScreen(
                    face = face,
                    onExit = { exitApp() },
                    onPickFace = { showFace(it) }, // stays until the next temi event
                )
            }
        }
    }

    /** Hidden exit (long-press the face): bring temi's top bar back, then close the app. */
    private fun exitApp() {
        robot.showTopBar()
        finish()
    }

    override fun onStart() {
        super.onStart()
        robot.addOnRobotReadyListener(this)
        robot.addWakeupWordListener(this)
        robot.addAsrListener(this)
        robot.addNlpListener(this)
        robot.addTtsListener(this)
        robot.addConversationViewAttachesListener(this)
        robot.addOnDetectionStateChangedListener(this)
        robot.addOnBeWithMeStatusChangedListener(this)
        robot.addOnGoToLocationStatusChangedListener(this)
        robot.addOnRobotLiftedListener(this)
        robot.addOnRobotDragStateChangedListener(this)
        robot.addOnFaceRecognizedListener(this)
        robot.addOnRequestPermissionResultListener(this)
    }

    override fun onStop() {
        robot.removeOnRobotReadyListener(this)
        robot.removeWakeupWordListener(this)
        robot.removeAsrListener(this)
        robot.removeNlpListener(this)
        robot.removeTtsListener(this)
        robot.removeConversationViewAttachesListener(this)
        robot.removeOnDetectionStateChangedListener(this)
        robot.removeOnBeWithMeStatusChangedListener(this)
        robot.removeOnGoToLocationStatusChangedListener(this)
        robot.removeOnRobotLiftedListener(this)
        robot.removeOnRobotDragStateChangedListener(this)
        robot.removeOnFaceRecognizedListener(this)
        robot.removeOnRequestPermissionResultListener(this)
        robot.stopFaceRecognition()
        handler.removeCallbacks(backToIdle)
        super.onStop()
    }

    override fun onRobotReady(isReady: Boolean) {
        if (!isReady) return
        Log.d(TAG, "Robot ready")
        robot.hideTopBar()
        // Turn on person detection so onDetectionStateChanged fires (distance in meters).
        robot.setDetectionModeOn(true, 1.5f)
        showFace(idleFace)
        startFaceRecognitionIfAllowed()
    }

    // ---------- Face recognition ----------

    /** When we last said hi to each recognized person (userId -> time), so we don't repeat it. */
    private val lastGreeted = mutableMapOf<String, Long>()

    /** Needs the FACE_RECOGNITION permission (declared in AndroidManifest.xml). Asks for it if missing. */
    private fun startFaceRecognitionIfAllowed() {
        if (robot.checkSelfPermission(Permission.FACE_RECOGNITION) == Permission.GRANTED) {
            robot.startFaceRecognition()
            Log.d(TAG, "Face recognition started")
        } else {
            robot.requestPermissions(listOf(Permission.FACE_RECOGNITION), REQUEST_FACE)
        }
    }

    override fun onRequestPermissionResult(permission: Permission, grantResult: Int, requestCode: Int) {
        if (requestCode != REQUEST_FACE) return
        if (grantResult == Permission.GRANTED) startFaceRecognitionIfAllowed()
        else {
            Log.w(TAG, "Face recognition permission denied")
            showFace(Face.UNSURE, holdMs = 3000)
        }
    }

    /** temi saw one or more faces. Known contacts have a name; check Logcat to see what unknown faces look like. */
    override fun onFaceRecognized(contactModelList: List<ContactModel>) {
        contactModelList.forEach {
            Log.d(TAG, "Face: firstName=${it.firstName} userId=${it.userId} userType=${it.userType} similarity=${it.similarity}")
        }
        if (contactModelList.isEmpty()) return
        showFace(Face.HAPPY, holdMs = 4000)

        val now = System.currentTimeMillis()
        val person = contactModelList.firstOrNull { it.firstName.isNotBlank() } ?: return
        val last = lastGreeted[person.userId] ?: 0L
        if (now - last < GREET_COOLDOWN_MS) return
        lastGreeted[person.userId] = now
        robot.speak(TtsRequest.create("Hi ${person.firstName}!", false))
    }

    // ---------- Voice ----------

    /** "Hey temi" heard: temi starts listening. */
    override fun onWakeupWord(wakeupWord: String, direction: Int, origin: WakeupOrigin) {
        showFace(Face.INQUISITIVE)
    }

    /** temi's listening overlay opened/closed. */
    override fun onConversationAttaches(isAttached: Boolean) {
        if (isAttached) showFace(Face.INQUISITIVE) else backToIdleNow()
    }

    /** Speech-to-text result. Blank means temi didn't catch anything. */
    override fun onAsrResult(asrResult: String, sttLanguage: SttLanguage) {
        Log.d(TAG, "ASR: $asrResult")
        if (asrResult.isBlank()) showFace(Face.UNSURE, holdMs = 3000)
        else showFace(Face.NERDY, holdMs = 2000) // "thinking"
    }

    /** Only fires for intents you set up for your app in the temi console. */
    override fun onNlpCompleted(nlpResult: NlpResult) {
        Log.d(TAG, "NLP action: ${nlpResult.action}")
        if (nlpResult.action.isNullOrBlank()) showFace(Face.UNSURE, holdMs = 3000)
        else showFace(Face.HAPPY, holdMs = 3000)
    }

    /** temi talking. */
    override fun onTtsStatusChanged(ttsRequest: TtsRequest) {
        when (ttsRequest.status) {
            TtsRequest.Status.STARTED -> if (face != Face.HAPPY) showFace(Face.NERDY)
            TtsRequest.Status.COMPLETED,
            TtsRequest.Status.CANCELED -> showFace(face, holdMs = 1500)
            TtsRequest.Status.ERROR,
            TtsRequest.Status.NOT_ALLOWED -> showFace(Face.UNSURE, holdMs = 3000)
            else -> Unit
        }
    }

    // ---------- People ----------

    override fun onDetectionStateChanged(state: Int) {
        when (state) {
            OnDetectionStateChangedListener.DETECTED -> showFace(Face.HAPPY, holdMs = 4000)
            OnDetectionStateChangedListener.LOST -> backToIdleNow()
            else -> Unit // IDLE
        }
    }

    // ---------- Follow (beWithMe) ----------

    override fun onBeWithMeStatusChanged(status: String) {
        Log.d(TAG, "Follow: $status")
        when (status) {
            OnBeWithMeStatusChangedListener.START,
            OnBeWithMeStatusChangedListener.CALCULATING,
            OnBeWithMeStatusChangedListener.SEARCH -> {
                busyFace = Face.INQUISITIVE
                showFace(Face.INQUISITIVE) // looking for you
            }
            OnBeWithMeStatusChangedListener.TRACK -> {
                busyFace = Face.HAPPY
                showFace(Face.HAPPY) // locked on, following
            }
            OnBeWithMeStatusChangedListener.OBSTACLE_DETECTED -> showFace(Face.SURPRISE, holdMs = 2000)
            OnBeWithMeStatusChangedListener.ABORT -> {
                busyFace = null
                showFace(Face.UNSURE, holdMs = 2500)
            }
        }
    }

    // ---------- Go to location ----------

    override fun onGoToLocationStatusChanged(location: String, status: String, descriptionId: Int, description: String) {
        Log.d(TAG, "GoTo $location: $status ($description)")
        when (status) {
            OnGoToLocationStatusChangedListener.START,
            OnGoToLocationStatusChangedListener.CALCULATING,
            OnGoToLocationStatusChangedListener.GOING -> showFace(Face.NERDY)
            OnGoToLocationStatusChangedListener.COMPLETE -> showFace(Face.HAPPY, holdMs = 3000)
            OnGoToLocationStatusChangedListener.ABORT -> showFace(Face.UNSURE, holdMs = 3000)
            OnGoToLocationStatusChangedListener.REPOSING -> showFace(Face.INQUISITIVE)
        }
    }

    // ---------- Picked up / pushed ----------

    override fun onRobotLifted(isLifted: Boolean, reason: String) {
        if (isLifted) showFace(Face.SURPRISE) else showFace(Face.SURPRISE, holdMs = 1500)
    }

    override fun onRobotDragStateChanged(isDragged: Boolean) {
        if (isDragged) showFace(Face.SURPRISE) else showFace(Face.SURPRISE, holdMs = 1500)
    }

    companion object {
        private const val TAG = "TemiFaces"
        private const val REQUEST_FACE = 1002
        private const val GREET_COOLDOWN_MS = 60_000L
    }
}

// ---------- UI ----------

/** Set to false to hide the row of face buttons (e.g. for a demo). */
private const val SHOW_TEST_BUTTONS = true

/**
 * Full-screen face on its background. The face changes from temi events in MainActivity.
 * Long-press the face to exit. When [SHOW_TEST_BUTTONS] is on, a row of buttons along the
 * bottom lets you pick any face by tapping (handy on the emulator, where no temi events fire).
 */
@Composable
fun EmotionFaceScreen(
    face: Face = Face.CHEEKY,
    onExit: () -> Unit = {},
    onPickFace: (Face) -> Unit = {},
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(face.background),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(face.res),
            contentDescription = face.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = if (SHOW_TEST_BUTTONS) 80.dp else 0.dp)
                .pointerInput(onExit) { detectTapGestures(onLongPress = { onExit() }) },
        )
        if (SHOW_TEST_BUTTONS) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            ) {
                Face.entries.forEach { f ->
                    Button(
                        onClick = { onPickFace(f) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Black.copy(alpha = 0.45f),
                            contentColor = Color.White,
                        ),
                    ) {
                        Text(f.name.lowercase().replaceFirstChar { it.uppercase() })
                    }
                }
            }
        }
    }
}

// ---------- Previews: one per face, so all six show side by side in the preview pane ----------

@TemiPreview
@Composable
fun CheekyFacePreview() {
    TemiTestTheme { EmotionFaceScreen(face = Face.CHEEKY) }
}

@TemiPreview
@Composable
fun HappyFacePreview() {
    TemiTestTheme { EmotionFaceScreen(face = Face.HAPPY) }
}

@TemiPreview
@Composable
fun InquisitiveFacePreview() {
    TemiTestTheme { EmotionFaceScreen(face = Face.INQUISITIVE) }
}

@TemiPreview
@Composable
fun NerdyFacePreview() {
    TemiTestTheme { EmotionFaceScreen(face = Face.NERDY) }
}

@TemiPreview
@Composable
fun SurpriseFacePreview() {
    TemiTestTheme { EmotionFaceScreen(face = Face.SURPRISE) }
}

@TemiPreview
@Composable
fun UnsureFacePreview() {
    TemiTestTheme { EmotionFaceScreen(face = Face.UNSURE) }
}
