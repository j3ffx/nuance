package io.github.j3ffx.nuance.core.naming

import io.github.j3ffx.nuance.core.Rgb
import io.github.j3ffx.nuance.core.color.Oklab
import io.github.j3ffx.nuance.core.color.Srgb
import io.github.j3ffx.nuance.core.color.Vec3
import kotlin.math.roundToInt

/** The basic colour terms the pipette uses; the UI translates them (FR and EN). */
enum class ColorTerm {
    Black,
    White,
    Grey,
    Red,
    Orange,
    Brown,
    Beige,
    Yellow,
    Olive,
    Green,
    Turquoise,
    Blue,
    Violet,
    Pink
}

/** "Light" or "dark", relative to what is typical for the term (a yellow is lighter than a blue). */
enum class Lightness { Light, Dark }

/** "Greyish" (weak colour) or "vivid" (strong colour). */
enum class Chroma { Greyish, Vivid }

/** A colour's name: a basic term and at most one modifier of each kind. */
data class ColorName(val term: ColorTerm, val lightness: Lightness? = null, val chroma: Chroma? = null)

/**
 * Names colours with Nuance's own rules, on Oklch (lightness, chroma, hue): about a dozen basic
 * terms plus "light", "dark", "greyish" and "vivid". No licensed name list is involved.
 *
 * Oklch rather than CIELAB's LCh: CIELAB bends blues towards violet (pure blue at 306°, a violet at
 * 312°), so hue sectors could not separate them; in Oklch they sit at 264° and 294°. It is also the
 * space hue isolation uses, so the pipette and "Isolate" agree on what a hue is.
 * The thresholds were set on reference swatches (pure primaries, navy, olive, brown, beige, pink…);
 * ColorNamingTest lists them.
 */
object ColorNaming {
    fun name(c: Rgb): ColorName = name(Oklab.toLch(Oklab.fromLinear(Srgb.toLinear(c.clamped()))))

    /** [lch] is Oklch: L 0..1, C about 0..0.33, h in degrees. */
    fun name(lch: Vec3): ColorName {
        val (l, c, h) = lch
        if (l < BLACK) return ColorName(ColorTerm.Black)
        if (c < GREY) {
            return when {
                l > WHITE -> ColorName(ColorTerm.White)
                l > 0.75 -> ColorName(ColorTerm.Grey, Lightness.Light)
                l < 0.45 -> ColorName(ColorTerm.Grey, Lightness.Dark)
                else -> ColorName(ColorTerm.Grey)
            }
        }
        val term = term(l, c, h)
        val chroma = when {
            c < GREYISH -> Chroma.Greyish
            c >= VIVID -> Chroma.Vivid
            else -> null
        }
        val reference = typicalLightness[term]
        // A vivid colour is named by its strength alone: "vivid light green" reads badly.
        val lightness = when {
            reference == null || chroma == Chroma.Vivid -> null
            l > reference + 0.12 -> Lightness.Light
            l < reference - 0.13 -> Lightness.Dark
            else -> null
        }
        return ColorName(term, lightness, chroma.takeUnless { term == ColorTerm.Beige })
    }

    private fun term(l: Double, c: Double, h: Double): ColorTerm = when {
        // Browns and beiges are dark and pale oranges and yellows.
        h in 35.0..100.0 && l < 0.58 && c < 0.16 -> ColorTerm.Brown

        h in 15.0..35.0 && l < 0.5 && c < 0.12 -> ColorTerm.Brown

        h in 50.0..112.0 && l > 0.75 && c < 0.09 -> ColorTerm.Beige

        h < 12 || h >= 345 -> if (l < 0.55) ColorTerm.Red else ColorTerm.Pink

        h < 40 -> if (l > 0.72 && c < 0.18) ColorTerm.Pink else ColorTerm.Red

        h < 72 -> ColorTerm.Orange

        h < 115 -> if (l < 0.65) ColorTerm.Olive else ColorTerm.Yellow

        h < 170 -> ColorTerm.Green

        h < 215 -> ColorTerm.Turquoise

        h < 280 -> ColorTerm.Blue

        h < 315 -> ColorTerm.Violet

        else -> if (l < 0.6) ColorTerm.Violet else ColorTerm.Pink
    }

    /** Oklch lightness of a typical swatch of each term: "light" and "dark" are relative to it. */
    private val typicalLightness = mapOf(
        ColorTerm.Red to 0.60,
        ColorTerm.Orange to 0.72,
        ColorTerm.Brown to 0.48,
        ColorTerm.Yellow to 0.90,
        ColorTerm.Green to 0.62,
        ColorTerm.Turquoise to 0.70,
        ColorTerm.Blue to 0.52,
        ColorTerm.Violet to 0.52,
        ColorTerm.Pink to 0.74
    )

    private const val BLACK = 0.2
    private const val WHITE = 0.95
    private const val GREY = 0.03
    private const val GREYISH = 0.07
    private const val VIVID = 0.2

    /** "#1E90FF". */
    fun hex(c: Rgb): String {
        fun ch(v: Float) = (v.coerceIn(0f, 1f) * 255).roundToInt().toString(16).padStart(2, '0')
        return "#" + (ch(c.r) + ch(c.g) + ch(c.b)).uppercase()
    }

    /**
     * The mean colour of packed 0xAARRGGBB pixels, averaged in linear light (averaging encoded
     * values would darken a mix of light and dark pixels). The pipette reads a small area this way
     * rather than one noisy pixel.
     */
    fun average(pixels: IntArray): Rgb {
        require(pixels.isNotEmpty())
        var sum = Vec3.Zero
        for (p in pixels) {
            sum += Srgb.toLinear(
                Rgb(((p shr 16) and 0xFF) / 255f, ((p shr 8) and 0xFF) / 255f, (p and 0xFF) / 255f)
            )
        }
        return Srgb.fromLinear(sum * (1.0 / pixels.size)).clamped()
    }
}
