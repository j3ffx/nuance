package io.github.j3ffx.nuance.core

import io.github.j3ffx.nuance.core.color.Srgb
import io.github.j3ffx.nuance.core.color.Vec3

/**
 * A 3D colour lookup table: [size]³ entries, each the RGB output for an input on a regular grid
 * over the sRGB cube. Entry (r, g, b) is at index `(b * size + g) * size + r`, red varying fastest,
 * which is the layout of a GL_TEXTURE_3D (x = red, y = green, z = blue).
 *
 * Every processing mode is a function `(Rgb) -> Rgb`, baked once here when a setting changes;
 * the platform shader then only samples this table, with trilinear interpolation.
 */
class Lut3d(val size: Int, val data: FloatArray) {
    init {
        require(size >= 2) { "a LUT needs at least 2 entries per axis, got $size" }
        require(data.size == size * size * size * 3) { "expected ${size * size * size * 3} floats, got ${data.size}" }
    }

    /** The table entry at grid coordinates (r, g, b), each in 0 until [size]. */
    fun entry(r: Int, g: Int, b: Int): Rgb {
        val i = ((b * size + g) * size + r) * 3
        return Rgb(data[i], data[i + 1], data[i + 2])
    }

    /** What the GPU computes: trilinear interpolation of the table at [rgb]. */
    fun sample(rgb: Rgb): Rgb {
        val max = size - 1
        fun axis(v: Float): Pair<Int, Float> {
            val x = v.coerceIn(0f, 1f) * max
            val i = x.toInt().coerceAtMost(max - 1)
            return i to (x - i)
        }
        val (r0, fr) = axis(rgb.r)
        val (g0, fg) = axis(rgb.g)
        val (b0, fb) = axis(rgb.b)
        val out = FloatArray(3)
        for (c in 0..2) {
            fun at(dr: Int, dg: Int, db: Int) = data[(((b0 + db) * size + (g0 + dg)) * size + (r0 + dr)) * 3 + c]
            val c00 = lerp(at(0, 0, 0), at(1, 0, 0), fr)
            val c10 = lerp(at(0, 1, 0), at(1, 1, 0), fr)
            val c01 = lerp(at(0, 0, 1), at(1, 0, 1), fr)
            val c11 = lerp(at(0, 1, 1), at(1, 1, 1), fr)
            out[c] = lerp(lerp(c00, c10, fg), lerp(c01, c11, fg), fb)
        }
        return Rgb(out[0], out[1], out[2])
    }

    /** RGBA, 8 bits per channel, alpha opaque: what a GL_RGBA8 3D texture takes. */
    fun toRgba8(): ByteArray {
        val count = size * size * size
        val bytes = ByteArray(count * 4)
        for (i in 0 until count) {
            for (c in 0..2) bytes[i * 4 + c] = (data[i * 3 + c].coerceIn(0f, 1f) * 255f + 0.5f).toInt().toByte()
            bytes[i * 4 + 3] = -1 // 0xFF
        }
        return bytes
    }

    companion object {
        /** Samples [transform] on a [size]³ grid. Outputs are clamped to the sRGB cube. */
        fun bake(size: Int, transform: (Rgb) -> Rgb): Lut3d {
            val data = FloatArray(size * size * size * 3)
            val step = 1f / (size - 1)
            var i = 0
            for (b in 0 until size) {
                for (g in 0 until size) {
                    for (r in 0 until size) {
                        val out = transform(Rgb(r * step, g * step, b * step)).clamped()
                        data[i++] = out.r
                        data[i++] = out.g
                        data[i++] = out.b
                    }
                }
            }
            return Lut3d(size, data)
        }

        /**
         * Samples [linear], a function of linear RGB, on a [size]³ grid of sRGB-encoded inputs.
         * Faster than [bake]: the grid is decoded once per axis value, and nothing is allocated
         * per entry but the function's own result.
         */
        fun bakeLinear(size: Int, linear: (Vec3) -> Vec3): Lut3d {
            val decoded = DoubleArray(size) { Srgb.decode(it / (size - 1.0)) }
            val data = FloatArray(size * size * size * 3)
            var i = 0
            for (b in 0 until size) {
                for (g in 0 until size) {
                    for (r in 0 until size) {
                        val out = linear(Vec3(decoded[r], decoded[g], decoded[b]))
                        data[i++] = Srgb.encode(out.x.coerceIn(0.0, 1.0)).toFloat()
                        data[i++] = Srgb.encode(out.y.coerceIn(0.0, 1.0)).toFloat()
                        data[i++] = Srgb.encode(out.z.coerceIn(0.0, 1.0)).toFloat()
                    }
                }
            }
            return Lut3d(size, data)
        }

        /** This table after the round trip through an RGBA8 texture: what the GPU really samples. */
        fun fromRgba8(size: Int, bytes: ByteArray): Lut3d {
            val count = size * size * size
            return Lut3d(size, FloatArray(count * 3) { (bytes[(it / 3) * 4 + it % 3].toInt() and 0xFF) / 255f })
        }

        /**
         * 33 entries per axis, 8 bits per channel: measured in docs/ALGORITHMS.md ("LUT size") as
         * the smallest size whose error stays below a just-noticeable difference for every mode.
         */
        const val DEFAULT_SIZE = 33
    }
}

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
