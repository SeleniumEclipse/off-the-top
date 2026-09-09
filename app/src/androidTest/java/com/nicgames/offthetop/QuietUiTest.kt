package com.nicgames.offthetop

import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.platform.InspectableValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
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
        awaitHome()
        compose.onAllNodesWithTag("app-title").assertCountEquals(1)
        compose.onNodeWithTag("deck-list")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollBy))
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.VerticalScrollAxisRange))
        listOf("Settings", "How to play", "Recent rounds").forEach { label ->
            compose.onNodeWithText(label).assertIsDisplayed().assertHasClickAction()
        }
        assertNoCopy("THE FOREHEAD GUESSING GAME", "Good clues.", "One phone.", "ALL OFFLINE", "NO ADS")

        val decks = onModel { it.decks.toList() }
        assertEquals(3, decks.size)
        decks.forEach { deck ->
            val printedTitle = when (deck.id) {
                "wild-world" -> "WILD\nWORLD"
                "everyday" -> "EVERYDAY\nTHINGS"
                else -> "DO YOUR\nTHING"
            }
            compose.onNodeWithTag("deck-${deck.id}").performScrollTo()
                .assertIsDisplayed().assertHasClickAction()
                .assertTextEquals(printedTitle, "${deck.words.size} cards")
                .assertContentDescriptionEquals("Choose ${deck.title}, ${deck.words.size} cards")
            compose.onAllNodesWithTag("category-${deck.id}", useUnmergedTree = true).assertCountEquals(2)
            assertNoCopy(deck.subtitle, deck.examples, "Nature", "Objects & food", "Actions & places")
            assertNoGlyphIconText()
        }

        tapText("How to play")
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
        assertNoGlyphIconText()
        tapText("Back")
        awaitHome()
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
        tapText("Settings")
        setToggle("Sound effects", false)
        setToggle("Vibration", false)
        assertToggle("Touch-only mode", false)
        assertNoCopy("Tap instead of tilting", "Smaller nods")
        assertNoGlyphIconText()
        tapText("Back")
        chooseDeck("Wild World")

        assertPracticeInstruction()
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
            .assertContentDescriptionEquals("Correct tested")
        compose.onNodeWithText("Correct").assertIsDisplayed().assertTextEquals("Correct")
        compose.onNodeWithContentDescription("Pass tested").performScrollTo().assertIsDisplayed()
            .assertContentDescriptionEquals("Pass tested")
        compose.onNodeWithText("Pass").assertIsDisplayed().assertTextEquals("Pass")
        assertNoGlyphIconText()
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
        tapText("Back")
        awaitHome()
    }

    @Test
    fun touchRoundKeepsCountdownPlayPauseAndResultsQuiet_withoutLosingScore() {
        useTouchControls()
        tapText("Settings")
        for (label in listOf("Sound effects", "Vibration", "Touch-only mode", "Gentle tilts")) {
            compose.onNodeWithText(label).performScrollTo().assertIsDisplayed()
            compose.onNodeWithContentDescription(label).assertHasClickAction()
        }
        assertNoCopy("Tap instead of tilting", "Smaller nods")
        assertNoGlyphIconText()
        tapText("Back")
        chooseDeck("Wild World")
        val deckSize = onModel { it.selected.words.size }
        assertPracticeInstruction()
        compose.onAllNodesWithText("Wild World").assertCountEquals(1)
        compose.onNodeWithTag("round-options").assertIsDisplayed().assertTextEquals("60s · $deckSize unseen")
        compose.onNodeWithTag("practice-feedback").performScrollTo().assertTextEquals("Touch controls")
        compose.onNodeWithTag("tilt-reading").assertDoesNotExist()
        compose.onNodeWithText("Tilt setup").assertDoesNotExist()
        compose.onNodeWithText("Reset tilt").assertDoesNotExist()
        assertNoCopy("$deckSize cards", "Friends give the clues.", "Large touch buttons", "3-second countdown")

        tapText("Start round")
        assertCountdown()
        assertNoCopy("PHONE TO FOREHEAD", "Screen facing your friends.")
        assertSelectedDeckHeaderAndMarkers()
        val first = awaitWord()
        assertLiveScore(0)
        assertScoringButtons()
        compose.onNodeWithTag("live-tilt-status").assertTextEquals("")
        compose.onNodeWithTag("correct").performClick()
        val second = awaitWord(excluding = setOf(first))
        assertLiveScore(1)
        assertScoringButtons()
        compose.onNodeWithTag("live-tilt-status").assertTextEquals("")
        assertNoCopy("FRIENDS TAP TO SCORE", "Return to your starting angle", "↓ GOT IT · ↑ PASS")
        assertSelectedDeckHeaderAndMarkers()

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
        assertNoCopy("ROUND COMPLETE", "NICE\nGUESSING.", "THE ROUND, RECAPTURED", "Tap to correct")
        assertNoGlyphIconText()
        assertSingleSavedRound("Wild World", 60,
            listOf(Answer(first, Outcome.CORRECT), Answer(second, Outcome.UNANSWERED)))

        tapText("Change deck", scroll = true)
        tapText("Recent rounds")
        assertHistoryDisclosure("Expand answers").performClick()
        assertHistoryAnswer(first, Outcome.CORRECT)
        assertHistoryAnswer(second, Outcome.UNANSWERED)
        compose.onAllNodes(hasContentDescription(", Correct", substring = true)).assertCountEquals(1)
        compose.onAllNodes(hasContentDescription(", Passed", substring = true)).assertCountEquals(0)
        compose.onAllNodes(hasContentDescription(", Unanswered", substring = true)).assertCountEquals(1)
        assertNoGlyphIconText()
        assertHistoryDisclosure("Collapse answers").performClick()
        assertHistoryDisclosure("Expand answers")
        compose.onNodeWithText(first).assertDoesNotExist()
        compose.onNodeWithText(second).assertDoesNotExist()
    }

    private fun assertPracticeInstruction() {
        compose.onNodeWithText("Hold at your forehead,\nfacing your friends.")
            .performScrollTo().assertIsDisplayed()
        val selected = onModel { it.selected }
        compose.onNodeWithTag("category-${selected.id}", useUnmergedTree = true)
            .performScrollTo().assertIsDisplayed()
        assertNoCopy("Phone to forehead", "Hold still. Screen facing friends.")
        assertNoGlyphIconText()
    }

    private fun assertSelectedDeckHeaderAndMarkers() {
        val selected = onModel { it.selected }
        // The new round header deliberately identifies the deck once. Do not ban
        // its name as filler, or accidentally allow duplicate headings/captions.
        compose.onAllNodesWithText(selected.title, ignoreCase = true, useUnmergedTree = true)
            .assertCountEquals(1)
        compose.onNodeWithText(selected.title).assertIsDisplayed()
        compose.onNodeWithText("Pause").assertIsDisplayed().assertHasClickAction()
        compose.onAllNodesWithTag("category-${selected.id}", useUnmergedTree = true).assertCountEquals(2)
        assertNoCopy(selected.subtitle, selected.examples, "${selected.words.size} cards")
        assertNoGlyphIconText()
    }

    private fun assertScoringButtons() {
        compose.onNodeWithTag("correct").assertIsDisplayed().assertIsEnabled()
            .assertHasClickAction().assertTextEquals("Correct")
        compose.onNodeWithTag("pass").assertIsDisplayed().assertIsEnabled()
            .assertHasClickAction().assertTextEquals("Pass")
        assertNoGlyphIconText()
    }

    private fun assertHistoryDisclosure(description: String): SemanticsNodeInteraction {
        val row = compose.onNode(hasClickAction() and hasText("Wild World"))
            .performScrollTo().assertIsDisplayed().assertContentDescriptionEquals(description)
        val image = compose.onNode(hasContentDescription(description) and
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Image), useUnmergedTree = true)
            .assertIsDisplayed().assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Text))
        // A description alone could hide a missing icon. Inspect the actual image's
        // paint modifier: it must draw a vector, not text or a bitmap stand-in.
        // The unit source guardrail separately verifies the official Sharp imports.
        val layout = image.fetchSemanticsNode().layoutInfo
        compose.runOnIdle {
            assertTrue("History disclosure must paint a vector", layout.getModifierInfo().any { info ->
                (info.modifier as? InspectableValue)?.inspectableElements?.any {
                    it.name == "painter" && it.value is VectorPainter
                } == true
            })
        }
        assertNoGlyphIconText()
        return row
    }

    private fun assertNoGlyphIconText() {
        val glyphOrEmoji = Regex("[✓↷↑↓→←‹›]|[\\x{1F000}-\\x{1FAFF}\\x{2600}-\\x{27BF}\\x{FE0F}\\x{20E3}]")
        val iconText = SemanticsMatcher("Text must not substitute a glyph or emoji for an icon") { node ->
            node.config.contains(SemanticsProperties.Text) && node.config[SemanticsProperties.Text].any {
                glyphOrEmoji.containsMatchIn(it.text) || it.text in listOf("+", "−", "—")
            }
        }
        compose.onAllNodes(iconText, useUnmergedTree = true).assertCountEquals(0)
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