package io.github.j3ffx.nuance.render

import io.github.j3ffx.nuance.core.Lut3d
import io.github.j3ffx.nuance.core.Rgb
import kotlinx.coroutines.flow.StateFlow

/**
 * The platform's camera and GPU side: it shows one camera image in 1, 2 or 4 views, each through
 * its own 3D LUT, laid out by [ViewLayout]. Everything else (which LUTs, the UI, the settings) is
 * common code.
 */
interface CameraRenderer {
    /** Live performance figures, for the dev overlay. */
    val stats: StateFlow<RenderStats>

    /**
     * Where the image comes from: the camera first (the default), then, in the dev variant, the raw
     * preview without filter and synthetic sources (charts, a moving pattern).
     */
    val sources: List<String>

    /** Switches to one of [sources]. */
    fun selectSource(name: String)

    /**
     * Shows [luts.size] views (1, 2 or 4), each through its LUT, from the next frame on. Cheap: the
     * tables are uploaded once, on the render thread. [bakeMs] is how long baking them took.
     */
    fun setViews(luts: List<Lut3d>, bakeMs: Float? = null)

    /**
     * The pipette: the colour of the *original* image (before any LUT) around the point ([x], [y]),
     * as fractions of the image area from its top left, averaged over a small area. In a
     * multi-view mode, the point is read in the view it falls in. Null when there is no image.
     */
    suspend fun sampleColor(x: Float, y: Float): Rgb?

    /** What the camera can do and is doing, for the controls. */
    val camera: StateFlow<CameraStatus>

    /** Optical or digital zoom of the live camera, within [CameraStatus.minZoom]..[CameraStatus.maxZoom]. */
    fun setZoom(ratio: Float)

    fun setTorch(on: Boolean)

    /** Back camera ↔ front camera. */
    fun switchFacing()

    /**
     * Freezes the image: the current frame is kept (on the GPU) and shown instead of the live one;
     * the views keep their LUTs, so every mode, the pipette and [setImageZoom] work on it.
     */
    fun setFrozen(frozen: Boolean)

    /**
     * Zoom into the frozen image: [scale] ≥ 1, centred on ([cx], [cy]), fractions of the image from
     * its top left. The renderer keeps the image filling the view.
     */
    fun setImageZoom(scale: Float, cx: Float, cy: Float)

    /**
     * Saves what the views show, without the controls (and, if [withOriginal], the original
     * alone), as photos in the gallery's "Nuance" album; [name] goes into the file names.
     */
    suspend fun takePhoto(name: String, withOriginal: Boolean): Boolean

    /** Films what the views show into the gallery, until [stopVideo]; false if it could not start. */
    suspend fun startVideo(name: String): Boolean

    /** Stops filming and keeps the video; false if nothing usable was recorded. */
    suspend fun stopVideo(): Boolean
}

/** The camera's state. Zoom ratios below 1 are the ultra wide lens, where the phone exposes it. */
data class CameraStatus(
    val minZoom: Float = 1f,
    val maxZoom: Float = 1f,
    val zoom: Float = 1f,
    val hasTorch: Boolean = false,
    val torch: Boolean = false,
    val front: Boolean = false,
    val canSwitchFacing: Boolean = false,
    val frozen: Boolean = false,
    val recording: Boolean = false
)

/** What the dev overlay shows. Null when not measured (yet, or not supported by the GPU). */
data class RenderStats(
    val source: String = "",
    /** Frames drawn per second. */
    val fps: Float = 0f,
    /** Camera frames received per second. */
    val cameraFps: Float = 0f,
    /** The camera stream's size, e.g. "1920×1080". */
    val resolution: String = "",
    /** Views drawn per frame. */
    val views: Int = 1,
    /** GPU time of one frame's draw, all views, in ms. */
    val gpuMs: Float? = null,
    /** From the sensor timestamp (start of exposure) to the frame reaching the app, in ms (median). */
    val captureToArrivalMs: Float? = null,
    /** From the frame reaching the app to its swap: what Nuance adds, in ms (median). */
    val arrivalToSwapMs: Float? = null,
    /** From the sensor timestamp to the buffer swap, in ms (median). */
    val captureToSwapMs: Float? = null,
    /** Display refresh period, in ms: a frame is shown 1 to 2 periods after its swap. */
    val vsyncMs: Float? = null,
    /** Time to bake the last LUTs, in ms. */
    val lutBakeMs: Float? = null,
    /** "none", "light", "moderate", "severe"… from the platform's thermal status. */
    val thermal: String = ""
)

/** A view's place on screen, as fractions of the whole, origin at the top left. */
data class ViewRect(val x: Float, val y: Float, val width: Float, val height: Float)

/** Where the 1, 2 or 4 views go: shared by the GPU side and the labels drawn over them. */
object ViewLayout {
    fun rects(count: Int, landscape: Boolean): List<ViewRect> = when (count) {
        1 -> listOf(ViewRect(0f, 0f, 1f, 1f))

        2 -> if (landscape) {
            listOf(ViewRect(0f, 0f, 0.5f, 1f), ViewRect(0.5f, 0f, 0.5f, 1f))
        } else {
            listOf(ViewRect(0f, 0f, 1f, 0.5f), ViewRect(0f, 0.5f, 1f, 0.5f))
        }

        4 -> listOf(
            ViewRect(0f, 0f, 0.5f, 0.5f),
            ViewRect(0.5f, 0f, 0.5f, 0.5f),
            ViewRect(0f, 0.5f, 0.5f, 0.5f),
            ViewRect(0.5f, 0.5f, 0.5f, 0.5f)
        )

        else -> throw IllegalArgumentException("1, 2 or 4 views, not $count")
    }
}
