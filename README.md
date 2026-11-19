# temi-playground

A practice Android app (Kotlin + Jetpack Compose) for the [temi robot](https://www.robotemi.com/), built on the [temi SDK](https://github.com/robotemi/sdk).

## Requirements

- Android Studio with JDK 17+
- temi V3 runs Android 11 (API 30). This app's minSdk is 23, so it also runs on temi V2 (Android 6.0.1).

## Previewing without a robot

`app/src/main/java/com/example/temitest/ui/preview/TemiPreview.kt` defines a `@TemiPreview` annotation. It previews a composable at temi V3's screen size: 13.3", 1920x1080, landscape. The 160 dpi density is an estimate.

To run the app on an emulator, create a virtual device in Android Studio's Device Manager with a new hardware profile: 13.3", 1920x1080, landscape, with an API 30 system image. Robot actions such as `speak()` do nothing on the emulator, but layouts render as they would on temi.
