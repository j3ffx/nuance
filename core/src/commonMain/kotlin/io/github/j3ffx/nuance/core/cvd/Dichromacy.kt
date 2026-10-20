package io.github.j3ffx.nuance.core.cvd

import io.github.j3ffx.nuance.core.color.Mat3
import io.github.j3ffx.nuance.core.color.Vec3

/**
 * Cone space and the two classic dichromacy models, derived here from first principles rather than
 * copied: a test checks the result against DaltonLens' published pre-computed values (libDaltonLens,
 * public domain, https://daltonlens.org/understanding-cvd-simulation/).
 */
internal object Lms {
    /**
     * Smith and Pokorny (1975) cone fundamentals as a Judd-Vos XYZ → LMS matrix, the model used by
     * Viénot, Brettel and Mollon (1999). Each row's scale doesn't matter: dichromacy is a projection.
     */
    private val XyzJvToLms = Mat3.of(
        0.15514, 0.54312, -0.03286,
        -0.15514, 0.45684, 0.03286,
        0.0, 0.0, 0.00801
    )

    /**
     * Linear sRGB → Judd-Vos corrected XYZ, the space Smith and Pokorny's fundamentals are defined
     * in. As in Viénot 1999, approximated linearly from the primaries' and white's chromaticities in
     * Judd-Vos coordinates; values from DaltonLens-Python (MIT), XYZJuddVos_from_linearRGB_BT709.
     * A test recomputes it from the chromaticities ([rgbToXyz]).
     */
    val XyzJvFromLinearRgb = Mat3.of(
        0.409568, 0.355041, 0.179167,
        0.213389, 0.706743, 0.0798680,
        0.0186297, 0.114620, 0.912367
    )

    val FromLinearRgb = XyzJvToLms * XyzJvFromLinearRgb
    val ToLinearRgb = FromLinearRgb.inverse()

    /** LMS of sRGB white: the neutral axis every dichromat keeps. */
    val White = FromLinearRgb * Vec3.One

    /**
     * LMS of a monochromatic light, from the Judd-Vos modified CIE 1931 2° colour matching
     * functions (Vos 1978; CVRL table ciexyzjv.csv, www.cvrl.org).
     */
    fun ofWavelength(nm: Int): Vec3 {
        val xyz = when (nm) {
            475 -> Vec3(0.13287, 0.11284, 0.9422)
            485 -> Vec3(0.056985, 0.16987, 0.5864)
            575 -> Vec3(0.84394, 0.91558, 0.0019706)
            660 -> Vec3(0.16161, 0.061, 0.000011906)
            else -> throw IllegalArgumentException("no CMF value kept for $nm nm")
        }
        return XyzJvToLms * xyz
    }

    /** RGB → XYZ for primaries and white given as xy chromaticities (white normalised to Y = 1). */
    internal fun rgbToXyz(r: DoubleArray, g: DoubleArray, b: DoubleArray, w: DoubleArray): Mat3 {
        fun column(xy: DoubleArray) = Vec3(xy[0] / xy[1], 1.0, (1 - xy[0] - xy[1]) / xy[1])
        val cr = column(r)
        val cg = column(g)
        val cb = column(b)
        val primaries = Mat3.of(cr.x, cg.x, cb.x, cr.y, cg.y, cb.y, cr.z, cg.z, cb.z)
        val s = primaries.inverse() * column(w)
        return Mat3.of(
            cr.x * s.x, cg.x * s.y, cb.x * s.z,
            cr.y * s.x, cg.y * s.y, cb.y * s.z,
            cr.z * s.x, cg.z * s.y, cb.z * s.z
        )
    }

    fun axis(cone: Int) = Vec3(if (cone == 0) 1.0 else 0.0, if (cone == 1) 1.0 else 0.0, if (cone == 2) 1.0 else 0.0)
}

/**
 * Replaces the missing cone's response by its value on the plane (through black) of normal [n]:
 * every colour along that cone's axis lands on the same point, which is what "confusion" means.
 */
internal fun projectionAlongCone(cone: Int, n: Vec3): Mat3 {
    val rows = Array(3) { i -> Lms.axis(i) }
    rows[cone] = Vec3(
        if (cone == 0) 0.0 else -n.x / n[cone],
        if (cone == 1) 0.0 else -n.y / n[cone],
        if (cone == 2) 0.0 else -n.z / n[cone]
    )
    return Mat3.of(
        rows[0].x, rows[0].y, rows[0].z,
        rows[1].x, rows[1].y, rows[1].z,
        rows[2].x, rows[2].y, rows[2].z
    )
}

/**
 * Viénot, Brettel and Mollon (1999), "Digital video colourmaps for checking the legibility of
 * displays by dichromats", Color Research & Application 24(4). Protanopia and deuteranopia as one
 * projection onto the plane holding white and the display's blue (hence its yellow too), so whites,
 * blues and yellows are unchanged. Not valid for tritanopia (Brettel 1997 is used there).
 */
internal object Vienot1999 {
    fun matrix(axis: Axis): Mat3 {
        require(axis != Axis.Tritan) { "Viénot 1999 does not model tritanopia" }
        val blue = Lms.FromLinearRgb * Vec3(0.0, 0.0, 1.0)
        val projection = projectionAlongCone(axis.cone, Lms.White cross blue)
        return Lms.ToLinearRgb * projection * Lms.FromLinearRgb
    }
}

/**
 * Brettel, Viénot and Mollon (1997), "Computerized simulation of color appearance for dichromats",
 * JOSA A 14(10). Two half-planes through white, anchored on monochromatic lights a dichromat sees
 * like everyone: 475 and 575 nm for protans and deutans, 485 and 660 nm for tritans. As in
 * DaltonLens and Vischeck, white is sRGB white rather than equal-energy white, which keeps more
 * colours inside the sRGB gamut.
 */
internal class Brettel1997(axis: Axis) {
    private val anchors = when (axis) {
        Axis.Protan, Axis.Deutan -> 475 to 575
        Axis.Tritan -> 485 to 660
    }
    private val anchor1 = Lms.ofWavelength(anchors.first)
    private val anchor2 = Lms.ofWavelength(anchors.second)

    /** Whole pipeline (linear RGB → LMS → projection → linear RGB) for each half-plane. */
    val matrix1: Mat3 = halfPlane(axis, anchor1)
    val matrix2: Mat3 = halfPlane(axis, anchor2)

    /**
     * Normal, in linear RGB, of the plane holding white and the missing cone's axis: it splits
     * colour space between the two half-planes. A colour uses the half-plane of the anchor on its side.
     */
    val separationInRgb: Vec3
    private val anchor1Side: Boolean

    init {
        val normalLms = Lms.White cross Lms.axis(axis.cone)
        // n · (M c) = (Mᵀ n) · c
        separationInRgb = Vec3(
            (0..2).sumOf { normalLms[it] * Lms.FromLinearRgb[it, 0] },
            (0..2).sumOf { normalLms[it] * Lms.FromLinearRgb[it, 1] },
            (0..2).sumOf { normalLms[it] * Lms.FromLinearRgb[it, 2] }
        )
        anchor1Side = (normalLms dot anchor1) >= 0
    }

    fun simulate(linear: Vec3): Vec3 {
        val side = (separationInRgb dot linear) >= 0
        return (if (side == anchor1Side) matrix1 else matrix2) * linear
    }

    private fun halfPlane(axis: Axis, anchor: Vec3) =
        Lms.ToLinearRgb * projectionAlongCone(axis.cone, Lms.White cross anchor) * Lms.FromLinearRgb
}
