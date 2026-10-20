package io.github.j3ffx.nuance.camera

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.PowerManager
import android.util.Range
import android.util.Size
import android.view.Display
import android.view.SurfaceView
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import io.github.j3ffx.nuance.core.Lut3d
import io.github.j3ffx.nuance.render.CameraRenderer
import io.github.j3ffx.nuance.render.GlThread
import io.github.j3ffx.nuance.render.RenderStats
import io.github.j3ffx.nuance.render.StatsCollector
import kotlinx.coroutines.flow.StateFlow

/**
 * Android's [CameraRenderer]: CameraX for the camera, OpenGL ES 3 for the LUT.
 * - [DIRECT]: our SurfaceTexture → render thread → SurfaceView. Chosen over a CameraX effect in J1
 *   (same latency, and we own the screen layout the Comparer mode needs: docs/ALGORITHMS.md).
 * - [RAW]: the PreviewView alone, no filter: the latency baseline, dev variant only.
 */
class AndroidCameraRenderer(private val context: Context) : CameraRenderer {
    private val collector = StatsCollector()
    override val stats: StateFlow<RenderStats> = collector.stats
    override val pipelines = listOf(DIRECT, RAW)

    private var pipeline by mutableStateOf(DIRECT)
    private var frameRate by mutableStateOf(30)
    private var lut: Lut3d? = null
    private var gl: GlThread? = null

    init {
        collector.update { it.copy(pipeline = pipeline) }
        val power = context.getSystemService(PowerManager::class.java)
        collector.update { it.copy(thermal = thermalName(power.currentThermalStatus)) }
        power.addThermalStatusListener(ContextCompat.getMainExecutor(context)) { status ->
            collector.update { it.copy(thermal = thermalName(status)) }
        }
        val refreshRate = context.getSystemService(
            DisplayManager::class.java
        ).getDisplay(Display.DEFAULT_DISPLAY).refreshRate
        collector.update { it.copy(vsyncMs = 1000f / refreshRate) }
    }

    override fun selectPipeline(name: String) {
        require(name in pipelines) { "unknown pipeline $name" }
        pipeline = name
        collector.update { RenderStats(pipeline = name, thermal = it.thermal, vsyncMs = it.vsyncMs) }
    }

    /** Dev variant: asks the camera for [fps] frames a second (30 or 60); the camera restarts. */
    fun selectFrameRate(fps: Int) {
        frameRate = fps
    }

    override fun setLut(lut: Lut3d, bakeMs: Float?) {
        this.lut = lut
        collector.update { it.copy(lutBakeMs = bakeMs) }
        gl?.setLut(lut)
    }

    /** The camera view of the current pipeline, bound to [lifecycleOwner]. */
    @Composable
    fun CameraView(lifecycleOwner: LifecycleOwner, modifier: Modifier) {
        key(pipeline, frameRate) {
            when (pipeline) {
                DIRECT -> DirectView(lifecycleOwner, modifier)
                RAW -> RawPreview(lifecycleOwner, modifier)
            }
        }
    }

    @Composable
    private fun DirectView(lifecycleOwner: LifecycleOwner, modifier: Modifier) {
        val thread = rememberGlThread("nuance-gl-direct")
        val direct = remember(thread) { DirectPipeline(thread, collector) }
        val preview = remember(thread) { preview().apply { surfaceProvider = direct } }
        AndroidView(factory = { SurfaceView(it).apply { holder.addCallback(direct) } }, modifier)
        // The activity handles rotation itself (configChanges): follow the display's rotation.
        val configuration = LocalConfiguration.current
        LaunchedEffect(configuration) { preview.targetRotation = displayRotation() }
        BindCamera(lifecycleOwner) { provider ->
            val camera = provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview)
            direct.setSensorRotation(camera.cameraInfo.sensorRotationDegrees)
        }
    }

    @Composable
    private fun RawPreview(lifecycleOwner: LifecycleOwner, modifier: Modifier) {
        var previewView by remember { mutableStateOf<PreviewView?>(null) }
        AndroidView(
            factory = {
                PreviewView(it).apply {
                    // SurfaceView-backed: the shortest path to the display.
                    implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    previewView = this
                }
            },
            modifier
        )
        val view = previewView ?: return
        BindCamera(lifecycleOwner) { provider ->
            val preview = preview().apply { surfaceProvider = view.surfaceProvider }
            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview)
        }
    }

    @Composable
    private fun rememberGlThread(name: String): GlThread {
        val thread = remember { GlThread(name, collector).also { t -> lut?.let(t::setLut) } }
        DisposableEffect(thread) {
            gl = thread
            onDispose {
                if (gl === thread) gl = null
                thread.quit()
            }
        }
        return thread
    }

    @Composable
    private fun BindCamera(lifecycleOwner: LifecycleOwner, bind: (ProcessCameraProvider) -> Unit) {
        var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
        LaunchedEffect(Unit) {
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({ provider = future.get() }, ContextCompat.getMainExecutor(context))
        }
        DisposableEffect(provider) {
            val p = provider
            if (p != null) {
                p.unbindAll()
                bind(p)
            }
            onDispose { p?.unbindAll() }
        }
    }

    private fun displayRotation(): Int =
        context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY).rotation

    /** 1080p, 16:9, 30 frames a second (the v0 target) unless the dev variant asks for 60. */
    private fun preview(): Preview = Preview.Builder()
        .setResolutionSelector(
            ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                .setResolutionStrategy(
                    ResolutionStrategy(Size(1920, 1080), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
                )
                .build()
        )
        .setTargetFrameRate(Range(frameRate, frameRate))
        .build()

    companion object {
        const val DIRECT = "direct"
        const val RAW = "raw"

        private fun thermalName(status: Int) = when (status) {
            PowerManager.THERMAL_STATUS_NONE -> "none"
            PowerManager.THERMAL_STATUS_LIGHT -> "light"
            PowerManager.THERMAL_STATUS_MODERATE -> "moderate"
            PowerManager.THERMAL_STATUS_SEVERE -> "severe"
            PowerManager.THERMAL_STATUS_CRITICAL -> "critical"
            PowerManager.THERMAL_STATUS_EMERGENCY -> "emergency"
            PowerManager.THERMAL_STATUS_SHUTDOWN -> "shutdown"
            else -> "?"
        }
    }
}
