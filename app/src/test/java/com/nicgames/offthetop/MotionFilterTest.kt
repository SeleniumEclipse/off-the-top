package com.nicgames.offthetop

import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import org.junit.Assert.*
import org.junit.Test

/** Synthetic constant-magnitude gravity traces, not measurements of human or device motion. */
class MotionFilterTest {
    @Test
    fun firstAndSteadySamplesPassThrough_andReturnedArraysCannotChangeFilterState() {
        val filter = MotionFilter()
        val gravity = vector(22f)
        val first = filter.sample(gravity[0], gravity[1], gravity[2], 0L)
        assertArrayEquals(gravity, first, 0f)
        first.fill(100f)
        val second = filter.sample(gravity[0], gravity[1], gravity[2], 20L)
        assertArrayEquals(gravity, second, 0f)
        val third = filter.sample(gravity[0], gravity[1], gravity[2], 40L)
        assertNotSame(second, third)
        assertArrayEquals(gravity, third, 0f)
    }

    @Test
    fun constantStepHasTheSameTimeBasedResponseAtFiveTenTwentyAndFiftyMilliseconds() {
        val start = vector(0f)
        val target = vector(34f)
        val expected = FloatArray(3) { axis ->
            (start[axis] + (target[axis] - start[axis]) * (1.0 - exp(-200.0 / 35.0))).toFloat()
        }
        for (period in listOf(5L, 10L, 20L, 50L)) {
            val filter = MotionFilter()
            filter.sample(start[0], start[1], start[2], 0L)
            var actual = start
            for (time in period..200L step period) {
                actual = filter.sample(target[0], target[1], target[2], time)
            }
            assertArrayEquals("35 ms smoothing at a $period ms sample period", expected, actual, 0.00001f)
        }
    }

    @Test
    fun completeFilteredNodsScoreExactlyOnceEachAcrossRatesHoldsAndLandscapeDirections() {
        for (period in listOf(5L, 10L, 20L, 50L)) {
            for (baseline in listOf(-22f, 0f, 22f)) for (landscape in listOf(-1f, 1f)) {
                val motion = FilteredMotion()
                val events = mutableListOf<Pair<Long, Outcome>>()
                // Sample a continuous piecewise trace on a REGULAR grid. In particular,
                // do not insert 120 ms endpoints into the 50 ms stream to improve it.
                for (time in 0L..1_800L step period) {
                    val elapsed = time - 600L
                    val relative = when {
                        elapsed < 0L -> 0f
                        elapsed < 590L -> -nodAngle(elapsed)
                        elapsed < 1_180L -> nodAngle(elapsed - 590L)
                        else -> 0f
                    }
                    motion.at(vector(baseline + relative, landscape), time)?.let { events += time to it }
                    if (time == 600L) {
                        assertTrue(motion.detector.armed)
                        assertEquals(baseline, checkNotNull(motion.detector.neutralDegrees), 0.001f)
                    }
                }
                val label = "period=$period ms, hold=$baseline degrees, landscape=$landscape"
                assertEquals(label, listOf(Outcome.CORRECT, Outcome.PASS), events.map { it.second })
                assertTrue("$label: correct belongs to the first nod", events[0].first in 600L until 1_190L)
                assertTrue("$label: pass belongs to the second nod", events[1].first in 1_190L until 1_780L)
                assertTrue("$label: return must rearm", motion.detector.armed)
                assertEquals(label, baseline, checkNotNull(motion.detector.neutralDegrees), 0.001f)
            }
        }
    }

    @Test
    fun invalidVectorsReachDetectorUnsmoothed_cancelPendingTiltAndRequireReturn() {
        val invalidVectors = mutableListOf(
            floatArrayOf(0f, 0f, 0f), floatArrayOf(4.999f, 0f, 0f),
            floatArrayOf(16.001f, 0f, 0f), floatArrayOf(12f, 0f, -12f),
        )
        for (invalid in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.MAX_VALUE)) {
            for (axis in 0..2) invalidVectors += vector(-12f).also { it[axis] = invalid }
        }
        for (invalid in invalidVectors) {
            val motion = pendingDownwardTilt()
            assertNull(motion.at(invalid, motion.now + 20L))
            assertArrayEquals("Invalid readings must not be smoothed into plausible gravity", invalid, motion.lastFiltered, 0f)
            assertFalse(motion.detector.armed)
            assertEquals(22f, checkNotNull(motion.detector.neutralDegrees), 0.001f)
            val recovery = vector(-12f)
            assertNull(motion.at(recovery, motion.now + 20L))
            assertArrayEquals("Invalid input must also clear filter history", recovery, motion.lastFiltered, 0f)
            motion.hold(-12f, 500L)
            assertTrue("A held tilt after a spike must not leak an event", motion.events.isEmpty())
            motion.hold(22f, 200L)
            assertTrue(motion.detector.armed)
            motion.hold(-12f, 180L)
            assertEquals(listOf(Outcome.CORRECT), motion.events)
        }
    }

    @Test
    fun duplicateTimestampsDoNotAdvanceSmoothingOrPendingDetectorDwell() {
        val motion = pendingDownwardTilt()
        val before = motion.lastFiltered.copyOf()
        repeat(100) {
            assertNull(motion.at(vector(-12f), motion.now))
            assertArrayEquals(before, motion.lastFiltered, 0f)
        }
        assertTrue(motion.events.isEmpty())
        assertTrue(motion.detector.armed)
        motion.hold(-12f, 180L)
        assertEquals(listOf(Outcome.CORRECT), motion.events)
    }

    @Test
    fun invalidDuplicateStillReachesDetectorAndBreaksThePendingGesture() {
        val motion = pendingDownwardTilt()
        val invalid = floatArrayOf(Float.NaN, 0f, 0f)
        assertNull(motion.at(invalid, motion.now))
        assertArrayEquals(invalid, motion.lastFiltered, 0f)
        assertFalse(motion.detector.armed)
        motion.hold(-12f, 500L)
        assertTrue(motion.events.isEmpty())
        assertEquals(22f, checkNotNull(motion.detector.neutralDegrees), 0.001f)
    }

    @Test
    fun backwardTimestampRestartsFilterButDoesNotRezeroOrCompleteDetectorGesture() {
        val motion = pendingDownwardTilt()
        val raw = vector(-12f)
        assertNull(motion.at(raw, motion.now - 20L))
        assertArrayEquals(raw, motion.lastFiltered, 0f)
        assertFalse(motion.detector.armed)
        motion.hold(-12f, 500L)
        assertTrue(motion.events.isEmpty())
        assertEquals(22f, checkNotNull(motion.detector.neutralDegrees), 0.001f)
        motion.hold(22f, 200L)
        assertTrue(motion.detector.armed)
        motion.hold(56f, 180L)
        assertEquals(listOf(Outcome.PASS), motion.events)
    }

    @Test
    fun gapOf250MillisecondsStillSmooths_but251RestartsAndDisarmsDetector() {
        val filter = MotionFilter()
        val start = vector(22f)
        val target = vector(-12f)
        filter.sample(start[0], start[1], start[2], 0L)
        val expected = FloatArray(3) { axis ->
            (start[axis] + (target[axis] - start[axis]) * (1.0 - exp(-250.0 / 35.0))).toFloat()
        }
        assertArrayEquals(expected, filter.sample(target[0], target[1], target[2], 250L), 0.00001f)
        val motion = pendingDownwardTilt()
        assertNull(motion.at(target, motion.now + 251L))
        assertArrayEquals(target, motion.lastFiltered, 0f)
        assertFalse(motion.detector.armed)
        motion.hold(-12f, 500L)
        assertTrue(motion.events.isEmpty())
        assertEquals(22f, checkNotNull(motion.detector.neutralDegrees), 0.001f)
        motion.hold(22f, 200L)
        motion.hold(-12f, 180L)
        assertEquals(listOf(Outcome.CORRECT), motion.events)
    }

    @Test
    fun explicitResetDropsFilterHistory_andDetectorNeedsAnEntireNewCalibration() {
        val motion = pendingDownwardTilt()
        val resetAt = motion.now
        motion.filter.reset()
        // The filter cannot reset a detector it does not own. Explicitly reset both,
        // as the app does when restarting motion after an interruption.
        motion.detector.reset()
        assertNull(motion.detector.neutralDegrees)
        assertFalse(motion.detector.armed)
        val newHold = vector(-22f)
        // Use the SAME timestamp: a backward clock or long gap would independently
        // reset smoothing and accidentally hide a broken explicit filter.reset().
        assertNull(motion.at(newHold, resetAt))
        assertArrayEquals(newHold, motion.lastFiltered, 0f)
        for (elapsed in 20L..340L step 20L) assertNull(motion.at(newHold, resetAt + elapsed))
        assertFalse("No pre-reset sample may count toward the new 350 ms hold", motion.detector.calibrated)
        assertNull(motion.at(newHold, resetAt + 360L))
        assertTrue(motion.detector.armed)
        assertEquals(-22f, checkNotNull(motion.detector.neutralDegrees), 0.001f)
        motion.hold(12f, 180L)
        assertEquals(listOf(Outcome.PASS), motion.events)
    }

    private fun pendingDownwardTilt(): FilteredMotion = FilteredMotion().also { motion ->
        motion.hold(22f, 600L)
        assertTrue(motion.detector.armed)
        motion.hold(-12f, 80L)
        assertTrue("The filtered angle must already cross activation", motion.detector.relativeDegrees < -28f)
        assertTrue("But its 50 ms activation dwell must still be pending", motion.events.isEmpty())
        assertTrue(motion.detector.armed)
    }

    /** 34-degree nod: 120 ms ramp, 120 ms peak, 200 ms return, 150 ms centered pause. */
    private fun nodAngle(elapsed: Long): Float = when {
        elapsed < 120L -> 34f * elapsed / 120f
        elapsed < 240L -> 34f
        elapsed < 440L -> 34f * (440L - elapsed) / 200f
        else -> 0f
    }

    private class FilteredMotion {
        val filter = MotionFilter()
        val detector = TiltDetector()
        val events = mutableListOf<Outcome>()
        var now = -20L
            private set
        var lastFiltered = FloatArray(3)
            private set

        fun at(raw: FloatArray, time: Long): Outcome? {
            now = time
            lastFiltered = filter.sample(raw[0], raw[1], raw[2], time)
            return detector.sample(lastFiltered[0], lastFiltered[1], lastFiltered[2], time)
                .also { if (it != null) events += it }
        }

        fun hold(degrees: Float, durationMs: Long) {
            require(durationMs >= 0L && durationMs % 20L == 0L)
            for (elapsed in 0L..durationMs step 20L) at(vector(degrees), now + 20L)
        }
    }

    private companion object {
        fun vector(degrees: Float, landscape: Float = 1f): FloatArray {
            val radians = Math.toRadians(degrees.toDouble())
            return floatArrayOf((cos(radians) * 9.81 * landscape).toFloat(), 0f, (sin(radians) * 9.81).toFloat())
        }
    }
}