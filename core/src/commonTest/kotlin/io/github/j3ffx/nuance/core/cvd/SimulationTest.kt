package io.github.j3ffx.nuance.core.cvd

import io.github.j3ffx.nuance.core.Rgb
import io.github.j3ffx.nuance.core.color.Lab
import io.github.j3ffx.nuance.core.color.Mat3
import io.github.j3ffx.nuance.core.color.Srgb
import io.github.j3ffx.nuance.core.color.Vec3
import io.github.j3ffx.nuance.core.color.deltaE2000
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SimulationTest {
    private fun assertMatrix(expected: DoubleArray, actual: Mat3, tolerance: Double, message: String) {
        val a = actual.toArray()
        val worst = expected.indices.maxOf { abs(expected[it] - a[it]) }
        assertTrue(worst <= tolerance, "$message: expected ${expected.toList()}, got $actual (off by $worst)")
    }

    @Test
    fun juddVosMatrixFollowsFromThePrimariesChromaticities() {
        val derived = Lms.rgbToXyz(
            doubleArrayOf(0.6384, 0.3326),
            doubleArrayOf(0.3018, 0.6008),
            doubleArrayOf(0.1530, 0.0682),
            doubleArrayOf(0.3157, 0.3345)
        )
        // The published matrix used rounded chromaticities: agreement within 2e-4.
        assertMatrix(Lms.XyzJvFromLinearRgb.toArray(), derived, 2e-4, "Judd-Vos XYZ")
    }

    @Test
    fun vienotDerivationMatchesDaltonLens() {
        // libDaltonLens.c, dl_vienot_protan_rgbCvd_from_rgb and dl_vienot_deutan_rgbCvd_from_rgb.
        assertMatrix(
            doubleArrayOf(0.11238, 0.88762, 0.0, 0.11238, 0.88762, 0.0, 0.00401, -0.00401, 1.0),
            Vienot1999.matrix(Axis.Protan),
            2e-4,
            "protan"
        )
        assertMatrix(
            doubleArrayOf(0.29275, 0.70725, 0.0, 0.29275, 0.70725, 0.0, -0.02234, 0.02234, 1.0),
            Vienot1999.matrix(Axis.Deutan),
            2e-4,
            "deutan"
        )
    }

    @Test
    fun brettelDerivationMatchesDaltonLens() {
        // libDaltonLens.c, brettel_*_params: the two half-plane matrices and the separation normal.
        val expected = mapOf(
            Axis.Protan to Triple(
                doubleArrayOf(0.14980, 1.19548, -0.34528, 0.10764, 0.84864, 0.04372, 0.00384, -0.00540, 1.00156),
                doubleArrayOf(0.14570, 1.16172, -0.30742, 0.10816, 0.85291, 0.03892, 0.00386, -0.00524, 1.00139),
                Vec3(0.00048, 0.00393, -0.00441)
            ),
            Axis.Deutan to Triple(
                doubleArrayOf(0.36477, 0.86381, -0.22858, 0.26294, 0.64245, 0.09462, -0.02006, 0.02728, 0.99278),
                doubleArrayOf(0.37298, 0.88166, -0.25464, 0.25954, 0.63506, 0.10540, -0.01980, 0.02784, 0.99196),
                Vec3(-0.00281, -0.00611, 0.00892)
            ),
            Axis.Tritan to Triple(
                doubleArrayOf(1.01277, 0.13548, -0.14826, -0.01243, 0.86812, 0.14431, 0.07589, 0.80500, 0.11911),
                doubleArrayOf(0.93678, 0.18979, -0.12657, 0.06154, 0.81526, 0.12320, -0.37562, 1.12767, 0.24796),
                Vec3(0.03901, -0.02788, -0.01113)
            )
        )
        for ((axis, values) in expected) {
            val b = Brettel1997(axis)
            val (m1, m2, normal) = values
            // DaltonLens orders the half-planes by the sign of the separation test; match either order.
            val direct = runCatching {
                assertMatrix(m1, b.matrix1, 2e-3, "$axis plane 1")
                assertMatrix(m2, b.matrix2, 2e-3, "$axis plane 2")
            }
            if (direct.isFailure) {
                assertMatrix(m1, b.matrix2, 2e-3, "$axis plane 1")
                assertMatrix(m2, b.matrix1, 2e-3, "$axis plane 2")
            }
            // Same plane up to scale and sign: the normals are parallel.
            val n = b.separationInRgb
            val cosine = abs(n dot normal) / kotlin.math.sqrt((n dot n) * (normal dot normal))
            assertTrue(cosine > 0.9999, "$axis separation normal $n is not parallel to $normal")
            // And each colour uses the half-plane DaltonLens would pick.
            for (c in sampleColours()) {
                val daltonLens = if ((normal dot c) >= 0) m1 else m2
                val expectedOut = Mat3(daltonLens) * c
                val out = b.simulate(c)
                assertTrue((out - expectedOut).let { maxOf(abs(it.x), abs(it.y), abs(it.z)) } < 3e-3, "$axis $c")
            }
        }
    }

    @Test
    fun machadoTableMatchesTheAuthorsPage() {
        // A few matrices as printed on the authors' page.
        assertMatrix(
            doubleArrayOf(0.152286, 1.052583, -0.204868, 0.114503, 0.786281, 0.099216, -0.003882, -0.048116, 1.051998),
            Machado2009.matrix(Axis.Protan, 1.0),
            1e-9,
            "protanomaly 1.0"
        )
        assertMatrix(
            doubleArrayOf(0.547494, 0.607765, -0.155259, 0.181692, 0.781742, 0.036566, -0.010410, 0.027275, 0.983136),
            Machado2009.matrix(Axis.Deutan, 0.5),
            1e-9,
            "deuteranomaly 0.5"
        )
        assertMatrix(
            doubleArrayOf(1.017277, 0.027029, -0.044306, -0.006113, 0.958479, 0.047634, 0.006379, 0.248708, 0.744913),
            Machado2009.matrix(Axis.Tritan, 0.5),
            1e-9,
            "tritanomaly 0.5"
        )
        // Between two steps, the matrix is interpolated.
        val half = Mat3.lerp(Machado2009.matrix(Axis.Protan, 0.5), Machado2009.matrix(Axis.Protan, 0.6), 0.5)
        assertMatrix(half.toArray(), Machado2009.matrix(Axis.Protan, 0.55), 1e-12, "protanomaly 0.55")
    }

    @Test
    fun zeroSeverityIsTheIdentity() {
        for (axis in Axis.entries) {
            val sim = Simulation.rgb(CvdProfile(axis, 0.0))
            for (c in listOf(Rgb(1f, 0f, 0f), Rgb(0.2f, 0.7f, 0.4f), Rgb(0.5f, 0.5f, 0.9f))) assertEquals(c, sim(c))
        }
    }

    @Test
    fun graysAndWhiteStayNeutralAtEverySeverity() {
        for (axis in Axis.entries) {
            for (step in 0..10) {
                val sim = Simulation.rgb(CvdProfile(axis, step / 10.0))
                for (g in listOf(0f, 0.1f, 0.5f, 0.8f, 1f)) {
                    val gray = Rgb(g, g, g)
                    val e = deltaE2000(gray, sim(gray))
                    assertTrue(e < 1.0, "$axis ${step * 10} %: gray $g moved by ΔE $e")
                }
            }
        }
    }

    @Test
    fun totalFormsMergeEachConfusionLine() {
        // Two colours that differ only in the missing cone's response: a dichromat can't tell them
        // apart, so the simulation must give the same colour.
        for (axis in Axis.entries) {
            val sim = Simulation.linear(CvdProfile(axis, 1.0))
            var checked = 0
            for (c in sampleColours()) {
                val lms = Lms.FromLinearRgb * c
                val shift = Lms.axis(axis.cone) * (lms[axis.cone] * 0.15)
                val other = Lms.ToLinearRgb * (lms + shift)
                if (!Srgb.inGamut(other)) continue
                val e = deltaE2000(Lab.fromLinear(sim(c)), Lab.fromLinear(sim(other)))
                // Viénot is exact; Brettel is exact within a half-plane, and both colours share one here.
                assertTrue(e < 0.5, "$axis: $c and $other still differ by ΔE $e")
                checked++
            }
            assertTrue(checked > 20, "$axis: only $checked pairs in gamut")
        }
    }

    @Test
    fun severityChangesSmoothlyUpToTheTotalForm() {
        val c = Rgb(0.8f, 0.3f, 0.2f)
        for (axis in Axis.entries) {
            var previous = c
            for (step in 1..100) {
                val now = Simulation.rgb(CvdProfile(axis, step / 100.0))(c)
                assertTrue(deltaE2000(previous, now) < 3.0, "$axis jumps at ${step - 1}→$step %")
                previous = now
            }
        }
    }

    @Test
    fun simulationWorksOnLinearLightNotOnEncodedValues() {
        // The classic mistake is to apply the matrix to gamma-encoded values. Pure red seen by a
        // protanope (Viénot 1999): linear (0.112, 0.112, 0.004) → sRGB (94, 94, 13). On encoded
        // values, the same matrix would give (29, 29, 1), far too dark.
        val out = Simulation.rgb(CvdProfile(Axis.Protan, 1.0))(Rgb(1f, 0f, 0f))
        assertEquals(listOf(94, 94, 13), listOf(out.r, out.g, out.b).map { (it * 255).roundToInt() })
    }

    private fun sampleColours() = buildList {
        val steps = listOf(0.05, 0.2, 0.4, 0.6, 0.8, 0.95)
        for (r in steps) for (g in steps) for (b in steps) add(Vec3(r, g, b))
    }
}
