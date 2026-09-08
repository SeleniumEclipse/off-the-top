package com.nicgames.offthetop

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.SystemClock
import android.view.Surface
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.ceil

@RunWith(AndroidJUnit4::class)
class LifecycleFlowTest : OffTheTopUiTest() {
    @Test
    fun backgroundAutomaticallyPauses_hidesTheWordAndFreezesTimeUntilResume() {
        useTouchControls()
        tapText("30s")
        chooseDeck("Everyday Things")
        val word = startAndAwaitWord(30)
        compose.waitUntil(timeoutMillis = 5_000) {
            onModel { checkNotNull(it.round).remainingMs <= 29_000 }
        }
        val timeBeforeBackground = onModel { checkNotNull(it.round).remainingMs }
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        val frozenTime = onModel { model ->
            val round = checkNotNull(model.round)
            assertEquals(Phase.PAUSED, round.phase)
            assertEquals(word, round.currentWord)
            assertTrue(round.remainingMs <= timeBeforeBackground)
            assertTrue(round.remainingMs > 0)
            round.remainingMs
        }

        // No Compose-root queries while the activity is stopped. This is real elapsed
        // time, and each observation proves the background clock has not moved.
        val stoppedAt = SystemClock.elapsedRealtime()
        compose.waitUntil(timeoutMillis = 5_000) {
            onModel { model ->
                assertEquals(Phase.PAUSED, model.round?.phase)
                assertEquals(frozenTime, model.round?.remainingMs)
                assertEquals(word, model.round?.currentWord)
            }
            SystemClock.elapsedRealtime() - stoppedAt >= 1_250
        }

        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        assertPaused()
        onModel {
            assertEquals(frozenTime, it.round?.remainingMs)
            assertEquals(0, it.round?.score)
            assertTrue(it.history.isEmpty())
        }
        tapText("Resume round")
        assertCountdown()
        onModel { assertEquals(frozenTime, it.round?.remainingMs) }
        assertEquals(word, awaitWord())
        val timerSeconds = compose.onNodeWithTag("timer").fetchSemanticsNode()
            .config[SemanticsProperties.Text].single().text.removeSuffix("s").toInt()
        val frozenSeconds = ceil(frozenTime / 1000.0).toInt()
        assertTrue("Resume countdown must not spend three seconds of round time", timerSeconds in (frozenSeconds - 1)..frozenSeconds)
        assertLiveScore(0)
        compose.waitUntil(timeoutMillis = 5_000) {
            onModel { checkNotNull(it.round).remainingMs <= frozenTime - 1_000 }
        }
        assertEquals(setOf(word), seenWords("everyday"))
        finishThroughPause()
        assertAnswer(0, word, Outcome.UNANSWERED)
        assertSingleSavedRound("Everyday Things", 30, listOf(Answer(word, Outcome.UNANSWERED)))
    }

    @Test
    fun landscapeRotationAndActivityRecreation_keepTheRoundAndNoRepeatMemory() {
        useTouchControls()
        tapText("60s")
        chooseDeck("Wild World")
        val first = startAndAwaitWord(60)
        compose.onNodeWithTag("correct").performClick()
        val second = awaitWord(excluding = setOf(first))
        assertLiveScore(1)
        tapText("Pause")
        assertPaused()
        val retainedModel = onModel { it }
        val timeOnHold = onModel { checkNotNull(it.round).remainingMs }
        val originalOrientation = compose.runOnUiThread { compose.activity.requestedOrientation }
        val originalRotation = compose.runOnUiThread {
            @Suppress("DEPRECATION")
            compose.activity.windowManager.defaultDisplay.rotation
        }

        try {
            // The manifest handles orientation changes itself. Test a genuine 180-degree
            // landscape rotation AND explicit recreation, because they are different paths.
            compose.runOnUiThread {
                compose.activity.requestedOrientation = if (
                    originalRotation == Surface.ROTATION_0 || originalRotation == Surface.ROTATION_90
                ) ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.runOnUiThread {
                    @Suppress("DEPRECATION")
                    val rotation = compose.activity.windowManager.defaultDisplay.rotation
                    rotation == (originalRotation + 2) % 4 &&
                        compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                }
            }
            assertPaused()
            assertRetainedRound(retainedModel, first, second, timeOnHold)

            val previousActivity = compose.activity
            compose.activityRule.scenario.recreate()
            assertPaused()
            assertNotSame("ActivityScenario must really recreate the activity", previousActivity, compose.activity)
            assertRetainedRound(retainedModel, first, second, timeOnHold)
            tapText("Resume round")
            assertCountdown()
            assertEquals(second, awaitWord())
            assertLiveScore(1)

            finishThroughPause()
            compose.onNodeWithTag("final-score").assertTextEquals("1")
            assertAnswer(0, first, Outcome.CORRECT)
            assertAnswer(1, second, Outcome.UNANSWERED)
            assertSingleSavedRound("Wild World", 60, listOf(Answer(first, Outcome.CORRECT), Answer(second, Outcome.UNANSWERED)))
            tapText("Play again", scroll = true)
            val replayWord = startAndAwaitWord(60)
            assertFalse(replayWord in setOf(first, second))
            assertEquals(setOf(first, second, replayWord), seenWords("wild-world"))
            assertLiveScore(0)
            onModel { model ->
                assertTrue(checkNotNull(model.round).words.none { it == first || it == second })
                assertEquals(1, model.history.size)
            }
        } finally {
            compose.runOnUiThread { compose.activity.requestedOrientation = originalOrientation }
        }
    }

    @Test
    fun realThirtySecondRoundExpiresWithUnansweredCard_andSavesExactlyOnce() {
        useTouchControls()
        tapText("30s")
        chooseDeck("Do Your Thing")
        val word = startAndAwaitWord(30)
        assertLiveScore(0)
        val revisionBeforeExpiry = onModel { it.revision }
        onModel { model ->
            val engine = checkNotNull(model.round)
            assertEquals(30, model.seconds)
            assertEquals(30, engine.durationSeconds)
            assertEquals(Phase.PLAYING, engine.phase)
            assertTrue(engine.remainingMs in 1..30_000L)
            // Exercise expiry on the ACTUAL round, not finish(), a replacement engine,
            // shortened duration, or Compose's animation clock. Then refresh AppModel
            // so its revision invalidates the UI and its normal completion path saves.
            engine.tick(AppModel.now() + 31_000L)
            model.tick()
            assertEquals(Phase.FINISHED, engine.phase)
            assertEquals(0L, engine.remainingMs)
            assertTrue(model.revision > revisionBeforeExpiry)
        }
        awaitTag("final-score")
        compose.onNodeWithTag("results-heading").assertIsDisplayed().assertTextEquals("Results")
        compose.onNodeWithTag("final-score").assertTextEquals("0")
        compose.onNodeWithText("0 passed · 1 unanswered").assertIsDisplayed()
        compose.onNodeWithTag("word").assertDoesNotExist()
        compose.onNodeWithTag("timer").assertDoesNotExist()
        compose.onNodeWithTag("correct").assertDoesNotExist()
        compose.onNodeWithTag("pass").assertDoesNotExist()
        assertAnswer(0, word, Outcome.UNANSWERED)
        val answers = listOf(Answer(word, Outcome.UNANSWERED))
        assertSingleSavedRound("Do Your Thing", 30, answers)
        val recordId = onModel { it.history.single().id }
        onModel { model -> repeat(3) { model.tick() } }
        compose.activityRule.scenario.recreate()
        awaitTag("final-score")
        compose.onNodeWithTag("final-score").assertTextEquals("0")
        assertSingleSavedRound("Do Your Thing", 30, answers)
        onModel { assertEquals(recordId, it.history.single().id) }

        tapText("Change deck", scroll = true)
        tapText("Recent rounds", scroll = true)
        compose.onNode(hasClickAction() and hasText("Do Your Thing") and hasText("0"))
            .assertIsDisplayed().assertTextContains("+").performClick()
        assertHistoryAnswer(word, Outcome.UNANSWERED)
        compose.onAllNodes(hasContentDescription(", Unanswered", substring = true)).assertCountEquals(1)
        compose.onNodeWithText("30s ·", substring = true).assertExists()
        assertEquals(setOf(word), seenWords("do-your-thing"))
    }

    private fun assertRetainedRound(modelBeforeChange: AppModel, first: String, second: String, frozenTime: Long) {
        onModel { model ->
            assertSame(modelBeforeChange, model)
            val round = checkNotNull(model.round)
            assertEquals(Phase.PAUSED, round.phase)
            assertEquals(second, round.currentWord)
            assertEquals(frozenTime, round.remainingMs)
            assertEquals(listOf(Answer(first, Outcome.CORRECT)), round.answers)
            assertEquals(1, round.score)
            assertTrue(model.history.isEmpty())
        }
        assertEquals(setOf(first, second), seenWords("wild-world"))
    }
}