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
 * Gravity-vector tilt detection, independent of landscape direction. Negative device
 * Z means screen-to-floor (correct); positive Z means screen-to-ceiling (pass).
 * Invalid/shaken vectors break stability and require a fresh upright hold.
 */
class TiltDetector {
    var threshold: Float = 0.68f
        set(value) {
            require(value.isFinite() && value > 0f && value < 1f) {
                "Tilt threshold must be between zero and one."
            }
            field = value
            tiltSince = null
            pendingOutcome = null
        }

    val armed: Boolean
        get() = isArmed

    private var isArmed = false
    private var uprightSince: Long? = null
    private var tiltSince: Long? = null
    private var pendingOutcome: Outcome? = null
    private var lastNow: Long? = null

    fun reset() {
        isArmed = false
        uprightSince = null
        tiltSince = null
        pendingOutcome = null
        lastNow = null
    }

    fun sample(x: Float, y: Float, z: Float, now: Long): Outcome? {
        val previousNow = lastNow
        if (previousNow != null && now < previousNow) {
            reset()
            lastNow = now
            return null
        }
        lastNow = now

        // Double intermediates also safely reject very large finite Float vectors.
        val norm = sqrt(x.toDouble() * x + y.toDouble() * y + z.toDouble() * z)
        if (!norm.isFinite() || norm < 5.0 || norm > 16.0) {
            reset()
            lastNow = now
            return null
        }
        val normalizedX = x / norm
        val normalizedY = y / norm
        val normalizedZ = z / norm
        val upright = abs(normalizedZ) < 0.28 &&
            maxOf(abs(normalizedX), abs(normalizedY)) > 0.65

        if (upright) {
            tiltSince = null
            pendingOutcome = null
            val since = uprightSince ?: now.also { uprightSince = it }
            if (now - since >= 250L) isArmed = true
            return null
        }
        uprightSince = null
        if (!armed) return null

        val outcome = when {
            normalizedZ < -threshold -> Outcome.CORRECT
            normalizedZ > threshold -> Outcome.PASS
            else -> null
        }
        if (outcome == null) {
            tiltSince = null
            pendingOutcome = null
            return null
        }
        if (pendingOutcome != outcome) {
            pendingOutcome = outcome
            tiltSince = now
            return null
        }
        if (now - checkNotNull(tiltSince) < 120L) return null

        isArmed = false
        tiltSince = null
        pendingOutcome = null
        return outcome
    }
}