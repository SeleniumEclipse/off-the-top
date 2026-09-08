package com.nicgames.offthetop

import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.cos
import kotlin.math.sin

@RunWith(AndroidJUnit4::class)
class QuietUiTest : OffTheTopUiTest() {
    @Test
    fun homeShowsOnlyShortDeckDetails_andHelpStillExplainsTheGame() {
        compose.onNodeWithTag("deck-heading").assertIsDisplayed().assertTextEquals("Choose a deck")
        compose.onAllNodesWithText("Choose a deck").assertCountEquals(1)
        compose.onNodeWithText("Recent rounds").performScrollTo().assertHasClickAction()
        assertNoCopy("THE FOREHEAD GUESSING GAME", "Good clues.", "One phone.", "ALL OFFLINE", "NO ADS")

        val decks = onModel { it.decks.toList() }
        val descriptions = listOf("Nature", "Objects & food", "Actions & places")
        assertEquals(descriptions.size, decks.size)
        decks.forEachIndexed { index, deck ->
            compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(deck.title))
            compose.onNodeWithContentDescription("Choose ${deck.title}, ${deck.words.size} cards")
                .assertIsDisplayed().assertHasClickAction()
                .assertTextEquals(deck.title, descriptions[index], "${deck.words.size} cards")
            assertNoCopy(deck.subtitle, deck.examples)
        }

        tapText("How to play", scroll = true)
        awaitText("How to play")
        onModel { assertEquals(Screen.HELP, it.screen) }
        for (heading in listOf("Hold", "Guess", "Tilt", "Score")) {
            compose.onNodeWithText(heading).performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithText("Down for correct, up to pass.", substring = true)
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("One point per correct answer.", substring = true)
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Leaving the app pauses the timer. Resume resets your tilt.").assertIsDisplayed()
        assertNoCopy("A little help up top", "GOOD TO KNOW")
        tapText("‹ Back")
        awaitText("Choose a deck")
        onModel { assertEquals(Screen.HOME, it.screen); assertTrue(it.history.isEmpty()) }
    }

    @Test
    fun tiltDiagnosticsAreOptIn_andResetLearnsANewHoldWithoutUsingCards() {
        val sensorsAvailable = compose.runOnUiThread {
            val manager = compose.activity.getSystemService(SensorManager::class.java)
            manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null ||
                manager.getDefaultSensor(Sensor.TYPE_GRAVITY) != null
        }
        assumeTrue("Tilt setup requires an accelerometer or gravity sensor", sensorsAvailable)
        tapText("Settings", scroll = true)
        setToggle("Sound effects", false)
        setToggle("Vibration", false)
        assertToggle("Touch-only mode", false)
        tapText("‹ Back")
        chooseDeck("Wild World")

        assertShortTiltFeedback()
        compose.onNodeWithTag("tilt-reading").assertDoesNotExist()
        compose.onNodeWithText("Reset tilt").assertDoesNotExist()
        compose.onNodeWithText("Hide setup").assertDoesNotExist()
        tapText("Tilt setup", scroll = true)
        compose.onNodeWithTag("tilt-reading").performScrollTo().assertIsDisplayed()

        val model = onModel { it }
        var sampleTime = AppModel.now()
        // Called only inside the synchronous button action below. Hardware events
        // cannot interleave with this burst, and no practice flags are assigned directly.
        fun hold(degrees: Float, durationMs: Long) {
            val radians = Math.toRadians(degrees.toDouble())
            for (elapsed in 0L..durationMs step 20L) {
                sampleTime += 20L
                model.motionSample((cos(radians) * 9.81).toFloat(), 0f,
                    (sin(radians) * 9.81).toFloat(), sampleTime)
            }
        }
        fun resetAndHold(block: () -> Unit) {
            compose.onNodeWithText("Reset tilt").performScrollTo().assertIsDisplayed()
                .performSemanticsAction(SemanticsActions.OnClick) { click ->
                    click() // The real button must forget both the hold and its timing.
                    assertFalse(model.tilt.calibrated)
                    assertFalse(model.tilt.armed)
                    assertNull(model.tilt.neutralDegrees)
                    sampleTime = AppModel.now()
                    block()
                }
        }
        resetAndHold {
            hold(22f, 360L)
            assertTrue(model.tilt.armed)
            assertEquals(22f, checkNotNull(model.tilt.neutralDegrees), 0.001f)
            hold(-12f, 120L)
            assertTrue(model.practicedCorrect)
            hold(22f, 100L)
            hold(54f, 120L)
            assertTrue(model.practicedPass)
        }
        compose.onNodeWithContentDescription("Correct tested").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Pass tested").performScrollTo().assertIsDisplayed()
        assertShortTiltFeedback()
        assertNoCopy("GOT IT! Return to your starting angle.", "PASS! Return to your starting angle.")
        resetAndHold {
            hold(-22f, 360L)
            assertTrue(model.tilt.armed)
            assertEquals(-22f, checkNotNull(model.tilt.neutralDegrees), 0.001f)
        }
        assertShortTiltFeedback()
        onModel {
            assertTrue(it.practicedCorrect)
            assertTrue(it.practicedPass)
            assertNull(it.round)
            assertTrue(it.history.isEmpty())
            assertEquals(it.selected.words.size, it.unseen(it.selected))
        }
        assertTrue(seenWords("wild-world").isEmpty())
        tapText("Hide setup", scroll = true)
        compose.onNodeWithTag("tilt-reading").assertDoesNotExist()
        compose.onNodeWithText("Reset tilt").assertDoesNotExist()
        compose.onNodeWithText("Tilt setup").assertIsDisplayed()
        tapText("‹ Back")
        awaitText("Choose a deck")
    }

    @Test
    fun touchRoundKeepsCountdownPlayPauseAndResultsQuiet_withoutLosingScore() {
        useTouchControls()
        chooseDeck("Wild World")
        val deckSize = onModel { it.selected.words.size }
        compose.onAllNodesWithText("Wild World").assertCountEquals(1)
        compose.onNodeWithTag("round-options").assertIsDisplayed().assertTextEquals("60s · $deckSize unseen")
        compose.onNodeWithTag("practice-feedback").performScrollTo().assertTextEquals("Touch controls")
        compose.onNodeWithTag("tilt-reading").assertDoesNotExist()
        compose.onNodeWithText("Tilt setup").assertDoesNotExist()
        compose.onNodeWithText("Reset tilt").assertDoesNotExist()
        assertNoCopy("$deckSize cards", "Friends give the clues.", "Large touch buttons", "3-second countdown")

        tapText("Start round  →")
        assertCountdown()
        assertNoCopy("PHONE TO FOREHEAD", "Screen facing your friends.", "WILD WORLD")
        val first = awaitWord()
        assertLiveScore(0)
        compose.onNodeWithTag("live-tilt-status").assertTextEquals("")
        compose.onNodeWithTag("correct").performClick()
        val second = awaitWord(excluding = setOf(first))
        assertLiveScore(1)
        compose.onNodeWithTag("live-tilt-status").assertTextEquals("")
        assertNoCopy("FRIENDS TAP TO SCORE", "Return to your starting angle", "↓ GOT IT · ↑ PASS", "WILD WORLD")

        tapText("Pause")
        assertPaused()
        assertNoCopy("TAKE A BREATHER", "ON HOLD", "The clock is stopped.")
        onModel {
            assertEquals(second, it.round?.currentWord)
            assertEquals(listOf(Answer(first, Outcome.CORRECT)), it.round?.answers)
        }
        tapText("Resume round")
        assertCountdown()
        assertEquals(second, awaitWord())
        assertLiveScore(1)
        finishThroughPause()
        compose.onNodeWithTag("final-score").assertTextEquals("1")
        compose.onNodeWithText("0 passed · 1 unanswered").assertIsDisplayed()
        assertAnswer(0, first, Outcome.CORRECT)
        assertAnswer(1, second, Outcome.UNANSWERED)
        assertNoCopy("ROUND COMPLETE", "NICE\nGUESSING.", "THE ROUND, RECAPTURED")
        assertSingleSavedRound("Wild World", 60,
            listOf(Answer(first, Outcome.CORRECT), Answer(second, Outcome.UNANSWERED)))
    }

    private fun assertShortTiltFeedback() {
        // Hardware is live between actions, so readiness can change. Only its short
        // user-facing status may render; durable tested outcomes are checked separately.
        compose.onNodeWithTag("practice-feedback").performScrollTo().assertIsDisplayed()
            .assert(hasText("Hold still") or hasText("Ready") or hasText("Return to start"))
    }

    private fun assertNoCopy(vararg snippets: String) {
        snippets.forEach { text ->
            compose.onAllNodesWithText(text, substring = true, ignoreCase = true).assertCountEquals(0)
        }
    }
}