package com.nicgames.offthetop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Renders the real AutoWord; its visibility must be made internal by the parent change.
 * The 12 longest entries per deck give 36 distinct words and 108 size/scale cases.
 * This bounded regression sample is not an exhaustive proof for all 1,844 entries.
 */
@RunWith(AndroidJUnit4::class)
class CardLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    private data class CardSize(val width: Int, val height: Int, val fontScale: Float)
    private data class Card(val word: String, val size: CardSize)
    private data class Shown(val card: Card, val word: String)

    private var currentCard by mutableStateOf<Card?>(null)
    // Only touched on the UI thread; recording a layout must not trigger recomposition.
    private val shown = mutableListOf<Shown>()

    private val sampledWords: List<String> by lazy {
        val assets = InstrumentationRegistry.getInstrumentation().targetContext.assets
        val decks = listOf("wild-world", "everyday", "do-your-thing").map { id ->
            assets.open("decks/$id.txt").bufferedReader().useLines { lines ->
                lines.map { it.trim() }
                    .filter { it.isNotEmpty() && !it.startsWith("#") }
                    .toList()
            }.also { words ->
                assertTrue("Deck $id must contain at least 12 entries", words.size >= 12)
            }
        }
        assertEquals("Sample must be selected from the complete bundled catalog", 1844, decks.sumOf { it.size })
        decks.flatMap { words ->
            words.sortedWith(compareByDescending<String> { it.length }.thenBy { it }).take(12)
        }.distinctBy { it.uppercase() }
    }

    @Before
    fun installIsolatedCard() {
        // Compose's frame/idle synchronization is sufficient: no game clock or timed waits.
        compose.mainClock.autoAdvance = true
        compose.setContent {
            val card = currentCard
            PressTheme("Day") {
                if (card != null) {
                    CompositionLocalProvider(LocalDensity provides Density(1f, card.size.fontScale)) {
                        Box(Modifier.size(card.size.width.dp, card.size.height.dp).testTag("card-container")) {
                            AutoWord(card.word) { word -> shown += Shown(card, word) }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun longestWordsFromEveryDeckFitLandscape() {
        assertSampleFits(CardSize(width = 508, height = 140, fontScale = 1f))
    }

    @Test
    fun longestWordsFromEveryDeckFitCompactLandscapeAtDoubleFontScale() {
        assertSampleFits(CardSize(width = 508, height = 80, fontScale = 2f))
    }

    @Test
    fun longestWordsFromEveryDeckFitNarrowCardsAtLargeFontScale() {
        assertSampleFits(CardSize(width = 280, height = 70, fontScale = 1.3f))
    }

    @Test
    fun onShownIsWithheldForOverflowAndDeliveredOnceTheSameWordFits() {
        val word = sampledWords.maxBy { it.length }
        // A single pixel cannot fit even AutoWord's minimum 6sp text size.
        val tooSmall = Card(word, CardSize(width = 1, height = 1, fontScale = 2f))
        render(tooSmall)
        assertTrue("The negative control must genuinely overflow", readLayout(tooSmall).hasVisualOverflow)
        compose.runOnIdle {
            assertTrue("Overflowing text must never be reported as shown: $shown", shown.isEmpty())
        }

        // Keep the same composable/word: changing constraints must invalidate its fitted style.
        assertFits(Card(word, CardSize(width = 508, height = 80, fontScale = 2f)))
    }

    private fun assertSampleFits(size: CardSize) {
        sampledWords.forEach { word -> assertFits(Card(word, size)) }
    }

    private fun render(card: Card) {
        compose.runOnIdle {
            shown.clear()
            currentCard = card
            assertTrue("Changing state alone must not acknowledge a card before layout: $card", shown.isEmpty())
        }
        // The next semantics query synchronizes the actual recomposition and layout.
    }

    private fun assertFits(card: Card) {
        render(card)
        val layout = readLayout(card)
        compose.onNodeWithTag("word", useUnmergedTree = true).assertIsDisplayed()
        assertFalse("Text must fit without visual overflow: $card", layout.hasVisualOverflow)
        assertTrue("The full word must occupy one to three lines: $card", layout.lineCount in 1..3)
        assertTrue("Fitting must respect the 6sp minimum: $card", layout.layoutInput.style.fontSize.value >= 6f)
        assertEquals(
            "The final visible line must include the end of the word: $card",
            card.word.uppercase().length,
            layout.getLineEnd(layout.lineCount - 1, visibleEnd = true),
        )
        for (line in 0 until layout.lineCount) {
            assertFalse("No line may hide text behind an ellipsis: $card, line $line", layout.isLineEllipsized(line))
        }
        compose.runOnIdle {
            assertTrue("A fitting layout must acknowledge the word: $card", shown.isNotEmpty())
            // A layout may be delivered more than once; every callback must name this card.
            shown.forEach { acknowledgement ->
                assertEquals("No stale card may be acknowledged", card, acknowledgement.card)
                assertEquals("Callback must retain the original deck word", card.word, acknowledgement.word)
            }
        }
    }

    private fun readLayout(card: Card): TextLayoutResult {
        val container = compose.onNodeWithTag("card-container", useUnmergedTree = true).fetchSemanticsNode()
        // Density is deliberately 1px/dp. Fail rather than silently testing device-clamped sizes.
        assertEquals("Actual card width: $card", card.size.width, container.size.width)
        assertEquals("Actual card height: $card", card.size.height, container.size.height)
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag("word", useUnmergedTree = true)
            .assertTextEquals(card.word.uppercase())
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { getLayout ->
                assertTrue("AutoWord must expose its real text layout: $card", getLayout(layouts))
            }
        assertEquals("Exactly one rendered text layout: $card", 1, layouts.size)
        return layouts.single().also { layout ->
            assertEquals("Layout must contain the whole uppercase word: $card", card.word.uppercase(), layout.layoutInput.text.text)
            assertEquals("Text width constraint: $card", card.size.width, layout.layoutInput.constraints.maxWidth)
            assertEquals("Text height constraint: $card", card.size.height, layout.layoutInput.constraints.maxHeight)
            assertEquals("Text density: $card", 1f, layout.layoutInput.density.density, 0f)
            assertEquals("Text font scale: $card", card.size.fontScale, layout.layoutInput.density.fontScale, 0f)
        }
    }
}