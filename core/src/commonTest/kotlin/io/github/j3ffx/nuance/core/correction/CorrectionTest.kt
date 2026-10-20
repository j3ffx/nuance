package io.github.j3ffx.nuance.core.correction

import io.github.j3ffx.nuance.core.color.Lab
import io.github.j3ffx.nuance.core.color.Srgb
import io.github.j3ffx.nuance.core.color.Vec3
import io.github.j3ffx.nuance.core.color.deltaE2000
import io.github.j3ffx.nuance.core.cvd.Axis
import io.github.j3ffx.nuance.core.cvd.CvdProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CorrectionTest {
    @Test
    fun zeroIntensityLeavesTheImageAlone() {
        for (strategy in CorrectionStrategy.all) {
            for (axis in Axis.entries) {
                val f = strategy.build(CvdProfile(axis, 1.0), 0.0)
                val c = Vec3(0.7, 0.2, 0.1)
                assertEquals(c, f(c))
            }
        }
    }

    @Test
    fun graysAndWhiteStayPut() {
        for (strategy in CorrectionStrategy.all) {
            for (axis in Axis.entries) {
                for (severity in listOf(0.3, 0.7, 1.0)) {
                    val f = strategy.build(CvdProfile(axis, severity), 1.0)
                    for (g in listOf(0.0, 0.05, 0.2, 0.5, 1.0)) {
                        val gray = Vec3.One * g
                        val e = deltaE2000(Lab.fromLinear(gray), Lab.fromLinear(CorrectionMetrics.clampLinear(f(gray))))
                        assertTrue(e < 0.5, "${strategy.id} $axis $severity: grey $g moved by ΔE $e")
                    }
                }
            }
        }
    }

    @Test
    fun correctionSeparatesColoursTheProfileConfuses() {
        // Floors set from the measurements in docs/ALGORITHMS.md, with a margin: a regression in
        // either strategy shows up here. Tritan confusion lines are short in CIELAB, hence lower floors.
        val floors = mapOf(Axis.Protan to 0.75, Axis.Deutan to 0.75, Axis.Tritan to 0.3)
        for ((axis, floor) in floors) {
            val r = CorrectionMetrics.evaluate(CorrectionStrategy.Default, CvdProfile(axis, 1.0), 1.0)
            assertTrue(r.pairs > 100, "$axis: only ${r.pairs} confused pairs")
            assertTrue(r.meanBefore < 1.0, "$axis: the total form should merge the pairs (ΔE ${r.meanBefore})")
            assertTrue(r.meanAfter > r.meanBefore + 4, "$axis: mean ΔE ${r.meanBefore} → ${r.meanAfter}")
            assertTrue(r.distinctAfter >= floor, "$axis: only ${r.distinctAfter} of pairs told apart")
        }
    }

    @Test
    fun defaultCorrectionKeepsSkinTonesPlausible() {
        for (axis in Axis.entries) {
            val r = CorrectionMetrics.evaluate(CorrectionStrategy.Default, CvdProfile(axis, 0.6), 0.7)
            // Skin keeps its hue family (orange-brown), and doesn't change more than the image does.
            assertTrue(r.skinHueShift < 20, "$axis: skin hue moves by up to ${r.skinHueShift}°")
            assertTrue(
                r.skinChange <= r.meanChange,
                "$axis: skin moves more (${r.skinChange}) than the rest (${r.meanChange})"
            )
        }
    }

    @Test
    fun confusionPairsAreRealConfusions() {
        val pairs = CorrectionMetrics.confusionPairs(CvdProfile(Axis.Deutan, 1.0))
        assertTrue(pairs.size > 300)
        for ((a, b) in pairs) {
            assertTrue(Srgb.inGamut(a) && Srgb.inGamut(b))
            assertTrue(deltaE2000(Lab.fromLinear(a), Lab.fromLinear(b)) >= 8, "typical vision tells them apart")
        }
    }
}
