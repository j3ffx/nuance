package io.github.j3ffx.nuance

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/** Entry point called from Swift (iosApp/iosApp/ContentView.swift). */
@Suppress("ktlint:standard:function-naming", "FunctionName")
fun MainViewController(): UIViewController = ComposeUIViewController { App() }
