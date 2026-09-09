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
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class GameplayFlowTest : OffTheTopUiTest() {
    @Test
    fun scorePauseReviewAndReplay_preserveHistoryAndNeverRepeatShownCards() {
        useTouchControls()
        tapText("60s")
        chooseDeck("Wild World")
        compose.onNodeWithTag("practice-feedback").performScrollTo()
            .assertTextEquals("Touch controls")
        onModel { assertTrue(it.history.isEmpty()) }

        val first = startAndAwaitWord(60)
        assertLiveScore(0)
        scoreWithRapidRepeatedClicks("correct", Outcome.CORRECT, first, 1, 1)
        val second = awaitWord(excluding = setOf(first))
        assertLiveScore(1)
        scoreWithRapidRepeatedClicks("pass", Outcome.PASS, second, 2, 1)
        val third = awaitWord(excluding = setOf(first, second))
        assertLiveScore(1)

        tapText("Pause")
        assertPaused()
        val timeOnHold = onModel { checkNotNull(it.round).remainingMs }
        onModel { assertEquals(third, it.round?.currentWord) }
        tapText("Resume round")
        assertCountdown()
        onModel { assertEquals(timeOnHold, it.round?.remainingMs) }
        assertEquals("Resuming must not draw a new card", third, awaitWord())
        assertLiveScore(1)

        finishThroughPause()
        compose.onNodeWithTag("final-score").assertTextEquals("1")
        compose.onNodeWithText("1 passed · 1 unanswered").assertIsDisplayed()
        assertAnswer(0, first, Outcome.CORRECT)
        assertAnswer(1, second, Outcome.PASS)
        assertAnswer(2, third, Outcome.UNANSWERED)

        // Review edits existing rows; it must neither append rows nor create a new history entry.
        compose.onNodeWithTag("answer-0").performScrollTo().performClick()
        compose.onNodeWithTag("final-score").assertTextEquals("0")
        assertAnswer(0, first, Outcome.PASS)
        compose.onNodeWithTag("answer-1").performScrollTo().performClick()
        compose.onNodeWithTag("final-score").assertTextEquals("1")
        assertAnswer(1, second, Outcome.CORRECT)
        compose.onNodeWithTag("answer-2").performScrollTo().performClick()
        compose.onNodeWithTag("final-score").assertTextEquals("2")
        assertAnswer(2, third, Outcome.CORRECT)
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
        assertAnswer(0, first, Outcome.PASS)
        assertAnswer(1, second, Outcome.CORRECT)
        assertAnswer(2, third, Outcome.CORRECT)
        assertSingleSavedRound("Wild World", 60, reviewedAnswers)
        onModel { assertEquals(savedId, it.history.single().id) }

        tapText("Play again", scroll = true)
        val remainingCards = onModel { it.selected.words.size - shown.size }
        compose.onNodeWithTag("round-options").assertIsDisplayed()
            .assertTextEquals("60s · $remainingCards unseen")
        val replayWord = startAndAwaitWord(60)
        assertFalse("Replay must exclude every previously displayed card", replayWord in shown)
        onModel { model ->
            val round = checkNotNull(model.round)
            assertEquals(0, round.score)
            assertTrue(round.answers.isEmpty())
            assertTrue("The replay draw pile must exclude all seen words", round.words.none { it in shown })
        }
        assertLiveScore(0)
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
        tapText("Recent rounds")
        val reviewedHistory = hasClickAction() and hasText("Wild World") and hasText("2")
        compose.onNode(reviewedHistory).performScrollTo().assertIsDisplayed()
            .assertContentDescriptionEquals("Expand answers").performClick()
        assertHistoryAnswer(first, Outcome.PASS)
        assertHistoryAnswer(second, Outcome.CORRECT)
        assertHistoryAnswer(third, Outcome.CORRECT)
        compose.onAllNodes(hasContentDescription(", Correct", substring = true)).assertCountEquals(2)
        compose.onAllNodes(hasContentDescription(", Passed", substring = true)).assertCountEquals(1)
        compose.onAllNodes(hasContentDescription(", Unanswered", substring = true)).assertCountEquals(0)
        compose.onNode(reviewedHistory).assertContentDescriptionEquals("Collapse answers").performScrollTo().performClick()
        compose.onNode(reviewedHistory).assertContentDescriptionEquals("Expand answers")
        compose.onNodeWithText(first).assertDoesNotExist()
        compose.onNodeWithText(second).assertDoesNotExist()
        compose.onNodeWithText(third).assertDoesNotExist()
    }

    @Test
    fun everyDeckOpensItsOwnPractice_withoutScoringOrUsingCards() {
        useTouchControls()
        val decks = onModel { it.decks.toList() }
        assertEquals(listOf("Wild World", "Everyday Things", "Do Your Thing", "Characters", "Silent Acting", "Food & Drink"),
            decks.map { it.title })
        for (deck in decks) {
            chooseDeck(deck.title)
            compose.onNodeWithText(deck.title).assertIsDisplayed()
            compose.onNodeWithText("${deck.words.size} cards", ignoreCase = true).assertDoesNotExist()
            compose.onNodeWithTag("practice-feedback").performScrollTo().assertIsDisplayed()
                .assertTextEquals("Touch controls")
            compose.onNodeWithTag("round-options").assertIsDisplayed()
                .assertTextEquals("60s · ${deck.words.size} unseen")
            assertEquals("Only Silent Acting changes the clue-giving rule", deck.id == "silent-acting", deck.silentActing)
            if (deck.silentActing) {
                compose.onNodeWithTag("silent-rule").performScrollTo().assertIsDisplayed().assertTextEquals(SILENT_RULE)
            } else compose.onNodeWithTag("silent-rule").assertDoesNotExist()
            compose.onNodeWithText("Start round").assertIsEnabled()
            compose.onNodeWithTag("word").assertDoesNotExist()
            compose.onNodeWithTag("live-score").assertDoesNotExist()
            onModel { model ->
                assertEquals(deck.id, model.selected.id)
                assertNull(model.round)
                assertTrue(model.history.isEmpty())
                assertEquals(deck.words.size, model.unseen(deck))
            }
            assertTrue(seenWords(deck.id).isEmpty())
            tapText("Back")
            awaitHome()
        }
        tapText("Recent rounds")
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
    private lateinit var bundledDecks: List<Deck>

    // An ordinary @Before runs AFTER ActivityScenarioRule launches the activity. This
    // outer rule commits the clear first, before AppModel can read stale preferences.
    @get:Rule
    val appRules: TestRule = RuleChain.outerRule(ClearGamePreferencesRule()).around(compose)

    @Before
    fun awaitFreshActivity() {
        bundledDecks = onModel { it.decks.toList() }
        awaitHome()
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
        val deck = showDeck(title)
        compose.onNodeWithTag("deck-${deck.id}").assertIsDisplayed()
            .assertContentDescriptionEquals("Choose ${deck.title}, ${deck.words.size} cards")
            .assertHasClickAction().performClick()
        awaitText("Start round")
        onModel { assertEquals(title, it.selected.title) }
    }

    protected fun showDeck(title: String): Deck {
        val index = bundledDecks.indexOfFirst { it.title == title }
        assertTrue("Unknown deck: $title", index >= 0)
        val (_, pages) = deckPage()
        val perPage = (bundledDecks.size + pages - 1) / pages
        goToDeckPage(index / perPage)
        return bundledDecks[index]
    }

    /** Navigate the real page buttons; HorizontalPager does not support performScrollTo. */
    protected fun goToDeckPage(target: Int) {
        val (_, pages) = deckPage()
        assertTrue("Requested deck page must exist", target in 0 until pages)
        repeat(pages) {
            val (current, _) = deckPage()
            if (current == target) {
                assertCurrentDeckPage()
                return
            }
            val next = current + if (target > current) 1 else -1
            compose.onNodeWithTag(if (target > current) "next-decks" else "previous-decks")
                .assertIsDisplayed().assertIsEnabled().assertHasClickAction().performClick()
            awaitDeckPage(next, pages)
        }
        assertEquals("Page navigation did not reach its target", target, deckPage().first)
        assertCurrentDeckPage()
    }

    protected fun deckPage(): Pair<Int, Int> {
        val label = compose.onNodeWithTag("deck-page").assertIsDisplayed().fetchSemanticsNode()
            .config[SemanticsProperties.Text].single().text
        val match = checkNotNull(Regex("([1-3]) / ([2-3])").matchEntire(label)) { "Unexpected page indicator: $label" }
        return match.groupValues[1].toInt() - 1 to match.groupValues[2].toInt()
    }

    protected fun awaitDeckPage(page: Int, total: Int) {
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodes(hasTestTag("deck-page") and hasText("${page + 1} / $total"))
                .fetchSemanticsNodes().size == 1
        }
        compose.waitForIdle() // Wait for the slide to settle, not just currentPage's midpoint update.
        assertEquals(page to total, deckPage())
        onModel { assertEquals("The model must remember the displayed page", page, it.deckPage) }
    }

    protected fun assertCurrentDeckPage(): List<Deck> {
        val (page, total) = deckPage()
        assertTrue(page in 0 until total)
        val perPage = (bundledDecks.size + total - 1) / total
        val visible = bundledDecks.chunked(perPage)[page]
        val pagerBounds = compose.onNodeWithTag("deck-list").fetchSemanticsNode().boundsInRoot
        bundledDecks.forEach { deck ->
            val node = compose.onNodeWithTag("deck-${deck.id}")
            if (deck in visible) {
                node.assertIsDisplayed().assertHasClickAction()
                    .assertTextEquals(printedDeckTitle(deck.id), "${deck.words.size} cards")
                    .assertContentDescriptionEquals("Choose ${deck.title}, ${deck.words.size} cards")
                compose.onAllNodesWithContentDescription("Choose ${deck.title}, ${deck.words.size} cards")
                    .assertCountEquals(1)
                val actual = node.fetchSemanticsNode()
                val bounds = actual.boundsInRoot
                assertEquals("${deck.title} must not be horizontally clipped", actual.size.width.toFloat(), bounds.width, 1f)
                assertEquals("${deck.title} must not be vertically clipped", actual.size.height.toFloat(), bounds.height, 1f)
                assertTrue("The whole ${deck.title} stack must fit its page", bounds.width > 0 && bounds.height > 0 &&
                    bounds.left >= pagerBounds.left && bounds.right <= pagerBounds.right &&
                    bounds.top >= pagerBounds.top && bounds.bottom <= pagerBounds.bottom)
            } else node.assertIsNotDisplayed()
        }
        compose.onNodeWithTag("deck-page").assertContentDescriptionEquals("Deck page ${page + 1} of $total")
        val previous = compose.onNodeWithTag("previous-decks").assertIsDisplayed().assertHasClickAction()
            .assertContentDescriptionEquals("Previous decks")
        val next = compose.onNodeWithTag("next-decks").assertIsDisplayed().assertHasClickAction()
            .assertContentDescriptionEquals("Next decks")
        if (page == 0) previous.assertIsNotEnabled() else previous.assertIsEnabled()
        if (page == total - 1) next.assertIsNotEnabled() else next.assertIsEnabled()
        onModel { assertEquals(page, it.deckPage) }
        return visible
    }

    protected fun printedDeckTitle(id: String): String = when (id) {
        "wild-world" -> "WILD\nWORLD"
        "everyday" -> "EVERYDAY\nTHINGS"
        "do-your-thing" -> "DO YOUR\nTHING"
        "characters" -> "CHARACTERS"
        "silent-acting" -> "SILENT\nACTING"
        "food-drink" -> "FOOD &\nDRINK"
        else -> error("Unknown test deck: $id")
    }

    protected fun awaitHome() {
        awaitTag("home")
        compose.onNodeWithTag("app-title").assertIsDisplayed().assertTextEquals("OFF THE TOP")
        compose.onNodeWithTag("deck-list").assertIsDisplayed()
            .assertContentDescriptionEquals("Choose a deck")
        compose.onNodeWithText("Choose a deck").assertDoesNotExist()
        assertCurrentDeckPage()
    }

    protected fun useTouchControls() {
        tapText("Settings")
        setToggle("Touch-only mode", true)
        setToggle("Sound effects", false)
        setToggle("Vibration", false)
        tapText("Back")
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
        tapText("Start round")
        assertCountdown()
        onModel { assertEquals(seconds, it.round?.durationSeconds) }
        return awaitWord()
    }

    protected fun assertCountdown() {
        awaitTag("countdown")
        val number = compose.onNodeWithTag("countdown").fetchSemanticsNode()
            .config[SemanticsProperties.Text].single().text.toInt()
        assertTrue("Countdown must display 3, 2 or 1", number in 1..3)
        val silent = onModel { it.selected.silentActing }
        compose.onNodeWithText(if (silent) SILENT_COUNTDOWN else "Hold still at your forehead").assertIsDisplayed()
        compose.onNodeWithText(if (silent) "Hold still at your forehead" else SILENT_COUNTDOWN).assertDoesNotExist()
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
        awaitText("Paused")
        compose.onNodeWithTag("word").assertDoesNotExist()
        compose.onNodeWithTag("timer").assertDoesNotExist()
        compose.onNodeWithTag("live-score").assertDoesNotExist()
        compose.onNodeWithTag("correct").assertDoesNotExist()
        compose.onNodeWithTag("pass").assertDoesNotExist()
        compose.onNodeWithText("End & review").assertIsEnabled()
        compose.onNodeWithText("Resume round").assertIsEnabled()
        onModel { assertEquals(Phase.PAUSED, it.round?.phase) }
    }

    protected fun finishThroughPause() {
        tapText("Pause")
        assertPaused()
        tapText("End & review")
        awaitTag("final-score")
        compose.onNodeWithTag("results-heading").assertIsDisplayed().assertTextEquals("Results")
    }

    protected fun assertLiveScore(score: Int) {
        compose.onNodeWithTag("live-score").assertIsDisplayed()
            .assertTextEquals("$score").assertContentDescriptionEquals("$score correct")
        onModel { assertEquals(score, it.round?.score) }
    }

    protected fun assertAnswer(index: Int, word: String, outcome: Outcome) {
        val row = compose.onNodeWithTag("answer-$index").assertHasClickAction()
        assertAnswerRow(row, word, outcome)
        assertEquals("Review must explain its action to accessibility services", "Change result",
            row.fetchSemanticsNode().config[SemanticsActions.OnClick].label)
        onModel { assertEquals(Answer(word, outcome), checkNotNull(it.round).answers[index]) }
    }

    protected fun assertHistoryAnswer(word: String, outcome: Outcome) {
        assertAnswerRow(compose.onNodeWithText(word), word, outcome)
    }

    private fun assertAnswerRow(row: SemanticsNodeInteraction, word: String, outcome: Outcome) {
        val description = when (outcome) {
            Outcome.CORRECT -> "Correct"
            Outcome.PASS -> "Passed"
            Outcome.UNANSWERED -> "Unanswered"
        }
        row.performScrollTo().assertIsDisplayed().assertTextEquals(word)
            .assertContentDescriptionEquals("$word, $description")
    }

    protected fun seenWords(deckId: String): Set<String> {
        val global = preferences().getStringSet("seen-shared-v1", emptySet()).orEmpty()
            .map { it.trim().lowercase(Locale.ROOT) }.toSet()
        // Keep the old assertions' original casing, while filtering the shared memory
        // to this deck. The cached catalog also avoids nested UI-thread synchronization.
        return bundledDecks.single { it.id == deckId }.words
            .filter { it.trim().lowercase(Locale.ROOT) in global }.toSet()
    }

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
        const val EMPTY_HISTORY = "No rounds yet"
        const val SILENT_RULE = "Clue givers: act without speaking.\nGuesser: say your answer."
        const val SILENT_COUNTDOWN = "Hold still. Clues are acted, not spoken."
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