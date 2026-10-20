package io.github.j3ffx.nuance

import android.Manifest
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
    private var devLut by mutableStateOf<String?>(null)

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
                devTools = BuildConfig.DEBUG,
                cameraGranted = granted,
                onRequestCamera = { request.launch(Manifest.permission.CAMERA) },
                devLut = devLut,
                camera = { modifier -> renderer.CameraView(this, modifier) }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        applyDevExtras(intent)
    }

    /**
     * Dev variant only: lets scripts switch pipelines without touching the screen, e.g.
     * `am start -n <app>/<activity> --es pipeline raw --ei fps 60 --es lut "corr D"`.
     */
    private fun applyDevExtras(intent: Intent) {
        if (!BuildConfig.DEBUG) return
        intent.getStringExtra("pipeline")?.takeIf { it in renderer.pipelines }?.let(renderer::selectPipeline)
        intent.getIntExtra("fps", 0).takeIf { it == 30 || it == 60 }?.let(renderer::selectFrameRate)
        intent.getStringExtra("lut")?.let { devLut = it }
    }

    private fun hasCamera() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
}
