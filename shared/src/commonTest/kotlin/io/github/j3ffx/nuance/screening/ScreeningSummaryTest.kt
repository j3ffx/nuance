package io.github.j3ffx.nuance.screening

import io.github.j3ffx.nuance.core.cvd.Axis
import io.github.j3ffx.nuance.core.cvd.CvdProfile
import io.github.j3ffx.nuance.core.screening.Confidence
import io.github.j3ffx.nuance.parseTestAs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScreeningSummaryTest {
    @Test
    fun readsWhatItWrites() {
        assertEquals(ScreeningSummary(Axis.Deutan, 64, Confidence.Medium), ScreeningSummary.decode("Deutan;64;Medium"))
        assertEquals(ScreeningSummary(null, 0, Confidence.High), ScreeningSummary.decode("none;0;High"))
    }

    @Test
    fun ignoresAnythingElse() {
        for (text in listOf("", "Deutan;64", "Deutan;164;High", "Blue;10;High", "Deutan;10;Sure")) {
            assertNull(ScreeningSummary.decode(text), text)
        }
    }

    @Test
    fun devCommandNamesAProfile() {
        assertEquals(CvdProfile(Axis.Deutan, 0.6), parseTestAs("deutan 60"))
        assertEquals(CvdProfile(Axis.Tritan, 1.0), parseTestAs("Tritan"))
        assertNull(parseTestAs("off"))
    }
}
