package io.github.j3ffx.nuance.core

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Lut3dTest {
    private fun assertClose(expected: Rgb, actual: Rgb, tolerance: Float = 1e-5f) {
        val worst = maxOf(abs(expected.r - actual.r), abs(expected.g - actual.g), abs(expected.b - actual.b))
        assertTrue(worst <= tolerance, "expected $expected, got $actual (off by $worst)")
    }

    @Test
    fun identityLutReturnsItsInputAnywhereInTheCube() {
        val lut = Lut3d.bake(17, Transforms.Identity)
        val probes = listOf(Rgb(0f, 0f, 0f), Rgb(1f, 1f, 1f), Rgb(0.3f, 0.71f, 0.05f), Rgb(0.999f, 0.5f, 0.001f))
        for (p in probes) assertClose(p, lut.sample(p))
    }

    @Test
    fun layoutHasRedVaryingFastest() {
        val lut = Lut3d.bake(3, Transforms.Identity)
        assertEquals(Rgb(0.5f, 0f, 0f), lut.entry(1, 0, 0))
        assertEquals(0.5f, lut.data[3]) // second entry, red channel
        assertEquals(Rgb(0f, 0f, 1f), lut.entry(0, 0, 2))
    }

    @Test
    fun invertLutIsExactBecauseInversionIsLinear() {
        val lut = Lut3d.bake(Lut3d.DEFAULT_SIZE, Transforms.Invert)
        assertClose(Rgb(0.8f, 0.6f, 0.1f), lut.sample(Rgb(0.2f, 0.4f, 0.9f)))
    }

    @Test
    fun outputsAreClampedToTheCube() {
        val lut = Lut3d.bake(2) { Rgb(it.r * 2f, -1f, 0.5f) }
        assertEquals(Rgb(1f, 0f, 0.5f), lut.entry(1, 1, 1))
    }

    @Test
    fun rgba8RoundsAndIsOpaque() {
        val bytes = Lut3d.bake(2, Transforms.Identity).toRgba8()
        assertEquals(2 * 2 * 2 * 4, bytes.size)
        assertEquals(255, bytes[7].toInt() and 0xFF) // alpha of the first entry
        assertEquals(255, bytes[4].toInt() and 0xFF) // red of entry (1, 0, 0)
    }
}
