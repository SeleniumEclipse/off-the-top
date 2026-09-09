package com.nicgames.offthetop

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.Surface
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real picker, practice, rounds and saved history. No device commands or microphone fixture. */
@RunWith(AndroidJUnit4::class)
class ExpansionFlowTest : OffTheTopUiTest() {
    @Test
    fun roundLengthLabelsStayWholeBesidePaging_orUseTheirOwnRowAtLargeFontSizes() {
        useTouchControls()
        val model = onModel { it }
        for (width in listOf(550, 650, 884)) for (scale in listOf(1f, 1.3f, 2f)) {
            compose.runOnUiThread {
                compose.activity.setContent {
                    CompositionLocalProvider(LocalDensity provides Density(1f, scale)) {
                        val density = LocalDensity.current
                        val direction = LocalLayoutDirection.current
                        val cutout = WindowInsets.displayCutout
                        val horizontalCutout = cutout.getLeft(density, direction) + cutout.getRight(density, direction)
                        Box(Modifier.size((width + 44 + horizontalCutout).dp, 420.dp)) { GameApp(model) }
                    }
                }
            }
            awaitHome()
            assertEquals(width, compose.onNodeWithTag("home").fetchSemanticsNode().size.width)
            val page = compose.onNodeWithTag("deck-page").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            for (seconds in listOf(30, 60, 90, 120)) {
                val node = compose.onNodeWithText("${seconds}s").assertIsDisplayed().assertHasClickAction()
                val text = compose.onNodeWithText("${seconds}s", useUnmergedTree = true)
                val layouts = mutableListOf<TextLayoutResult>()
                text.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { assertTrue(it(layouts)) }
                val layout = layouts.single()
                assertEquals(scale, layout.layoutInput.density.fontScale, 0f)
                assertEquals("Keep ${seconds}s on one line: width=$width font=$scale", 1, layout.lineCount)
                assertFalse("Do not clip ${seconds}s: width=$width font=$scale", layout.hasVisualOverflow)
                assertEquals("${seconds}s".length, layout.getLineEnd(0, visibleEnd = true))
                assertFalse("Duration and paging must not overlap", text.fetchSemanticsNode().boundsInRoot.overlaps(page))
                val target = node.fetchSemanticsNode().touchBoundsInRoot
                assertTrue(target.width >= 48f && target.height >= 48f)
                node.performClick()
                onModel { assertEquals(seconds, it.seconds) }
            }
        }
    }

    @Test
    fun pageButtonsAndSwipesReachAllSixDecks_andRememberThePageInBothLandscapeDirections() {
        useTouchControls()
        val originalOrientation = compose.runOnUiThread { compose.activity.requestedOrientation }
        val originalRotation = rotation()
        try {
            exercisePagerAndReturnPaths(compose.density.density)
            compose.runOnUiThread {
                compose.activity.requestedOrientation = if (
                    originalRotation == Surface.ROTATION_0 || originalRotation == Surface.ROTATION_90
                ) ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            compose.waitUntil(timeoutMillis = 10_000) {
                rotation() == (originalRotation + 2) % 4 && compose.runOnUiThread {
                    compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                }
            }
            awaitHome()
            exercisePagerAndReturnPaths(compose.density.density)
        } finally {
            compose.runOnUiThread { compose.activity.requestedOrientation = originalOrientation }
        }

        // Exercise BOTH width branches on the real GameApp, not a duplicated picker.
        // Density 1 makes the actual home-width assertion independent of device DPI.
        val model = onModel { it }
        for ((width, perPage) in listOf(649 to 2, 650 to 3)) {
            compose.runOnUiThread {
                compose.activity.setContent {
                    CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                        val density = LocalDensity.current
                        val direction = LocalLayoutDirection.current
                        val cutout = WindowInsets.displayCutout
                        val horizontalCutout = cutout.getLeft(density, direction) + cutout.getRight(density, direction)
                        // Preserve real cutout handling while giving HOME exactly the
                        // requested width after GameApp's cutout and 22dp side insets.
                        Box(Modifier.size((width + 44 + horizontalCutout).dp, 360.dp)) { GameApp(model) }
                    }
                }
            }
            awaitHome()
            assertEquals("Do not silently test a clamped breakpoint fixture", width,
                compose.onNodeWithTag("home").fetchSemanticsNode().size.width)
            assertEquals(6 / perPage, deckPage().second)
            goToDeckPage(0)
            val reached = mutableSetOf<String>()
            repeat(6 / perPage) { page ->
                goToDeckPage(page)
                val visible = assertCurrentDeckPage()
                assertEquals("Cards per page at $width dp", perPage, visible.size)
                reached += visible.map { it.id }
                assertPageSizing(perPage, 1f)
            }
            assertEquals(onModel { it.decks.map { deck -> deck.id }.toSet() }, reached)
            goToDeckPage(0)
            compose.onNodeWithTag("deck-list").performTouchInput { swipeLeft() }
            awaitDeckPage(1, 6 / perPage)
            assertCurrentDeckPage()
            tapText("Settings")
            tapText("Back")
            awaitHome()
            assertEquals(1 to 6 / perPage, deckPage())
        }
    }

    @Test
    fun charactersRoundScoresPausesFinishesAndPersistsItsOwnHistory() {
        playExpansionRound("Characters", "characters", 580, silent = false)
    }

    @Test
    fun silentActingExplainsBothRoles_andUsesTheSameScoringPauseAndHistoryFlow() {
        playExpansionRound("Silent Acting", "silent-acting", 534, silent = true)
        // A later ordinary deck must not inherit the acting-only instructions.
        chooseDeck("Everyday Things")
        compose.onNodeWithTag("silent-rule").assertDoesNotExist()
        compose.onNodeWithText("Hold at your forehead,\nfacing your friends.").performScrollTo().assertIsDisplayed()
        tapText("Start round")
        assertCountdown() // Checks normal wording and absence of the silent countdown wording.
        val word = awaitWord()
        finishThroughPause()
        assertAnswer(0, word, Outcome.UNANSWERED)
        withReloadedModel { model ->
            assertEquals(listOf("Everyday Things", "Silent Acting"), model.history.map { it.deck })
        }
    }

    @Test
    fun foodAndDrinkRoundScoresPausesFinishesAndPersistsItsOwnHistory() {
        playExpansionRound("Food & Drink", "food-drink", 550, silent = false)
    }

    @Test
    fun sharedCardsStayExcludedAcrossDecks_andExhaustionAndReshufflePreserveUnrelatedState() {
        useTouchControls()
        val source = onModel { it.decks.single { deck -> deck.id == "everyday" } }
        val themed = onModel { it.decks.single { deck -> deck.id == "food-drink" } }
        val unrelated = onModel { it.decks.single { deck -> deck.id == "characters" }.words.first() }
        val shared = source.words.first { it in themed.words }
        val sourceIdentities = source.words.map(::identity).toSet()
        val seeded = (sourceIdentities - identity(shared)) + identity(unrelated)
        // Leave exactly one real source-deck card available, so the overlap is not
        // dependent on a lucky shuffle. The stale legacy set must stay ignored.
        assertTrue(preferences().edit().putStringSet(SeenCards.KEY, seeded)
            .putStringSet("seen-everyday", setOf(shared)).commit())
        chooseDeck(source.title)
        compose.onNodeWithTag("round-options").assertTextEquals("60s · 1 unseen")
        assertEquals(shared, startAndAwaitWord(60))
        assertEquals(seeded + identity(shared), sharedMemory())
        assertEquals(setOf(shared), preferences().getStringSet("seen-everyday", null))
        compose.onNodeWithTag("correct").performClick()
        awaitTag("final-score")
        compose.onNodeWithTag("final-score").assertTextEquals("1")
        assertAnswer(0, shared, Outcome.CORRECT)
        assertSingleSavedRound(source.title, 60, listOf(Answer(shared, Outcome.CORRECT)))

        tapText("Change deck", scroll = true)
        chooseDeck(themed.title)
        compose.onNodeWithTag("round-options").assertTextEquals("60s · ${themed.words.size - 115} unseen")
        val beforeThemedRound = sharedMemory()
        val themedWord = startAndAwaitWord(60)
        assertFalse("The same card cannot reappear just by changing decks", identity(themedWord) in beforeThemedRound)
        onModel { model ->
            assertTrue(checkNotNull(model.round).words.none { identity(it) in beforeThemedRound })
            assertEquals(115, seenWords(themed.id).count { it in source.words })
        }
        assertEquals(beforeThemedRound + identity(themedWord), sharedMemory())
        assertEquals(setOf(unrelated), seenWords("characters"))
        finishThroughPause()
        assertAnswer(0, themedWord, Outcome.UNANSWERED)

        tapText("Change deck", scroll = true)
        chooseDeck(source.title)
        compose.onNodeWithTag("round-options").assertTextEquals("60s · 0 unseen")
        val beforeRestart = sharedMemory()
        val model = onModel { it }
        compose.onNodeWithText("Start round").performSemanticsAction(SemanticsActions.OnClick) { click ->
            click()
            // Synchronous with the real start callback: no clue can have laid out yet.
            assertEquals(beforeRestart - sourceIdentities, sharedMemory())
            assertEquals(source.words.toSet(), checkNotNull(model.round).words.toSet())
            assertEquals(2, model.history.size)
        }
        assertCountdown()
        val restartedWord = awaitWord()
        assertEquals((beforeRestart - sourceIdentities) + identity(restartedWord), sharedMemory())
        assertEquals(setOf(unrelated), seenWords("characters"))
        assertTrue("An unrelated themed card must survive the original deck's restart", identity(themedWord) in sharedMemory())
        finishThroughPause()
        assertAnswer(0, restartedWord, Outcome.UNANSWERED)
        val history = onModel { it.history.toList() }
        assertEquals(3, history.size)

        tapText("Change deck", scroll = true)
        tapText("Settings")
        val beforeReset = sharedMemory()
        tapText("Reshuffle all cards", scroll = true)
        awaitText("Make all cards available?")
        tapText("Cancel")
        assertEquals(beforeReset, sharedMemory())
        onModel { assertEquals(history, it.history) }
        tapText("Reshuffle all cards", scroll = true)
        tapText("Reshuffle")
        assertTrue(preferences().contains(SeenCards.KEY))
        assertTrue(sharedMemory().isEmpty())
        assertEquals(setOf(shared), preferences().getStringSet("seen-everyday", null))
        compose.activityRule.scenario.recreate()
        awaitText("Settings")
        withReloadedModel { reloaded ->
            assertEquals(history, reloaded.history)
            reloaded.decks.forEach { assertEquals("Every deck is unseen after reshuffle: ${it.title}", it.words.size, reloaded.unseen(it)) }
        }
        assertTrue("Recreation must not migrate the stale legacy sets again", sharedMemory().isEmpty())
        tapText("Back")
        chooseDeck(source.title)
        compose.onNodeWithTag("round-options").assertTextEquals("60s · ${source.words.size} unseen")
    }

    private fun exercisePagerAndReturnPaths(density: Float) {
        awaitHome()
        val widthDp = compose.onNodeWithTag("home").fetchSemanticsNode().size.width / density
        val perPage = if (widthDp >= 650f) 3 else 2
        val total = 6 / perPage
        assertEquals(total, deckPage().second)
        goToDeckPage(0)
        val reached = mutableSetOf<String>()
        repeat(total) { page ->
            goToDeckPage(page)
            reached += assertCurrentDeckPage().map { it.id }
            assertPageSizing(perPage, density)
        }
        assertEquals(onModel { it.decks.map { deck -> deck.id }.toSet() }, reached)
        val retained = deckPage()
        for (screen in listOf("Settings", "How to play", "Recent rounds")) {
            tapText(screen)
            tapText("Back")
            awaitHome()
            assertEquals("Returning from $screen must keep the last page", retained, deckPage())
        }
        val previousActivity = compose.activity
        val previousModel = onModel { it }
        compose.activityRule.scenario.recreate()
        awaitHome()
        assertNotSame(previousActivity, compose.activity)
        onModel { assertSame(previousModel, it) }
        assertEquals(retained, deckPage())
        goToDeckPage(0)
        compose.onNodeWithTag("deck-list").performTouchInput { swipeLeft() }
        awaitDeckPage(1, total)
        assertCurrentDeckPage()
        tapText("Settings")
        tapText("Back")
        awaitHome()
        assertEquals("A swiped page must survive Settings too", 1 to total, deckPage())
        compose.onNodeWithTag("deck-list").performTouchInput { swipeRight() }
        awaitDeckPage(0, total)
        assertCurrentDeckPage()
    }

    private fun assertPageSizing(perPage: Int, density: Float) {
        val visible = assertCurrentDeckPage()
        assertEquals(perPage, visible.size)
        val pager = compose.onNodeWithTag("deck-list").fetchSemanticsNode().boundsInRoot
        val expectedWidth = (pager.width - 16f * density * (perPage - 1)) / perPage - 16f * density
        val bounds = visible.map { deck ->
            compose.onNodeWithTag("deck-${deck.id}").fetchSemanticsNode().boundsInRoot.also {
                assertEquals("${deck.title} must use its full page share, not become a tiny card", expectedWidth, it.width, 1f)
            }
        }
        bounds.zipWithNext().forEach { (left, right) -> assertTrue(left.right < right.left) }
        for (tag in listOf("next-decks", "previous-decks")) {
            // Material's 40dp painted button has a larger 48dp touch region.
            val target = compose.onNodeWithTag(tag).assertIsDisplayed().fetchSemanticsNode().touchBoundsInRoot
            assertTrue("$tag must retain a 48dp touch target", target.width >= 48f * density - 1 && target.height >= 48f * density - 1)
        }
    }

    private fun playExpansionRound(title: String, id: String, count: Int, silent: Boolean) {
        useTouchControls()
        chooseDeck(title)
        val selectedPage = onModel { it.deckPage }
        onModel { model ->
            assertEquals(id, model.selected.id)
            assertEquals(count, model.selected.words.size)
            assertEquals(silent, model.selected.silentActing)
            assertTrue(model.history.isEmpty())
        }
        compose.onNodeWithText("Hold at your forehead,\nfacing your friends.").performScrollTo().assertIsDisplayed()
        if (silent) {
            compose.onNodeWithTag("silent-rule").performScrollTo().assertIsDisplayed().assertTextEquals(SILENT_RULE)
            compose.onAllNodesWithTag("silent-rule").assertCountEquals(1)
        } else compose.onNodeWithTag("silent-rule").assertDoesNotExist()
        compose.onNodeWithTag("round-options").assertTextEquals("60s · $count unseen")
        compose.onNodeWithTag("practice-feedback").performScrollTo().assertTextEquals("Touch controls")
        val first = startAndAwaitWord(60)
        assertSelectedCategory(id, title)
        assertLiveScore(0)
        compose.onNodeWithTag("correct").performClick()
        val second = awaitWord(excluding = setOf(first))
        assertLiveScore(1)
        compose.onNodeWithTag("pass").performClick()
        val third = awaitWord(excluding = setOf(first, second))
        assertLiveScore(1)
        tapText("Pause")
        assertPaused()
        val frozen = onModel { checkNotNull(it.round).remainingMs }
        assertEquals(setOf(first, second, third), seenWords(id))
        compose.activityRule.scenario.recreate()
        assertPaused()
        onModel {
            assertEquals(id, it.selected.id)
            assertEquals(frozen, it.round?.remainingMs)
            assertEquals(third, it.round?.currentWord)
            assertEquals(listOf(Answer(first, Outcome.CORRECT), Answer(second, Outcome.PASS)), it.round?.answers)
            assertTrue(it.history.isEmpty())
        }
        tapText("Resume round")
        assertCountdown()
        assertEquals(third, awaitWord())
        assertLiveScore(1)
        assertSelectedCategory(id, title)
        finishThroughPause()
        compose.onNodeWithTag("final-score").assertTextEquals("1")
        compose.onNodeWithText("1 passed · 1 unanswered").assertIsDisplayed()
        val answers = listOf(Answer(first, Outcome.CORRECT), Answer(second, Outcome.PASS), Answer(third, Outcome.UNANSWERED))
        answers.forEachIndexed { index, answer -> assertAnswer(index, answer.word, answer.outcome) }
        assertSingleSavedRound(title, 60, answers)
        assertEquals(setOf(first, second, third), seenWords(id))
        tapText("Change deck", scroll = true)
        awaitHome()
        assertEquals(selectedPage, deckPage().first)
        tapText("Recent rounds")
        val record = compose.onNode(hasClickAction() and hasText(title) and hasText("1"))
        record.performScrollTo().assertContentDescriptionEquals("Expand answers").performClick()
        answers.forEach { assertHistoryAnswer(it.word, it.outcome) }
        for (label in listOf("Correct", "Passed", "Unanswered")) {
            compose.onAllNodes(hasContentDescription(", $label", substring = true)).assertCountEquals(1)
        }
        record.performScrollTo().assertContentDescriptionEquals("Collapse answers").performClick()
        answers.forEach { compose.onNodeWithText(it.word).assertDoesNotExist() }
        tapText("Back")
        awaitHome()
        assertEquals(selectedPage, deckPage().first)
    }

    private fun assertSelectedCategory(id: String, title: String) {
        compose.onAllNodesWithText(title, useUnmergedTree = true).assertCountEquals(1)
        compose.onNodeWithText(title).assertIsDisplayed()
        onModel { it.decks.map { deck -> deck.id } }.forEach { candidate ->
            compose.onAllNodesWithTag("category-$candidate", useUnmergedTree = true)
                .assertCountEquals(if (candidate == id) 2 else 0)
        }
        compose.onNodeWithTag("silent-rule").assertDoesNotExist()
    }

    private fun rotation(): Int = compose.runOnUiThread {
        @Suppress("DEPRECATION")
        compose.activity.windowManager.defaultDisplay.rotation
    }

    private fun identity(word: String) = word.trim().lowercase(Locale.ROOT)
    private fun sharedMemory(): Set<String> = checkNotNull(preferences().getStringSet(SeenCards.KEY, null)).toSet()
}