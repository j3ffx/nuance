package io.github.j3ffx.nuance.core.color

import io.github.j3ffx.nuance.core.Rgb
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * sRGB (IEC 61966-2-1): the transfer function, and linear sRGB ↔ CIE XYZ (D65).
 * Every model and correction works on *linear* values: decode first, encode after.
 */
object Srgb {
    /** Gamma-encoded channel (0..1) → linear light. */
    fun decode(v: Double): Double = if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)

    /** Linear light → gamma-encoded channel. Values outside 0..1 keep their sign (no clamping). */
    fun encode(v: Double): Double = when {
        v <= 0.0031308 && v >= -0.0031308 -> v * 12.92
        v < 0 -> -encode(-v)
        else -> 1.055 * v.pow(1 / 2.4) - 0.055
    }

    fun toLinear(c: Rgb) = Vec3(decode(c.r.toDouble()), decode(c.g.toDouble()), decode(c.b.toDouble()))

    /** Linear → encoded, unclamped: callers decide what to do out of gamut. */
    fun fromLinear(v: Vec3) = Rgb(encode(v.x).toFloat(), encode(v.y).toFloat(), encode(v.z).toFloat())

    /** IEC 61966-2-1 primaries and D65 white point. */
    val LinearToXyz = Mat3.of(
        0.4124564, 0.3575761, 0.1804375,
        0.2126729, 0.7151522, 0.0721750,
        0.0193339, 0.1191920, 0.9503041
    )
    val XyzToLinear = LinearToXyz.inverse()

    /** XYZ of sRGB white (1, 1, 1): the D65 reference white of every Lab conversion here. */
    val WhiteXyz = LinearToXyz * Vec3.One

    fun inGamut(linear: Vec3, tolerance: Double = 1e-9) = linear.x in -tolerance..1 + tolerance &&
        linear.y in -tolerance..1 + tolerance &&
        linear.z in -tolerance..1 + tolerance
}

/** CIE 1976 L*a*b* relative to sRGB's D65 white, and its polar form LCh. */
object Lab {
    private const val EPSILON = 216.0 / 24389.0
    private const val KAPPA = 24389.0 / 27.0

    fun fromXyz(xyz: Vec3): Vec3 {
        val w = Srgb.WhiteXyz
        fun f(t: Double) = if (t > EPSILON) cbrt(t) else (KAPPA * t + 16) / 116
        val fx = f(xyz.x / w.x)
        val fy = f(xyz.y / w.y)
        val fz = f(xyz.z / w.z)
        return Vec3(116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz))
    }

    fun toXyz(lab: Vec3): Vec3 {
        val w = Srgb.WhiteXyz
        val fy = (lab.x + 16) / 116
        val fx = fy + lab.y / 500
        val fz = fy - lab.z / 200
        fun inv(f: Double) = if (f * f * f > EPSILON) f * f * f else (116 * f - 16) / KAPPA
        val y = if (lab.x > KAPPA * EPSILON) fy * fy * fy else lab.x / KAPPA
        return Vec3(inv(fx) * w.x, y * w.y, inv(fz) * w.z)
    }

    fun fromLinear(linear: Vec3) = fromXyz(Srgb.LinearToXyz * linear)

    fun toLinear(lab: Vec3) = Srgb.XyzToLinear * toXyz(lab)

    fun fromRgb(c: Rgb) = fromLinear(Srgb.toLinear(c))

    /** (L, C, h°) with h in 0 until 360. */
    fun toLch(lab: Vec3) = Vec3(lab.x, sqrt(lab.y * lab.y + lab.z * lab.z), hueDegrees(lab.y, lab.z))
}

/**
 * Oklab (Björn Ottosson, 2020, https://bottosson.github.io/posts/oklab/): a perceptual space where
 * lightness, chroma and hue are well separated. Used for contrast, saturation and hue isolation.
 */
object Oklab {
    fun fromLinear(c: Vec3): Vec3 {
        val l = cbrt(0.4122214708 * c.x + 0.5363325363 * c.y + 0.0514459929 * c.z)
        val m = cbrt(0.2119034982 * c.x + 0.6806995451 * c.y + 0.1073969566 * c.z)
        val s = cbrt(0.0883024619 * c.x + 0.2817188376 * c.y + 0.6299787005 * c.z)
        return Vec3(
            0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
            1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
            0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s
        )
    }

    fun toLinear(lab: Vec3): Vec3 {
        val l = lab.x + 0.3963377774 * lab.y + 0.2158037573 * lab.z
        val m = lab.x - 0.1055613458 * lab.y - 0.0638541728 * lab.z
        val s = lab.x - 0.0894841775 * lab.y - 1.2914855480 * lab.z
        val l3 = l * l * l
        val m3 = m * m * m
        val s3 = s * s * s
        return Vec3(
            4.0767416621 * l3 - 3.3077115913 * m3 + 0.2309699292 * s3,
            -1.2684380046 * l3 + 2.6097574011 * m3 - 0.3413193965 * s3,
            -0.0041960863 * l3 - 0.7034186147 * m3 + 1.7076147010 * s3
        )
    }

    /** (L, C, h°) with h in 0 until 360. */
    fun toLch(lab: Vec3) = Vec3(lab.x, sqrt(lab.y * lab.y + lab.z * lab.z), hueDegrees(lab.y, lab.z))

    fun fromLch(lch: Vec3): Vec3 {
        val h = lch.z * DEG
        return Vec3(lch.x, lch.y * cos(h), lch.y * sin(h))
    }
}

/**
 * CIEDE2000 colour difference, after Sharma, Wu and Dalal (2005), "The CIEDE2000 color-difference
 * formula: implementation notes, supplementary test data, and mathematical observations",
 * Color Research & Application 30(1). kL = kC = kH = 1. About 1 is a just-noticeable difference.
 */
fun deltaE2000(lab1: Vec3, lab2: Vec3): Double {
    val (l1, a1, b1) = lab1
    val (l2, a2, b2) = lab2
    val c1 = sqrt(a1 * a1 + b1 * b1)
    val c2 = sqrt(a2 * a2 + b2 * b2)
    val cMean7 = ((c1 + c2) / 2).pow(7)
    val g = 0.5 * (1 - sqrt(cMean7 / (cMean7 + POW25_7)))
    val a1p = (1 + g) * a1
    val a2p = (1 + g) * a2
    val c1p = sqrt(a1p * a1p + b1 * b1)
    val c2p = sqrt(a2p * a2p + b2 * b2)
    val h1p = if (a1p == 0.0 && b1 == 0.0) 0.0 else hueDegrees(a1p, b1)
    val h2p = if (a2p == 0.0 && b2 == 0.0) 0.0 else hueDegrees(a2p, b2)

    val dLp = l2 - l1
    val dCp = c2p - c1p
    val dhp = when {
        c1p * c2p == 0.0 -> 0.0
        abs(h2p - h1p) <= 180 -> h2p - h1p
        h2p - h1p > 180 -> h2p - h1p - 360
        else -> h2p - h1p + 360
    }
    val dHp = 2 * sqrt(c1p * c2p) * sin(dhp / 2 * DEG)

    val lMean = (l1 + l2) / 2
    val cMeanP = (c1p + c2p) / 2
    val hMeanP = when {
        c1p * c2p == 0.0 -> h1p + h2p
        abs(h1p - h2p) <= 180 -> (h1p + h2p) / 2
        h1p + h2p < 360 -> (h1p + h2p + 360) / 2
        else -> (h1p + h2p - 360) / 2
    }
    val t = 1 - 0.17 * cos((hMeanP - 30) * DEG) + 0.24 * cos(2 * hMeanP * DEG) +
        0.32 * cos((3 * hMeanP + 6) * DEG) - 0.20 * cos((4 * hMeanP - 63) * DEG)
    val dTheta = 30 * kotlin.math.exp(-((hMeanP - 275) / 25).pow(2))
    val cMeanP7 = cMeanP.pow(7)
    val rc = 2 * sqrt(cMeanP7 / (cMeanP7 + POW25_7))
    val sl = 1 + 0.015 * (lMean - 50).pow(2) / sqrt(20 + (lMean - 50).pow(2))
    val sc = 1 + 0.045 * cMeanP
    val sh = 1 + 0.015 * cMeanP * t
    val rt = -sin(2 * dTheta * DEG) * rc
    return sqrt(
        (dLp / sl).pow(2) + (dCp / sc).pow(2) + (dHp / sh).pow(2) + rt * (dCp / sc) * (dHp / sh)
    )
}

/** ΔE2000 between two sRGB colours. */
fun deltaE2000(c1: Rgb, c2: Rgb) = deltaE2000(Lab.fromRgb(c1), Lab.fromRgb(c2))

private const val DEG = kotlin.math.PI / 180
private val POW25_7 = 25.0.pow(7)

/** atan2 in degrees, in 0 until 360. */
internal fun hueDegrees(a: Double, b: Double): Double {
    val h = atan2(b, a) / DEG
    return if (h < 0) h + 360 else h
}
