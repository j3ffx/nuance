package io.github.j3ffx.nuance.core.cvd

import io.github.j3ffx.nuance.core.color.Vec3
import io.github.j3ffx.nuance.core.correction.CorrectionMetrics
import io.github.j3ffx.nuance.core.correction.CorrectionStrategy
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Profiles mixing several axes (docs/ALGORITHMS.md, "Profils mixtes"). */
class MixedProfileTest {
    private val samples = CorrectionMetrics.grid().filterIndexed { i, _ -> i % 7 == 0 }
    private val severities = listOf(0.1, 0.5, 0.9, 0.95, 1.0)

    private fun distance(a: Vec3, b: Vec3) = maxOf(abs(a.x - b.x), abs(a.y - b.y), abs(a.z - b.z))

    @Test
    fun oneAxisIsSimulatedExactlyAsBefore() {
        val brettel = Brettel1997(Axis.Tritan)
        for (s in severities) {
            for (axis in listOf(Axis.Protan, Axis.Deutan)) {
                val m = Simulation.redGreenMatrix(axis, s)
                val f = Simulation.linear(CvdProfile(axis, s))
                for (c in samples) assertEquals(m * c, f(c), "$axis $s")
            }
            val f = Simulation.linear(CvdProfile(Axis.Tritan, s))
            for (c in samples) assertEquals(Vec3.lerp(c, brettel.simulate(c), s), f(c), "tritan $s")
        }
    }

    @Test
    fun noAxisIsTheIdentity() {
        val f = Simulation.linear(CvdProfile())
        for (c in samples) assertEquals(c, f(c))
    }

    @Test
    fun mixesKeepGreys() {
        val mixes = listOf(CvdProfile(1.0, 1.0, 0.0), CvdProfile(1.0, 0.0, 0.3), CvdProfile(0.4, 0.6, 1.0))
        for (p in mixes) {
            val f = Simulation.linear(p)
            for (v in listOf(0.05, 0.2, 0.5, 1.0)) {
                val grey = Vec3(v, v, v)
                assertTrue(distance(grey, f(grey)) < 2e-3, "$p moved grey $v to ${f(grey)}")
            }
        }
    }

    @Test
    fun aSmallSecondAxisChangesLittle() {
        // Continuity: adding 1 % of another axis can't jump.
        for ((base, axis) in listOf(
            CvdProfile(Axis.Protan, 1.0) to Axis.Deutan,
            CvdProfile(Axis.Deutan, 0.6) to Axis.Tritan
        )) {
            val f = Simulation.linear(base)
            val g = Simulation.linear(base.with(axis, 0.01))
            for (c in samples) assertTrue(distance(f(c), g(c)) < 0.02, "$base + 1 % $axis")
        }
    }

    @Test
    fun theMainAxisIsTheStrongest() {
        assertEquals(Axis.Protan, CvdProfile(1.0, 0.0, 0.3).axis)
        assertEquals(Axis.Tritan, CvdProfile(0.2, 0.0, 0.5).axis)
        assertEquals(Axis.Deutan, CvdProfile().axis, "none: deutan, the most common")
        assertEquals(listOf(Axis.Protan, Axis.Tritan), CvdProfile(1.0, 0.0, 0.3).components)
        assertTrue(CvdProfile(1.0, 0.0, 0.3).isMixed)
        assertTrue(!CvdProfile(Axis.Tritan, 0.4).isMixed)
    }

    @Test
    fun scalingKeepsTheProportions() {
        val p = CvdProfile(1.0, 0.0, 0.3).scaledTo(0.5)
        assertEquals(0.5, p.protan, 1e-12)
        assertEquals(0.15, p.tritan, 1e-12)
        assertEquals(CvdProfile(Axis.Deutan, 0.4), CvdProfile().scaledTo(0.4))
    }

    @Test
    fun correctionsSeparateTheColoursOfEachAxisOfAMix() {
        // Protan + some tritan, the documented kind of mix (inherited red-green, acquired blue-yellow).
        val mix = CvdProfile(1.0, 0.0, 0.5)
        for (strategy in CorrectionStrategy.all) {
            val r = CorrectionMetrics.evaluate(strategy, mix, 1.0)
            assertTrue(r.pairs > 0)
            assertTrue(r.distinctAfter > r.distinctBefore, "${strategy.id}: ${r.distinctBefore} → ${r.distinctAfter}")
            assertTrue(r.worstGrayChange < 1.0, "${strategy.id} moved a grey by ${r.worstGrayChange}")
        }
    }
}
