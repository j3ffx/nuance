package io.github.j3ffx.nuance.render

import android.opengl.EGLSurface
import android.opengl.GLES30
import android.opengl.Matrix
import android.os.Handler
import android.os.HandlerThread
import io.github.j3ffx.nuance.core.Lut3d
import io.github.j3ffx.nuance.core.modes.ColorFunction
import java.nio.ByteBuffer
import java.nio.ByteOrder
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
    private lateinit var sampler: Sampler
    var cameraTexture = 0
        private set

    /**
     * False once [quit] has torn the context down. Messages already queued on the thread (a
     * SurfaceTexture's frame-available callback, say) still run afterwards: they must check this
     * before any GL call.
     */
    @Volatile
    var alive = true
        private set
    private var views: List<LutTexture> = emptyList()
    private val pendingViews = AtomicReference<List<Lut3d>?>()
    private val pendingSample = AtomicReference<SampleRequest?>()
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
            sampler = Sampler()
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

    /**
     * The pipette: on the next frame, reads the original image (identity LUT) in a small square
     * around ([x], [y]), fractions of the surface from its top left, and gives [onResult] its
     * pixels as 0xAARRGGBB, on the render thread. A newer request replaces an unanswered one, which
     * gets null.
     */
    fun requestSample(x: Float, y: Float, onResult: (IntArray?) -> Unit) {
        pendingSample.getAndSet(SampleRequest(x, y, onResult))?.onResult?.invoke(null)
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
        pendingSample.getAndSet(null)?.let { request ->
            request.onResult(sample(request, source, rects, width, height, program))
            egl.makeCurrent(target)
        }
        // Present as soon as possible: the frame's capture time, on the display's (monotonic) clock.
        egl.swap(target, source.captureNs?.let { stats.toMonotonic(it) })
        stats.onDraw(source.captureNs, arrivalNs, timer.lastMs)
    }

    /**
     * Draws the view under the point once more, without its LUT, into a tiny framebuffer placed
     * so that it holds the [Sampler.SIZE]² pixels around the point, and reads them back. Only on
     * request: the render loop itself never copies a frame to the CPU.
     */
    private fun sample(
        request: SampleRequest,
        source: FrameSource,
        rects: List<ViewRect>,
        width: Int,
        height: Int,
        program: LutProgram
    ): IntArray {
        val rect = rects.firstOrNull { request.x in it.x..it.x + it.width && request.y in it.y..it.y + it.height }
            ?: rects.first()
        val w = (rect.width * width).toInt()
        val h = (rect.height * height).toInt()
        val px = ((request.x - rect.x) / rect.width * w).toInt()
        val py = h - ((request.y - rect.y) / rect.height * h).toInt()
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, sampler.framebuffer)
        GLES30.glViewport(Sampler.SIZE / 2 - px, Sampler.SIZE / 2 - py, w, h)
        fillMatrix(source, w.toFloat() / h)
        program.draw(source.texture, source.texMatrix, mvp, sampler.identity)
        val pixels = sampler.read()
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        return pixels
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
            alive = false
            pendingSample.getAndSet(null)?.onResult?.invoke(null)
            views.forEach { it.release() }
            sampler.release()
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

private class SampleRequest(val x: Float, val y: Float, val onResult: (IntArray?) -> Unit)

/** The pipette's tiny framebuffer, and the identity LUT that shows the original image. */
private class Sampler {
    val identity = LutTexture(Lut3d.bakeLinear(2, ColorFunction.identity))
    private val texture: Int
    val framebuffer: Int
    private val buffer = ByteBuffer.allocateDirect(SIZE * SIZE * 4).order(ByteOrder.nativeOrder())

    init {
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        texture = ids[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture)
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D,
            0,
            GLES30.GL_RGBA8,
            SIZE,
            SIZE,
            0,
            GLES30.GL_RGBA,
            GLES30.GL_UNSIGNED_BYTE,
            null
        )
        GLES30.glGenFramebuffers(1, ids, 0)
        framebuffer = ids[0]
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, framebuffer)
        GLES30.glFramebufferTexture2D(
            GLES30.GL_FRAMEBUFFER,
            GLES30.GL_COLOR_ATTACHMENT0,
            GLES30.GL_TEXTURE_2D,
            texture,
            0
        )
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
    }

    /** The framebuffer's pixels as 0xAARRGGBB. */
    fun read(): IntArray {
        buffer.position(0)
        GLES30.glReadPixels(0, 0, SIZE, SIZE, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buffer)
        return IntArray(SIZE * SIZE) { i ->
            val r = buffer.get(i * 4).toInt() and 0xFF
            val g = buffer.get(i * 4 + 1).toInt() and 0xFF
            val b = buffer.get(i * 4 + 2).toInt() and 0xFF
            (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
    }

    fun release() {
        identity.release()
        GLES30.glDeleteFramebuffers(1, intArrayOf(framebuffer), 0)
        GLES30.glDeleteTextures(1, intArrayOf(texture), 0)
    }

    companion object {
        /** Side of the averaged square, in screen pixels: about 2 mm on the test phone. */
        const val SIZE = 20
    }
}
