package io.github.j3ffx.nuance.core.screening

import io.github.j3ffx.nuance.core.Rgb
import io.github.j3ffx.nuance.core.color.Vec3
import io.github.j3ffx.nuance.core.color.deltaE2000
import io.github.j3ffx.nuance.core.cvd.Axis
import io.github.j3ffx.nuance.core.cvd.CvdProfile
import io.github.j3ffx.nuance.core.cvd.Simulation
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The test of the test: an observer who sees through a simulation model takes the whole test.
 * - a plate's digit is seen when figure and field differ by at least [PLATE_SEEN] once simulated
 *   (the dots' random lightness hides smaller differences);
 * - the caps are arranged by nearest simulated colour (D15.arrangeAsSeenBy);
 * - the odd patch is found when it differs by at least Staircase.JND once simulated, else "no difference".
 */
class ScreeningTest {
    private class Observer(val profile: CvdProfile?) {
        val see: (Rgb) -> Rgb = profile?.let { Simulation.rgb(it) } ?: { it }

        fun take(seed: Long): ScreeningResult {
            val s = Screening(seed)
            while (s.step == Screening.Step.Plates) {
                val plate = s.plate!!
                s.answerPlate(plate.digit.takeIf { deltaE2000(see(plate.figure), see(plate.field)) >= PLATE_SEEN })
            }
            for (cap in D15.arrangeAsSeenBy(see)) s.place(cap)
            while (s.step == Screening.Step.Severity) {
                val t = s.staircase!!.trial
                s.answerTrial(t.odd.takeIf { deltaE2000(see(t.common), see(t.different)) >= Staircase.JND })
            }
            return s.result()
        }
    }

    /** Someone who misses every plate, sorts the caps in the order offered and never sees a patch. */
    private fun seesNothing(seed: Long): Screening {
        val s = Screening(seed)
        while (s.step == Screening.Step.Plates) s.answerPlate(null)
        for (cap in s.offeredCaps) s.place(cap)
        while (s.step == Screening.Step.Severity) s.answerTrial(null)
        return s
    }

    @Test
    fun aRedGreenTieGoesToDeutanWithLowConfidence() {
        // Bug seen on the phone: equal protan and deutan severities gave protan, at medium confidence.
        for (seed in 1L..20L) {
            val r = seesNothing(seed).result()
            if (r.severities.keys != setOf(Axis.Protan, Axis.Deutan)) continue
            assertEquals(Axis.Deutan, r.axis, "seed $seed: $r")
            assertEquals(Confidence.Low, r.confidence, "seed $seed: $r")
        }
    }

    @Test
    fun trialsAreCountedAcrossStaircases() {
        val s = Screening(4)
        while (s.step == Screening.Step.Plates) s.answerPlate(null)
        for (cap in s.offeredCaps) s.place(cap)
        var expected = 1
        while (s.step == Screening.Step.Severity) {
            assertEquals(expected++, s.trialNumber)
            s.answerTrial(null)
        }
    }

    @Test
    fun typicalVisionFindsNothing() {
        val r = Observer(null).take(1)
        assertNull(r.axis)
        assertEquals(Confidence.High, r.confidence)
    }

    @Test
    fun simulatedProfilesFindTheirAxisAndSeverity() {
        val lines = ArrayList<String>()
        val wrong = ArrayList<String>()
        for (axis in Axis.entries) {
            for (severity in listOf(40, 60, 80, 100)) {
                val r = Observer(CvdProfile(axis, severity / 100.0)).take(severity.toLong())
                lines += "$axis $severity: ${r.axis} ${r.severity} ${r.confidence} plates ${r.plateScores} " +
                    "angle ${r.arrangement.angle.toInt()} C ${(r.arrangement.cIndex * 100).toInt() / 100.0} " +
                    "sev ${r.severities}"
                if (r.axis != axis || abs(r.severity - severity) > 15) wrong += lines.last()
            }
        }
        println(lines.joinToString("\n"))
        assertEquals(emptyList(), wrong)
    }

    @Test
    fun staircaseRecoversTheSeverityItWasGiven() {
        for (axis in Axis.entries) {
            for (severity in listOf(30, 50, 70, 90)) {
                val see = Simulation.rgb(CvdProfile(axis, severity / 100.0))
                val st = Staircase(axis, kotlin.random.Random(3))
                while (!st.done) {
                    val t = st.trial
                    st.answer(t.odd.takeIf { deltaE2000(see(t.common), see(t.different)) >= Staircase.JND })
                }
                assertTrue(abs(st.severity() - severity) <= 10, "$axis $severity % measured ${st.severity()}")
            }
        }
    }

    @Test
    fun neverSeeingReadsAsTotalAndAFineThresholdAsTypical() {
        for (axis in Axis.entries) {
            assertEquals(100, Staircase.severityFor(axis, null))
            assertEquals(0, Staircase.severityFor(axis, Staircase.levels(axis, Vec3(0.2, 0.2, 0.2)).first()))
        }
    }

    companion object {
        const val PLATE_SEEN = 5.0
    }
}
