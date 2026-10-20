package io.github.j3ffx.nuance.render

import io.github.j3ffx.nuance.core.Lut3d
import kotlinx.coroutines.flow.StateFlow

/**
 * The platform's camera and GPU side: it shows the camera feed through a 3D LUT. Everything
 * else (which LUT, the UI, the settings) is common code.
 */
interface CameraRenderer {
    /** Live performance figures, for the dev overlay. */
    val stats: StateFlow<RenderStats>

    /** Ways to build the pipeline, compared in the dev variant; the first one is the default. */
    val pipelines: List<String>

    /** Switches to one of [pipelines]; the camera restarts. */
    fun selectPipeline(name: String)

    /**
     * Uses [lut] from the next frame on. Cheap: the table is uploaded once, off the UI thread.
     * [bakeMs] is how long baking it took, for the dev overlay.
     */
    fun setLut(lut: Lut3d, bakeMs: Float? = null)
}

/** What the dev overlay shows. Null when not measured (yet, or not supported by the GPU). */
data class RenderStats(
    val pipeline: String = "",
    /** Frames drawn per second. */
    val fps: Float = 0f,
    /** Camera frames received per second. */
    val cameraFps: Float = 0f,
    /** The camera stream's size, e.g. "1920×1080". */
    val resolution: String = "",
    /** GPU time of one frame's draw, in ms. */
    val gpuMs: Float? = null,
    /** From the sensor timestamp (start of exposure) to the frame reaching the app, in ms (median). */
    val captureToArrivalMs: Float? = null,
    /** From the frame reaching the app to its swap: what Nuance adds, in ms (median). */
    val arrivalToSwapMs: Float? = null,
    /** From the sensor timestamp to the buffer swap, in ms (median). */
    val captureToSwapMs: Float? = null,
    /** Display refresh period, in ms: a frame is shown 1 to 2 periods after its swap. */
    val vsyncMs: Float? = null,
    /** Time to bake the last LUT, in ms. */
    val lutBakeMs: Float? = null,
    /** "none", "light", "moderate", "severe"… from the platform's thermal status. */
    val thermal: String = ""
)
