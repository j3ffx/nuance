package io.github.j3ffx.nuance.render

import android.os.SystemClock
import android.util.Log
import io.github.j3ffx.nuance.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Collects per-frame figures on the render thread and publishes a summary twice a second.
 * Only the render thread calls [onCameraFrame] and [onDraw].
 *
 * Clocks: camera timestamps are in boot time on most phones (SENSOR_INFO_TIMESTAMP_SOURCE
 * REALTIME), monotonic on others; the display works in monotonic time. [toMonotonic] converts.
 */
class StatsCollector {
    private val _stats = MutableStateFlow(RenderStats())
    val stats: StateFlow<RenderStats> = _stats

    private var windowStart = SystemClock.elapsedRealtimeNanos()
    private var cameraFrames = 0
    private var draws = 0
    private val captureToArrival = ArrayList<Float>(64)
    private val arrivalToSwap = ArrayList<Float>(64)
    private val captureToSwap = ArrayList<Float>(64)
    private var gpuMs: Float? = null

    fun onCameraFrame() {
        cameraFrames++
    }

    /**
     * A frame was swapped. [sensorTimestampNs] is the camera's timestamp (start of exposure),
     * [arrivalNs] the monotonic time the render thread learnt the frame was there.
     */
    fun onDraw(sensorTimestampNs: Long?, arrivalNs: Long, gpuMs: Float?) {
        draws++
        this.gpuMs = gpuMs ?: this.gpuMs
        val swapNs = System.nanoTime()
        sensorTimestampNs?.let(::toMonotonic)?.let { capture ->
            captureToArrival += (arrivalNs - capture) / 1e6f
            captureToSwap += (swapNs - capture) / 1e6f
        }
        arrivalToSwap += (swapNs - arrivalNs) / 1e6f
        val now = SystemClock.elapsedRealtimeNanos()
        val elapsed = now - windowStart
        if (elapsed < WINDOW_NS) return
        val seconds = elapsed / 1e9f
        _stats.update {
            it.copy(
                fps = draws / seconds,
                cameraFps = cameraFrames / seconds,
                gpuMs = this.gpuMs,
                captureToArrivalMs = captureToArrival.median(),
                arrivalToSwapMs = arrivalToSwap.median(),
                captureToSwapMs = captureToSwap.median()
            )
        }
        log()
        windowStart = now
        draws = 0
        cameraFrames = 0
        captureToArrival.clear()
        arrivalToSwap.clear()
        captureToSwap.clear()
    }

    fun update(transform: (RenderStats) -> RenderStats) {
        _stats.update(transform)
        log()
    }

    /** [sensorTimestampNs] on the monotonic clock, or null if it fits neither clock. */
    fun toMonotonic(sensorTimestampNs: Long): Long? {
        if (sensorTimestampNs <= 0) return null
        val monotonic = System.nanoTime()
        val bootOffset = SystemClock.elapsedRealtimeNanos() - monotonic
        // One of the two readings gives a frame between 0 and a second old.
        if (monotonic - (sensorTimestampNs - bootOffset) in 0..ONE_SECOND) return sensorTimestampNs - bootOffset
        if (monotonic - sensorTimestampNs in 0..ONE_SECOND) return sensorTimestampNs
        return null
    }

    /**
     * The dev variant logs each summary, so measurements can be collected over ADB
     * (scripts/latency.mjs, `logcat -s NuanceStats`), with the clock offset they need.
     */
    private fun log() {
        if (!BuildConfig.DEBUG) return
        val bootOffset = SystemClock.elapsedRealtimeNanos() - System.nanoTime()
        Log.i("NuanceStats", "${_stats.value} bootMinusMonotonicNs=$bootOffset")
    }

    private fun List<Float>.median(): Float? = if (isEmpty()) null else sorted()[size / 2]

    private companion object {
        const val WINDOW_NS = 500_000_000L
        const val ONE_SECOND = 1_000_000_000L
    }
}
