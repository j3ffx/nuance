package io.github.j3ffx.nuance.camera

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * When to pause the camera to save battery and heat: lying face down, or [delayMinutes] without
 * a touch nor a notable movement. Never while filming; 0 minutes turns the delay off (face down
 * still pauses). A touch resumes.
 */
object AutoPause {
    fun shouldPause(faceDown: Boolean, idle: Duration, delayMinutes: Int, recording: Boolean): Boolean = when {
        recording -> false
        faceDown -> true
        delayMinutes <= 0 -> false
        else -> idle >= delayMinutes.minutes
    }
}
