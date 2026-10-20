package io.github.j3ffx.nuance.render

import android.opengl.GLES30
import io.github.j3ffx.nuance.core.Lut3d
import java.nio.ByteBuffer

/** A [Lut3d] as an RGBA8 GL_TEXTURE_3D with linear filtering: the GPU does the trilinear lookup. */
class LutTexture(lut: Lut3d) {
    val id: Int
    val size: Int = lut.size

    init {
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        id = ids[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, id)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        for (wrap in listOf(GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_TEXTURE_WRAP_R)) {
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, wrap, GLES30.GL_CLAMP_TO_EDGE)
        }
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1)
        val bytes = lut.toRgba8()
        val buffer = ByteBuffer.allocateDirect(bytes.size).put(bytes).position(0)
        GLES30.glTexImage3D(
            GLES30.GL_TEXTURE_3D,
            0,
            GLES30.GL_RGBA8,
            size,
            size,
            size,
            0,
            GLES30.GL_RGBA,
            GLES30.GL_UNSIGNED_BYTE,
            buffer
        )
    }

    fun release() = GLES30.glDeleteTextures(1, intArrayOf(id), 0)
}
