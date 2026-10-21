package io.github.j3ffx.nuance.core.screening

import io.github.j3ffx.nuance.core.Rgb
import io.github.j3ffx.nuance.core.color.Lab
import io.github.j3ffx.nuance.core.color.Mat3
import io.github.j3ffx.nuance.core.color.Srgb
import io.github.j3ffx.nuance.core.color.Vec3
import io.github.j3ffx.nuance.core.color.deltaE2000
import io.github.j3ffx.nuance.core.cvd.Axis
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A hue arrangement inspired by the Farnsworth D-15 (Farnsworth 1947): starting from a fixed cap,
 * order 15 caps by colour. People with a deficiency jump across the hue circle along their
 * confusion lines; the direction of those jumps gives the axis.
 *
 * The caps' colours come from their CIE 1976 u*v* coordinates published by Vingrys and
 * King-Smith (1988), "A quantitative scoring technique for panel tests of color vision",
 * Investigative Ophthalmology and Visual Science 29(1), the same values the CVD package (R, Jose
 * Gama, GPL) uses. The caps are Munsell value 5 (Y = 19.77, ASTM D1535), so L* = 51.6; the u*v*
 * are relative to illuminant C, adapted here to sRGB's D65 white with the Bradford transform.
 */
object D15 {
    /** u*, v* of caps 1 to 15, then the reference (pilot) cap, as published. */
    private val uv = listOf(
        -21.54 to -38.39, // pilot, 10B 5/6
        -23.26 to -25.56,
        -22.41 to -15.53,
        -23.11 to -7.45,
        -22.45 to 1.10,
        -21.67 to 7.35,
        -14.08 to 18.74,
        -2.72 to 28.13,
        14.84 to 31.13,
        23.87 to 26.35,
        31.82 to 14.76,
        31.42 to 6.99,
        29.79 to 0.10,
        26.64 to -9.38,
        22.92 to -18.65,
        11.20 to -24.61
    )

    /** The caps' sRGB colours: index 0 is the pilot, then caps 1 to 15. */
    val caps: List<Rgb> by lazy { uv.map { (u, v) -> Srgb.fromLinear(Srgb.XyzToLinear * luvToXyzD65(L, u, v)) } }

    /** Lightness of Munsell value 5. */
    private const val L = 51.58

    /** Illuminant C white (CIE 1931, x = 0.31006, y = 0.31616), Y = 1. */
    private val WhiteC = Vec3(0.31006 / 0.31616, 1.0, (1 - 0.31006 - 0.31616) / 0.31616)

    /** Bradford cone response matrix (Lam 1985), for the chromatic adaptation C → D65. */
    private val Bradford = Mat3.of(
        0.8951, 0.2664, -0.1614,
        -0.7502, 1.7135, 0.0367,
        0.0389, -0.0685, 1.0296
    )

    internal fun luvToXyzD65(l: Double, u: Double, v: Double): Vec3 {
        val w = WhiteC
        val un = 4 * w.x / (w.x + 15 * w.y + 3 * w.z)
        val vn = 9 * w.y / (w.x + 15 * w.y + 3 * w.z)
        val y = ((l + 16) / 116).pow(3)
        val up = u / (13 * l) + un
        val vp = v / (13 * l) + vn
        val xyzC = Vec3(y * 9 * up / (4 * vp), y, y * (12 - 3 * up - 20 * vp) / (4 * vp))
        val src = Bradford * w
        val dst = Bradford * Srgb.WhiteXyz
        val scale = Mat3.of(dst.x / src.x, 0.0, 0.0, 0.0, dst.y / src.y, 0.0, 0.0, 0.0, dst.z / src.z)
        return Bradford.inverse() * scale * Bradford * xyzC
    }

    /**
     * Vingrys and King-Smith's moment of inertia method (1988), after the CVD package's code
     * (Brian Foutch's implementation): the colour difference vectors between consecutive caps,
     * from the pilot on, give a major and a minor radius and an angle.
     * - [Score.angle]: the confusion axis, about +8° protan, −7° deutan, −80° tritan;
     * - [Score.cIndex]: the major radius over a perfect arrangement's (1 = perfect, > 1.78 abnormal);
     * - [Score.sIndex]: major over minor radius: selective (≥ 2) or random errors.
     */
    data class Score(val angle: Double, val cIndex: Double, val sIndex: Double, val totalError: Double) {
        val abnormal: Boolean get() = cIndex > ABNORMAL

        /** The axis the angle points to, when the arrangement is abnormal and its errors selective. */
        val axis: Axis?
            get() {
                if (!abnormal || sIndex < SELECTIVE) return null
                return referenceAngles.minBy { (_, ref) -> axialDistance(angle, ref) }.key
            }
    }

    /** [order]: cap numbers 1 to 15, in the order the person placed them after the pilot. */
    fun score(order: List<Int>): Score {
        require(order.sorted() == (1..15).toList()) { "an arrangement uses each cap 1 to 15 once" }
        var u2 = 0.0
        var v2 = 0.0
        var uvSum = 0.0
        for ((a, b) in (listOf(0) + order).zipWithNext()) {
            val du = uv[b].first - uv[a].first
            val dv = uv[b].second - uv[a].second
            u2 += du * du
            v2 += dv * dv
            uvSum += du * dv
        }
        val d = u2 - v2
        var a0 = if (d == 0.0) PI / 4 else atan(2 * uvSum / d) / 2
        var i0 = moment(u2, v2, uvSum, a0)
        var a1 = if (a0 < 0) a0 + PI / 2 else a0 - PI / 2
        var i1 = moment(u2, v2, uvSum, a1)
        if (i1 > i0) {
            a0 = a1.also { a1 = a0 }
            i0 = i1.also { i1 = i0 }
        }
        val r0 = sqrt(i0 / 15)
        val r1 = sqrt(i1 / 15)
        return Score(
            angle = a1 * 180 / PI,
            cIndex = r0 / PERFECT_MAJOR_RADIUS,
            sIndex = r0 / r1,
            totalError = sqrt(r0 * r0 + r1 * r1)
        )
    }

    private fun moment(u2: Double, v2: Double, uv: Double, a: Double) =
        u2 * sin(a).pow(2) + v2 * cos(a).pow(2) - 2 * uv * sin(a) * cos(a)

    /**
     * How someone who sees colours through [see] arranges the caps: from the pilot, always the
     * closest remaining cap (ΔE2000 between what they see). The usual model of a dichromat's
     * D-15, used to check the scoring.
     */
    fun arrangeAsSeenBy(see: (Rgb) -> Rgb): List<Int> {
        val seen = caps.map { Lab.fromRgb(see(it)) }
        val left = (1..15).toMutableList()
        val order = ArrayList<Int>()
        var last = 0
        while (left.isNotEmpty()) {
            val next = left.minBy { deltaE2000(seen[last], seen[it]) }
            order += next
            left -= next
            last = next
        }
        return order
    }

    /** Major radius of the perfect arrangement 1, 2… 15 (Vingrys and King-Smith's value). */
    private const val PERFECT_MAJOR_RADIUS = 9.234669
    const val ABNORMAL = 1.78
    const val SELECTIVE = 2.0

    /** Typical confusion angles (Vingrys and King-Smith 1988): protan +8°, deutan −7°, tritan −82°. */
    private val referenceAngles = mapOf(Axis.Protan to 8.0, Axis.Deutan to -7.0, Axis.Tritan to -82.0)

    /** Angles of axes, which have no direction: −82° and +98° are the same axis. */
    private fun axialDistance(a: Double, b: Double): Double {
        val d = abs(a - b) % 180
        return if (d > 90) 180 - d else d
    }
}
