package io.github.j3ffx.nuance.core

import kotlin.test.Test
import kotlin.test.assertEquals

class RgbTest {
    @Test
    fun clampedKeepsChannelsInRange() {
        assertEquals(Rgb(0f, 0.5f, 1f), Rgb(-0.2f, 0.5f, 1.3f).clamped())
    }
}
