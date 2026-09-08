package com.nicgames.offthetop

import kotlin.math.abs
import kotlin.math.sqrt

enum class Outcome { CORRECT, PASS, UNANSWERED }

data class Answer(val word: String, val outcome: Outcome)

enum class Phase { READY, COUNTDOWN, PLAYING, PAUSED, FINISHED }

/**
 * A single, ordered round. All timestamps are caller-supplied monotonic milliseconds.
 * Call [tick] to advance time; there are no timers, threads, or Android dependencies.
 * Pausing a countdown preserves its exact remainder. Resuming gameplay adds a fresh
 * three-second countdown without consuming round time or feedback/cooldown time.
 */
class RoundEngine(val words: List<String>, val durationSeconds: Int) {
    // A caller's mutable list must not change the cards in an already-created round.
    private val deck = words.toList()
    private val recordedAnswers = mutableListOf<Answer>()
    private var lastNow = 0L
    private var countdownRemainingMs = COUNTDOWN_MS
    private var cooldownRemainingMs = 0L
    private var pausedPhase: Phase? = null
    private var hasPlayed = false
    private var roundPhase = Phase.READY
    private var roundRemainingMs = durationSeconds.toLong() * 1_000L
    private var countdownNumber = 3
    private var currentFeedback: Outcome? = null

    init {
        require(durationSeconds > 0) { "Round duration must be positive." }
        require(deck.isNotEmpty()) { "A round needs at least one word." }
        require(deck.none { it.isBlank() }) { "Words must not be blank." }
        require(deck.map { it.trim().lowercase() }.toSet().size == deck.size) {
            "Words must be unique, ignoring case and surrounding whitespace."
        }
    }

    val phase: Phase
        get() = roundPhase

    /** A snapshot, so review and caller mutations cannot change earlier snapshots. */
    val answers: List<Answer>
        get() = recordedAnswers.toList()

    /** No card before first playing, during feedback, or after finishing; pauses keep shown cards. */
    val currentWord: String?
        get() = if (hasPlayed && phase != Phase.FINISHED && currentFeedback == null) {
            deck.getOrNull(recordedAnswers.size)
        } else {
            null
        }

    val remainingMs: Long
        get() = roundRemainingMs

    val countdown: Int
        get() = countdownNumber

    val score: Int
        get() = recordedAnswers.count { it.outcome == Outcome.CORRECT }

    val feedback: Outcome?
        get() = currentFeedback

    fun begin(now: Long) {
        if (phase != Phase.READY) return
        lastNow = now
        roundPhase = Phase.COUNTDOWN
    }

    fun tick(now: Long) {
        if (phase != Phase.COUNTDOWN && phase != Phase.PLAYING) return
        if (now <= lastNow) return
        // Subtract rather than adding a deadline, so a large timestamp cannot overflow it.
        var elapsed = now - lastNow
        if (elapsed < 0L) elapsed = Long.MAX_VALUE
        lastNow = now

        if (phase == Phase.COUNTDOWN) {
            val consumed = minOf(elapsed, countdownRemainingMs)
            countdownRemainingMs -= consumed
            elapsed -= consumed
            countdownNumber = ((countdownRemainingMs + 999L) / 1_000L).toInt()
            if (countdownRemainingMs > 0L) return
            roundPhase = Phase.PLAYING
            hasPlayed = true
        }

        roundRemainingMs = (remainingMs - elapsed).coerceAtLeast(0L)
        // Expiry wins over input and cooldown, including when a frame arrives late.
        if (remainingMs == 0L) {
            endRound()
            return
        }
        cooldownRemainingMs = (cooldownRemainingMs - elapsed).coerceAtLeast(0L)
        if (cooldownRemainingMs == 0L) currentFeedback = null
    }

    fun mark(outcome: Outcome, now: Long): Boolean {
        // Input that clears feedback must not also answer the next, still-hidden card.
        val feedbackActiveAtEntry = currentFeedback != null
        tick(now)
        if (feedbackActiveAtEntry || phase != Phase.PLAYING || outcome == Outcome.UNANSWERED ||
            cooldownRemainingMs > 0L
        ) return false

        val word = currentWord ?: return false
        recordedAnswers += Answer(word, outcome)
        if (recordedAnswers.size == deck.size) {
            endRound()
        } else {
            currentFeedback = outcome
            cooldownRemainingMs = FEEDBACK_MS
        }
        return true
    }

    fun pause(now: Long) {
        tick(now)
        if (phase != Phase.COUNTDOWN && phase != Phase.PLAYING) return
        pausedPhase = phase
        roundPhase = Phase.PAUSED
    }

    fun resume(now: Long) {
        if (phase != Phase.PAUSED) return
        if (pausedPhase == Phase.PLAYING) {
            countdownRemainingMs = COUNTDOWN_MS
            countdownNumber = 3
        }
        lastNow = now
        roundPhase = Phase.COUNTDOWN
        pausedPhase = null
    }

    /** Ends even while paused; only an already-shown current card becomes unanswered. */
    fun finish(now: Long) {
        if (phase == Phase.FINISHED) return
        tick(now)
        if (phase != Phase.FINISHED) endRound()
    }

    /** Review toggles correct to pass, and pass/unanswered to correct. Never adds rows. */
    fun correctAnswer(index: Int) {
        if (phase != Phase.FINISHED || index !in recordedAnswers.indices) return
        val answer = recordedAnswers[index]
        recordedAnswers[index] = answer.copy(
            outcome = if (answer.outcome == Outcome.CORRECT) Outcome.PASS else Outcome.CORRECT,
        )
    }

    private fun endRound() {
        currentWord?.let { recordedAnswers += Answer(it, Outcome.UNANSWERED) }
        roundPhase = Phase.FINISHED
        roundRemainingMs = 0L
        countdownNumber = 0
        cooldownRemainingMs = 0L
        currentFeedback = null
        pausedPhase = null
    }

    private companion object {
        const val COUNTDOWN_MS = 3_000L
        const val FEEDBACK_MS = 650L
    }
}

/**
 * Gravity-vector tilt relative to a stable forehead hold, in either landscape direction.
 * Negative relative angles mean floor/correct; positive angles mean ceiling/pass.
 * Supply sensor timestamps in monotonic milliseconds, including during hidden feedback.
 * Countdown may retrack the hold; play never changes it without an explicit [reset].
 */
class TiltDetector {
    var activationDegrees: Float = 28f
        set(value) {
            require(value.isFinite() && value in 20f..45f) {
                "Tilt activation must be between 20 and 45 degrees."
            }
            field = value
            clearTilt()
        }

    val armed: Boolean
        get() = isArmed

    val calibrated: Boolean
        get() = baseline != null && !settlingCountdown

    /** Last valid angle minus the baseline; zero until calibrated. Invalid samples keep it. */
    val relativeDegrees: Float
        get() = relativeAngle

    val neutralDegrees: Float?
        get() = baseline

    private var isArmed = false
    private var baseline: Float? = null
    private var settlingCountdown = false
    private var relativeAngle = 0f
    private var stableSince: Long? = null
    private var stableMin = 0f
    private var stableMax = 0f
    private var stableMean = 0.0
    private var stableCount = 0L
    private var centerSince: Long? = null
    private var tiltSince: Long? = null
    private var pendingOutcome: Outcome? = null
    private var lastNow: Long? = null

    /** Explicit recenter: forget the hold and all timing, but preserve sensitivity. */
    fun reset() {
        breakContinuity()
        baseline = null
        settlingCountdown = false
        relativeAngle = 0f
        lastNow = null
    }

    fun sample(
        x: Float,
        y: Float,
        z: Float,
        now: Long,
        calibrating: Boolean = false,
        acceptTilt: Boolean = true,
    ): Outcome? {
        val previousNow = lastNow
        if (previousNow != null && now < previousNow) {
            // Discard this reading, not the learned hold, when the clock goes backwards.
            breakContinuity()
            lastNow = now
            return null
        }
        if (previousNow != null) {
            val gap = now - previousNow
            // A negative subtraction here is overflow across a very large forward gap.
            if (gap < 0L || gap > MAX_SAMPLE_GAP_MS) breakContinuity()
        }
        lastNow = now

        // Double intermediates safely reject huge finite Floats as well as NaN/infinity.
        val inPlaneSquared = x.toDouble() * x + y.toDouble() * y
        val norm = sqrt(inPlaneSquared + z.toDouble() * z)
        if (!norm.isFinite() || norm < 5.0 || norm > 16.0) {
            breakContinuity()
            if (calibrating) settlingCountdown = true
            return null
        }
        val angle = Math.toDegrees(Math.atan2(z.toDouble(), sqrt(inPlaneSquared))).toFloat()
        relativeAngle = baseline?.let { angle - it } ?: 0f

        // Even a repeated timestamp must not carry a hidden gesture into a visible card.
        if (calibrating || !acceptTilt) {
            clearTilt()
            if (abs(relativeAngle) > CENTER_DEGREES) {
                isArmed = false
                centerSince = null
            }
        }
        // Duplicates neither advance timers nor weight the calibration mean.
        if (now == previousNow) return null

        if (calibrating || !calibrated) {
            // A late move to the forehead must finish settling even if countdown ends.
            if (calibrating) settlingCountdown = true
            learnHold(angle, now)
            relativeAngle = baseline?.let { angle - it } ?: 0f
        } else {
            clearStability()
        }
        if (!calibrated) {
            isArmed = false
            return null
        }

        if (abs(relativeAngle) <= CENTER_DEGREES) {
            clearTilt()
            val since = centerSince ?: now.also { centerSince = it }
            if (now - since >= CENTER_DWELL_MS) isArmed = true
            return null
        }
        centerSince = null
        if (calibrating || !acceptTilt) {
            isArmed = false
            clearTilt()
            return null
        }
        if (!armed) return null

        val outcome = when {
            relativeAngle <= -activationDegrees -> Outcome.CORRECT
            relativeAngle >= activationDegrees -> Outcome.PASS
            else -> null
        }
        if (outcome == null) {
            clearTilt()
            return null
        }
        if (pendingOutcome != outcome) {
            pendingOutcome = outcome
            tiltSince = now
            return null
        }
        if (now - checkNotNull(tiltSince) < TILT_DWELL_MS) return null

        isArmed = false
        clearTilt()
        return outcome
    }

    private fun learnHold(angle: Float, now: Long) {
        if (abs(angle) > MAX_HOLD_DEGREES) {
            clearStability()
            return
        }
        if (stableSince == null ||
            maxOf(stableMax, angle) - minOf(stableMin, angle) > STABLE_RANGE_DEGREES
        ) {
            stableSince = now
            stableMin = angle
            stableMax = angle
            stableMean = angle.toDouble()
            stableCount = 1L
        } else {
            stableMin = minOf(stableMin, angle)
            stableMax = maxOf(stableMax, angle)
            stableCount++
            stableMean += (angle - stableMean) / stableCount
        }
        if (now - checkNotNull(stableSince) >= CALIBRATION_DWELL_MS) {
            baseline = stableMean.toFloat()
            settlingCountdown = false
            isArmed = true
        }
    }

    private fun clearStability() {
        stableSince = null
        stableCount = 0L
    }

    private fun clearTilt() {
        tiltSince = null
        pendingOutcome = null
    }

    private fun breakContinuity() {
        isArmed = false
        centerSince = null
        clearStability()
        clearTilt()
    }

    private companion object {
        private const val MAX_HOLD_DEGREES = 40f
        private const val STABLE_RANGE_DEGREES = 6f
        private const val CENTER_DEGREES = 14f
        private const val CALIBRATION_DWELL_MS = 350L
        private const val CENTER_DWELL_MS = 90L
        private const val TILT_DWELL_MS = 50L
        private const val MAX_SAMPLE_GAP_MS = 250L
    }
}