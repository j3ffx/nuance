package io.github.j3ffx.nuance.camera

import android.graphics.SurfaceTexture
import android.opengl.EGLSurface
import android.opengl.Matrix
import android.view.Surface
import android.view.SurfaceHolder
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import io.github.j3ffx.nuance.render.GlThread
import io.github.j3ffx.nuance.render.StatsCollector

/**
 * Pipeline B: CameraX writes into our own SurfaceTexture, and the render thread draws it through
 * the LUT straight into a SurfaceView, as soon as each camera frame arrives. We own rotation and
 * scaling, so the multi-view layouts of the Comparer mode are plain viewports in screen space.
 */
class DirectPipeline(private val gl: GlThread, private val stats: StatsCollector) :
    SurfaceHolder.Callback,
    Preview.SurfaceProvider {
    // Render-thread state.
    private var cameraTexture: SurfaceTexture? = null
    private var bufferWidth = 0
    private var bufferHeight = 0
    private var rotationDegrees = 0
    private var cameraTransform = false
    private var sensorRotationDegrees = 0
    private var mirroring = false
    private var window: EGLSurface? = null
    private var windowWidth = 0
    private var windowHeight = 0
    private val texMatrix = FloatArray(16)
    private val mvp = FloatArray(16)

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
        if (texture !== cameraTexture) return
        val arrivalNs = System.nanoTime()
        texture.updateTexImage()
        stats.onCameraFrame()
        val target = window ?: return
        texture.getTransformMatrix(texMatrix)
        fillMatrix()
        gl.drawFrame(texture, target, windowWidth, windowHeight, texMatrix, mvp, arrivalNs)
    }

    /**
     * Rotates the image upright, then scales it to fill the window, cropping the overflow (centred).
     *
     * [rotationDegrees] is the whole turn from the sensor to the screen. With a camera transform
     * (most phones), the camera has already applied the sensor orientation: the SurfaceTexture
     * matrix carries it, and only the rest is ours to apply.
     */
    private fun fillMatrix() {
        val applied = if (cameraTransform) sensorRotationDegrees else 0
        val extra = Math.floorMod(rotationDegrees - applied, 360)
        // Size of the image once fully upright: a quarter turn in total swaps the buffer's sides.
        val quarterTurn = rotationDegrees % 180 != 0
        val contentAspect =
            if (quarterTurn) bufferHeight.toFloat() / bufferWidth else bufferWidth.toFloat() / bufferHeight
        val windowAspect = windowWidth.toFloat() / windowHeight
        val sx = if (contentAspect > windowAspect) contentAspect / windowAspect else 1f
        val sy = if (contentAspect > windowAspect) 1f else windowAspect / contentAspect
        val rotation = FloatArray(16)
        // Clockwise on screen; GL's z rotation is counter-clockwise.
        Matrix.setRotateM(rotation, 0, -extra.toFloat(), 0f, 0f, 1f)
        val scale = FloatArray(16)
        Matrix.setIdentityM(scale, 0)
        Matrix.scaleM(scale, 0, if (mirroring) -sx else sx, sy, 1f)
        Matrix.multiplyMM(mvp, 0, scale, 0, rotation, 0)
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
