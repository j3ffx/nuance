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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import io.github.j3ffx.nuance.BuildConfig
import io.github.j3ffx.nuance.core.Lut3d
import io.github.j3ffx.nuance.core.Rgb
import io.github.j3ffx.nuance.core.charts.SyntheticCharts
import io.github.j3ffx.nuance.core.charts.SyntheticImage
import io.github.j3ffx.nuance.core.cvd.Axis
import io.github.j3ffx.nuance.core.naming.ColorNaming
import io.github.j3ffx.nuance.render.CameraRenderer
import io.github.j3ffx.nuance.render.GlThread
import io.github.j3ffx.nuance.render.RenderStats
import io.github.j3ffx.nuance.render.StatsCollector
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Android's [CameraRenderer]: CameraX for the camera, OpenGL ES 3 for the LUTs.
 * - [CAMERA]: our SurfaceTexture → render thread → SurfaceView ([DirectPipeline]), chosen in J1
 *   over a CameraX effect (docs/ALGORITHMS.md, "Choix du pipeline caméra").
 * - Dev variant only: [RAW], the PreviewView alone without filter (the latency baseline), and
 *   synthetic sources drawn through the same views and LUTs ([FakePipeline]).
 */
class AndroidCameraRenderer(private val context: Context) : CameraRenderer {
    private val collector = StatsCollector()
    override val stats: StateFlow<RenderStats> = collector.stats

    private val synthetic: Map<String, () -> SyntheticImage> = mapOf(
        "chart" to { SyntheticCharts.patchGrid() },
        "hues" to { SyntheticCharts.hueSweep(720, 240) },
        "stripes P" to { SyntheticCharts.confusionStripes(Axis.Protan) },
        "stripes D" to { SyntheticCharts.confusionStripes(Axis.Deutan) },
        "stripes T" to { SyntheticCharts.confusionStripes(Axis.Tritan) },
        MOVING to { SyntheticCharts.hueSweep(720, 240) }
    )
    override val sources = if (BuildConfig.DEBUG) listOf(CAMERA, RAW) + synthetic.keys else listOf(CAMERA)

    private var source by mutableStateOf(CAMERA)
    private var frameRate by mutableIntStateOf(30)
    private var luts: List<Lut3d> = emptyList()
    private var gl: GlThread? = null

    init {
        collector.update { it.copy(source = source) }
        val power = context.getSystemService(PowerManager::class.java)
        collector.update { it.copy(thermal = thermalName(power.currentThermalStatus)) }
        power.addThermalStatusListener(ContextCompat.getMainExecutor(context)) { status ->
            collector.update { it.copy(thermal = thermalName(status)) }
        }
        collector.update { it.copy(vsyncMs = 1000f / display().refreshRate) }
    }

    override fun selectSource(name: String) {
        require(name in sources) { "unknown source $name" }
        source = name
        collector.update { RenderStats(source = name, thermal = it.thermal, vsyncMs = it.vsyncMs, views = it.views) }
    }

    /** Dev variant: asks the camera for [fps] frames a second (30 or 60); the camera restarts. */
    fun selectFrameRate(fps: Int) {
        frameRate = fps
    }

    override fun setViews(luts: List<Lut3d>, bakeMs: Float?) {
        this.luts = luts
        collector.update { it.copy(lutBakeMs = bakeMs) }
        gl?.setViews(luts)
    }

    override suspend fun sampleColor(x: Float, y: Float): Rgb? {
        val thread = gl ?: return null // the raw preview has no GL side
        val pixels = withTimeoutOrNull(SAMPLE_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont -> thread.requestSample(x, y) { cont.resume(it) } }
        }
        return pixels?.let { ColorNaming.average(it) }
    }

    /** The image of the current source, bound to [lifecycleOwner]. */
    @Composable
    fun CameraView(lifecycleOwner: LifecycleOwner, modifier: Modifier) {
        key(source, frameRate) {
            when (source) {
                CAMERA -> DirectView(lifecycleOwner, modifier)
                RAW -> RawPreview(lifecycleOwner, modifier)
                else -> FakeView(synthetic.getValue(source), scrolling = source == MOVING, modifier)
            }
        }
    }

    @Composable
    private fun DirectView(lifecycleOwner: LifecycleOwner, modifier: Modifier) {
        val thread = rememberGlThread("nuance-gl-camera")
        val direct = remember(thread) { DirectPipeline(thread, collector) }
        val preview = remember(thread) { preview().apply { surfaceProvider = direct } }
        AndroidView(factory = { SurfaceView(it).apply { holder.addCallback(direct) } }, modifier)
        // The activity handles rotation itself (configChanges): follow the display's rotation.
        val configuration = LocalConfiguration.current
        LaunchedEffect(configuration) { preview.targetRotation = display().rotation }
        BindCamera(lifecycleOwner) { provider ->
            val camera = provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview)
            direct.setSensorRotation(camera.cameraInfo.sensorRotationDegrees)
        }
    }

    @Composable
    private fun FakeView(image: () -> SyntheticImage, scrolling: Boolean, modifier: Modifier) {
        val thread = rememberGlThread("nuance-gl-fake")
        val fake = remember(thread) { FakePipeline(thread, collector, image(), scrolling) }
        AndroidView(factory = { SurfaceView(it).apply { holder.addCallback(fake) } }, modifier)
        DisposableEffect(fake) {
            fake.start()
            onDispose { fake.stop() }
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
        val thread = remember { GlThread(name, collector).also { t -> if (luts.isNotEmpty()) t.setViews(luts) } }
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

    private fun display(): Display =
        context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)

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
        const val CAMERA = "camera"
        const val RAW = "raw"
        const val MOVING = "moving"

        /** A frame comes every 33 ms; past this, the image has stopped (activity paused…). */
        private const val SAMPLE_TIMEOUT_MS = 1000L

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
