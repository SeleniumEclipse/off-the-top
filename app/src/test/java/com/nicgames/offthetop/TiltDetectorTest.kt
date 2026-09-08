package com.nicgames.offthetop

import org.junit.Assert.*
import org.junit.Test

class TiltDetectorTest {
    private fun arm(detector: TiltDetector, start: Long = 0L, x: Float = 10f, y: Float = 0f) {
        assertNull(detector.sample(x, y, 0f, start))
        assertFalse(detector.armed)
        assertNull(detector.sample(x, y, 0f, start + 249L))
        assertFalse(detector.armed)
        assertNull(detector.sample(x, y, 0f, start + 250L))
        assertTrue(detector.armed)
    }

    @Test fun startsAndResetsUnarmedWithDefaultThreshold() {
        val detector = TiltDetector()
        assertEquals(0.68f, detector.threshold, 0f)
        assertFalse(detector.armed)
        arm(detector)
        detector.reset()
        assertFalse(detector.armed)
        assertEquals(0.68f, detector.threshold, 0f)
        assertNull(detector.sample(6f, 0f, -8f, 1_000L))
        assertNull(detector.sample(6f, 0f, -8f, 2_000L))
    }

    @Test fun uprightMustBeHeldForExactlyAtLeast250Milliseconds() {
        val detector = TiltDetector()
        assertNull(detector.sample(10f, 0f, 0f, 0L))
        for (now in 1L..249L) {
            assertNull(detector.sample(10f, 0f, 0f, now))
            assertFalse("armed too early at $now", detector.armed)
        }
        assertNull(detector.sample(10f, 0f, 0f, 250L))
        assertTrue(detector.armed)
    }

    @Test fun bothLandscapeDirectionsArmAndBothZSignsHaveCorrectMeaning() {
        for (direction in listOf(-1f, 1f)) {
            for ((z, expected) in listOf(-8f to Outcome.CORRECT, 8f to Outcome.PASS)) {
                val detector = TiltDetector()
                arm(detector, x = direction * 10f)
                assertNull(detector.sample(direction * 6f, 0f, z, 300L))
                assertNull(detector.sample(direction * 6f, 0f, z, 419L))
                assertEquals(expected, detector.sample(direction * 6f, 0f, z, 420L))
                assertFalse(detector.armed)
            }
        }
    }

    @Test fun naturalLandscapeTabletsCanArmUsingEitherYDirection() {
        for (direction in listOf(-1f, 1f)) {
            for ((z, expected) in listOf(-8f to Outcome.CORRECT, 8f to Outcome.PASS)) {
                val detector = TiltDetector()
                arm(detector, x = 0f, y = direction * 10f)
                assertNull(detector.sample(0f, direction * 6f, z, 300L))
                assertEquals(expected, detector.sample(0f, direction * 6f, z, 420L))
            }
        }
    }

    @Test fun diagonalUprightPostureCanArmWithoutRequiringOneSpecificAxis() {
        val detector = TiltDetector()
        assertNull(detector.sample(7f, -7f, 1f, 0L))
        assertNull(detector.sample(7f, -7f, 1f, 250L))
        assertTrue(detector.armed)
    }

    @Test fun tiltedOrFlatPhoneNeverArmsEvenAfterLongHold() {
        for ((x, z) in listOf(0f to -10f, 0f to 10f, 6f to -8f, 6f to 8f,
            8f to 6f, 8f to -6f)) {
            val detector = TiltDetector()
            for (now in listOf(0L, 250L, 10_000L)) {
                assertNull(detector.sample(x, 0f, z, now))
                assertFalse(detector.armed)
            }
        }
    }

    @Test fun uprightZBoundaryIsStrictAndSymmetric() {
        for (sign in listOf(-1f, 1f)) {
            val detector = TiltDetector()
            // These exactly representable components have norm 12.5 and |Z| / norm = 0.28.
            assertNull(detector.sample(12f, 0f, sign * 3.5f, 0L))
            assertNull(detector.sample(12f, 0f, sign * 3.5f, 250L))
            assertFalse(detector.armed)
            assertNull(detector.sample(12f, 0f, sign * 3.49f, 300L))
            assertNull(detector.sample(12f, 0f, sign * 3.49f, 550L))
            assertTrue(detector.armed)
        }
    }

    @Test fun smallUprightJitterDoesNotRestartTheStableHold() {
        val detector = TiltDetector()
        assertNull(detector.sample(10f, 0f, 0f, 0L))
        assertNull(detector.sample(9f, 1f, 2f, 100L))
        assertNull(detector.sample(9f, -1f, -2f, 249L))
        assertFalse(detector.armed)
        assertNull(detector.sample(10f, 0f, 0.1f, 250L))
        assertTrue(detector.armed)
    }

    @Test fun leavingUprightBeforeArmingRestartsTheFullStableHold() {
        val detector = TiltDetector()
        assertNull(detector.sample(10f, 0f, 0f, 0L))
        assertNull(detector.sample(9f, 0f, 4f, 200L))
        assertNull(detector.sample(10f, 0f, 0f, 250L))
        assertFalse(detector.armed)
        assertNull(detector.sample(10f, 0f, 0f, 499L))
        assertFalse(detector.armed)
        assertNull(detector.sample(10f, 0f, 0f, 500L))
        assertTrue(detector.armed)
    }

    @Test fun armedDetectorNeedsFull120MillisecondTiltHold() {
        for (z in listOf(-8f, 8f)) {
            val detector = TiltDetector()
            arm(detector)
            assertNull(detector.sample(6f, 0f, z, 1_000L))
            for (now in 1_001L..1_119L) {
                assertNull("scored too early at $now", detector.sample(6f, 0f, z, now))
                assertTrue(detector.armed)
            }
            assertEquals(if (z < 0f) Outcome.CORRECT else Outcome.PASS,
                detector.sample(6f, 0f, z, 1_120L))
            assertFalse(detector.armed)
        }
    }

    @Test fun tiltHoldLongerThanDeadlineScoresAtNextSample() {
        val detector = TiltDetector()
        arm(detector)
        assertNull(detector.sample(6f, 0f, -8f, 300L))
        assertEquals(Outcome.CORRECT, detector.sample(6f, 0f, -8f, 700L))
    }

    @Test fun neutralPostureBelowThresholdNeverScoresButKeepsDetectorArmed() {
        val detector = TiltDetector()
        arm(detector)
        for (now in listOf(300L, 500L, 1_000L, 10_000L)) {
            assertNull(detector.sample(8f, 0f, 6f, now))
            assertTrue(detector.armed)
        }
        assertNull(detector.sample(6f, 0f, 8f, 10_001L))
        assertEquals(Outcome.PASS, detector.sample(6f, 0f, 8f, 10_121L))
    }

    @Test fun crossingThresholdBrieflyAndReturningToNeutralDoesNotScore() {
        val detector = TiltDetector()
        arm(detector)
        assertNull(detector.sample(6f, 0f, -8f, 300L))
        assertNull(detector.sample(8f, 0f, -6f, 419L))
        assertNull(detector.sample(6f, 0f, -8f, 420L))
        assertNull(detector.sample(6f, 0f, -8f, 539L))
        assertEquals(Outcome.CORRECT, detector.sample(6f, 0f, -8f, 540L))
    }

    @Test fun repeatedThresholdJitterCannotAccumulateSeparateShortTilts() {
        for (sign in listOf(-1f, 1f)) {
            val detector = TiltDetector()
            arm(detector)
            repeat(20) { index ->
                val now = 300L + index * 120L
                assertNull(detector.sample(6f, 0f, sign * 8f, now))
                assertNull(detector.sample(8f, 0f, sign * 6f, now + 119L))
            }
            assertTrue(detector.armed)
            assertNull(detector.sample(6f, 0f, sign * 8f, 3_000L))
            assertEquals(if (sign < 0f) Outcome.CORRECT else Outcome.PASS,
                detector.sample(6f, 0f, sign * 8f, 3_120L))
        }
    }

    @Test fun switchingTiltDirectionRestartsDwellRatherThanReusingOtherDirection() {
        for (sign in listOf(-1f, 1f)) {
            val detector = TiltDetector()
            arm(detector)
            assertNull(detector.sample(6f, 0f, -sign * 8f, 300L))
            assertNull(detector.sample(6f, 0f, sign * 8f, 419L))
            assertNull(detector.sample(6f, 0f, sign * 8f, 538L))
            assertEquals(if (sign < 0f) Outcome.CORRECT else Outcome.PASS,
                detector.sample(6f, 0f, sign * 8f, 539L))
        }
    }

    @Test fun returningUprightCancelsPendingTiltWithoutScoring() {
        val detector = TiltDetector()
        arm(detector)
        assertNull(detector.sample(6f, 0f, -8f, 300L))
        assertNull(detector.sample(10f, 0f, 0f, 419L))
        assertNull(detector.sample(6f, 0f, -8f, 420L))
        assertNull(detector.sample(6f, 0f, -8f, 539L))
        assertEquals(Outcome.CORRECT, detector.sample(6f, 0f, -8f, 540L))
    }

    @Test fun heldTiltNeverScoresTwiceEvenAfterLongTime() {
        for (z in listOf(-8f, 8f)) {
            val detector = TiltDetector()
            arm(detector)
            assertNull(detector.sample(6f, 0f, z, 300L))
            assertNotNull(detector.sample(6f, 0f, z, 420L))
            for (now in 421L..10_000L) {
                assertNull(detector.sample(6f, 0f, z, now))
                assertFalse(detector.armed)
            }
        }
    }

    @Test fun oppositeTiltWithoutUprightCannotScoreAfterLatching() {
        val detector = TiltDetector()
        arm(detector)
        detector.sample(6f, 0f, -8f, 300L)
        assertEquals(Outcome.CORRECT, detector.sample(6f, 0f, -8f, 420L))
        assertNull(detector.sample(6f, 0f, 8f, 500L))
        assertNull(detector.sample(6f, 0f, 8f, 10_000L))
        assertFalse(detector.armed)
    }

    @Test fun neutralButNotUprightCannotRearmAfterScoring() {
        val detector = TiltDetector()
        arm(detector)
        detector.sample(6f, 0f, -8f, 300L)
        assertEquals(Outcome.CORRECT, detector.sample(6f, 0f, -8f, 420L))
        assertNull(detector.sample(8f, 0f, 6f, 500L))
        assertNull(detector.sample(8f, 0f, 6f, 10_000L))
        assertFalse(detector.armed)
        assertNull(detector.sample(6f, 0f, 8f, 10_001L))
        assertNull(detector.sample(6f, 0f, 8f, 10_121L))
    }

    @Test fun fresh250MillisecondUprightHoldRearmsForNextCard() {
        val detector = TiltDetector()
        arm(detector)
        detector.sample(6f, 0f, -8f, 300L)
        assertEquals(Outcome.CORRECT, detector.sample(6f, 0f, -8f, 420L))
        arm(detector, start = 500L)
        assertNull(detector.sample(6f, 0f, 8f, 800L))
        assertEquals(Outcome.PASS, detector.sample(6f, 0f, 8f, 920L))
    }

    @Test fun briefReturnUprightDoesNotRearmOrScore() {
        val detector = TiltDetector()
        arm(detector)
        detector.sample(6f, 0f, -8f, 300L)
        assertEquals(Outcome.CORRECT, detector.sample(6f, 0f, -8f, 420L))
        assertNull(detector.sample(10f, 0f, 0f, 500L))
        assertNull(detector.sample(10f, 0f, 0f, 749L))
        assertNull(detector.sample(6f, 0f, 8f, 750L))
        assertNull(detector.sample(6f, 0f, 8f, 1_000L))
        assertFalse(detector.armed)
        arm(detector, start = 1_100L)
    }

    @Test fun validGravityMagnitudesAllGiveSameDirectionAndTiming() {
        for (scale in listOf(0.5f, 0.8f, 1f, 1.5f)) {
            for ((z, outcome) in listOf(-8f to Outcome.CORRECT, 8f to Outcome.PASS)) {
                val detector = TiltDetector()
                arm(detector, x = 10f * scale)
                assertNull(detector.sample(6f * scale, 0f, z * scale, 300L))
                assertEquals(outcome, detector.sample(6f * scale, 0f, z * scale, 420L))
            }
        }
    }

    @Test fun gravityMagnitudeBoundsFiveAndSixteenAreInclusive() {
        for (magnitude in listOf(5f, 16f)) {
            for ((sign, outcome) in listOf(-1f to Outcome.CORRECT, 1f to Outcome.PASS)) {
                val detector = TiltDetector()
                arm(detector, x = magnitude)
                assertNull(detector.sample(0f, 0f, sign * magnitude, 300L))
                assertEquals(outcome, detector.sample(0f, 0f, sign * magnitude, 420L))
            }
        }
    }

    @Test fun zeroFreeFallAndShakenVectorsCannotArm() {
        for (magnitude in listOf(0f, 1f, 4.999f, 16.001f, 30f, Float.MAX_VALUE)) {
            val detector = TiltDetector()
            assertNull(detector.sample(magnitude, 0f, 0f, 0L))
            assertNull(detector.sample(magnitude, 0f, 0f, 10_000L))
            assertFalse(detector.armed)
        }
    }

    @Test fun rejectsCombinedVectorMagnitudeEvenIfEachComponentLooksValid() {
        val detector = TiltDetector()
        arm(detector)
        assertNull(detector.sample(12f, 0f, -12f, 300L))
        assertNull(detector.sample(12f, 0f, -12f, 1_000L))
        assertFalse(detector.armed)
    }

    @Test fun nonFiniteValuesInAnyComponentAreIgnoredAndDisarm() {
        for (invalid in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            for (axis in 0..2) {
                val detector = TiltDetector()
                arm(detector)
                val vector = floatArrayOf(6f, 0f, -8f)
                vector[axis] = invalid
                assertNull(detector.sample(vector[0], vector[1], vector[2], 300L))
                assertFalse(detector.armed)
                assertNull(detector.sample(6f, 0f, -8f, 420L))
            }
        }
    }

    @Test fun invalidVectorBreaksUprightStabilityInsteadOfCountingMissingTime() {
        for (magnitude in listOf(0f, 4f, 17f)) {
            val detector = TiltDetector()
            assertNull(detector.sample(10f, 0f, 0f, 0L))
            assertNull(detector.sample(magnitude, 0f, 0f, 200L))
            assertNull(detector.sample(10f, 0f, 0f, 250L))
            assertFalse(detector.armed)
            assertNull(detector.sample(10f, 0f, 0f, 499L))
            assertFalse(detector.armed)
            assertNull(detector.sample(10f, 0f, 0f, 500L))
            assertTrue(detector.armed)
        }
    }

    @Test fun invalidVectorCancelsPendingTiltAndRequiresFreshArming() {
        for (z in listOf(0f, -4f, 4f, -17f, 17f)) {
            val detector = TiltDetector()
            arm(detector)
            assertNull(detector.sample(6f, 0f, -8f, 300L))
            assertNull(detector.sample(0f, 0f, z, 419L))
            assertFalse(detector.armed)
            assertNull(detector.sample(6f, 0f, -8f, 420L))
            assertNull(detector.sample(6f, 0f, -8f, 1_000L))
            arm(detector, start = 1_100L)
            assertNull(detector.sample(6f, 0f, -8f, 1_400L))
            assertEquals(Outcome.CORRECT, detector.sample(6f, 0f, -8f, 1_520L))
        }
    }

    @Test fun motionImmediatelyAfterResetCannotReusePendingTilt() {
        val detector = TiltDetector()
        arm(detector)
        detector.sample(6f, 0f, -8f, 300L)
        detector.reset()
        assertFalse(detector.armed)
        assertNull(detector.sample(6f, 0f, -8f, 420L))
        assertNull(detector.sample(6f, 0f, -8f, 10_000L))
        arm(detector, start = 11_000L)
        assertNull(detector.sample(6f, 0f, -8f, 11_300L))
        assertEquals(Outcome.CORRECT, detector.sample(6f, 0f, -8f, 11_420L))
    }

    @Test fun resetDiscardsPartialUprightHoldAndAllowsNewTimestampEpoch() {
        val detector = TiltDetector()
        detector.sample(10f, 0f, 0f, 100_000L)
        detector.sample(10f, 0f, 0f, 100_200L)
        detector.reset()
        arm(detector, start = 0L)
    }

    @Test fun repeatedResetIsSafeAndPreservesSensitivity() {
        val detector = TiltDetector()
        detector.threshold = 0.85f
        repeat(10) { detector.reset() }
        assertFalse(detector.armed)
        assertEquals(0.85f, detector.threshold, 0f)
        arm(detector)
        assertNull(detector.sample(6f, 0f, -8f, 300L))
        assertNull(detector.sample(6f, 0f, -8f, 420L))
        assertNull(detector.sample(0f, 0f, -10f, 500L))
        assertEquals(Outcome.CORRECT, detector.sample(0f, 0f, -10f, 620L))
    }

    @Test fun customThresholdAppliesSymmetricallyToNormalizedZ() {
        for (sign in listOf(-1f, 1f)) {
            val detector = TiltDetector()
            detector.threshold = 0.5f
            arm(detector)
            assertNull(detector.sample(8f, 0f, sign * 6f, 300L))
            assertEquals(if (sign < 0f) Outcome.CORRECT else Outcome.PASS,
                detector.sample(8f, 0f, sign * 6f, 420L))
        }
    }

    @Test fun thresholdBoundaryDoesNotScoreUntilExceeded() {
        for (sign in listOf(-1f, 1f)) {
            val detector = TiltDetector()
            detector.threshold = 0.6f
            arm(detector)
            assertNull(detector.sample(8f, 0f, sign * 6f, 300L))
            assertNull(detector.sample(8f, 0f, sign * 6f, 1_000L))
            assertNull(detector.sample(8f, 0f, sign * 6.01f, 1_100L))
            assertEquals(if (sign < 0f) Outcome.CORRECT else Outcome.PASS,
                detector.sample(8f, 0f, sign * 6.01f, 1_220L))
        }
    }

    @Test fun changingThresholdRestartsPendingDwell() {
        val detector = TiltDetector()
        arm(detector)
        detector.sample(6f, 0f, -8f, 300L)
        detector.threshold = 0.7f
        assertNull(detector.sample(6f, 0f, -8f, 419L))
        assertNull(detector.sample(6f, 0f, -8f, 538L))
        assertEquals(Outcome.CORRECT, detector.sample(6f, 0f, -8f, 539L))
    }

    @Test fun rejectsNonFiniteOrUnusableThresholds() {
        val detector = TiltDetector()
        for (value in listOf(-1f, 0f, 1f, 2f, Float.NaN, Float.POSITIVE_INFINITY,
            Float.NEGATIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) { detector.threshold = value }
        }
        assertEquals(0.68f, detector.threshold, 0f)
    }

    @Test fun duplicateTimestampSamplesCannotSatisfyEitherHold() {
        val detector = TiltDetector()
        repeat(100) { assertNull(detector.sample(10f, 0f, 0f, 0L)) }
        assertFalse(detector.armed)
        assertNull(detector.sample(10f, 0f, 0f, 250L))
        assertTrue(detector.armed)
        repeat(100) { assertNull(detector.sample(6f, 0f, -8f, 300L)) }
        assertTrue(detector.armed)
        assertEquals(Outcome.CORRECT, detector.sample(6f, 0f, -8f, 420L))
        repeat(100) { assertNull(detector.sample(6f, 0f, -8f, 420L)) }
    }

    @Test fun backwardsTimestampDisarmsAndCannotCreditAPendingTilt() {
        val detector = TiltDetector()
        arm(detector)
        detector.sample(6f, 0f, -8f, 300L)
        assertNull(detector.sample(6f, 0f, -8f, 299L))
        assertFalse(detector.armed)
        assertNull(detector.sample(6f, 0f, -8f, 420L))
        arm(detector, start = 500L)
    }

    @Test fun arbitraryTimestampEpochsDoNotChangeArmingOrTiltDeadlines() {
        for (start in listOf(-1_000_000L, 0L, 8_000_000_000L, Long.MAX_VALUE - 420L)) {
            val detector = TiltDetector()
            arm(detector, start)
            assertNull(detector.sample(6f, 0f, -8f, start + 300L))
            assertNull(detector.sample(6f, 0f, -8f, start + 419L))
            assertEquals(Outcome.CORRECT, detector.sample(6f, 0f, -8f, start + 420L))
        }
    }

    @Test fun repeatedUprightTiltCyclesProduceExactlyOneEventPerCycle() {
        val detector = TiltDetector()
        val outcomes = mutableListOf<Outcome>()
        repeat(100) { index ->
            val start = index * 1_000L
            val z = if (index % 2 == 0) -8f else 8f
            arm(detector, start)
            assertNull(detector.sample(6f, 0f, z, start + 300L))
            detector.sample(6f, 0f, z, start + 420L)?.let(outcomes::add)
            assertNull(detector.sample(6f, 0f, z, start + 900L))
        }
        assertEquals(100, outcomes.size)
        assertEquals(50, outcomes.count { it == Outcome.CORRECT })
        assertEquals(50, outcomes.count { it == Outcome.PASS })
        assertFalse(outcomes.contains(Outcome.UNANSWERED))
    }

    @Test fun tiltEventsDriveEngineAndEngineCooldownRejectsExtraFastGestures() {
        val engine = RoundEngine(listOf("Apple", "Bicycle", "Cloud"), 30)
        val detector = TiltDetector()
        engine.begin(0L)
        engine.tick(3_000L)
        arm(detector, start = 3_000L)
        assertNull(detector.sample(6f, 0f, -8f, 3_300L))
        val first = detector.sample(6f, 0f, -8f, 3_420L)
        assertEquals(Outcome.CORRECT, first)
        assertTrue(engine.mark(checkNotNull(first), 3_420L))
        assertNull(detector.sample(6f, 0f, -8f, 3_430L))
        arm(detector, start = 3_440L)
        assertNull(detector.sample(6f, 0f, 8f, 3_700L))
        val fast = detector.sample(6f, 0f, 8f, 3_820L)
        assertEquals(Outcome.PASS, fast)
        assertFalse(engine.mark(checkNotNull(fast), 3_820L))
        assertEquals(1, engine.answers.size)
        arm(detector, start = 3_900L)
        engine.tick(4_150L)
        assertNull(engine.feedback)
        assertEquals("Bicycle", engine.currentWord)
        assertNull(detector.sample(6f, 0f, 8f, 4_200L))
        val next = detector.sample(6f, 0f, 8f, 4_320L)
        assertEquals(Outcome.PASS, next)
        assertTrue(engine.mark(checkNotNull(next), 4_320L))
        assertEquals(listOf(Answer("Apple", Outcome.CORRECT), Answer("Bicycle", Outcome.PASS)),
            engine.answers)
        assertEquals(1, engine.score)
    }
}