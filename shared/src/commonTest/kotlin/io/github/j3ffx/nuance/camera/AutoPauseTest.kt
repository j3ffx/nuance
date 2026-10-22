package io.github.j3ffx.nuance.camera

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class AutoPauseTest {
    @Test
    fun pausesAfterTheDelayWithoutTouchNorMovement() {
        assertFalse(AutoPause.shouldPause(faceDown = false, idle = 119.seconds, delayMinutes = 2, recording = false))
        assertTrue(AutoPause.shouldPause(faceDown = false, idle = 2.minutes, delayMinutes = 2, recording = false))
    }

    @Test
    fun faceDownPausesAtOnceEvenWithoutADelay() {
        assertTrue(AutoPause.shouldPause(faceDown = true, idle = 0.seconds, delayMinutes = 0, recording = false))
    }

    @Test
    fun neverWhileFilmingNorWithTheDelayOff() {
        assertFalse(AutoPause.shouldPause(faceDown = true, idle = 10.minutes, delayMinutes = 2, recording = true))
        assertFalse(AutoPause.shouldPause(faceDown = false, idle = 10.minutes, delayMinutes = 0, recording = false))
    }
}
