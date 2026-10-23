package io.github.j3ffx.nuance.core.correction

import io.github.j3ffx.nuance.core.color.Lab
import io.github.j3ffx.nuance.core.color.Mat3
import io.github.j3ffx.nuance.core.color.Vec3
import io.github.j3ffx.nuance.core.cvd.Axis
import io.github.j3ffx.nuance.core.cvd.CvdProfile
import io.github.j3ffx.nuance.core.cvd.Simulation

/**
 * A way to correct colours for a profile: linear RGB in, linear RGB out (possibly out of gamut,
 * the caller clamps). [intensity] 0 is the original image, 1 the full correction.
 *
 * Both strategies leave neutral colours alone: a grey loses nothing in any simulation, so there
 * is nothing to move.
 */
interface CorrectionStrategy {
    /** Stable identifier, kept in profiles. */
    val id: String

    fun build(profile: CvdProfile, intensity: Double): (Vec3) -> Vec3

    companion object {
        val all: List<CorrectionStrategy> = listOf(LabRemap.Default, ErrorRedistribution)

        fun byId(id: String): CorrectionStrategy = all.firstOrNull { it.id == id } ?: Default

        /**
         * The CIELAB remap, which the separation measure preferred. Error redistribution was the
         * default first (chosen by eye, through the simulation), until a protan tester saw reds
         * fade to white with it (docs/ALGORITHMS.md, "Corrections").
         */
        val Default: CorrectionStrategy get() = LabRemap.Default

        /** The former default, which saved profiles name and [Preferences] migrates once. */
        const val FORMER_DEFAULT_ID = "error-redistribution"
    }
}

/**
 * Daltonisation by error redistribution, after Fidaner, Lin and Ozguven (2005), "Analysis of Color
 * Blindness" (Stanford EE368): simulate, take what the person loses (original − simulated), and
 * add it to the channels they still see. Protan and deutan: the lost red goes to green and blue
 * (their matrix); tritan: the lost blue goes to red and green (the usual adaptation, which the
 * paper doesn't cover); a mix adds both shifts, weighted by axis. Computed on linear RGB, unlike the original, which used encoded values.
 */
object ErrorRedistribution : CorrectionStrategy {
    override val id = "error-redistribution"

    private val RedGreenShift = Mat3.of(0.0, 0.0, 0.0, 0.7, 1.0, 0.0, 0.7, 0.0, 1.0)
    private val TritanShift = Mat3.of(1.0, 0.0, 0.7, 0.0, 1.0, 0.7, 0.0, 0.0, 0.0)

    override fun build(profile: CvdProfile, intensity: Double): (Vec3) -> Vec3 {
        if (intensity == 0.0 || profile.severity == 0.0) return { it }
        val simulate = Simulation.linear(profile)
        val (redGreen, tritan) = axisWeights(profile)
        val shift = RedGreenShift * redGreen + TritanShift * tritan
        return { c ->
            val error = c - simulate(c)
            c + (shift * error) * intensity
        }
    }
}

/**
 * Remapping in CIELAB: what the simulation removes along the lost opponent axis (red-green a* for
 * protans and deutans, blue-yellow b* for tritans; both, weighted, for a mix) is written onto the
 * axes the person still sees: mostly lightness, and a little the other opponent axis. Two colours that differ only along a confusion
 * line then differ along an axis the person perceives.
 */
class LabRemap(
    /** Share of the lost difference written onto the other opponent axis. */
    val chromaGain: Double,
    /** Share of the lost difference written onto lightness. */
    val lightnessGain: Double
) : CorrectionStrategy {
    override val id = "lab-remap"

    override fun build(profile: CvdProfile, intensity: Double): (Vec3) -> Vec3 {
        if (intensity == 0.0 || profile.severity == 0.0) return { it }
        val simulate = Simulation.linear(profile)
        val (redGreen, tritan) = axisWeights(profile)
        val chroma = chromaGain * intensity
        val lightness = lightnessGain * intensity
        return { c ->
            val lab = Lab.fromLinear(c)
            val lost = lab - Lab.fromLinear(simulate(c))
            // Lost a* (red-green) and lost b* (blue-yellow), each weighted by its axis.
            val a = lost.y * redGreen
            val b = lost.z * tritan
            Lab.toLinear(Vec3(lab.x + lightness * (a + b), lab.y + chroma * b, lab.z + chroma * a))
        }
    }
    companion object {
        /**
         * The best gains by the separation measure (docs/ALGORITHMS.md): at equal image change,
         * putting most of the lost difference into lightness rescues the most confused pairs and
         * shifts skin hues the least. The scale makes 100 % a strong but bearable correction.
         */
        val Default = LabRemap(chromaGain = 0.2, lightnessGain = 0.65)
    }
}

/**
 * How much each opponent axis counts in a correction: red-green (the stronger of protan and
 * deutan) and blue-yellow (tritan), relative to the stronger of the two. One axis gives 1 and 0,
 * so a single-axis profile is corrected exactly as before mixes existed.
 */
internal fun axisWeights(profile: CvdProfile): Pair<Double, Double> {
    val redGreen = maxOf(profile.protan, profile.deutan)
    val tritan = profile.tritan
    val max = maxOf(redGreen, tritan)
    return if (max == 0.0) 0.0 to 0.0 else redGreen / max to tritan / max
}
