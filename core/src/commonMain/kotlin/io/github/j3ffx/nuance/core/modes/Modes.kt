package io.github.j3ffx.nuance.core.modes

import io.github.j3ffx.nuance.core.Rgb
import io.github.j3ffx.nuance.core.color.Oklab
import io.github.j3ffx.nuance.core.color.Srgb
import io.github.j3ffx.nuance.core.color.Vec3
import io.github.j3ffx.nuance.core.correction.CorrectionMetrics
import io.github.j3ffx.nuance.core.correction.CorrectionStrategy
import io.github.j3ffx.nuance.core.cvd.CvdProfile
import io.github.j3ffx.nuance.core.cvd.Simulation
import kotlin.math.abs

/** A colour transform on linear RGB; [ColorFunction.rgb] wraps it for sRGB colours. */
typealias LinearFunction = (Vec3) -> Vec3

/** What the screen shows. */
enum class Mode { Correct, Isolate, Simulate, Compare }

/** The colours offered by "Isolate a hue", as sRGB swatches: their Oklch hue is the target. */
enum class HuePreset(val swatch: Rgb) {
    Red(Rgb(1f, 0f, 0f)),
    Orange(Rgb(1f, 0.5f, 0f)),
    Yellow(Rgb(1f, 0.9f, 0f)),
    Green(Rgb(0f, 0.7f, 0.1f)),
    Blue(Rgb(0f, 0.35f, 1f)),
    Violet(Rgb(0.55f, 0.1f, 0.9f)),
    Pink(Rgb(1f, 0.4f, 0.7f))
    ;

    val hue: Double get() = Oklab.toLch(Oklab.fromLinear(Srgb.toLinear(swatch))).z
}

/** Settings of every mode, as fractions (the UI shows percentages). */
data class Settings(
    val profile: CvdProfile,
    /** Correction strength, 0 (original) to 1. */
    val intensity: Double = 1.0,
    val strategyId: String = CorrectionStrategy.Default.id,
    /** Extra contrast and saturation, 0 (none) to 1 (strong); applied after the correction. */
    val contrast: Double = 0.0,
    val saturation: Double = 0.0,
    /** Hue isolation: target hue in degrees (Oklch), half-width of the kept range, boost of the kept colours. */
    val isolateHue: Double = HuePreset.Red.hue,
    val isolateWidth: Double = 30.0,
    val isolateBoost: Double = 0.3
)

/** Builds the functions the LUTs are baked from. */
object ColorFunction {
    fun rgb(f: LinearFunction): (Rgb) -> Rgb =
        { Srgb.fromLinear(CorrectionMetrics.clampLinear(f(Srgb.toLinear(it)))).clamped() }

    val identity: LinearFunction = { it }

    fun simulation(profile: CvdProfile): LinearFunction = Simulation.linear(profile)

    /** Correction, then the optional contrast and saturation boost. */
    fun correction(s: Settings): LinearFunction {
        val correct = CorrectionStrategy.byId(s.strategyId).build(s.profile, s.intensity)
        val enhance = enhancement(s.contrast, s.saturation)
        return { enhance(CorrectionMetrics.clampLinear(correct(it))) }
    }

    /** The four views of the Comparer mode: real, real as seen, corrected, corrected as seen. */
    fun comparer(s: Settings): List<LinearFunction> {
        val simulate = simulation(s.profile)
        val correct = correction(s)
        return listOf(identity, simulate, correct, { simulate(CorrectionMetrics.clampLinear(correct(it))) })
    }

    /**
     * Contrast and saturation in Oklab: lightness stretched around mid-grey, chroma scaled.
     * At 0 and 0, the identity.
     */
    fun enhancement(contrast: Double, saturation: Double): LinearFunction {
        if (contrast == 0.0 && saturation == 0.0) return { it }
        val lGain = 1 + contrast
        val cGain = 1 + saturation
        return { c ->
            val lab = Oklab.fromLinear(c)
            Oklab.toLinear(Vec3(MID + (lab.x - MID) * lGain, lab.y * cGain, lab.z * cGain))
        }
    }

    /**
     * "Show me only the red": colours whose Oklch hue is within [Settings.isolateWidth] of the target
     * keep their colour (boosted), the rest turns grey at the same lightness. The edge is soft (the
     * last 40 % of the width fades), and near-greys, whose hue means nothing, stay grey.
     */
    fun isolation(s: Settings): LinearFunction {
        val target = s.isolateHue
        val width = s.isolateWidth
        val boost = 1 + s.isolateBoost
        return { c ->
            val lch = Oklab.toLch(Oklab.fromLinear(c))
            val d = abs(lch.z - target).let { if (it > 180) 360 - it else it }
            val inRange = 1 - smoothstep(width * 0.6, width, d)
            val chromatic = smoothstep(0.02, 0.05, lch.y)
            val keep = inRange * chromatic
            Oklab.toLinear(Oklab.fromLch(Vec3(lch.x, lch.y * keep * boost, lch.z)))
        }
    }

    private const val MID = 0.6

    private fun smoothstep(edge0: Double, edge1: Double, x: Double): Double {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0.0, 1.0)
        return t * t * (3 - 2 * t)
    }
}
