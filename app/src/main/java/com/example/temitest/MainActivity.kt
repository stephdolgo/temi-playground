package com.example.temitest

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.temitest.ui.preview.TemiPreview
import com.example.temitest.ui.theme.TemiTestTheme
import com.robotemi.sdk.Robot
import com.robotemi.sdk.TtsRequest
import com.robotemi.sdk.listeners.OnRobotReadyListener

class MainActivity : ComponentActivity(), OnRobotReadyListener {

    private val robot by lazy { Robot.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TemiTestTheme {
                HomeScreen(onSayHello = {
                    robot.speak(TtsRequest.create("Hello! I am temi.", false))
                })
            }
        }
    }

    override fun onStart() {
        super.onStart()
        robot.addOnRobotReadyListener(this)
    }

    override fun onStop() {
        super.onStop()
        robot.removeOnRobotReadyListener(this)
    }

    override fun onRobotReady(isReady: Boolean) {
        if (isReady) {
            val info = packageManager.getActivityInfo(componentName, PackageManager.GET_META_DATA)
            robot.onStart(info)
        }
    }
}

@Composable
fun HomeScreen(onSayHello: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Button(onClick = onSayHello) {
            Text("Say hello")
        }
    }
}

@TemiPreview
@Composable
fun HomeScreenPreview() {
    TemiTestTheme {
        HomeScreen(onSayHello = {})
    }
}
