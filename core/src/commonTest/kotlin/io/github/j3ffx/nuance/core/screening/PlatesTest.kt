package io.github.j3ffx.nuance.core.screening

import io.github.j3ffx.nuance.core.color.deltaE2000
import io.github.j3ffx.nuance.core.cvd.Axis
import io.github.j3ffx.nuance.core.cvd.CvdProfile
import io.github.j3ffx.nuance.core.cvd.Simulation
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Simulated checks of the plates; ScreeningTest takes the whole test with simulated observers. */
class PlatesTest {
    private val plates = Plates.set(Random(7))

    /** Figure/field difference of a plate, as [profile] sees it (null: typical vision). */
    private fun seen(plate: Plate, profile: CvdProfile?): Double {
        val sim = profile?.let { Simulation.rgb(it) } ?: { c -> c }
        return deltaE2000(sim(plate.figure), sim(plate.field))
    }

    /** Below this, the dots' random lightness hides the digit (ScreeningTest.PLATE_SEEN). */
    private val seenThreshold = ScreeningTest.PLATE_SEEN

    @Test
    fun eachPlateHidesItsDigitFromItsAxisOnly() {
        for (plate in plates.drop(1)) {
            val axis = plate.axis!!
            val label = "$axis plate ${plate.digit}${if (plate.subtle) " (subtle)" else ""}"
            // Typical vision: clearly visible.
            assertTrue(seen(plate, null) >= 6.5, "$label: typical sees only ${seen(plate, null)}")
            // The total form of its axis: the same colour.
            assertTrue(seen(plate, CvdProfile(axis, 1.0)) < 0.5, "$label: still visible to a total $axis")
            // A milder form misses the subtle plates, sees the strong ones.
            val half = seen(plate, CvdProfile(axis, 0.5))
            if (plate.subtle) {
                assertTrue(half < seenThreshold, "$label: a 50 % $axis sees $half")
            } else {
                assertTrue(half >= seenThreshold, "$label: a 50 % $axis sees only $half")
            }
            // The blue-yellow axis and the red-green ones don't hide each other's strong plates.
            for (other in Axis.entries.filter { (it == Axis.Tritan) != (axis == Axis.Tritan) }) {
                if (!plate.subtle) {
                    assertTrue(seen(plate, CvdProfile(other, 1.0)) >= seenThreshold, "$label: hidden from $other")
                }
            }
        }
    }

    @Test
    fun theDemoIsSeenByEveryone() {
        val demo = plates.first()
        for (axis in Axis.entries) assertTrue(seen(demo, CvdProfile(axis, 1.0)) >= 20, "demo hidden from $axis")
    }

    @Test
    fun aSetHasTheDemoFirstThenFourPlatesPerAxis() {
        assertEquals(null, plates.first().axis)
        assertEquals(13, plates.size)
        for (axis in Axis.entries) {
            assertEquals(Plates.PER_AXIS, plates.count { it.axis == axis })
            assertEquals(Plates.PER_AXIS, plates.filter { it.axis == axis }.map { it.digit }.toSet().size)
        }
        assertTrue(plates.all { it.digit in 1..9 })
    }

    @Test
    fun dotsFillThePlateWithoutOverlapping() {
        val dots = plates[1].dots
        assertTrue(dots.size > 500, "only ${dots.size} dots")
        for (i in dots.indices) {
            for (j in i + 1 until dots.size) {
                val a = dots[i]
                val b = dots[j]
                val d = kotlin.math.hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble())
                assertTrue(d >= a.radius + b.radius, "dots $i and $j overlap")
            }
        }
    }
}
