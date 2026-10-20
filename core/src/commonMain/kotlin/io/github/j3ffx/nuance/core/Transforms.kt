package io.github.j3ffx.nuance.core

/** Colour transforms that need no profile: the reference points of the rendering pipeline. */
object Transforms {
    val Identity: (Rgb) -> Rgb = { it }

    /** Negative image: a test pattern that makes a working LUT obvious on screen. */
    val Invert: (Rgb) -> Rgb = { Rgb(1f - it.r, 1f - it.g, 1f - it.b) }
}
