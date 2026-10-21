package io.github.j3ffx.nuance

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import io.github.j3ffx.nuance.camera.AndroidCameraRenderer

class MainActivity : ComponentActivity() {
    private val renderer by lazy { AndroidCameraRenderer(this) }
    private val platform by lazy { AndroidPlatform(applicationContext) { recreate() } }
    private var devCommand by mutableStateOf<DevCommand?>(null)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AndroidPlatform.withAppLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // A viewfinder: the screen stays on while the camera runs.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        applyDevExtras(intent)
        setContent {
            var granted by remember { mutableStateOf(hasCamera()) }
            val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
                granted = it
            }
            App(
                renderer = renderer,
                platform = platform,
                devTools = BuildConfig.DEBUG,
                cameraGranted = granted,
                onRequestCamera = { request.launch(Manifest.permission.CAMERA) },
                devCommand = devCommand,
                camera = { modifier -> renderer.CameraView(this, modifier) }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        applyDevExtras(intent)
    }

    /**
     * Dev variant only: lets scripts drive the app without touching the screen, e.g.
     * `am start -n <app>/<activity> --es source "stripes D" --es mode compare --ei views 4 --ei fps 30 --es screen settings --ez narrow true --ez large true`.
     */
    private fun applyDevExtras(intent: Intent) {
        if (!BuildConfig.DEBUG) return
        intent.getStringExtra("source")?.takeIf { it in renderer.sources }?.let(renderer::selectSource)
        intent.getIntExtra("fps", 0).takeIf { it == 30 || it == 60 }?.let(renderer::selectFrameRate)
        val mode = intent.getStringExtra("mode")
        val views = intent.getIntExtra("views", 0).takeIf { it > 0 }
        val screen = intent.getStringExtra("screen")
        val narrow = if (intent.hasExtra("narrow")) intent.getBooleanExtra("narrow", false) else null
        val large = if (intent.hasExtra("large")) intent.getBooleanExtra("large", false) else null
        val testAs = intent.getStringExtra("testAs")
        val cross = if (intent.hasExtra("cross")) intent.getBooleanExtra("cross", false) else null
        if (listOf(mode, views, screen, narrow, large, testAs, cross).any { it != null }) {
            devCommand = DevCommand(mode, views, screen, narrow, large, testAs, cross)
        }
    }

    private fun hasCamera() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
}
