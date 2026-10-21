package io.github.j3ffx.nuance.core.screening

import io.github.j3ffx.nuance.core.color.Srgb
import io.github.j3ffx.nuance.core.cvd.Axis
import io.github.j3ffx.nuance.core.cvd.CvdProfile
import io.github.j3ffx.nuance.core.cvd.Simulation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class D15Test {
    @Test
    fun capsAreInGamutAndMidGrey() {
        for ((i, c) in D15.caps.withIndex()) {
            val linear = Srgb.toLinear(c)
            assertTrue(listOf(c.r, c.g, c.b).all { it in 0f..1f }, "cap $i out of gamut: $c")
            val y = Srgb.LinearToXyz.row(1) dot linear
            assertEquals(0.1977, y, 0.002, "cap $i luminance")
        }
    }

    @Test
    fun thePerfectArrangementScoresOne() {
        val score = D15.score((1..15).toList())
        assertEquals(1.0, score.cIndex, 0.001)
        assertNull(score.axis)
    }

    @Test
    fun typicalVisionArrangesTheCapsInOrder() {
        assertEquals((1..15).toList(), D15.arrangeAsSeenBy { it })
    }

    @Test
    fun simulatedDichromatsLandOnTheirAxis() {
        for (axis in Axis.entries) {
            val order = D15.arrangeAsSeenBy(Simulation.rgb(CvdProfile(axis, 1.0)))
            val score = D15.score(order)
            println("$axis: $order angle ${score.angle} C ${score.cIndex} S ${score.sIndex}")
            assertEquals(axis, score.axis, "$axis arranged $order, scored $score")
        }
    }

    @Test
    fun publishedDichromatArrangementsLandOnTheirAxis() {
        // Classic arrangements from Farnsworth's manual (as reproduced in the D-15 literature).
        val protan = listOf(15, 1, 14, 2, 13, 3, 12, 4, 11, 5, 10, 6, 9, 7, 8)
        val deutan = listOf(1, 15, 2, 3, 14, 13, 4, 12, 5, 11, 6, 10, 7, 9, 8)
        val tritan = listOf(1, 2, 3, 4, 5, 6, 7, 15, 8, 14, 9, 13, 10, 11, 12)
        assertEquals(Axis.Protan, D15.score(protan).axis)
        assertEquals(Axis.Deutan, D15.score(deutan).axis)
        assertEquals(Axis.Tritan, D15.score(tritan).axis)
    }
}
