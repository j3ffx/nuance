package io.github.j3ffx.nuance.render

import android.opengl.EGLSurface
import android.opengl.GLES30
import android.opengl.Matrix
import android.os.Handler
import android.os.HandlerThread
import io.github.j3ffx.nuance.core.Lut3d
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicReference

/**
 * One frame's source image, as the render thread draws it.
 * - [texture]: an external (camera) or 2D texture, per [external];
 * - [texMatrix]: its texture transform (SurfaceTexture's, or a flip for 2D images);
 * - [contentAspect]: width / height of the image once upright;
 * - [extraRotation]: clockwise quarter turns, in degrees, still to apply on screen;
 * - [captureNs]: the camera's timestamp, for latency figures (null for synthetic sources).
 */
class FrameSource(
    val texture: Int,
    val external: Boolean,
    val texMatrix: FloatArray,
    val contentAspect: Float,
    val extraRotation: Int = 0,
    val mirror: Boolean = false,
    val captureNs: Long? = null
)

/**
 * The render thread: owns the EGL context, the camera texture, the shaders and the views' LUT
 * textures. Every GL call of a pipeline goes through [post] or [run]; nothing here blocks on the
 * camera.
 */
class GlThread(name: String, private val stats: StatsCollector) {
    private val thread = HandlerThread(name).apply { start() }
    val handler = Handler(thread.looper)
    val executor = Executor { handler.post(it) }

    private lateinit var egl: EglCore
    private lateinit var idleSurface: EGLSurface
    private lateinit var cameraProgram: LutProgram
    private lateinit var imageProgram: LutProgram
    private lateinit var timer: GpuTimer
    var cameraTexture = 0
        private set
    private var views: List<LutTexture> = emptyList()
    private val pendingViews = AtomicReference<List<Lut3d>?>()
    private val mvp = FloatArray(16)
    private val rotation = FloatArray(16)
    private val scale = FloatArray(16)

    init {
        run {
            egl = EglCore()
            idleSurface = egl.createPbufferSurface()
            egl.makeCurrent(idleSurface)
            cameraProgram = LutProgram(external = true)
            imageProgram = LutProgram(external = false)
            timer = GpuTimer()
            cameraTexture = LutProgram.createCameraTexture()
        }
    }

    fun post(block: () -> Unit) {
        handler.post(block)
    }

    fun postDelayed(delayMs: Long, block: () -> Unit) {
        handler.postDelayed(block, delayMs)
    }

    /** Runs [block] on the render thread and waits for it (surface lifecycle callbacks need this). */
    fun <T> run(block: () -> T): T {
        if (Thread.currentThread() == thread) return block()
        val result = AtomicReference<Result<T>>()
        val done = CountDownLatch(1)
        handler.post {
            result.set(runCatching(block))
            done.countDown()
        }
        done.await()
        return result.get().getOrThrow()
    }

    /** Takes [luts] (one per view) from the next frame on; the upload happens on the render thread. */
    fun setViews(luts: List<Lut3d>) {
        pendingViews.set(luts)
    }

    fun createWindowSurface(surface: android.view.Surface): EGLSurface = run { egl.createWindowSurface(surface) }

    fun releaseWindowSurface(surface: EGLSurface) = run {
        egl.makeCurrent(idleSurface)
        egl.releaseSurface(surface)
    }

    /** Makes the context current, for a pipeline's own GL work (uploading a synthetic image). */
    fun makeIdleCurrent() = egl.makeCurrent(idleSurface)

    /**
     * Draws [source] into [target] ([width]×[height]) once per view, each in its own area
     * ([ViewLayout]) and through its own LUT, then swaps. Called on the render thread.
     */
    fun drawViews(target: EGLSurface, width: Int, height: Int, source: FrameSource, arrivalNs: Long) {
        pendingViews.getAndSet(null)?.let { luts ->
            views.forEach { it.release() }
            views = luts.map { LutTexture(it) }
            stats.update { it.copy(views = luts.size) }
        }
        if (views.isEmpty()) return // nothing to draw with until the first LUTs are baked
        egl.makeCurrent(target)
        GLES30.glViewport(0, 0, width, height)
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        val program = if (source.external) cameraProgram else imageProgram
        val rects = ViewLayout.rects(views.size, landscape = width > height)
        timer.begin()
        for ((lut, rect) in views.zip(rects)) {
            val x = (rect.x * width).toInt()
            val w = (rect.width * width).toInt()
            val h = (rect.height * height).toInt()
            // GL's viewport origin is at the bottom left; ViewRect's at the top left.
            val y = height - (rect.y * height).toInt() - h
            GLES30.glViewport(x, y, w, h)
            fillMatrix(source, w.toFloat() / h)
            program.draw(source.texture, source.texMatrix, mvp, lut)
        }
        timer.end()
        // Present as soon as possible: the frame's capture time, on the display's (monotonic) clock.
        egl.swap(target, source.captureNs?.let { stats.toMonotonic(it) })
        stats.onDraw(source.captureNs, arrivalNs, timer.lastMs)
    }

    /** Rotates the image upright, then scales it to fill the view, cropping the overflow (centred). */
    private fun fillMatrix(source: FrameSource, viewAspect: Float) {
        val content = source.contentAspect
        val sx = if (content > viewAspect) content / viewAspect else 1f
        val sy = if (content > viewAspect) 1f else viewAspect / content
        // Clockwise on screen; GL's z rotation is counter-clockwise.
        Matrix.setRotateM(rotation, 0, -source.extraRotation.toFloat(), 0f, 0f, 1f)
        Matrix.setIdentityM(scale, 0)
        Matrix.scaleM(scale, 0, if (source.mirror) -sx else sx, sy, 1f)
        Matrix.multiplyMM(mvp, 0, scale, 0, rotation, 0)
    }

    fun quit() {
        run {
            views.forEach { it.release() }
            timer.release()
            cameraProgram.release()
            imageProgram.release()
            GLES30.glDeleteTextures(1, intArrayOf(cameraTexture), 0)
            egl.releaseSurface(idleSurface)
            egl.release()
        }
        thread.quitSafely()
    }
}
