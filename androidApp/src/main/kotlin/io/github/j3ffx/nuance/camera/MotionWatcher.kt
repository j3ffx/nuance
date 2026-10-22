package io.github.j3ffx.nuance.camera

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * The accelerometer, for the automatic pause: [onChange] gets whether the phone lies face down
 * (gravity pulling through the screen for a second), and whether it just moved notably (a change
 * of acceleration beyond the hand's tremor). Low rate: a few readings a second are enough.
 */
class MotionWatcher(context: Context, private val onChange: (faceDown: Boolean, moved: Boolean) -> Unit) :
    SensorEventListener {
    private val sensors = context.getSystemService(SensorManager::class.java)
    private val accelerometer = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var last: FloatArray? = null
    private var faceDownSinceNs = 0L
    private var faceDown = false

    fun start() {
        accelerometer ?: return
        sensors.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
    }

    fun stop() {
        sensors.unregisterListener(this)
        last = null
    }

    override fun onSensorChanged(event: SensorEvent) {
        val (x, y, z) = Triple(event.values[0], event.values[1], event.values[2])
        val previous = last
        last = floatArrayOf(x, y, z)
        val moved = previous != null &&
            sqrt(
                (x - previous[0]) * (x - previous[0]) + (y - previous[1]) * (y - previous[1]) +
                    (z - previous[2]) * (z - previous[2])
            ) >
            MOVE
        // Screen down: gravity along −z, the phone nearly flat.
        val down = z < -FLAT && abs(x) < 2 && abs(y) < 2
        faceDownSinceNs = if (down) faceDownSinceNs.takeIf { it != 0L } ?: event.timestamp else 0L
        val nowFaceDown = down && event.timestamp - faceDownSinceNs >= FACE_DOWN_NS
        if (moved || nowFaceDown != faceDown) {
            faceDown = nowFaceDown
            onChange(faceDown, moved)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        /** m/s² between two readings: a deliberate movement, not a steady hand. */
        const val MOVE = 1.5f
        const val FLAT = 9f
        const val FACE_DOWN_NS = 1_000_000_000L
    }
}
