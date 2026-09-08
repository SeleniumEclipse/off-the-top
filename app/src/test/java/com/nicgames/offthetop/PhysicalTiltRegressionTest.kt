package com.nicgames.offthetop

import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.*
import org.junit.Test

/** Ordinary forehead holds and gentle nods, rather than only ideal 90-degree flips. */
class PhysicalTiltRegressionTest {
    private fun sample(detector: TiltDetector, degrees: Double, now: Long): Outcome? {
        val radians = Math.toRadians(degrees)
        return detector.sample((cos(radians) * 9.81).toFloat(), 0f, (sin(radians) * 9.81).toFloat(), now)
    }

    @Test fun naturallyLeaningForeheadHoldCanArm() {
        val detector = TiltDetector()
        for (now in 0L..600L step 20L) assertNull(sample(detector, 22.0, now))
        assertTrue("A comfortable 22-degree hold must not require perfectly vertical alignment", detector.armed)
    }

    @Test fun normalDownwardNodDoesNotRequireScreenFlatToFloor() {
        val detector = TiltDetector()
        for (now in 0L..600L step 20L) sample(detector, 0.0, now)
        val outcomes = (620L..900L step 20L).mapNotNull { sample(detector, -32.0, it) }
        assertEquals("A deliberate 32-degree downward nod should score exactly once", listOf(Outcome.CORRECT), outcomes)
    }
}