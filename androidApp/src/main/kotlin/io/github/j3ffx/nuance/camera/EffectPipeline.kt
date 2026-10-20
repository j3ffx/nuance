package io.github.j3ffx.nuance.camera

import android.graphics.SurfaceTexture
import android.opengl.EGLSurface
import android.opengl.Matrix
import android.view.Surface
import androidx.camera.core.CameraEffect
import androidx.camera.core.SurfaceOutput
import androidx.camera.core.SurfaceProcessor
import androidx.camera.core.SurfaceRequest
import io.github.j3ffx.nuance.render.GlThread
import io.github.j3ffx.nuance.render.StatsCollector

/**
 * Pipeline A: a CameraX effect. The camera writes into our SurfaceTexture, we draw through the LUT
 * into the output surface CameraX hands us (here, the PreviewView's), and CameraX/PreviewView
 * handle rotation and scaling. The same effect could also feed photo and video capture.
 */
class EffectPipeline(private val gl: GlThread, private val stats: StatsCollector) : SurfaceProcessor {
    // Render-thread state.
    private var cameraTexture: SurfaceTexture? = null
    private var output: SurfaceOutput? = null
    private var window: EGLSurface? = null
    private val stMatrix = FloatArray(16)
    private val texMatrix = FloatArray(16)
    private val identity = FloatArray(16).also { Matrix.setIdentityM(it, 0) }

    val effect: CameraEffect = object : CameraEffect(PREVIEW, gl.executor, this, { error ->
        android.util.Log.e("Nuance", "camera effect failed", error)
    }) {}

    override fun onInputSurface(request: SurfaceRequest) {
        val size = request.resolution
        val texture = SurfaceTexture(gl.cameraTexture).apply {
            setDefaultBufferSize(size.width, size.height)
            setOnFrameAvailableListener({ onFrame(it) }, gl.handler)
        }
        cameraTexture = texture
        stats.update { it.copy(resolution = "${size.width}×${size.height}") }
        val surface = Surface(texture)
        request.provideSurface(surface, gl.executor) {
            surface.release()
            texture.release()
            if (cameraTexture === texture) cameraTexture = null
        }
    }

    override fun onOutputSurface(surfaceOutput: SurfaceOutput) {
        val surface = surfaceOutput.getSurface(gl.executor) { event ->
            if (event.eventCode == SurfaceOutput.Event.EVENT_REQUEST_CLOSE) {
                if (output === surfaceOutput) {
                    window?.let { gl.releaseWindowSurface(it) }
                    window = null
                    output = null
                }
                surfaceOutput.close()
            }
        }
        window?.let { gl.releaseWindowSurface(it) }
        window = gl.createWindowSurface(surface)
        output = surfaceOutput
    }

    private fun onFrame(texture: SurfaceTexture) {
        if (texture !== cameraTexture) return
        val arrivalNs = System.nanoTime()
        texture.updateTexImage()
        stats.onCameraFrame()
        val out = output ?: return
        val target = window ?: return
        texture.getTransformMatrix(stMatrix)
        out.updateTransformMatrix(texMatrix, stMatrix)
        gl.drawFrame(texture, target, out.size.width, out.size.height, texMatrix, identity, arrivalNs)
    }

    /** Releases what the render thread still holds; CameraX has closed its surfaces by then. */
    fun release() = gl.run {
        window?.let { gl.releaseWindowSurface(it) }
        window = null
        output?.close()
        output = null
    }
}
