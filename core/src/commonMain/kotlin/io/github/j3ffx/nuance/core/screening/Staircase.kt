package io.github.j3ffx.nuance.core.screening

import io.github.j3ffx.nuance.core.Rgb
import io.github.j3ffx.nuance.core.color.Lab
import io.github.j3ffx.nuance.core.color.Srgb
import io.github.j3ffx.nuance.core.color.Vec3
import io.github.j3ffx.nuance.core.color.deltaE2000
import io.github.j3ffx.nuance.core.cvd.Axis
import io.github.j3ffx.nuance.core.cvd.CvdProfile
import io.github.j3ffx.nuance.core.cvd.Simulation
import kotlin.math.exp
import kotlin.math.ln
import kotlin.random.Random

/**
 * Severity, by an adaptive staircase. Four patches, one of them different along a confusion line
 * of [axis] (the axis' cone response × (1 ± δ) around a mid grey): "touch the different one", or
 * "I see no difference". Two right answers in a row make δ smaller, a wrong one makes it larger
 * (2-down 1-up, which settles where the person is right about 71 % of the time). The threshold is
 * the geometric mean of δ at the last reversals.
 *
 * The threshold becomes a severity through the simulation models: the severity at which the
 * model predicts that the two colours, at the threshold, differ by just [JND] (ΔE2000). A total
 * form never sees any δ (the threshold is "never"), which reads as 100 %.
 */
class Staircase(val axis: Axis, private val random: Random) {
    /** A trial: four patches, [odd] (0 to 3) the different one. */
    data class Trial(val odd: Int, val common: Rgb, val different: Rgb, val delta: Double)

    private val base = Vec3(0.2, 0.2, 0.2)
    val levels: List<Double> = levels(axis, base)
    private var level = levels.lastIndex
    private var streak = 0
    private var lastStep = 0
    private val reversals = ArrayList<Double>()
    private var missesAtTop = 0
    var trials = 0
        private set

    var trial: Trial = newTrial()
        private set

    val done: Boolean
        get() = reversals.size >= REVERSALS || trials >= MAX_TRIALS || missesAtTop >= MAX_MISSES_AT_TOP

    /** [choice]: the patch the person touched, or null for "I see no difference". */
    fun answer(choice: Int?) {
        check(!done) { "the staircase is over" }
        trials++
        val right = choice == trial.odd
        if (right) {
            streak++
            // Until the first reversal, one right answer is enough: it gets to the threshold faster.
            if (streak >= 2 || (reversals.isEmpty() && lastStep <= 0)) {
                streak = 0
                move(-1)
            }
        } else {
            streak = 0
            if (level == levels.lastIndex) missesAtTop++
            move(+1)
        }
        trial = newTrial()
    }

    private fun move(step: Int) {
        if (lastStep != 0 && step != lastStep) reversals += levels[level]
        lastStep = step
        level = (level + step).coerceIn(0, levels.lastIndex)
    }

    /** The δ the person tells apart, or null if they never did, even at the largest. */
    val threshold: Double?
        get() {
            if (missesAtTop >= MAX_MISSES_AT_TOP) return null
            val last = reversals.takeLast(4).ifEmpty { listOf(levels[level]) }
            return exp(last.sumOf { ln(it) } / last.size)
        }

    /** Severity in percent, from [threshold] (see the class comment). */
    fun severity(): Int = severityFor(axis, threshold)

    private fun newTrial(): Trial {
        val delta = levels[level]
        val (plus, minus) = Plates.coneStep(axis, base, delta)
        // Which side is the odd one is random: the person can't learn "the redder one".
        val (common, different) = if (random.nextBoolean()) plus to minus else minus to plus
        return Trial(random.nextInt(4), rgb(common), rgb(different), delta)
    }

    companion object {
        /** A just-noticeable difference for two patches side by side on a phone, in ΔE2000. */
        const val JND = 2.5
        const val REVERSALS = 8
        const val MAX_TRIALS = 40
        const val MAX_MISSES_AT_TOP = 3

        /** δ from 0.4 % up to what the gamut allows (60 % at most), each step × 1.4. */
        internal fun levels(axis: Axis, base: Vec3): List<Double> {
            var top = 0.0
            for (step in 1..600) {
                val (a, b) = Plates.coneStep(axis, base, step / 1000.0)
                if (!Srgb.inGamut(a) || !Srgb.inGamut(b)) break
                top = step / 1000.0
            }
            return generateSequence(0.004) { it * 1.4 }.takeWhile { it < top }.toList() + top
        }

        /** How different the two colours of step δ look with [profile] (ΔE2000, after simulation). */
        fun seenDifference(axis: Axis, severity: Double, delta: Double): Double {
            val (a, b) = Plates.coneStep(axis, Vec3(0.2, 0.2, 0.2), delta)
            val sim = Simulation.rgb(CvdProfile(axis, severity))
            return deltaE2000(Lab.fromRgb(sim(rgb(a))), Lab.fromRgb(sim(rgb(b))))
        }

        /**
         * The severity, in percent, at which the model sees step [delta] as just [JND]: the
         * smallest one at which it looks no larger. 0 when even typical vision would barely see
         * it, 100 when nothing was ever seen.
         */
        fun severityFor(axis: Axis, delta: Double?): Int {
            delta ?: return 100
            return (0..100).firstOrNull { seenDifference(axis, it / 100.0, delta) <= JND } ?: 100
        }

        private fun rgb(linear: Vec3) = Srgb.fromLinear(linear).clamped()

        /** Spread of a geometric series, for confidence: max / min of the values. */
        internal fun spread(values: List<Double>) = if (values.isEmpty()) 1.0 else values.max() / values.min()
    }

    /** max / min δ over the last reversals: near 1 when the answers were consistent. */
    val spread: Double get() = spread(reversals.takeLast(4))
}
