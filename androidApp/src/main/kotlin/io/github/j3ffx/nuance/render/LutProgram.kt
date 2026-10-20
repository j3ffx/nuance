package io.github.j3ffx.nuance.render

import android.opengl.GLES11Ext
import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * The whole filter: read the camera pixel, look it up in the 3D LUT (trilinear, done by the
 * texture unit), write it. All colour logic lives in :core; changing an algorithm never touches
 * this shader. [external] reads a camera (external OES) texture; otherwise an ordinary 2D texture,
 * for the dev variant's synthetic sources.
 */
class LutProgram(private val external: Boolean) {
    private val program = link(VERTEX, fragment(external))
    private val aPosition = GLES30.glGetAttribLocation(program, "aPosition")
    private val aTexCoord = GLES30.glGetAttribLocation(program, "aTexCoord")
    private val uMvp = GLES30.glGetUniformLocation(program, "uMvp")
    private val uTexMatrix = GLES30.glGetUniformLocation(program, "uTexMatrix")
    private val uCamera = GLES30.glGetUniformLocation(program, "uCamera")
    private val uLut = GLES30.glGetUniformLocation(program, "uLut")
    private val uLutScale = GLES30.glGetUniformLocation(program, "uLutScale")
    private val uLutOffset = GLES30.glGetUniformLocation(program, "uLutOffset")

    // x, y, u, v: a full-viewport triangle strip.
    private val quad: FloatBuffer = floats(
        -1f, -1f, 0f, 0f,
        1f, -1f, 1f, 0f,
        -1f, 1f, 0f, 1f,
        1f, 1f, 1f, 1f
    )

    /** Draws the source texture [sourceTexture] through [lut] into the current viewport. */
    fun draw(sourceTexture: Int, texMatrix: FloatArray, mvp: FloatArray, lut: LutTexture) {
        GLES30.glUseProgram(program)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(if (external) GLES11Ext.GL_TEXTURE_EXTERNAL_OES else GLES30.GL_TEXTURE_2D, sourceTexture)
        GLES30.glUniform1i(uCamera, 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, lut.id)
        GLES30.glUniform1i(uLut, 1)
        // Texel centres: input 0 must hit the first entry's centre, input 1 the last one's.
        GLES30.glUniform1f(uLutScale, (lut.size - 1f) / lut.size)
        GLES30.glUniform1f(uLutOffset, 0.5f / lut.size)
        GLES30.glUniformMatrix4fv(uMvp, 1, false, mvp, 0)
        GLES30.glUniformMatrix4fv(uTexMatrix, 1, false, texMatrix, 0)
        quad.position(0)
        GLES30.glVertexAttribPointer(aPosition, 2, GLES30.GL_FLOAT, false, 16, quad)
        GLES30.glEnableVertexAttribArray(aPosition)
        quad.position(2)
        GLES30.glVertexAttribPointer(aTexCoord, 2, GLES30.GL_FLOAT, false, 16, quad)
        GLES30.glEnableVertexAttribArray(aTexCoord)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
    }

    fun release() = GLES30.glDeleteProgram(program)

    companion object {
        private const val VERTEX = """#version 300 es
in vec4 aPosition;
in vec4 aTexCoord;
uniform mat4 uMvp;
uniform mat4 uTexMatrix;
out vec2 vTexCoord;
void main() {
    gl_Position = uMvp * aPosition;
    vTexCoord = (uTexMatrix * aTexCoord).xy;
}
"""

        private fun fragment(external: Boolean) = """#version 300 es
${if (external) "#extension GL_OES_EGL_image_external_essl3 : require" else ""}
precision mediump float;
uniform ${if (external) "samplerExternalOES" else "sampler2D"} uCamera;
uniform mediump sampler3D uLut;
uniform float uLutScale;
uniform float uLutOffset;
in vec2 vTexCoord;
out vec4 fragColor;
void main() {
    vec3 rgb = texture(uCamera, vTexCoord).rgb;
    fragColor = vec4(texture(uLut, rgb * uLutScale + uLutOffset).rgb, 1.0);
}
"""

        /** An external (camera) texture, for a SurfaceTexture to fill. */
        fun createCameraTexture(): Int {
            val ids = IntArray(1)
            GLES30.glGenTextures(1, ids, 0)
            val target = GLES11Ext.GL_TEXTURE_EXTERNAL_OES
            GLES30.glBindTexture(target, ids[0])
            GLES30.glTexParameteri(target, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(target, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(target, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(target, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
            return ids[0]
        }

        private fun compile(type: Int, source: String): Int {
            val shader = GLES30.glCreateShader(type)
            GLES30.glShaderSource(shader, source)
            GLES30.glCompileShader(shader)
            val ok = IntArray(1)
            GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, ok, 0)
            check(ok[0] != 0) { "shader compile failed: ${GLES30.glGetShaderInfoLog(shader)}" }
            return shader
        }

        private fun link(vertex: String, fragment: String): Int {
            val program = GLES30.glCreateProgram()
            GLES30.glAttachShader(program, compile(GLES30.GL_VERTEX_SHADER, vertex))
            GLES30.glAttachShader(program, compile(GLES30.GL_FRAGMENT_SHADER, fragment))
            GLES30.glLinkProgram(program)
            val ok = IntArray(1)
            GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, ok, 0)
            check(ok[0] != 0) { "program link failed: ${GLES30.glGetProgramInfoLog(program)}" }
            return program
        }

        private fun floats(vararg values: Float): FloatBuffer = ByteBuffer.allocateDirect(values.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { put(values).position(0) }
    }
}
