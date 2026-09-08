package com.nicgames.offthetop

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.*
import org.junit.Test

class TiltDetectorTest {
    /** Continuous 50 Hz gravity readings, not two endpoints pretending to be a hold. */
    private class Motion(
        val detector: TiltDetector = TiltDetector(),
        private val inPlaneX: Float = 1f,
        private val inPlaneY: Float = 0f,
        var now: Long = -20L,
    ) {
        fun sample(
            degrees: Float,
            afterMs: Long = 20L,
            calibrating: Boolean = false,
            acceptTilt: Boolean = true,
            magnitude: Float = 9.81f,
        ): Outcome? {
            val radians = Math.toRadians(degrees.toDouble())
            val inPlaneLength = sqrt(inPlaneX.toDouble() * inPlaneX + inPlaneY.toDouble() * inPlaneY)
            return raw(
                (cos(radians) * magnitude * inPlaneX / inPlaneLength).toFloat(),
                (cos(radians) * magnitude * inPlaneY / inPlaneLength).toFloat(),
                (sin(radians) * magnitude).toFloat(),
                afterMs, calibrating, acceptTilt,
            )
        }

        fun raw(
            x: Float, y: Float, z: Float,
            afterMs: Long = 20L,
            calibrating: Boolean = false,
            acceptTilt: Boolean = true,
        ): Outcome? {
            now += afterMs
            return detector.sample(x, y, z, now, calibrating, acceptTilt)
        }

        /** Duration between the first and last reading; the first arrives 20 ms after now. */
        fun hold(
            degrees: Float,
            durationMs: Long = 360L,
            calibrating: Boolean = false,
            acceptTilt: Boolean = true,
            magnitude: Float = 9.81f,
        ): List<Outcome> {
            require(durationMs >= 0L && durationMs % 20L == 0L)
            return (0L..durationMs step 20L).mapNotNull {
                sample(degrees, calibrating = calibrating, acceptTilt = acceptTilt, magnitude = magnitude)
            }
        }

        fun calibrate(degrees: Float = 0f, calibrating: Boolean = false, magnitude: Float = 9.81f) {
            assertTrue(hold(degrees, calibrating = calibrating, magnitude = magnitude).isEmpty())
            assertTrue(detector.calibrated)
            assertTrue(detector.armed)
            assertEquals(degrees, checkNotNull(detector.neutralDegrees), 0.0001f)
            assertEquals(0f, detector.relativeDegrees, 0.0001f)
        }

        fun rearm(degrees: Float = checkNotNull(detector.neutralDegrees), acceptTilt: Boolean = true) {
            assertTrue(hold(degrees, 100L, acceptTilt = acceptTilt).isEmpty())
            assertTrue(detector.armed)
        }

        fun score(degrees: Float, expected: Outcome) {
            repeat(3) { assertNull("Must wait a fresh 50 ms before scoring", sample(degrees)) }
            assertEquals(expected, sample(degrees))
            assertFalse(detector.armed)
        }
    }

    private fun outcome(sign: Float) = if (sign < 0f) Outcome.CORRECT else Outcome.PASS

    private fun invalidVectors(): List<FloatArray> = buildList {
        for (magnitude in listOf(0f, 1f, 4.999f, 16.001f, 30f)) {
            add(floatArrayOf(magnitude, 0f, 0f))
        }
        add(floatArrayOf(12f, 0f, -12f)) // Combined magnitude, not per-component limits.
        for (invalid in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.MAX_VALUE)) {
            for (axis in 0..2) add(floatArrayOf(8f, 0f, -5f).also { it[axis] = invalid })
        }
    }

    @Test fun startsUncalibratedAndUnarmedWithNormalDegreeSensitivity() {
        val detector = TiltDetector()
        assertEquals(28f, detector.activationDegrees, 0f)
        assertFalse(detector.calibrated)
        assertFalse(detector.armed)
        assertNull(detector.neutralDegrees)
        assertEquals(0f, detector.relativeDegrees, 0f)
    }

    @Test fun lateForeheadPlacementFinishesSettlingAcrossCountdownEnd() {
        val motion = Motion()
        motion.calibrate(0f, calibrating = true)
        assertTrue(motion.hold(22f, 200L, calibrating = true).isEmpty())
        assertFalse("Old hand-held baseline must not be used after moving to the forehead", motion.detector.calibrated)
        assertTrue(motion.hold(22f, 160L).isEmpty())
        assertTrue(motion.detector.calibrated)
        assertEquals(22f, checkNotNull(motion.detector.neutralDegrees), .001f)
        motion.score(-12f, Outcome.CORRECT)
        motion.rearm(22f)
        motion.score(56f, Outcome.PASS)
    }

    @Test fun initialStableHoldNeedsAtLeast350MillisecondsAndArmsImmediately() {
        val motion = Motion()
        assertTrue(motion.hold(22f, 340L).isEmpty())
        assertFalse(motion.detector.calibrated)
        assertFalse(motion.detector.armed)
        assertNull(motion.sample(22f, afterMs = 9L))
        assertFalse(motion.detector.calibrated)
        assertNull(motion.sample(22f, afterMs = 1L))
        assertTrue(motion.detector.calibrated)
        assertTrue(motion.detector.armed)
        assertEquals(22f, checkNotNull(motion.detector.neutralDegrees), 0.0001f)
    }

    @Test fun baselineIsMeanOfStableJitterRatherThanLastReading() {
        val motion = Motion()
        val angles = (0 until 18).map { 19f + it % 6 } + 21.5f
        angles.dropLast(1).forEach { assertNull(motion.sample(it)) }
        assertFalse(motion.detector.calibrated)
        assertNull(motion.sample(angles.last(), afterMs = 10L))
        assertEquals(angles.average().toFloat(), checkNotNull(motion.detector.neutralDegrees), 0.0001f)
        assertTrue(motion.detector.armed)
    }

    @Test fun sixDegreeStableRangeIsInclusive() {
        val motion = Motion()
        val angles = (0 until 18).map { if (it % 2 == 0) -3f else 3f } + 3f
        angles.dropLast(1).forEach { assertNull(motion.sample(it)) }
        assertNull(motion.sample(3f, afterMs = 10L))
        assertTrue(motion.detector.calibrated)
        assertEquals(angles.average().toFloat(), checkNotNull(motion.detector.neutralDegrees), 0.0001f)
    }

    @Test fun cumulativeMotionBeyondSixDegreesRestartsMeanAndFullHold() {
        val motion = Motion()
        assertTrue(motion.hold(0f, 200L).isEmpty())
        assertNull(motion.sample(4f))
        assertNull(motion.sample(8f)) // Only four from last sample, but eight across the window.
        assertTrue(motion.hold(8f, 320L).isEmpty())
        assertFalse(motion.detector.calibrated)
        assertNull(motion.sample(8f, afterMs = 10L))
        assertTrue(motion.detector.armed)
        assertEquals(8f, checkNotNull(motion.detector.neutralDegrees), 0.0001f)
    }

    @Test fun allPermittedForeheadHoldsIncludingFortyDegreeEndpointsCalibrate() {
        for (baseline in listOf(-40f, -30f, -22f, 0f, 22f, 30f, 40f)) {
            Motion().calibrate(baseline)
        }
    }

    @Test fun bothLandscapeDirectionsTabletsAndDiagonalsHaveSymmetricRelativeNods() {
        val axes = listOf(1f to 0f, -1f to 0f, 0f to 1f, 0f to -1f, 1f to -1f, -1f to 1f)
        for ((x, y) in axes) for (baseline in listOf(-30f, -22f, 0f, 22f, 30f)) {
            for (sign in listOf(-1f, 1f)) {
                val motion = Motion(inPlaneX = x, inPlaneY = y)
                motion.calibrate(baseline)
                motion.score(baseline + sign * 32f, outcome(sign))
                assertEquals(sign * 32f, motion.detector.relativeDegrees, 0.0001f)
                assertEquals(baseline, checkNotNull(motion.detector.neutralDegrees), 0.0001f)
            }
        }
    }

    @Test fun relativeSignWinsEvenWhenAbsoluteZHasTheOtherSign() {
        val positiveHold = Motion()
        positiveHold.calibrate(30f)
        positiveHold.score(1f, Outcome.CORRECT)
        val negativeHold = Motion()
        negativeHold.calibrate(-30f)
        negativeHold.score(-1f, Outcome.PASS)
    }

    @Test fun flatOrBeyondFortyDegreesNeverCalibratesInPlayOrCountdown() {
        for (degrees in listOf(-90f, -60f, -40.01f, 40.01f, 60f, 90f)) {
            for (countdown in listOf(false, true)) {
                val motion = Motion()
                assertTrue(motion.hold(degrees, 2_000L, calibrating = countdown).isEmpty())
                assertFalse(motion.detector.calibrated)
                assertFalse(motion.detector.armed)
                assertNull(motion.detector.neutralDegrees)
            }
        }
    }

    @Test fun flatPlacementBreaksPartialForeheadCalibration() {
        val motion = Motion()
        assertTrue(motion.hold(22f, 300L).isEmpty())
        assertNull(motion.sample(90f))
        assertTrue(motion.hold(22f, 340L).isEmpty())
        assertFalse(motion.detector.calibrated)
        assertNull(motion.sample(22f, afterMs = 10L))
        assertTrue(motion.detector.armed)
    }

    @Test fun motionCannotOutputBeforeAStableBaselineExists() {
        val motion = Motion()
        repeat(10) {
            assertTrue(motion.hold(-32f, 240L).isEmpty())
            assertTrue(motion.hold(32f, 240L).isEmpty())
            assertFalse(motion.detector.calibrated)
            assertFalse(motion.detector.armed)
        }
    }

    @Test fun naturalFastNodRampsAndBriefPeakDwellScoreBothDirections() {
        for (baseline in listOf(-22f, 0f, 22f)) for (sign in listOf(-1f, 1f)) {
            val motion = Motion()
            motion.calibrate(baseline)
            val results = listOf(8f, 16f, 24f, 30f, 32f, 31f, 30f, 20f, 10f, 0f)
                .mapNotNull { motion.sample(baseline + it * sign) }
            assertEquals(listOf(outcome(sign)), results)
            motion.rearm()
        }
    }

    @Test fun activationRequiresExactlyAtLeastFiftyMilliseconds() {
        for (sign in listOf(-1f, 1f)) {
            val motion = Motion()
            motion.calibrate()
            assertNull(motion.sample(sign * 32f))
            assertNull(motion.sample(sign * 32f))
            assertNull(motion.sample(sign * 32f))
            assertNull(motion.sample(sign * 32f, afterMs = 9L))
            assertTrue(motion.detector.armed)
            assertEquals(outcome(sign), motion.sample(sign * 32f, afterMs = 1L))
            assertFalse(motion.detector.armed)
        }
    }

    @Test fun activationBoundaryIsInclusiveForBothSignsAndSensitivities() {
        for (degrees in listOf(20f, 28f, 45f)) for (sign in listOf(-1f, 1f)) {
            val motion = Motion()
            motion.calibrate()
            assertNull(motion.sample(sign * degrees))
            // Use the measured Float angle itself to test equality, not trig rounding.
            motion.detector.activationDegrees = abs(motion.detector.relativeDegrees)
            motion.score(sign * degrees, outcome(sign))
        }
    }

    @Test fun belowThresholdNeverScoresAndDoesNotDriftThePlayingBaseline() {
        val motion = Motion()
        motion.calibrate()
        for (angle in listOf(-27.5f, 27.5f, 20f, -20f)) {
            assertTrue(motion.hold(angle, 2_000L).isEmpty())
            assertTrue(motion.detector.armed)
            assertEquals(0f, checkNotNull(motion.detector.neutralDegrees), 0f)
        }
        motion.score(-32f, Outcome.CORRECT)
    }

    @Test fun bouncingAroundEitherActivationThresholdCannotAccumulateDwell() {
        for (sign in listOf(-1f, 1f)) {
            val motion = Motion()
            motion.calibrate()
            repeat(50) {
                assertNull(motion.sample(sign * 28.2f))
                assertNull(motion.sample(sign * 28.2f))
                assertNull(motion.sample(sign * 27.8f))
            }
            motion.score(sign * 30f, outcome(sign))
        }
    }

    @Test fun switchingDirectlyBetweenDirectionsStartsANewDwell() {
        for (sign in listOf(-1f, 1f)) {
            val motion = Motion()
            motion.calibrate()
            assertNull(motion.sample(-sign * 32f))
            assertNull(motion.sample(-sign * 32f))
            assertNull(motion.sample(sign * 32f))
            assertNull(motion.sample(sign * 32f))
            assertNull(motion.sample(sign * 32f))
            assertNull(motion.sample(sign * 32f, afterMs = 9L))
            assertEquals(outcome(sign), motion.sample(sign * 32f, afterMs = 1L))
        }
    }

    @Test fun returningToCenterCancelsAPendingTilt() {
        val motion = Motion()
        motion.calibrate()
        assertNull(motion.sample(-32f))
        assertNull(motion.sample(-32f))
        assertNull(motion.sample(0f))
        motion.score(-32f, Outcome.CORRECT)
    }

    @Test fun longHoldAndOppositeTiltWithoutCenterCannotRepeat() {
        for (sign in listOf(-1f, 1f)) {
            val motion = Motion()
            motion.calibrate()
            assertEquals(listOf(outcome(sign)), motion.hold(sign * 32f, 5_000L))
            assertTrue(motion.hold(-sign * 32f, 2_000L).isEmpty())
            assertFalse(motion.detector.armed)
        }
    }

    @Test fun rearmNeedsNinetyMillisecondsAtInclusiveFourteenDegreeCenter() {
        for (sign in listOf(-1f, 1f)) {
            val motion = Motion()
            motion.calibrate()
            motion.score(-32f, Outcome.CORRECT)
            assertTrue(motion.hold(sign * 14f, 80L).isEmpty())
            assertFalse(motion.detector.armed)
            assertNull(motion.sample(sign * 14f, afterMs = 9L))
            assertFalse(motion.detector.armed)
            assertNull(motion.sample(sign * 14f, afterMs = 1L))
            assertTrue(motion.detector.armed)
        }
    }

    @Test fun relativeCenterRearmsWithoutAnyAbsoluteUprightRestriction() {
        for (baseline in listOf(-30f, -22f, 22f, 30f)) {
            val sign = if (baseline < 0f) -1f else 1f
            val motion = Motion()
            motion.calibrate(baseline)
            motion.score(baseline - 32f, Outcome.CORRECT)
            motion.rearm(baseline + sign * 13f) // Up to 43 degrees absolute, still centered.
            motion.score(baseline + 32f, Outcome.PASS)
        }
    }

    @Test fun justOutsideCenterCannotRearmEvenAfterALongHold() {
        for (sign in listOf(-1f, 1f)) {
            val motion = Motion()
            motion.calibrate()
            motion.score(-32f, Outcome.CORRECT)
            assertTrue(motion.hold(sign * 14.1f, 1_000L).isEmpty())
            assertFalse(motion.detector.armed)
            motion.rearm(sign * 13.9f)
        }
    }

    @Test fun leavingCenterRestartsItsEntireDwell() {
        val motion = Motion()
        motion.calibrate()
        motion.score(-32f, Outcome.CORRECT)
        assertTrue(motion.hold(0f, 80L).isEmpty())
        assertNull(motion.sample(15f))
        assertTrue(motion.hold(0f, 80L).isEmpty())
        assertFalse(motion.detector.armed)
        assertNull(motion.sample(0f, afterMs = 10L))
        assertTrue(motion.detector.armed)
    }

    @Test fun smallJitterInsideRelativeCenterDoesNotRestartRearming() {
        val motion = Motion()
        motion.calibrate(22f)
        motion.score(-10f, Outcome.CORRECT)
        listOf(20f, 22f, 24f, 21f, 23f).forEach { assertNull(motion.sample(it)) }
        assertFalse(motion.detector.armed)
        assertNull(motion.sample(22f, afterMs = 10L))
        assertTrue(motion.detector.armed)
    }

    @Test fun centerRearmsDuringHiddenFeedbackAndNextVisibleThirtyDegreeTiltScores() {
        for (baseline in listOf(-22f, 22f)) {
            val motion = Motion()
            motion.calibrate(baseline)
            motion.score(baseline - 32f, Outcome.CORRECT)
            motion.rearm(acceptTilt = false)
            assertTrue(motion.hold(baseline, 600L, acceptTilt = false).isEmpty())
            assertTrue(motion.detector.armed)
            motion.score(baseline + 30f, Outcome.PASS)
        }
    }

    @Test fun blockedTiltDisarmsEvenBelowActivationAndCannotLeakAfterUnblocking() {
        for (angle in listOf(-30f, -20f, -15f, 15f, 20f, 30f)) {
            val motion = Motion()
            motion.calibrate()
            assertTrue(motion.hold(angle, 100L, acceptTilt = false).isEmpty())
            assertFalse(motion.detector.armed)
            val tilt = if (angle < 0f) -32f else 32f
            assertTrue(motion.hold(tilt, 1_000L).isEmpty())
            motion.rearm(acceptTilt = false)
            motion.score(tilt, outcome(angle))
        }
    }

    @Test fun blockingDuringPendingTiltClearsDwellInsteadOfDeferringTheAnswer() {
        val motion = Motion()
        motion.calibrate()
        assertNull(motion.sample(-32f))
        assertNull(motion.sample(-32f))
        assertNull(motion.sample(-32f))
        assertNull(motion.sample(-32f, afterMs = 10L, acceptTilt = false))
        assertFalse(motion.detector.armed)
        assertTrue(motion.hold(-32f, 1_000L).isEmpty())
        motion.rearm()
        motion.score(-32f, Outcome.CORRECT)
    }

    @Test fun briefHiddenCenterCannotRearmBeforeAVisibleTilt() {
        val motion = Motion()
        motion.calibrate()
        motion.score(-32f, Outcome.CORRECT)
        assertTrue(motion.hold(0f, 80L, acceptTilt = false).isEmpty())
        assertTrue(motion.hold(30f, 500L).isEmpty())
        assertFalse(motion.detector.armed)
    }

    @Test fun countdownLateStablePlacementReplacesHandheldBaselineAfterFullHold() {
        val motion = Motion()
        motion.calibrate(0f, calibrating = true)
        for (degrees in listOf(8f, 16f, 24f, 32f, 22f)) {
            assertNull(motion.sample(degrees, calibrating = true))
        }
        assertTrue(motion.hold(22f, 320L, calibrating = true).isEmpty())
        assertEquals(0f, checkNotNull(motion.detector.neutralDegrees), 0f)
        assertNull(motion.sample(22f, afterMs = 10L, calibrating = true))
        assertEquals(22f, checkNotNull(motion.detector.neutralDegrees), 0.0001f)
        assertTrue(motion.detector.armed)
    }

    @Test fun countdownNeverOutputsEvenAtActivationOrDuringLongTiltedHolds() {
        val motion = Motion()
        motion.calibrate(calibrating = true)
        for (degrees in listOf(-32f, 32f, -90f, 90f, -22f, 22f)) {
            assertTrue(motion.hold(degrees, 1_000L, calibrating = true).isEmpty())
        }
        assertEquals(22f, checkNotNull(motion.detector.neutralDegrees), 0.0001f)
    }

    @Test fun countdownKeepsUpdatingMeanWithinAnAlreadyStablePose() {
        val motion = Motion()
        motion.calibrate(calibrating = true)
        assertTrue(motion.hold(4f, 1_000L, calibrating = true).isEmpty())
        val expectedMean = 4f * 51f / (19f + 51f)
        assertEquals(expectedMean, checkNotNull(motion.detector.neutralDegrees), 0.0001f)
        assertTrue(motion.detector.armed)
    }

    @Test fun countdownToPlayingPreservesHoldAndArmingWithoutAnotherCalibration() {
        val motion = Motion()
        motion.calibrate(22f, calibrating = true)
        motion.score(52f, Outcome.PASS)
        assertEquals(22f, checkNotNull(motion.detector.neutralDegrees), 0.0001f)
    }

    @Test fun countdownCannotReplaceBaselineWithAFlatPose() {
        val motion = Motion()
        motion.calibrate(22f, calibrating = true)
        assertTrue(motion.hold(90f, 1_000L, calibrating = true).isEmpty())
        assertFalse("A flat countdown finish still needs a settled forehead hold", motion.detector.calibrated)
        assertEquals(22f, checkNotNull(motion.detector.neutralDegrees), 0.0001f)
        assertFalse(motion.detector.armed)
        assertTrue(motion.hold(22f, 360L).isEmpty())
        assertTrue(motion.detector.calibrated)
        motion.score(-10f, Outcome.CORRECT)
    }

    @Test fun invalidVectorsNeverCalibrateOrScore() {
        for (vector in invalidVectors()) {
            val motion = Motion()
            repeat(30) { assertNull(motion.raw(vector[0], vector[1], vector[2])) }
            assertFalse(motion.detector.armed)
            assertFalse(motion.detector.calibrated)
        }
    }

    @Test fun invalidVectorsClearDwellButRetainBaselineAndNeverRezeroOnTiltedRecovery() {
        for (vector in invalidVectors()) {
            val motion = Motion()
            motion.calibrate(22f)
            assertNull(motion.sample(-10f))
            assertNull(motion.sample(-10f))
            val lastRelative = motion.detector.relativeDegrees
            assertNull(motion.raw(vector[0], vector[1], vector[2]))
            assertFalse(motion.detector.armed)
            assertTrue(motion.detector.calibrated)
            assertEquals(lastRelative, motion.detector.relativeDegrees, 0f)
            assertTrue(motion.hold(-10f, 1_000L).isEmpty()) // Valid and within calibration range.
            assertEquals(22f, checkNotNull(motion.detector.neutralDegrees), 0.0001f)
            assertFalse(motion.detector.armed)
            motion.rearm()
            motion.score(54f, Outcome.PASS)
        }
    }

    @Test fun invalidReadingBreaksPartialInitialCalibration() {
        val motion = Motion()
        assertTrue(motion.hold(22f, 340L).isEmpty())
        assertNull(motion.raw(0f, 0f, 0f))
        assertTrue(motion.hold(22f, 340L).isEmpty())
        assertFalse(motion.detector.calibrated)
        assertNull(motion.sample(22f, afterMs = 10L))
        assertTrue(motion.detector.armed)
    }

    @Test fun invalidReadingBreaksPartialCenterDwell() {
        val motion = Motion()
        motion.calibrate()
        motion.score(-32f, Outcome.CORRECT)
        assertTrue(motion.hold(0f, 80L).isEmpty())
        assertNull(motion.raw(0f, 0f, 0f))
        assertTrue(motion.hold(0f, 80L).isEmpty())
        assertFalse(motion.detector.armed)
        assertNull(motion.sample(0f, afterMs = 10L))
        assertTrue(motion.detector.armed)
    }

    @Test fun invalidCountdownReadingRequiresNewStableWindowButKeepsOldBaseline() {
        val motion = Motion()
        motion.calibrate(calibrating = true)
        assertTrue(motion.hold(22f, 320L, calibrating = true).isEmpty())
        assertNull(motion.raw(0f, 0f, 0f, calibrating = true))
        assertTrue(motion.hold(22f, 340L, calibrating = true).isEmpty())
        assertEquals(0f, checkNotNull(motion.detector.neutralDegrees), 0f)
        assertNull(motion.sample(22f, afterMs = 10L, calibrating = true))
        assertEquals(22f, checkNotNull(motion.detector.neutralDegrees), 0.0001f)
    }

    @Test fun validGravityMagnitudesGiveTheSameRelativeDirection() {
        for (magnitude in listOf(6f, 9.81f, 15f)) for (sign in listOf(-1f, 1f)) {
            val motion = Motion()
            motion.calibrate(22f, magnitude = magnitude)
            assertEquals(listOf(outcome(sign)), motion.hold(22f + sign * 32f, 60L, magnitude = magnitude))
        }
    }

    @Test fun gravityMagnitudeBoundsFiveAndSixteenAreInclusive() {
        for (magnitude in listOf(5f, 16f)) for (sign in listOf(-1f, 1f)) {
            val motion = Motion()
            motion.calibrate(magnitude = magnitude)
            assertEquals(listOf(outcome(sign)), motion.hold(sign * 90f, 60L, magnitude = magnitude))
        }
    }

    @Test fun resetForgetsBaselineAndPendingTiltButPreservesSensitivity() {
        val motion = Motion()
        motion.detector.activationDegrees = 20f
        motion.calibrate(22f)
        assertNull(motion.sample(54f))
        repeat(10) { motion.detector.reset() }
        assertFalse(motion.detector.armed)
        assertFalse(motion.detector.calibrated)
        assertNull(motion.detector.neutralDegrees)
        assertEquals(0f, motion.detector.relativeDegrees, 0f)
        assertEquals(20f, motion.detector.activationDegrees, 0f)
        assertTrue(motion.hold(32f, 340L).isEmpty())
        assertFalse(motion.detector.calibrated)
        assertNull(motion.sample(32f, afterMs = 10L))
        // After explicit reset a stable tilted hold is indistinguishable from natural posture.
        assertEquals(32f, checkNotNull(motion.detector.neutralDegrees), 0.0001f)
        assertTrue(motion.detector.armed)
        motion.score(54f, Outcome.PASS)
    }

    @Test fun resetDiscardsPartialCalibrationAndAllowsANewTimestampEpoch() {
        val motion = Motion(now = 100_000L)
        assertTrue(motion.hold(22f, 340L).isEmpty())
        motion.detector.reset()
        motion.now = -20L
        assertTrue(motion.hold(-22f, 340L).isEmpty())
        assertFalse(motion.detector.calibrated)
        assertNull(motion.sample(-22f, afterMs = 10L))
        assertTrue(motion.detector.armed)
        assertEquals(-22f, checkNotNull(motion.detector.neutralDegrees), 0.0001f)
    }

    @Test fun sensitivityMustBeFiniteAndInInclusiveTwentyToFortyFiveDegreeRange() {
        val detector = TiltDetector()
        for (valid in listOf(20f, 28f, 45f)) {
            detector.activationDegrees = valid
            assertEquals(valid, detector.activationDegrees, 0f)
        }
        for (invalid in listOf(-1f, 0f, 19.99f, 45.01f, Float.NaN,
            Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) { detector.activationDegrees = invalid }
            assertEquals(45f, detector.activationDegrees, 0f)
        }
    }

    @Test fun changingSensitivityClearsPendingDwellNotCalibrationOrArming() {
        val motion = Motion()
        motion.calibrate(22f)
        assertNull(motion.sample(54f))
        assertNull(motion.sample(54f))
        assertNull(motion.sample(54f))
        motion.detector.activationDegrees = 20f
        assertTrue(motion.detector.armed)
        assertEquals(22f, checkNotNull(motion.detector.neutralDegrees), 0.0001f)
        motion.score(54f, Outcome.PASS)
    }

    @Test fun gentleTwentyDegreesRecognizesSmallNodsThatNormalTwentyEightDoesNot() {
        for (baseline in listOf(-22f, 22f)) for (sign in listOf(-1f, 1f)) {
            val gentle = Motion()
            gentle.detector.activationDegrees = 20f
            gentle.calibrate(baseline)
            assertTrue(gentle.hold(baseline + sign * 19f, 500L).isEmpty())
            gentle.score(baseline + sign * 22f, outcome(sign))
            val normal = Motion()
            normal.calibrate(baseline)
            assertTrue(normal.hold(baseline + sign * 22f, 500L).isEmpty())
            normal.score(baseline + sign * 30f, outcome(sign))
        }
    }

    @Test fun duplicateTimestampsDoNotEarnCalibrationTimeOrWeightTheMean() {
        val motion = Motion()
        assertTrue(motion.hold(22f, 320L).isEmpty())
        assertNull(motion.sample(24f))
        repeat(100) { assertNull(motion.sample(26f, afterMs = 0L)) }
        assertFalse(motion.detector.calibrated)
        assertNull(motion.sample(22f, afterMs = 10L))
        assertEquals((17f * 22f + 24f + 22f) / 19f,
            checkNotNull(motion.detector.neutralDegrees), 0.0001f)
    }

    @Test fun duplicateTimestampsCannotEarnTiltOrCenterDwell() {
        val motion = Motion()
        motion.calibrate()
        assertNull(motion.sample(-32f))
        repeat(100) { assertNull(motion.sample(-32f, afterMs = 0L)) }
        assertNull(motion.sample(-32f))
        assertNull(motion.sample(-32f))
        assertNull(motion.sample(-32f, afterMs = 9L))
        assertEquals(Outcome.CORRECT, motion.sample(-32f, afterMs = 1L))
        assertTrue(motion.hold(0f, 80L).isEmpty())
        repeat(100) { assertNull(motion.sample(0f, afterMs = 0L)) }
        assertFalse(motion.detector.armed)
        assertNull(motion.sample(0f, afterMs = 10L))
        assertTrue(motion.detector.armed)
    }

    @Test fun duplicateInvalidOrBlockedTiltStillDisarmsAndClearsPendingAnswer() {
        for (invalid in listOf(false, true)) {
            val motion = Motion()
            motion.calibrate(22f)
            assertNull(motion.sample(-10f))
            if (invalid) assertNull(motion.raw(0f, 0f, 0f, afterMs = 0L))
            else assertNull(motion.sample(-10f, afterMs = 0L, acceptTilt = false))
            assertFalse(motion.detector.armed)
            assertTrue(motion.hold(-10f, 1_000L).isEmpty())
            assertEquals(22f, checkNotNull(motion.detector.neutralDegrees), 0.0001f)
        }
    }

    @Test fun backwardsTimeDiscardsReadingAndPartialCalibration() {
        val motion = Motion()
        assertTrue(motion.hold(22f, 340L).isEmpty())
        assertNull(motion.sample(-22f, afterMs = -1L))
        assertTrue(motion.hold(22f, 340L).isEmpty())
        assertFalse(motion.detector.calibrated)
        assertNull(motion.sample(22f, afterMs = 10L))
        assertTrue(motion.detector.armed)
        assertEquals(22f, checkNotNull(motion.detector.neutralDegrees), 0.0001f)
    }

    @Test fun backwardsTimeDisarmsAndClearsTiltButPreservesBaseline() {
        val motion = Motion()
        motion.calibrate(22f)
        assertNull(motion.sample(-10f))
        assertNull(motion.sample(22f, afterMs = -1L)) // Discard even this centered reading.
        assertFalse(motion.detector.armed)
        assertTrue(motion.hold(-10f, 1_000L).isEmpty())
        assertEquals(22f, checkNotNull(motion.detector.neutralDegrees), 0.0001f)
        motion.rearm()
        motion.score(-10f, Outcome.CORRECT)
    }

    @Test fun missingSamplesCannotBeCreditedAsInitialCalibration() {
        val motion = Motion()
        assertNull(motion.sample(22f))
        repeat(10) { assertNull(motion.sample(22f, afterMs = 350L)) }
        assertFalse(motion.detector.calibrated)
        assertTrue(motion.hold(22f, 320L).isEmpty()) // 340 ms since last gap endpoint.
        assertFalse(motion.detector.calibrated)
        assertNull(motion.sample(22f, afterMs = 10L))
        assertTrue(motion.detector.armed)
    }

    @Test fun gapOver250ClearsTiltAndArmingWhileKeepingLearnedBaseline() {
        val motion = Motion()
        motion.calibrate(22f)
        assertNull(motion.sample(-10f))
        assertNull(motion.sample(-10f, afterMs = 251L))
        assertFalse(motion.detector.armed)
        assertTrue(motion.hold(-10f, 1_000L).isEmpty())
        assertEquals(22f, checkNotNull(motion.detector.neutralDegrees), 0.0001f)
        motion.rearm()
        motion.score(-10f, Outcome.CORRECT)
    }

    @Test fun gapOver250RestartsPartialCenterDwell() {
        val motion = Motion()
        motion.calibrate()
        motion.score(-32f, Outcome.CORRECT)
        assertTrue(motion.hold(0f, 80L).isEmpty())
        assertNull(motion.sample(0f, afterMs = 251L))
        assertFalse(motion.detector.armed)
        repeat(4) { assertNull(motion.sample(0f)) }
        assertFalse(motion.detector.armed)
        assertNull(motion.sample(0f, afterMs = 10L))
        assertTrue(motion.detector.armed)
    }

    @Test fun gapDuringCountdownRestartsPartialReplacementNotTheOldBaseline() {
        val motion = Motion()
        motion.calibrate(calibrating = true)
        assertTrue(motion.hold(22f, 320L, calibrating = true).isEmpty())
        assertNull(motion.sample(22f, afterMs = 251L, calibrating = true))
        assertTrue(motion.hold(22f, 320L, calibrating = true).isEmpty())
        assertEquals(0f, checkNotNull(motion.detector.neutralDegrees), 0f)
        assertNull(motion.sample(22f, afterMs = 10L, calibrating = true))
        assertEquals(22f, checkNotNull(motion.detector.neutralDegrees), 0.0001f)
    }

    @Test fun gapLimitIsInclusiveAndAppliesPerReadingNotToTotalHoldDuration() {
        val motion = Motion()
        assertNull(motion.sample(0f))
        assertNull(motion.sample(0f, afterMs = 250L)) // Deliberate exact gap boundary test.
        repeat(4) { assertNull(motion.sample(0f)) }
        assertFalse(motion.detector.calibrated)
        assertNull(motion.sample(0f)) // Total 350 ms, but no individual gap over 250.
        assertTrue(motion.detector.armed)
        assertNull(motion.sample(-32f))
        assertEquals(Outcome.CORRECT, motion.sample(-32f, afterMs = 250L))
    }

    @Test fun arbitraryTimestampEpochsDoNotChangeCalibrationOrScoring() {
        for (start in listOf(Long.MIN_VALUE + 1_000L, -1_000_000L, 0L, 8_000_000_000L, Long.MAX_VALUE - 2_000L)) {
            val motion = Motion(now = start - 20L)
            motion.calibrate(22f)
            motion.score(-10f, Outcome.CORRECT)
        }
    }

    @Test fun overflowingForwardTimeDifferenceIsAGapNotEarnedDwell() {
        val motion = Motion(now = Long.MIN_VALUE)
        motion.calibrate(22f)
        assertNull(motion.sample(-10f))
        motion.now = Long.MAX_VALUE - 2_000L
        assertNull(motion.sample(-10f, afterMs = 0L))
        assertFalse(motion.detector.armed)
        assertTrue(motion.hold(-10f, 500L).isEmpty())
        assertEquals(22f, checkNotNull(motion.detector.neutralDegrees), 0.0001f)
        motion.rearm()
        motion.score(-10f, Outcome.CORRECT)
    }

    @Test fun continuousCyclesEmitExactlyOneAnswerEachAndNeverUnanswered() {
        val motion = Motion()
        motion.calibrate(22f)
        val results = mutableListOf<Outcome>()
        repeat(100) { index ->
            if (index > 0) motion.rearm(acceptTilt = false)
            val angle = if (index % 2 == 0) -10f else 54f
            results += motion.hold(angle, 300L)
            assertFalse(motion.detector.armed)
        }
        assertEquals(100, results.size)
        assertEquals(50, results.count { it == Outcome.CORRECT })
        assertEquals(50, results.count { it == Outcome.PASS })
        assertFalse(results.contains(Outcome.UNANSWERED))
        assertEquals(22f, checkNotNull(motion.detector.neutralDegrees), 0.0001f)
    }

    @Test fun bothNormalNodsRemainReachableAtTheMaximumForeheadLean() {
        for (baseline in listOf(-40f, 40f)) for (sign in listOf(-1f, 1f)) {
            val motion = Motion()
            motion.calibrate(baseline)
            motion.score(baseline + sign * 28f, outcome(sign))
        }
    }

    @Test fun continuousCountdownAndFeedbackSamplingAnswersOnlyVisibleCards() {
        val engine = RoundEngine(listOf("Apple", "Bicycle", "Cloud"), 30)
        val motion = Motion()
        engine.begin(0L)

        fun feed(degrees: Float, samples: Int) {
            repeat(samples) {
                // Input gating uses visibility at entry, not a tick that may reveal a new card.
                val visibleAtEntry = engine.phase == Phase.PLAYING && engine.currentWord != null
                val answer = motion.sample(
                    degrees,
                    calibrating = engine.phase == Phase.COUNTDOWN,
                    acceptTilt = visibleAtEntry,
                )
                engine.tick(motion.now)
                if (answer != null) {
                    assertTrue(visibleAtEntry)
                    assertTrue(engine.mark(answer, motion.now))
                }
            }
        }

        feed(22f, 151) // 0..3000 ms of countdown, retaining the learned hold into play.
        assertEquals("Apple", engine.currentWord)
        assertTrue(motion.detector.armed)
        feed(-10f, 4)
        assertEquals(1, engine.answers.size)
        assertNull(engine.currentWord)
        feed(22f, 6) // Rearm while the first answer's feedback is still covering the card.
        assertTrue(motion.detector.armed)
        feed(52f, 40) // Hidden nod stays held through the next card becoming visible.
        assertEquals(1, engine.answers.size)
        assertEquals("Bicycle", engine.currentWord)
        assertFalse(motion.detector.armed)
        feed(22f, 6)
        feed(52f, 4)
        assertEquals(listOf(Answer("Apple", Outcome.CORRECT), Answer("Bicycle", Outcome.PASS)),
            engine.answers)
        assertEquals(1, engine.score)
        assertEquals(22f, checkNotNull(motion.detector.neutralDegrees), 0.0001f)
    }
}