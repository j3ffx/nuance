package io.github.j3ffx.nuance.core.cvd

import io.github.j3ffx.nuance.core.Rgb
import io.github.j3ffx.nuance.core.color.Mat3
import io.github.j3ffx.nuance.core.color.Srgb
import io.github.j3ffx.nuance.core.color.Vec3

/**
 * What a person with a given profile perceives, as the sRGB colour that looks the same to someone
 * with typical vision. Used by the Comparer mode, to check corrections, and by the built-in test.
 *
 * Model choice (docs/ALGORITHMS.md):
 * - protan and deutan, partial: Machado 2009, made for anomalous trichromacy;
 * - protan and deutan, total: Viénot 1999, which merges each confusion line into exactly one colour;
 *   from 90 % to 100 %, the matrix moves linearly from Machado's 90 % step to Viénot, so the
 *   severity slider has no jump;
 * - tritan: Brettel 1997 for the total form; partial forms interpolate it with the original colour
 *   (Machado's tritan matrices were never validated with tritan observers).
 *
 * Everything happens on linear RGB.
 */
object Simulation {
    /** The simulation as a function of linear RGB. Matrices are computed once, here. */
    fun linear(profile: CvdProfile): (Vec3) -> Vec3 {
        val s = profile.severity
        if (s == 0.0) return { it }
        return when (profile.axis) {
            Axis.Protan, Axis.Deutan -> {
                val m = redGreenMatrix(profile.axis, s)
                return { m * it }
            }

            Axis.Tritan -> {
                val brettel = Brettel1997(Axis.Tritan)
                return { Vec3.lerp(it, brettel.simulate(it), s) }
            }
        }
    }

    /** The simulation on sRGB colours, clamped to the gamut. */
    fun rgb(profile: CvdProfile): (Rgb) -> Rgb {
        val f = linear(profile)
        return { Srgb.fromLinear(f(Srgb.toLinear(it))).clamped() }
    }

    internal fun redGreenMatrix(axis: Axis, severity: Double): Mat3 = when {
        severity <= BLEND_START -> Machado2009.matrix(axis, severity)

        else -> Mat3.lerp(
            Machado2009.matrix(axis, BLEND_START),
            Vienot1999.matrix(axis),
            (severity - BLEND_START) / (1 - BLEND_START)
        )
    }

    private const val BLEND_START = 0.9
}
