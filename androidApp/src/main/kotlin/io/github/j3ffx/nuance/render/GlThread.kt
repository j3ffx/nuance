package io.github.j3ffx.nuance.render

import android.graphics.SurfaceTexture
import android.opengl.EGLSurface
import android.opengl.GLES30
import android.os.Handler
import android.os.HandlerThread
import io.github.j3ffx.nuance.core.Lut3d
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicReference

/**
 * The render thread: owns the EGL context, the camera texture, the shader and the LUT texture.
 * Every GL call of a pipeline goes through [post] or [run]; nothing here blocks on the camera.
 */
class GlThread(name: String, private val stats: StatsCollector) {
    private val thread = HandlerThread(name).apply { start() }
    val handler = Handler(thread.looper)
    val executor = Executor { handler.post(it) }

    private lateinit var egl: EglCore
    private lateinit var idleSurface: EGLSurface
    private lateinit var program: LutProgram
    private lateinit var timer: GpuTimer
    var cameraTexture = 0
        private set
    private var lutTexture: LutTexture? = null
    private val pendingLut = AtomicReference<Lut3d?>()

    init {
        run {
            egl = EglCore()
            idleSurface = egl.createPbufferSurface()
            egl.makeCurrent(idleSurface)
            program = LutProgram()
            timer = GpuTimer()
            cameraTexture = LutProgram.createCameraTexture()
        }
    }

    fun post(block: () -> Unit) {
        handler.post(block)
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

    /** Takes [lut] from the next frame on; the upload happens on the render thread. */
    fun setLut(lut: Lut3d) {
        pendingLut.set(lut)
    }

    fun createWindowSurface(surface: android.view.Surface): EGLSurface = run { egl.createWindowSurface(surface) }

    fun releaseWindowSurface(surface: EGLSurface) = run {
        egl.makeCurrent(idleSurface)
        egl.releaseSurface(surface)
    }

    /**
     * Draws the latest camera image of [texture] into [target] ([width]×[height]) and swaps.
     * Called on the render thread, right after `updateTexImage`.
     */
    fun drawFrame(
        texture: SurfaceTexture,
        target: EGLSurface,
        width: Int,
        height: Int,
        texMatrix: FloatArray,
        mvp: FloatArray,
        arrivalNs: Long
    ) {
        pendingLut.getAndSet(null)?.let { lut ->
            lutTexture?.release()
            lutTexture = LutTexture(lut)
        }
        val lut = lutTexture ?: return // nothing to draw with until the first LUT is baked
        egl.makeCurrent(target)
        GLES30.glViewport(0, 0, width, height)
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        timer.begin()
        program.draw(cameraTexture, texMatrix, mvp, lut)
        timer.end()
        // Present as soon as possible: the frame's capture time, on the display's (monotonic) clock.
        egl.swap(target, stats.toMonotonic(texture.timestamp))
        stats.onDraw(texture.timestamp, arrivalNs, timer.lastMs)
    }

    fun quit() {
        run {
            lutTexture?.release()
            timer.release()
            program.release()
            GLES30.glDeleteTextures(1, intArrayOf(cameraTexture), 0)
            egl.releaseSurface(idleSurface)
            egl.release()
        }
        thread.quitSafely()
    }
}
