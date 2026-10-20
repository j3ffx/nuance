package io.github.j3ffx.nuance.core

/**
 * A colour with gamma-encoded sRGB channels in 0..1. Every processing mode is a function
 * `(Rgb) -> Rgb`, baked into a 3D LUT that the platform shader samples.
 */
data class Rgb(val r: Float, val g: Float, val b: Float) {
    /** The same colour with every channel clamped to 0..1. */
    fun clamped(): Rgb = Rgb(r.coerceIn(0f, 1f), g.coerceIn(0f, 1f), b.coerceIn(0f, 1f))

    companion object {
        val Black = Rgb(0f, 0f, 0f)
        val White = Rgb(1f, 1f, 1f)
    }
}
