package io.github.j3ffx.nuance.camera

import android.opengl.GLES30
import android.opengl.Matrix
import android.os.SystemClock
import io.github.j3ffx.nuance.core.charts.SyntheticImage
import io.github.j3ffx.nuance.render.FrameSource
import io.github.j3ffx.nuance.render.GlThread
import io.github.j3ffx.nuance.render.StatsCollector
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Dev variant: a synthetic source instead of the camera, for reproducible tests and the emulator.
 * Shows [image] at 30 frames a second through the same views and LUTs as the camera; when
 * [scrolling], the image slides sideways (and wraps), standing in for a video.
 */
class FakePipeline(
    gl: GlThread,
    stats: StatsCollector,
    private val image: SyntheticImage,
    private val scrolling: Boolean
) : SurfaceViewPipeline(gl, stats) {
    private var texture = 0
    private var running = false
    private var frame = 0
    private var nextFrameMs = 0L
    private val texMatrix = FloatArray(16)

    fun start() = gl.post {
        gl.makeIdleCurrent()
        texture = upload(image)
        stats.update { it.copy(resolution = "${image.width}×${image.height} (synthetic)") }
        running = true
        nextFrameMs = SystemClock.uptimeMillis()
        tick()
    }

    fun stop() = gl.run {
        running = false
        if (texture != 0) GLES30.glDeleteTextures(1, intArrayOf(texture), 0)
        texture = 0
    }

    private fun tick() {
        if (!running) return
        val arrivalNs = System.nanoTime()
        stats.onCameraFrame()
        // Rows were uploaded top first, so v runs downwards: flip it; then slide u when scrolling.
        Matrix.setIdentityM(texMatrix, 0)
        if (scrolling) Matrix.translateM(texMatrix, 0, (frame % 300) / 300f, 0f, 0f)
        Matrix.translateM(texMatrix, 0, 0f, 1f, 0f)
        Matrix.scaleM(texMatrix, 0, 1f, -1f, 1f)
        frame++
        drawFrame(FrameSource(texture, false, texMatrix, image.width.toFloat() / image.height), arrivalNs)
        // Fixed rate: the next frame is due one period after this one was due, whatever drawing took.
        nextFrameMs += FRAME_MS
        gl.handler.postAtTime({ tick() }, nextFrameMs)
    }

    private fun upload(image: SyntheticImage): Int {
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, ids[0])
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_REPEAT)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        val bytes = ByteBuffer.allocateDirect(image.pixels.size * 4).order(ByteOrder.nativeOrder())
        for (p in image.pixels) {
            bytes.put((p shr 16).toByte()).put((p shr 8).toByte()).put(p.toByte()).put(-1)
        }
        bytes.position(0)
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1)
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D,
            0,
            GLES30.GL_RGBA8,
            image.width,
            image.height,
            0,
            GLES30.GL_RGBA,
            GLES30.GL_UNSIGNED_BYTE,
            bytes
        )
        return ids[0]
    }

    private companion object {
        /** 30 frames a second, the camera's rate. */
        const val FRAME_MS = 1000L / 30
    }
}
