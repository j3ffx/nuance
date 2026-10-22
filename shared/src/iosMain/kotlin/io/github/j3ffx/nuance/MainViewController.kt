package io.github.j3ffx.nuance

import androidx.compose.ui.window.ComposeUIViewController
import io.github.j3ffx.nuance.platform.KeyValueStore
import io.github.j3ffx.nuance.platform.Platform
import io.github.j3ffx.nuance.platform.ReleaseInfo
import io.github.j3ffx.nuance.platform.UpdateOutcome
import io.github.j3ffx.nuance.platform.UpdateProgress
import io.github.j3ffx.nuance.render.NoCameraRenderer
import platform.Foundation.NSBundle
import platform.Foundation.NSUserDefaults
import platform.UIKit.UIViewController

/** Entry point called from Swift (iosApp/iosApp/ContentView.swift). No camera yet: J7. */
@Suppress("ktlint:standard:function-naming", "FunctionName")
fun MainViewController(): UIViewController = ComposeUIViewController {
    App(
        renderer = NoCameraRenderer(),
        platform = IosPlatform,
        devTools = false,
        cameraGranted = true,
        onRequestCamera = {},
        camera = {}
    )
}

/** iOS's [Platform]: NSUserDefaults for preferences; sharing and language settings come with J7. */
private object IosPlatform : Platform {
    override val versionName: String =
        NSBundle.mainBundle.infoDictionary?.get("CFBundleShortVersionString") as? String ?: "?"

    override val store: KeyValueStore = object : KeyValueStore {
        private val defaults = NSUserDefaults.standardUserDefaults

        override fun getString(key: String): String? = defaults.stringForKey("nuance.$key")

        override fun putString(key: String, value: String?) {
            if (value == null) defaults.removeObjectForKey("nuance.$key") else defaults.setObject(value, "nuance.$key")
        }
    }

    override fun shareText(text: String, title: String) = Unit

    /** No update from inside the app on iOS: the App Store or TestFlight will do it (J7). */
    override suspend fun latestRelease(): ReleaseInfo? = null

    override suspend fun installRelease(release: ReleaseInfo, onProgress: (UpdateProgress) -> Unit) =
        UpdateOutcome.Failed

    /** iOS reads AppleLanguages at launch: the choice applies the next time the app starts. */
    override var appLanguage: String?
        get() = NSUserDefaults.standardUserDefaults.stringForKey("nuance.appLanguage")
        set(value) {
            val defaults = NSUserDefaults.standardUserDefaults
            if (value == null) {
                defaults.removeObjectForKey("nuance.appLanguage")
                defaults.removeObjectForKey("AppleLanguages")
            } else {
                defaults.setObject(value, "nuance.appLanguage")
                defaults.setObject(listOf(value), "AppleLanguages")
            }
        }
}
