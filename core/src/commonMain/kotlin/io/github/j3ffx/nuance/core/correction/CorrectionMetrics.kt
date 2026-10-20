package io.github.j3ffx.nuance.core.correction

import io.github.j3ffx.nuance.core.color.Lab
import io.github.j3ffx.nuance.core.color.Srgb
import io.github.j3ffx.nuance.core.color.Vec3
import io.github.j3ffx.nuance.core.color.deltaE2000
import io.github.j3ffx.nuance.core.cvd.CvdProfile
import io.github.j3ffx.nuance.core.cvd.Lms
import io.github.j3ffx.nuance.core.cvd.Simulation

/**
 * The objective criterion for corrections (brief §6.4): take pairs of colours a person with the
 * profile confuses, and measure how far apart they are *as that person sees them* (ΔE2000 through
 * the simulation), before and after correction. Also measures what the correction costs: how
 * much the image changes, greys, skin tones.
 */
object CorrectionMetrics {
    /** A difference this large is clearly visible side by side. */
    const val DISTINCT = 5.0

    data class Result(
        /** Pairs evaluated. */
        val pairs: Int,
        /** Mean ΔE2000 between the two colours of a pair, seen through the simulation. */
        val meanBefore: Double,
        val meanAfter: Double,
        /** Share of pairs at least [DISTINCT] apart, seen through the simulation. */
        val distinctBefore: Double,
        val distinctAfter: Double,
        /** Mean ΔE2000 between a colour and its correction, for typical vision: the cost. */
        val meanChange: Double,
        /** Largest ΔE2000 a grey or white moves. */
        val worstGrayChange: Double,
        /** Mean ΔE2000 skin tones move, and their largest hue shift in degrees. */
        val skinChange: Double,
        val skinHueShift: Double
    )

    fun evaluate(strategy: CorrectionStrategy, profile: CvdProfile, intensity: Double): Result {
        val simulate = Simulation.linear(profile)
        val correct = strategy.build(profile, intensity)
        fun corrected(c: Vec3) = clampLinear(correct(c))
        fun seen(c: Vec3) = Lab.fromLinear(clampLinear(simulate(c)))

        // Only the pairs this profile actually confuses: at low severity, many confusion-line pairs
        // are still told apart, and there is nothing to rescue.
        val pairs = confusionPairs(profile).filter { (a, b) -> deltaE2000(seen(a), seen(b)) < DISTINCT }
        val before = pairs.map { (a, b) -> deltaE2000(seen(a), seen(b)) }
        val after = pairs.map { (a, b) -> deltaE2000(seen(corrected(a)), seen(corrected(b))) }
        val grid = grid()
        val change = grid.map { deltaE2000(Lab.fromLinear(it), Lab.fromLinear(corrected(it))) }
        val grays = (0..10).map { Vec3.One * Srgb.decode(it / 10.0) }
        val skin = skinTones()
        val skinLab = skin.map { Lab.fromLinear(it) to Lab.fromLinear(corrected(it)) }
        return Result(
            pairs = pairs.size,
            meanBefore = before.average(),
            meanAfter = after.average(),
            distinctBefore = before.count { it >= DISTINCT }.toDouble() / pairs.size,
            distinctAfter = after.count { it >= DISTINCT }.toDouble() / pairs.size,
            meanChange = change.average(),
            worstGrayChange = grays.maxOf { deltaE2000(Lab.fromLinear(it), Lab.fromLinear(corrected(it))) },
            skinChange = skinLab.map { (a, b) -> deltaE2000(a, b) }.average(),
            skinHueShift = skinLab.maxOf { (a, b) -> hueDistance(Lab.toLch(a).z, Lab.toLch(b).z) }
        )
    }

    /**
     * Pairs of in-gamut colours that differ only in the affected cone's response, by 10 to 40 %:
     * on the profile's confusion lines, clearly different for typical vision (ΔE2000 ≥ 8).
     */
    fun confusionPairs(profile: CvdProfile): List<Pair<Vec3, Vec3>> {
        val cone = profile.axis.cone
        val out = ArrayList<Pair<Vec3, Vec3>>()
        for (c in grid()) {
            val lms = Lms.FromLinearRgb * c
            for (factor in doubleArrayOf(0.6, 0.75, 0.9, 1.1, 1.25, 1.4)) {
                val moved = Lms.ToLinearRgb * (lms + Lms.axis(cone) * (lms[cone] * (factor - 1)))
                if (!Srgb.inGamut(moved)) continue
                if (deltaE2000(Lab.fromLinear(c), Lab.fromLinear(moved)) < 8) continue
                out += c to moved
            }
        }
        return out
    }

    /** A regular 7×7×7 grid inside the sRGB cube, on encoded values (so it is perceptually spread). */
    fun grid(): List<Vec3> = buildList {
        val steps = (1..7).map { Srgb.decode(it / 8.0) }
        for (r in steps) for (g in steps) for (b in steps) add(Vec3(r, g, b))
    }

    /**
     * Synthetic skin tones: CIELAB lightness 35 to 80, chroma 12 to 32, hue 40 to 65°, roughly
     * where human skin falls, light to dark. Generated, no photo involved.
     */
    fun skinTones(): List<Vec3> = buildList {
        for (l in listOf(35.0, 50.0, 65.0, 80.0)) {
            for (c in listOf(12.0, 22.0, 32.0)) {
                for (h in listOf(40.0, 52.0, 65.0)) {
                    val rad = h * kotlin.math.PI / 180
                    val linear = Lab.toLinear(Vec3(l, c * kotlin.math.cos(rad), c * kotlin.math.sin(rad)))
                    if (Srgb.inGamut(linear)) add(linear)
                }
            }
        }
    }

    fun clampLinear(v: Vec3) = Vec3(v.x.coerceIn(0.0, 1.0), v.y.coerceIn(0.0, 1.0), v.z.coerceIn(0.0, 1.0))

    private fun hueDistance(a: Double, b: Double): Double {
        val d = kotlin.math.abs(a - b) % 360
        return if (d > 180) 360 - d else d
    }
}
