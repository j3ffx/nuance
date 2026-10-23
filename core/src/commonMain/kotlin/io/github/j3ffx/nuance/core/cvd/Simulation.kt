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
 *   (Machado's tritan matrices were never validated with tritan observers);
 * - several axes: chained, see [linear].
 *
 * Everything happens on linear RGB.
 */
object Simulation {
    /**
     * The simulation as a function of linear RGB. Matrices are computed once, here. A mixed profile
     * chains the axes, red-green first (protan, then deutan), then tritan: an approximation, since
     * no model was validated for more than one cone type (docs/ALGORITHMS.md, "Profils mixtes").
     * With one axis, it is that axis's simulation exactly.
     */
    fun linear(profile: CvdProfile): (Vec3) -> Vec3 {
        val redGreen = listOf(Axis.Protan, Axis.Deutan)
            .filter { profile.severityOf(it) > 0 }
            .map { redGreenMatrix(it, profile.severityOf(it)) }
            .reduceOrNull { first, then -> then * first }
        val t = profile.tritan
        val brettel = if (t > 0) Brettel1997(Axis.Tritan) else null
        return when {
            brettel == null && redGreen == null -> { c -> c }

            brettel == null -> { c -> redGreen!! * c }

            else -> { c ->
                val rg = redGreen?.let { it * c } ?: c
                Vec3.lerp(rg, brettel.simulate(rg), t)
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
