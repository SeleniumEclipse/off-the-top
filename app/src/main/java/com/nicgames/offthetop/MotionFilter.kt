package com.nicgames.offthetop

import kotlin.math.exp
import kotlin.math.sqrt

/** Short, time-based accelerometer smoothing; identical latency at different sensor rates. */
class MotionFilter {
    private var previous: FloatArray? = null
    private var timestamp: Long? = null

    fun reset() { previous = null; timestamp = null }

    fun sample(x: Float, y: Float, z: Float, now: Long): FloatArray {
        val raw = floatArrayOf(x, y, z)
        val magnitude = sqrt(x.toDouble() * x + y.toDouble() * y + z.toDouble() * z)
        if (!magnitude.isFinite() || magnitude !in 5.0..16.0) {
            reset()
            return raw // Let the detector reject the spike; never turn shaking into a tilt.
        }
        val last = previous
        val elapsed = timestamp?.let { now - it }
        val filtered = if (last == null || elapsed == null || elapsed < 0 || elapsed > 250) raw else {
            val alpha = (1.0 - exp(-elapsed / 35.0)).toFloat()
            FloatArray(3) { last[it] + alpha * (raw[it] - last[it]) }
        }
        previous = filtered
        timestamp = now
        return filtered.copyOf()
    }
}