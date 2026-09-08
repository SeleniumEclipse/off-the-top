package com.nicgames.offthetop

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

@RunWith(AndroidJUnit4::class)
class GameplayFlowTest : OffTheTopUiTest() {
    @Test
    fun scorePauseReviewAndReplay_preserveHistoryAndNeverRepeatShownCards() {
        useTouchControls()
        tapText("60s")
        chooseDeck("Wild World")
        compose.onNodeWithTag("practice-feedback").performScrollTo()
            .assertTextEquals("Large touch buttons stay available during every round.")
        onModel { assertTrue(it.history.isEmpty()) }

        val first = startAndAwaitWord(60)
        compose.onNodeWithTag("live-score").assertTextEquals("0 CORRECT")
        scoreWithRapidRepeatedClicks("correct", Outcome.CORRECT, first, 1, 1)
        val second = awaitWord(excluding = setOf(first))
        compose.onNodeWithTag("live-score").assertTextEquals("1 CORRECT")
        scoreWithRapidRepeatedClicks("pass", Outcome.PASS, second, 2, 1)
        val third = awaitWord(excluding = setOf(first, second))
        compose.onNodeWithTag("live-score").assertTextEquals("1 CORRECT")

        tapText("Pause")
        assertPaused()
        val timeOnHold = onModel { checkNotNull(it.round).remainingMs }
        onModel { assertEquals(third, it.round?.currentWord) }
        tapText("Resume round")
        assertCountdown()
        onModel { assertEquals(timeOnHold, it.round?.remainingMs) }
        assertEquals("Resuming must not draw a new card", third, awaitWord())
        compose.onNodeWithTag("live-score").assertTextEquals("1 CORRECT")

        finishThroughPause()
        compose.onNodeWithTag("final-score").assertTextEquals("1")
        compose.onNodeWithText("1 passed · 1 unanswered").assertIsDisplayed()
        assertAnswer(0, first, "✓ CORRECT")
        assertAnswer(1, second, "↷ PASSED")
        assertAnswer(2, third, "— NO ANSWER")

        // Review edits existing rows; it must neither append rows nor create a new history entry.
        compose.onNodeWithTag("answer-0").performScrollTo().performClick()
        compose.onNodeWithTag("final-score").assertTextEquals("0")
        assertAnswer(0, first, "↷ PASSED")
        compose.onNodeWithTag("answer-1").performScrollTo().performClick()
        compose.onNodeWithTag("final-score").assertTextEquals("1")
        compose.onNodeWithTag("answer-2").performScrollTo().performClick()
        compose.onNodeWithTag("final-score").assertTextEquals("2")
        compose.onNodeWithText("1 passed · 0 unanswered").assertIsDisplayed()
        val reviewedAnswers = listOf(
            Answer(first, Outcome.PASS), Answer(second, Outcome.CORRECT),
            Answer(third, Outcome.CORRECT),
        )
        assertSingleSavedRound("Wild World", 60, reviewedAnswers)
        val savedId = onModel { it.history.single().id }
        val shown = setOf(first, second, third)
        assertEquals(shown, seenWords("wild-world"))

        compose.activityRule.scenario.recreate()
        awaitTag("final-score")
        compose.onNodeWithTag("final-score").assertTextEquals("2")
        assertAnswer(0, first, "↷ PASSED")
        assertAnswer(1, second, "✓ CORRECT")
        assertAnswer(2, third, "✓ CORRECT")
        assertSingleSavedRound("Wild World", 60, reviewedAnswers)
        onModel { assertEquals(savedId, it.history.single().id) }

        tapText("Play again", scroll = true)
        val remainingCards = onModel { it.selected.words.size - shown.size }
        compose.onNodeWithText("$remainingCards UNSEEN · 60 SECOND ROUND")
            .performScrollTo().assertIsDisplayed()
        val replayWord = startAndAwaitWord(60)
        assertFalse("Replay must exclude every previously displayed card", replayWord in shown)
        onModel { model ->
            val round = checkNotNull(model.round)
            assertEquals(0, round.score)
            assertTrue(round.answers.isEmpty())
            assertTrue("The replay draw pile must exclude all seen words", round.words.none { it in shown })
        }
        compose.onNodeWithTag("live-score").assertTextEquals("0 CORRECT")
        finishThroughPause()
        compose.onNodeWithTag("final-score").assertTextEquals("0")
        assertEquals(shown + replayWord, seenWords("wild-world"))
        withReloadedModel { model ->
            assertEquals(2, model.history.size)
            assertEquals(listOf(Answer(replayWord, Outcome.UNANSWERED)), model.history[0].answers)
            assertEquals(reviewedAnswers, model.history[1].answers)
            assertEquals(savedId, model.history[1].id)
        }

        tapText("Change deck", scroll = true)
        tapText("Recent rounds  →", scroll = true)
        val reviewedHistory = hasClickAction() and hasText("Wild World") and hasText("2")
        compose.onNode(reviewedHistory).performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText(first).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(second).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(third).performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("✓ CORRECT").assertCountEquals(2)
        compose.onAllNodesWithText("↷ PASSED").assertCountEquals(1)
        compose.onNodeWithText("HIDE −").assertExists()
    }

    @Test
    fun everyDeckOpensItsOwnPractice_withoutScoringOrUsingCards() {
        useTouchControls()
        val decks = onModel { it.decks.toList() }
        assertEquals(listOf("Wild World", "Everyday Things", "Do Your Thing"), decks.map { it.title })
        for (deck in decks) {
            chooseDeck(deck.title)
            compose.onNodeWithText(deck.title).assertIsDisplayed()
            compose.onNodeWithText("${deck.words.size} CARDS").assertIsDisplayed()
            compose.onNodeWithTag("practice-feedback").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("${deck.words.size} UNSEEN · 60 SECOND ROUND")
                .performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Start round  →").assertIsEnabled()
            compose.onNodeWithTag("word").assertDoesNotExist()
            compose.onNodeWithTag("live-score").assertDoesNotExist()
            onModel { model ->
                assertEquals(deck.id, model.selected.id)
                assertNull(model.round)
                assertTrue(model.history.isEmpty())
                assertEquals(deck.words.size, model.unseen(deck))
            }
            assertTrue(seenWords(deck.id).isEmpty())
            tapText("‹ Back")
            awaitText("PICK YOUR DECK")
        }
        tapText("Recent rounds  →", scroll = true)
        compose.onNodeWithText(EMPTY_HISTORY).assertIsDisplayed()
        withReloadedModel { assertTrue(it.history.isEmpty()) }
    }

    private fun scoreWithRapidRepeatedClicks(
        tag: String,
        outcome: Outcome,
        word: String,
        expectedAnswers: Int,
        expectedScore: Int,
    ) {
        val otherTag = if (tag == "correct") "pass" else "correct"
        val otherClick = checkNotNull(
            compose.onNodeWithTag(otherTag).assertIsEnabled().fetchSemanticsNode()
                .config[SemanticsActions.OnClick].action,
        )
        val model = onModel { it }
        compose.onNodeWithTag(tag).assertIsEnabled()
            .performSemanticsAction(SemanticsActions.OnClick) { click ->
                // One main-thread burst of the actual buttons' callbacks. No synchronization
                // between clicks can accidentally let the 650ms feedback window expire.
                click()
                click()
                otherClick()
                click()
                val round = checkNotNull(model.round)
                assertEquals(outcome, round.feedback)
                assertNull("The next card must stay hidden during feedback", round.currentWord)
                assertEquals(expectedAnswers, round.answers.size)
                assertEquals(Answer(word, outcome), round.answers.last())
                assertEquals(expectedScore, round.score)
            }
        onModel {
            assertEquals(expectedAnswers, it.round?.answers?.size)
            assertEquals(expectedScore, it.round?.score)
        }
    }
}

/** Shared only by these instrumented tests; always hosts the real MainActivity. */
abstract class OffTheTopUiTest {
    protected val compose = createAndroidComposeRule<MainActivity>()

    // An ordinary @Before runs AFTER ActivityScenarioRule launches the activity. This
    // outer rule commits the clear first, before AppModel can read stale preferences.
    @get:Rule
    val appRules: TestRule = RuleChain.outerRule(ClearGamePreferencesRule()).around(compose)

    @Before
    fun awaitFreshActivity() {
        awaitText("PICK YOUR DECK")
        onModel { model ->
            assertEquals(Screen.HOME, model.screen)
            assertTrue(model.history.isEmpty())
            assertEquals(60, model.seconds)
        }
    }

    protected fun tapText(text: String, scroll: Boolean = false) {
        val node = compose.onNodeWithText(text)
        if (scroll) node.performScrollTo()
        node.assertIsDisplayed().performClick()
    }

    protected fun awaitText(text: String, timeoutMillis: Long = 10_000) {
        compose.waitUntil(timeoutMillis = timeoutMillis) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(text).assertIsDisplayed()
    }

    protected fun awaitTag(tag: String, timeoutMillis: Long = 10_000) {
        compose.waitUntil(timeoutMillis = timeoutMillis) {
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes().size == 1
        }
        compose.onNodeWithTag(tag).assertIsDisplayed()
    }

    protected fun chooseDeck(title: String) {
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(title))
        tapText(title)
        awaitText("Start round  →")
        onModel { assertEquals(title, it.selected.title) }
    }

    protected fun useTouchControls() {
        tapText("Settings", scroll = true)
        setToggle("Touch-only mode", true)
        setToggle("Sound effects", false)
        setToggle("Vibration", false)
        tapText("‹ Back")
    }

    protected fun setToggle(label: String, enabled: Boolean) {
        val node = compose.onNodeWithContentDescription(label).performScrollTo()
        val isOn = node.fetchSemanticsNode().config[SemanticsProperties.ToggleableState] == ToggleableState.On
        if (isOn != enabled) node.performClick()
        if (enabled) node.assertIsOn() else node.assertIsOff()
    }

    protected fun assertToggle(label: String, enabled: Boolean) {
        val node = compose.onNodeWithContentDescription(label).performScrollTo()
        if (enabled) node.assertIsOn() else node.assertIsOff()
    }

    protected fun startAndAwaitWord(seconds: Int): String {
        tapText("Start round  →")
        assertCountdown()
        onModel { assertEquals(seconds, it.round?.durationSeconds) }
        return awaitWord()
    }

    protected fun assertCountdown() {
        awaitTag("countdown")
        val number = compose.onNodeWithTag("countdown").fetchSemanticsNode()
            .config[SemanticsProperties.Text].single().text.toInt()
        assertTrue("Countdown must display 3, 2 or 1", number in 1..3)
        compose.onNodeWithTag("word").assertDoesNotExist()
        compose.onNodeWithTag("correct").assertDoesNotExist()
        compose.onNodeWithTag("pass").assertDoesNotExist()
    }

    protected fun awaitWord(excluding: Set<String> = emptySet()): String {
        // The app uses elapsedRealtime, not the Compose test clock. Also wait for the
        // layout acknowledgement (seen words) before tapping: semantics alone can exist
        // just before AutoWord's onTextLayout has enabled scoring in the real model.
        compose.waitUntil(timeoutMillis = 10_000) {
            val ready = onModel { model ->
                val round = model.round
                val word = round?.currentWord
                round?.phase == Phase.PLAYING && round.feedback == null &&
                    word != null && word !in excluding && word in seenWords(model.selected.id)
            }
            ready && compose.onAllNodesWithTag("word").fetchSemanticsNodes().size == 1
        }
        val word = onModel { checkNotNull(it.round?.currentWord) }
        compose.onNodeWithTag("word").assertIsDisplayed().assertTextEquals(word.uppercase())
        compose.onNodeWithTag("timer").assertIsDisplayed()
        compose.onNodeWithTag("correct").assertIsEnabled()
        compose.onNodeWithTag("pass").assertIsEnabled()
        return word
    }

    protected fun assertPaused() {
        awaitText("ON HOLD")
        compose.onNodeWithTag("word").assertDoesNotExist()
        compose.onNodeWithTag("timer").assertDoesNotExist()
        compose.onNodeWithTag("correct").assertDoesNotExist()
        compose.onNodeWithTag("pass").assertDoesNotExist()
        onModel { assertEquals(Phase.PAUSED, it.round?.phase) }
    }

    protected fun finishThroughPause() {
        tapText("Pause")
        assertPaused()
        tapText("End & review")
        awaitTag("final-score")
    }

    protected fun assertAnswer(index: Int, word: String, label: String) {
        compose.onNodeWithTag("answer-$index").performScrollTo()
            .assertIsDisplayed().assertTextEquals(word, label)
    }

    protected fun seenWords(deckId: String): Set<String> = preferences()
        .getStringSet("seen-$deckId", emptySet()).orEmpty().toSet()

    protected fun preferences() = InstrumentationRegistry.getInstrumentation().targetContext
        .getSharedPreferences("off-the-top", Context.MODE_PRIVATE)

    protected fun <T> onModel(block: (AppModel) -> T): T = compose.runOnUiThread {
        block(ViewModelProvider(compose.activity)[AppModel::class.java])
    }

    protected fun <T> withReloadedModel(block: (AppModel) -> T): T = compose.runOnUiThread {
        // Recreation retains a ViewModel. A second, real model in a separate store also
        // proves that settings/history were saved and can be read by a fresh instance.
        val store = ViewModelStore()
        try {
            val factory = ViewModelProvider.AndroidViewModelFactory.getInstance(compose.activity.application)
            block(ViewModelProvider(store, factory)[AppModel::class.java])
        } finally {
            store.clear()
        }
    }

    protected fun assertSingleSavedRound(deck: String, seconds: Int, answers: List<Answer>) {
        fun check(model: AppModel) {
            assertEquals(1, model.history.size)
            val record = model.history.single()
            assertEquals(deck, record.deck)
            assertEquals(seconds, record.seconds)
            assertEquals(answers, record.answers)
            assertEquals(answers.count { it.outcome == Outcome.CORRECT }, record.score)
        }
        onModel { check(it) }
        withReloadedModel { check(it) }
    }

    protected companion object {
        const val EMPTY_HISTORY = "Your first round is still ahead of you. Pick a deck and play!"
    }
}

private class ClearGamePreferencesRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            val prefs = InstrumentationRegistry.getInstrumentation().targetContext
                .getSharedPreferences("off-the-top", Context.MODE_PRIVATE)
            assertTrue("Preferences must be cleared before MainActivity launches", prefs.edit().clear().commit())
            try {
                base.evaluate()
            } finally {
                // The inner activity rule has already closed the activity before cleanup.
                assertTrue("Test preferences must be cleaned up", prefs.edit().clear().commit())
            }
        }
    }
}