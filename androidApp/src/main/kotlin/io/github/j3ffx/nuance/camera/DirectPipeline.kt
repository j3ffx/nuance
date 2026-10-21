package io.github.j3ffx.nuance.camera

import android.graphics.SurfaceTexture
import android.opengl.EGLSurface
import android.view.Surface
import android.view.SurfaceHolder
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import io.github.j3ffx.nuance.render.FrameSource
import io.github.j3ffx.nuance.render.GlThread
import io.github.j3ffx.nuance.render.StatsCollector

/**
 * Draws into a SurfaceView from the render thread. The window surface follows the SurfaceView's
 * lifecycle; [drawFrame] does nothing while there is none.
 */
abstract class SurfaceViewPipeline(protected val gl: GlThread, protected val stats: StatsCollector) :
    SurfaceHolder.Callback {
    // Render-thread state.
    private var window: EGLSurface? = null
    private var windowWidth = 0
    private var windowHeight = 0

    protected fun drawFrame(source: FrameSource, arrivalNs: Long) {
        val target = window ?: return
        gl.drawViews(target, windowWidth, windowHeight, source, arrivalNs)
    }

    override fun surfaceCreated(holder: SurfaceHolder) = Unit

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        gl.run {
            if (window == null) window = gl.createWindowSurface(holder.surface)
            windowWidth = width
            windowHeight = height
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        // Must finish before returning: the surface is gone afterwards.
        gl.run {
            window?.let { gl.releaseWindowSurface(it) }
            window = null
        }
    }
}

/**
 * The camera pipeline: CameraX writes into our own SurfaceTexture, and the render thread draws it
 * through the views' LUTs straight into a SurfaceView, as soon as each frame arrives. Chosen in J1
 * over a CameraX effect (same latency; we own the screen layout of the Comparer mode).
 */
class DirectPipeline(gl: GlThread, stats: StatsCollector) :
    SurfaceViewPipeline(gl, stats),
    Preview.SurfaceProvider {
    // Render-thread state.
    private var cameraTexture: SurfaceTexture? = null
    private var bufferWidth = 0
    private var bufferHeight = 0
    private var rotationDegrees = 0
    private var cameraTransform = false
    private var sensorRotationDegrees = 0
    private var mirroring = false
    private val texMatrix = FloatArray(16)

    /** The camera's sensor orientation, once it is bound (CameraInfo.getSensorRotationDegrees). */
    fun setSensorRotation(degrees: Int) = gl.post { sensorRotationDegrees = degrees }

    override fun onSurfaceRequested(request: SurfaceRequest) {
        gl.post {
            val size = request.resolution
            val texture = SurfaceTexture(gl.cameraTexture).apply {
                setDefaultBufferSize(size.width, size.height)
                setOnFrameAvailableListener({ onFrame(it) }, gl.handler)
            }
            cameraTexture = texture
            bufferWidth = size.width
            bufferHeight = size.height
            stats.update { it.copy(resolution = "${size.width}×${size.height}") }
            request.setTransformationInfoListener(gl.executor) { info ->
                rotationDegrees = info.rotationDegrees
                cameraTransform = info.hasCameraTransform()
                mirroring = info.isMirroring
            }
            val surface = Surface(texture)
            request.provideSurface(surface, gl.executor) {
                surface.release()
                texture.release()
                if (cameraTexture === texture) cameraTexture = null
            }
        }
    }

    private fun onFrame(texture: SurfaceTexture) {
        // A frame can still arrive after the render thread was torn down (the activity recreated by
        // a language change, a source switch): its context is gone, so drop the frame.
        if (texture !== cameraTexture || !gl.alive) return
        val arrivalNs = System.nanoTime()
        texture.updateTexImage()
        stats.onCameraFrame()
        texture.getTransformMatrix(texMatrix)
        // rotationDegrees is the whole turn from the sensor to the screen. With a camera transform
        // (most phones), the camera has already applied the sensor orientation through the
        // SurfaceTexture matrix: only the rest is ours to apply.
        val applied = if (cameraTransform) sensorRotationDegrees else 0
        val extra = Math.floorMod(rotationDegrees - applied, 360)
        // Size of the image once fully upright: a quarter turn in total swaps the buffer's sides.
        val quarterTurn = rotationDegrees % 180 != 0
        val aspect = if (quarterTurn) bufferHeight.toFloat() / bufferWidth else bufferWidth.toFloat() / bufferHeight
        drawFrame(
            FrameSource(gl.cameraTexture, true, texMatrix, aspect, extra, mirroring, texture.timestamp),
            arrivalNs
        )
    }
}
