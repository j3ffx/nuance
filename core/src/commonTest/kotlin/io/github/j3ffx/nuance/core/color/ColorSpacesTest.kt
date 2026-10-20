package io.github.j3ffx.nuance.core.color

import io.github.j3ffx.nuance.core.Rgb
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ColorSpacesTest {
    private fun assertVec(expected: Vec3, actual: Vec3, tolerance: Double, message: String = "") {
        val worst = maxOf(abs(expected.x - actual.x), abs(expected.y - actual.y), abs(expected.z - actual.z))
        assertTrue(worst <= tolerance, "$message expected $expected, got $actual (off by $worst)")
    }

    @Test
    fun srgbTransferRoundTrips() {
        for (i in 0..255) {
            val v = i / 255.0
            assertEquals(v, Srgb.encode(Srgb.decode(v)), 1e-12)
        }
        // The linear segment and the curve meet at 0.04045.
        assertEquals(0.04045 / 12.92, Srgb.decode(0.04045), 1e-12)
        assertEquals(0.21404114, Srgb.decode(0.5), 1e-8)
    }

    @Test
    fun matricesInvertEachOther() {
        val v = Vec3(0.2, 0.5, 0.9)
        assertVec(v, Srgb.XyzToLinear * (Srgb.LinearToXyz * v), 1e-12)
    }

    @Test
    fun labOfReferenceColours() {
        assertVec(Vec3(100.0, 0.0, 0.0), Lab.fromRgb(Rgb.White), 1e-9, "white")
        assertVec(Vec3(0.0, 0.0, 0.0), Lab.fromRgb(Rgb.Black), 1e-9, "black")
        // sRGB red, green and blue in CIELAB (D65), as published by Bruce Lindbloom's calculator.
        assertVec(Vec3(53.2408, 80.0925, 67.2032), Lab.fromRgb(Rgb(1f, 0f, 0f)), 2e-3, "red")
        assertVec(Vec3(87.7347, -86.1827, 83.1793), Lab.fromRgb(Rgb(0f, 1f, 0f)), 2e-3, "green")
        assertVec(Vec3(32.2970, 79.1875, -107.8602), Lab.fromRgb(Rgb(0f, 0f, 1f)), 2e-3, "blue")
    }

    @Test
    fun labRoundTrips() {
        for (c in listOf(Vec3(0.1, 0.2, 0.3), Vec3(0.9, 0.05, 0.4), Vec3(0.001, 0.002, 0.0005))) {
            assertVec(c, Lab.toLinear(Lab.fromLinear(c)), 1e-9)
        }
    }

    @Test
    fun oklabOfReferenceColours() {
        assertVec(Vec3(1.0, 0.0, 0.0), Oklab.fromLinear(Vec3.One), 1e-4, "white")
        // Values from Ottosson's reference post (sRGB red).
        assertVec(Vec3(0.627955, 0.224863, 0.125846), Oklab.fromLinear(Vec3(1.0, 0.0, 0.0)), 1e-5, "red")
        val c = Vec3(0.3, 0.6, 0.1)
        assertVec(c, Oklab.toLinear(Oklab.fromLinear(c)), 1e-6)
        assertVec(Oklab.fromLinear(c), Oklab.fromLch(Oklab.toLch(Oklab.fromLinear(c))), 1e-12)
    }

    @Test
    fun deltaE2000MatchesSharmaTestData() {
        for ((i, row) in SHARMA.withIndex()) {
            val e = deltaE2000(Vec3(row[0], row[1], row[2]), Vec3(row[3], row[4], row[5]))
            assertEquals(row[6], e, 1e-4, "pair ${i + 1}")
            // The formula is symmetric.
            assertEquals(e, deltaE2000(Vec3(row[3], row[4], row[5]), Vec3(row[0], row[1], row[2])), 1e-9)
        }
    }

    private companion object {
        /**
         * Sharma, Wu and Dalal (2005), Table 1: L1, a1, b1, L2, a2, b2, ΔE00.
         * hajim.rochester.edu/ece/sites/gsharma/ciede2000/dataNprograms/ciede2000testdata.txt
         */
        val SHARMA = listOf(
            doubleArrayOf(50.0000, 2.6772, -79.7751, 50.0000, 0.0000, -82.7485, 2.0425),
            doubleArrayOf(50.0000, 3.1571, -77.2803, 50.0000, 0.0000, -82.7485, 2.8615),
            doubleArrayOf(50.0000, 2.8361, -74.0200, 50.0000, 0.0000, -82.7485, 3.4412),
            doubleArrayOf(50.0000, -1.3802, -84.2814, 50.0000, 0.0000, -82.7485, 1.0000),
            doubleArrayOf(50.0000, -1.1848, -84.8006, 50.0000, 0.0000, -82.7485, 1.0000),
            doubleArrayOf(50.0000, -0.9009, -85.5211, 50.0000, 0.0000, -82.7485, 1.0000),
            doubleArrayOf(50.0000, 0.0000, 0.0000, 50.0000, -1.0000, 2.0000, 2.3669),
            doubleArrayOf(50.0000, -1.0000, 2.0000, 50.0000, 0.0000, 0.0000, 2.3669),
            doubleArrayOf(50.0000, 2.4900, -0.0010, 50.0000, -2.4900, 0.0009, 7.1792),
            doubleArrayOf(50.0000, 2.4900, -0.0010, 50.0000, -2.4900, 0.0010, 7.1792),
            doubleArrayOf(50.0000, 2.4900, -0.0010, 50.0000, -2.4900, 0.0011, 7.2195),
            doubleArrayOf(50.0000, 2.4900, -0.0010, 50.0000, -2.4900, 0.0012, 7.2195),
            doubleArrayOf(50.0000, -0.0010, 2.4900, 50.0000, 0.0009, -2.4900, 4.8045),
            doubleArrayOf(50.0000, -0.0010, 2.4900, 50.0000, 0.0010, -2.4900, 4.8045),
            doubleArrayOf(50.0000, -0.0010, 2.4900, 50.0000, 0.0011, -2.4900, 4.7461),
            doubleArrayOf(50.0000, 2.5000, 0.0000, 50.0000, 0.0000, -2.5000, 4.3065),
            doubleArrayOf(50.0000, 2.5000, 0.0000, 73.0000, 25.0000, -18.0000, 27.1492),
            doubleArrayOf(50.0000, 2.5000, 0.0000, 61.0000, -5.0000, 29.0000, 22.8977),
            doubleArrayOf(50.0000, 2.5000, 0.0000, 56.0000, -27.0000, -3.0000, 31.9030),
            doubleArrayOf(50.0000, 2.5000, 0.0000, 58.0000, 24.0000, 15.0000, 19.4535),
            doubleArrayOf(50.0000, 2.5000, 0.0000, 50.0000, 3.1736, 0.5854, 1.0000),
            doubleArrayOf(50.0000, 2.5000, 0.0000, 50.0000, 3.2972, 0.0000, 1.0000),
            doubleArrayOf(50.0000, 2.5000, 0.0000, 50.0000, 1.8634, 0.5757, 1.0000),
            doubleArrayOf(50.0000, 2.5000, 0.0000, 50.0000, 3.2592, 0.3350, 1.0000),
            doubleArrayOf(60.2574, -34.0099, 36.2677, 60.4626, -34.1751, 39.4387, 1.2644),
            doubleArrayOf(63.0109, -31.0961, -5.8663, 62.8187, -29.7946, -4.0864, 1.2630),
            doubleArrayOf(61.2901, 3.7196, -5.3901, 61.4292, 2.2480, -4.9620, 1.8731),
            doubleArrayOf(35.0831, -44.1164, 3.7933, 35.0232, -40.0716, 1.5901, 1.8645),
            doubleArrayOf(22.7233, 20.0904, -46.6940, 23.0331, 14.9730, -42.5619, 2.0373),
            doubleArrayOf(36.4612, 47.8580, 18.3852, 36.2715, 50.5065, 21.2231, 1.4146),
            doubleArrayOf(90.8027, -2.0831, 1.4410, 91.1528, -1.6435, 0.0447, 1.4441),
            doubleArrayOf(90.9257, -0.5406, -0.9208, 88.6381, -0.8985, -0.7239, 1.5381),
            doubleArrayOf(6.7747, -0.2908, -2.4247, 5.8714, -0.0985, -2.2286, 0.6377),
            doubleArrayOf(2.0776, 0.0795, -1.1350, 0.9033, -0.0636, -0.5514, 0.9082)
        )
    }
}
