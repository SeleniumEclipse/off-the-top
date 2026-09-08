package com.nicgames.offthetop

import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Simulated gravity vectors through the real AppModel.motionSample, not sensor hardware.
 * Isolated models have no GameApp/MotionInput attached. The activity test separately checks
 * UI reset boundaries with hardware stopped; it does not verify sensor registration or feel.
 */
@RunWith(AndroidJUnit4::class)
class MotionFlowTest : OffTheTopUiTest() {
    @Test
    fun countdownHoldAndReturnDuringFeedback_allowOneCorrectThenOnePass() = withMotionModel { model ->
        model.choose(model.decks.first())
        model.start()
        val motion = ModelMotion(model)
        val round = checkNotNull(model.round)
        val base = motion.now

        // Continuous 20 ms samples cross both countdown-number changes and PLAYING.
        for ((elapsed, countdown) in listOf(1_000L to 2, 2_000L to 1)) {
            motion.holdUntil(22f, base + elapsed)
            assertEquals(Phase.COUNTDOWN, round.phase)
            assertEquals(countdown, round.countdown)
            assertNull(round.currentWord)
            assertTrue(round.answers.isEmpty())
            assertReadyAt(model, 22f)
        }
        motion.holdUntil(22f, base + 3_000L)
        assertEquals(Screen.ROUND, model.screen)
        assertEquals(Phase.PLAYING, round.phase)
        assertReadyAt(model, 22f)
        assertTrue(round.answers.isEmpty())
        assertTrue(seenWords(model.selected.id).isEmpty())

        val first = checkNotNull(round.currentWord)
        // No UI exists for this model: deliberately acknowledge the actual current word.
        model.revealWord(first)
        motion.hold(-12f, 120L) // -34 degrees relative to the learned +22-degree hold.
        assertEquals(listOf(Answer(first, Outcome.CORRECT)), round.answers)
        assertEquals(1, round.score)
        assertEquals(Outcome.CORRECT, round.feedback)
        assertNull(round.currentWord)
        assertFalse(model.tilt.armed)

        motion.hold(22f, 100L)
        assertEquals("The entire return must occur while feedback still hides the card", Outcome.CORRECT, round.feedback)
        assertReadyAt(model, 22f)
        assertEquals(1, round.answers.size)
        motion.holdUntil(22f, motion.now + 700L)
        assertNull(round.feedback)
        assertReadyAt(model, 22f)

        val second = checkNotNull(round.currentWord)
        assertNotEquals(first, second)
        assertEquals(setOf(first), seenWords(model.selected.id))
        assertEquals(1, round.answers.size)
        model.revealWord(second)
        motion.hold(54f, 120L) // +32 relative; no extra calibration after feedback.
        assertEquals(listOf(Answer(first, Outcome.CORRECT), Answer(second, Outcome.PASS)), round.answers)
        assertEquals(1, round.score)
        assertEquals(Outcome.PASS, round.feedback)
        assertEquals(22f, checkNotNull(model.tilt.neutralDegrees), 0.001f)
        assertEquals(setOf(first, second), seenWords(model.selected.id))
        assertTrue(model.history.isEmpty())
    }

    @Test
    fun hiddenAndFeedbackTilts_cannotLeakWhenTheirCardIsLaterRevealed() = withMotionModel { model ->
        model.choose(model.decks.first())
        model.start()
        val motion = ModelMotion(model)
        val round = checkNotNull(model.round)
        motion.holdUntil(22f, motion.now + 3_000L)
        val first = checkNotNull(round.currentWord)

        model.revealWord(round.words[1]) // A different card is not a layout acknowledgement.
        motion.hold(-12f, 120L)
        assertTrue("An engine card is not necessarily a displayed card", round.answers.isEmpty())
        assertTrue(seenWords(model.selected.id).isEmpty())
        assertFalse("A hidden tilt must be consumed, not queued", model.tilt.armed)
        model.revealWord(first)
        motion.hold(-12f, 400L)
        assertTrue("Revealing a card must not score a tilt already being held", round.answers.isEmpty())
        motion.hold(22f, 100L)
        assertReadyAt(model, 22f)
        motion.hold(-12f, 120L)
        assertEquals(listOf(Answer(first, Outcome.CORRECT)), round.answers)

        motion.hold(22f, 100L)
        assertReadyAt(model, 22f)
        assertEquals(Outcome.CORRECT, round.feedback)
        model.revealWord(round.words[1]) // Still invalid while feedback hides the next card.
        motion.hold(54f, 120L)
        assertEquals(Outcome.CORRECT, round.feedback)
        assertFalse(model.tilt.armed)
        assertEquals(1, round.answers.size)
        assertEquals(setOf(first), seenWords(model.selected.id))

        motion.holdUntil(54f, motion.now + 800L)
        assertNull(round.feedback)
        val second = checkNotNull(round.currentWord)
        model.revealWord(second)
        motion.hold(54f, 400L)
        assertEquals("A gesture begun during feedback cannot answer the next card", 1, round.answers.size)
        assertEquals("Long playing holds must not replace the countdown baseline", 22f, checkNotNull(model.tilt.neutralDegrees), 0.001f)
        motion.hold(22f, 100L)
        assertReadyAt(model, 22f)
        motion.hold(54f, 120L)
        assertEquals(listOf(Answer(first, Outcome.CORRECT), Answer(second, Outcome.PASS)), round.answers)
        assertEquals(1, round.score)
    }

    @Test
    fun practiceAndInputGates_doNotScoreOutsideAnActiveVisibleRound() = withMotionModel { model ->
        val motion = ModelMotion(model)
        motion.hold(22f, 400L)
        assertFalse("Home must not calibrate from background motion", model.tilt.calibrated)
        model.choose(model.decks.first())
        model.setTouch(true)
        motion.hold(22f, 400L)
        assertFalse("Touch-only mode must ignore motion", model.tilt.calibrated)
        model.setTouch(false)
        motion.hold(22f, 360L)
        assertReadyAt(model, 22f)
        motion.hold(-12f, 120L)
        assertTrue(model.practicedCorrect)
        assertFalse(model.practicedPass)
        assertEquals("GOT IT! Return to your starting angle.", model.practiceFeedback)
        motion.hold(22f, 100L)
        motion.hold(54f, 120L)
        assertTrue(model.practicedPass)
        assertEquals("PASS! Return to your starting angle.", model.practiceFeedback)
        assertNull(model.round)
        assertTrue(model.history.isEmpty())
        assertTrue(seenWords(model.selected.id).isEmpty())
        assertEquals(model.selected.words.size, model.unseen(model.selected))

        model.start()
        motion.holdUntil(22f, motion.now + 3_000L)
        val round = checkNotNull(model.round)
        val word = checkNotNull(round.currentWord)
        model.revealWord(word)
        assertReadyAt(model, 22f)
        for (screen in listOf(Screen.HOME, Screen.SETTINGS, Screen.HELP, Screen.HISTORY)) {
            model.screen = screen
            motion.hold(-12f, 120L)
            assertTrue("Motion must not answer on $screen", round.answers.isEmpty())
            assertReadyAt(model, 22f) // Even the last reading must remain untouched.
        }
        model.screen = Screen.ROUND
        model.setTouch(true)
        motion.hold(-12f, 120L)
        assertTrue(round.answers.isEmpty())
        assertReadyAt(model, 22f)
        model.setTouch(false)
        model.pause()
        assertEquals(Phase.PAUSED, round.phase)
        motion.hold(-12f, 120L)
        assertTrue(round.answers.isEmpty())
        assertReadyAt(model, 22f)
        model.finish()
        assertEquals(Phase.FINISHED, round.phase)
        val answers = listOf(Answer(word, Outcome.UNANSWERED))
        assertEquals(answers, round.answers)
        motion.hold(54f, 400L)
        assertEquals(answers, round.answers)
        assertEquals(0, round.score)
        assertEquals(answers, model.history.single().answers)
    }

    @Test
    fun activityEffects_preserveCountdownAndFeedbackHold_butRecalibrateAfterPause() {
        useTouchControls() // Let GameApp actually stop hardware before supplying any samples.
        chooseDeck("Wild World")
        tapText("Start round  →")
        assertCountdown()
        val motion = onModel { model ->
            ModelMotion(model).also { stream ->
                motionBurst(model) {
                    stream.holdUntil(22f, stream.now + 3_000L)
                    assertEquals(Phase.PLAYING, model.round?.phase)
                    assertTrue(checkNotNull(model.round).answers.isEmpty())
                    assertReadyAt(model, 22f)
                }
            }
        }
        // This time only AutoWord's real layout callback may reveal the card.
        val first = awaitWord()
        onModel { model ->
            assertReadyAt(model, 22f) // Assert AFTER the countdown -> playing UI effect.
            motionBurst(model) {
                motion.hold(-12f, 120L)
                motion.hold(22f, 100L)
                assertEquals(Outcome.CORRECT, model.round?.feedback)
                assertReadyAt(model, 22f)
            }
        }
        awaitText("✓ GOT IT!")
        compose.onNodeWithTag("word").assertDoesNotExist()
        onModel { model ->
            assertReadyAt(model, 22f) // Feedback rendering must not reset the returned hold.
            motionBurst(model) { motion.holdUntil(22f, motion.now + 700L) }
        }
        val second = awaitWord(excluding = setOf(first))
        onModel { model ->
            assertReadyAt(model, 22f) // Nor may feedback -> next card reset it.
            assertEquals(listOf(Answer(first, Outcome.CORRECT)), model.round?.answers)
        }

        tapText("Pause")
        assertPaused()
        onModel { model ->
            assertFalse("The actual pause UI effect must forget the old hold", model.tilt.calibrated)
            assertFalse(model.tilt.armed)
            assertEquals("HOLD AT YOUR FOREHEAD", model.tiltStatus)
        }
        tapText("Resume round")
        assertCountdown()
        val resumedMotion = onModel { model ->
            assertFalse(model.tilt.calibrated)
            // resume() starts a new real clock epoch; the UI has reset detector timing.
            ModelMotion(model).also { stream ->
                motionBurst(model) {
                    stream.holdUntil(-22f, stream.now + 3_000L)
                    assertEquals(1, checkNotNull(model.round).answers.size)
                    assertReadyAt(model, -22f)
                }
            }
        }
        assertEquals(second, awaitWord())
        onModel { model ->
            assertReadyAt(model, -22f)
            motionBurst(model) { resumedMotion.hold(12f, 120L) } // +34 from the NEW hold.
            assertEquals(listOf(Answer(first, Outcome.CORRECT), Answer(second, Outcome.PASS)), model.round?.answers)
            assertEquals(1, model.round?.score)
        }
        awaitText("↷ PASS")
        assertLiveScore(1)
    }

    private fun withMotionModel(block: (AppModel) -> Unit) = withReloadedModel { model ->
        // The helper owns a fresh ViewModelStore and clears it in finally on the UI thread.
        // Its model is not composed; the fixture's real activity stays on HOME.
        model.changeSound(false)
        model.changeHaptics(false)
        model.setTouch(false)
        block(model)
    }

    /** Caller is already in onModel/compose.runOnUiThread; never synchronize inside this. */
    private fun motionBurst(model: AppModel, block: () -> Unit) {
        assertTrue("Hardware must already be stopped by Touch-only mode", model.touchOnly)
        model.setTouch(false)
        try {
            block()
        } finally {
            // GameApp observes true before AND after the synchronous burst. Its effects
            // cannot interleave, so this toggle neither starts hardware nor hides a reset
            // caused by a phase/feedback change. Those effects run before our next assertion.
            model.setTouch(true)
        }
    }

    private fun assertReadyAt(model: AppModel, degrees: Float) {
        assertTrue(model.tilt.calibrated)
        assertTrue(model.tilt.armed)
        assertEquals(degrees, checkNotNull(model.tilt.neutralDegrees), 0.001f)
        assertEquals(0f, model.tilt.relativeDegrees, 0.001f)
        assertEquals("READY TO TILT", model.tiltStatus)
        assertEquals("At your starting angle", model.tiltReading)
    }

    private class ModelMotion(private val model: AppModel) {
        var now: Long = AppModel.now()
            private set

        private fun sample(degrees: Float) {
            now += 20L
            // Advance the actual engine, then use model.tick() to propagate phase/revision.
            // AppModel.mark uses real now(); RoundEngine ignores backward clock reads once
            // advanced here. No replacement round, reflection, or production clock hook.
            model.round?.tick(now)
            model.tick()
            val radians = Math.toRadians(degrees.toDouble())
            model.motionSample((cos(radians) * 9.81).toFloat(), 0f, (sin(radians) * 9.81).toFloat(), now)
        }

        /** Duration between first and last samples; every next sample is exactly 20 ms later. */
        fun hold(degrees: Float, durationMs: Long) {
            require(durationMs >= 0L && durationMs % 20L == 0L)
            for (elapsed in 0L..durationMs step 20L) sample(degrees)
        }

        fun holdUntil(degrees: Float, end: Long) {
            require(end >= now && (end - now) % 20L == 0L)
            while (now < end) sample(degrees)
        }
    }
}