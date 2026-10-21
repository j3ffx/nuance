package io.github.j3ffx.nuance.core.screening

import io.github.j3ffx.nuance.core.Rgb
import io.github.j3ffx.nuance.core.color.Lab
import io.github.j3ffx.nuance.core.color.Srgb
import io.github.j3ffx.nuance.core.color.Vec3
import io.github.j3ffx.nuance.core.color.deltaE2000
import io.github.j3ffx.nuance.core.cvd.Axis
import io.github.j3ffx.nuance.core.cvd.Lms
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** One dot of a plate, in a unit square (the plate is the disc of diameter 1 at its centre). */
data class Dot(val x: Float, val y: Float, val radius: Float, val color: Rgb)

/**
 * A pseudo-isochromatic plate generated here, never copied (Ishihara's plates are protected):
 * dots of random sizes and lightness, the ones on a digit in one colour, the others in a colour
 * that differs only in the response of [axis]'s cone. Someone missing that cone sees one uniform
 * field; anyone else sees the digit. [axis] null is the demonstration plate, seen by everyone.
 */
data class Plate(
    val axis: Axis?,
    val digit: Int,
    val dots: List<Dot>,
    val figure: Rgb,
    val field: Rgb,
    /** A fainter difference, missed by milder forms too. */
    val subtle: Boolean = false
)

/**
 * Builds the plates. The two colours of a plate sit on one confusion line of the axis: the same
 * cone responses except the axis' cone, scaled by 1 ± δ. Two plates per axis are strong (δ up to a
 * typical difference of ΔE2000 20, or what the gamut allows): only total and severe forms miss
 * them. Two are subtle (ΔE2000 9, 7 for tritan): milder forms miss them too. Every dot's light is then scaled at
 * random (×0.75 to ×1.15), the same way inside and outside the digit, so that brightness gives
 * nothing away.
 */
object Plates {
    /** Plates per axis (two strong, two subtle), each on its own base colour, plus the demonstration. */
    const val PER_AXIS = 4
    const val STRONG = 20.0
    const val SUBTLE = 9.0

    /** Partial tritan forms are simulated by fading the total form in, so they need a fainter plate. */
    const val SUBTLE_TRITAN = 7.0

    /** Base colours (linear RGB): muted tints, like classic plates, with room for the dots' jitter. */
    private val bases = mapOf(
        Axis.Protan to listOf(
            Vec3(0.22, 0.16, 0.07),
            Vec3(0.14, 0.16, 0.09),
            Vec3(0.18, 0.15, 0.13),
            Vec3(0.2, 0.18, 0.1)
        ),
        Axis.Deutan to listOf(
            Vec3(0.20, 0.17, 0.08),
            Vec3(0.13, 0.17, 0.11),
            Vec3(0.19, 0.14, 0.12),
            Vec3(0.16, 0.18, 0.1)
        ),
        Axis.Tritan to listOf(
            Vec3(0.16, 0.16, 0.20),
            Vec3(0.19, 0.18, 0.11),
            Vec3(0.13, 0.17, 0.16),
            Vec3(0.18, 0.16, 0.16)
        )
    )

    /** The 13 plates of a test, the demonstration first, the others in random order. */
    fun set(random: Random): List<Plate> {
        val plates = Axis.entries.flatMap { axis ->
            // Each axis gets four different digits, so two plates of an axis never look alike.
            val digits = (1..9).shuffled(random)
            (0 until PER_AXIS).map { i ->
                val subtle = i >= PER_AXIS / 2
                val (figure, field) = confusionColors(axis, bases.getValue(axis)[i], target(axis, subtle))
                // Swap at random which colour draws the digit.
                val (a, b) = if (random.nextBoolean()) figure to field else field to figure
                generate(axis, digits[i], a, b, random).copy(subtle = subtle)
            }
        }
        return listOf(demo(random)) + plates.shuffled(random)
    }

    private fun target(axis: Axis, subtle: Boolean) = when {
        !subtle -> STRONG
        axis == Axis.Tritan -> SUBTLE_TRITAN
        else -> SUBTLE
    }

    /** Light digit on a darker field: brightness alone shows it, whatever the colour vision. */
    fun demo(random: Random): Plate = generate(
        null,
        (1..9).random(random),
        Vec3(0.45, 0.32, 0.12),
        Vec3(0.09, 0.11, 0.13),
        random,
        jitter = 0.9..1.1
    )

    /**
     * Two colours that [axis] confuses: [base] with the axis' cone response scaled by 1 + δ and
     * 1 − δ, δ the smallest step (by 0.005, 0.6 at most) that reaches a typical difference of
     * [target] ΔE2000, or the largest that keeps both, jittered, in gamut.
     */
    fun confusionColors(axis: Axis, base: Vec3, target: Double): Pair<Vec3, Vec3> {
        var best: Pair<Vec3, Vec3>? = null
        for (step in 1..120) {
            val pair = coneStep(axis, base, step / 200.0)
            val ok = listOf(pair.first, pair.second).all { Srgb.inGamut(it * JITTER.endInclusive) }
            if (!ok) break
            best = pair
            if (deltaE2000(Lab.fromLinear(pair.first), Lab.fromLinear(pair.second)) >= target) break
        }
        return checkNotNull(best) { "no room on the $axis confusion line around $base" }
    }

    /** [base] with [axis]' cone response × (1 + δ) and × (1 − δ). */
    fun coneStep(axis: Axis, base: Vec3, delta: Double): Pair<Vec3, Vec3> {
        val lms = Lms.FromLinearRgb * base
        val cone = Lms.axis(axis.cone) * lms[axis.cone]
        return Lms.ToLinearRgb * (lms + cone * delta) to Lms.ToLinearRgb * (lms - cone * delta)
    }

    private val JITTER = 0.75..1.15

    private fun generate(
        axis: Axis?,
        digit: Int,
        figure: Vec3,
        field: Vec3,
        random: Random,
        jitter: ClosedFloatingPointRange<Double> = JITTER
    ): Plate {
        val glyph = Glyphs.digit(digit)
        val dots = pack(random).map { (x, y, r) ->
            val inside = Glyphs.contains(glyph, (x - 0.5) / GLYPH_SIZE + 0.5, (y - 0.5) / GLYPH_SIZE + 0.5)
            val k = jitter.start + random.nextDouble() * (jitter.endInclusive - jitter.start)
            val c = (if (inside) figure else field) * k
            Dot(x.toFloat(), y.toFloat(), r.toFloat(), Srgb.fromLinear(c).clamped())
        }
        return Plate(axis, digit, dots, Srgb.fromLinear(figure).clamped(), Srgb.fromLinear(field).clamped())
    }

    /** Fraction of the plate's diameter the digit's box takes. */
    private const val GLYPH_SIZE = 0.62

    /**
     * Random non-overlapping dots filling the disc: candidates of decreasing size, each kept if it
     * fits ([Triple] of x, y, radius). A grid of cells makes the overlap check local.
     */
    private fun pack(random: Random): List<Triple<Double, Double, Double>> {
        val placed = ArrayList<Triple<Double, Double, Double>>()
        val cell = 2 * MAX_R + GAP
        val cells = (1 / cell).toInt() + 1
        val grid = Array(cells * cells) { ArrayList<Int>() }
        repeat(ATTEMPTS) { attempt ->
            // Large dots first, then smaller ones fill the gaps.
            val t = attempt.toDouble() / ATTEMPTS
            val r = MAX_R - (MAX_R - MIN_R) * sqrt(t) * (0.6 + 0.4 * random.nextDouble())
            val angle = random.nextDouble() * 2 * PI
            val dist = sqrt(random.nextDouble()) * (0.5 - r - GAP)
            val x = 0.5 + dist * cos(angle)
            val y = 0.5 + dist * sin(angle)
            val cx = floor(x / cell).toInt()
            val cy = floor(y / cell).toInt()
            for (gx in cx - 1..cx + 1) {
                for (gy in cy - 1..cy + 1) {
                    if (gx !in 0 until cells || gy !in 0 until cells) continue
                    for (i in grid[gy * cells + gx]) {
                        val (ox, oy, or) = placed[i]
                        if (hypot(ox - x, oy - y) < or + r + GAP) return@repeat
                    }
                }
            }
            grid[cy * cells + cx] += placed.size
            placed += Triple(x, y, r)
        }
        return placed
    }

    private const val MIN_R = 0.005
    private const val MAX_R = 0.02
    private const val GAP = 0.003
    private const val ATTEMPTS = 20000
}

/**
 * Digits 1 to 9 as thick strokes (polylines in a unit box, y downwards), so a dot belongs to the
 * digit when its centre is near a stroke. Drawn here: no font involved. 0 is left out (it reads as
 * a plain ring).
 */
internal object Glyphs {
    private const val HALF_WIDTH = 0.085

    fun digit(d: Int): List<List<Pair<Double, Double>>> = when (d) {
        1 -> listOf(listOf(0.32 to 0.26, 0.56 to 0.08, 0.56 to 0.92))

        2 -> listOf(
            listOf(
                0.24 to 0.3,
                0.32 to 0.15,
                0.5 to 0.08,
                0.68 to 0.14,
                0.76 to 0.3,
                0.7 to 0.47,
                0.24 to 0.9,
                0.8 to 0.9
            )
        )

        3 -> listOf(
            listOf(
                0.24 to 0.12,
                0.74 to 0.1,
                0.46 to 0.42,
                0.66 to 0.47,
                0.77 to 0.64,
                0.71 to 0.83,
                0.5 to 0.91,
                0.24 to 0.85
            )
        )

        4 -> listOf(listOf(0.64 to 0.92, 0.64 to 0.08, 0.2 to 0.66, 0.84 to 0.66))

        5 -> listOf(
            listOf(
                0.76 to 0.1, 0.3 to 0.1, 0.26 to 0.46, 0.5 to 0.4, 0.7 to 0.48,
                0.78 to 0.66, 0.7 to 0.84, 0.5 to 0.91, 0.24 to 0.85
            )
        )

        6 -> listOf(six)

        7 -> listOf(listOf(0.2 to 0.1, 0.8 to 0.1, 0.42 to 0.92))

        8 -> listOf(ellipse(0.5, 0.29, 0.2, 0.19), ellipse(0.5, 0.7, 0.25, 0.21))

        9 -> listOf(six.map { (x, y) -> 1 - x to 1 - y })

        else -> throw IllegalArgumentException("digits 1 to 9 only, not $d")
    }

    private val six = listOf(
        0.72 to 0.12, 0.5 to 0.08, 0.32 to 0.2, 0.24 to 0.48, 0.26 to 0.74, 0.42 to 0.91,
        0.62 to 0.91, 0.76 to 0.76, 0.74 to 0.58, 0.58 to 0.47, 0.4 to 0.48, 0.25 to 0.6
    )

    private fun ellipse(cx: Double, cy: Double, rx: Double, ry: Double) =
        (0..24).map { i -> (cx + rx * cos(i * PI / 12)) to (cy + ry * sin(i * PI / 12)) }

    fun contains(strokes: List<List<Pair<Double, Double>>>, x: Double, y: Double): Boolean =
        strokes.any { line -> line.zipWithNext().any { (a, b) -> distance(x, y, a, b) <= HALF_WIDTH } }

    private fun distance(x: Double, y: Double, a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
        val (ax, ay) = a
        val (bx, by) = b
        val dx = bx - ax
        val dy = by - ay
        val t = (((x - ax) * dx + (y - ay) * dy) / (dx * dx + dy * dy)).coerceIn(0.0, 1.0)
        return hypot(x - (ax + t * dx), y - (ay + t * dy))
    }
}
