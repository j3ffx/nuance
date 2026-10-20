package io.github.j3ffx.nuance

import androidx.compose.ui.window.ComposeUIViewController
import io.github.j3ffx.nuance.render.NoCameraRenderer
import platform.UIKit.UIViewController

/** Entry point called from Swift (iosApp/iosApp/ContentView.swift). No camera yet: J7. */
@Suppress("ktlint:standard:function-naming", "FunctionName")
fun MainViewController(): UIViewController = ComposeUIViewController {
    App(renderer = NoCameraRenderer(), devTools = false, cameraGranted = true, onRequestCamera = {}, camera = {})
}
