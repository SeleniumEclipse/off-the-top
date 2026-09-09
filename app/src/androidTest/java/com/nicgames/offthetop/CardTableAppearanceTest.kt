package com.nicgames.offthetop

import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.sharp.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.platform.InspectableValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Four bounded checks: native artwork/surfaces, real scoring callbacks, and compact type. */
@RunWith(AndroidJUnit4::class)
class CardTableAppearanceTest : OffTheTopUiTest() {
    private data class Category(val id: String, val title: String, val printed: String, val resource: Int)
    private val categories = listOf(
        Category("wild-world", "Wild World", "WILD\nWORLD", R.drawable.category_wild),
        Category("everyday", "Everyday Things", "EVERYDAY\nTHINGS", R.drawable.category_everyday),
        Category("do-your-thing", "Do Your Thing", "DO YOUR\nTHING", R.drawable.category_actions),
    )
    private var mode by mutableStateOf("Day")
    private var selected by mutableStateOf(categories.first())
    private var fontScale by mutableStateOf(1f)

    @Test
    fun threeNativeStacksPaintOriginalVectors_andChooseTheirAccessibleDeck() {
        val decks = onModel { it.decks.toList() }
        val chosen = mutableListOf<String>()
        installIsolatedContent {
            val p = LocalPress.current
            PageBackground(Modifier.size(620.dp, 300.dp).testTag("table-stage")) {
                Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth().height(225.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        decks.forEach { deck ->
                            DeckStack(deck, { chosen += deck.id }, Modifier.weight(1f).fillMaxHeight())
                        }
                    }
                    // Independent resource bindings, NOT CategoryIcon. Unit guardrails
                    // freeze these resources to the approved original SVG path shapes.
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        categories.forEach { category ->
                            Box(Modifier.background(p.cardFace)) {
                                Icon(painterResource(category.resource), contentDescription = null,
                                    tint = if (category.id == "everyday") p.pass else p.cardInk,
                                    modifier = Modifier.size(30.dp).testTag("reference-${category.id}"))
                            }
                        }
                    }
                }
            }
        }
        val bounds = categories.map { category ->
            val deck = decks.single { it.id == category.id }
            val stack = compose.onNodeWithTag("deck-${category.id}")
                .assertIsDisplayed().assertHasClickAction()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
                .assertContentDescriptionEquals("Choose ${category.title}, ${deck.words.size} cards")
                .assertTextEquals(category.printed, "${deck.words.size} cards")
            val title = readLayout(compose.onNodeWithText(category.printed, useUnmergedTree = true))
            assertFalse("Printed title must fit: ${category.title}", title.hasVisualOverflow)
            val countLayout = assertTextFitsActualNode(
                compose.onNodeWithText("${deck.words.size} cards", useUnmergedTree = true))
            assertEquals("Card count must remain a single line: ${category.title}", 1, countLayout.lineCount)
            val markers = compose.onAllNodesWithTag("category-${category.id}", useUnmergedTree = true)
                .assertCountEquals(2)
            val reference = compose.onNodeWithTag("reference-${category.id}").captureToImage().toPixelMap()
            val tint = if (category.id == "everyday") Day.pass else Day.cardInk
            repeat(2) { index ->
                assertVectorPainter(markers[index])
                assertSamePrintedVector(markers[index].captureToImage().toPixelMap(), reference,
                    tint, Day.cardFace, mirrored = index == 1, label = "${category.title} corner $index")
            }
            stack.performClick()
            stack.fetchSemanticsNode().boundsInRoot
        }
        bounds.zipWithNext().forEach { (left, right) ->
            assertTrue("All three stacks must be laid out horizontally", left.right < right.left)
            assertEquals("Stacks share a top edge", left.top, right.top, 1f)
        }
        compose.runOnIdle { assertEquals(categories.map { it.id }, chosen) }
        assertNoSuitOrGlyphText()
        onModel { model ->
            assertNull(model.round)
            assertTrue(model.history.isEmpty())
            decks.forEach { assertTrue(seenWords(it.id).isEmpty()) }
        }
    }

    @Test
    fun actualPlayingCardKeepsCreamFaceRedBackAndSelectedMarkers_onFlatDayAndNightPages() {
        installIsolatedContent {
            PageBackground(Modifier.size(400.dp, 240.dp).testTag("table-stage")) {
                PlayingCard(selected.id, Modifier.align(Alignment.Center).size(360.dp, 196.dp).testTag("sample-card")) {
                    AutoWord("Giraffe", LocalPress.current.cardInk) { }
                }
            }
        }
        for ((theme, palette) in listOf("Day" to Day, "Night" to Night)) {
            categories.forEach { category ->
                compose.runOnIdle { mode = theme; selected = category }
                val stage = compose.onNodeWithTag("table-stage").captureToImage().toPixelMap()
                assertEquals("Do not silently test a device-clamped stage", 400, stage.width)
                assertEquals(240, stage.height)
                assertExposedPageIsFlat(stage, palette.paper)
                val card = compose.onNodeWithTag("sample-card").captureToImage().toPixelMap()
                assertTrue("$theme actual card must be predominantly opaque cream stock",
                    fractionNear(card, palette.cardFace) > .60f)
                assertTrue("$theme actual playing card must expose its red back",
                    fractionNear(card, palette.pass) > .001f)
                assertEquals("Clue ink is fixed to the cream face, not page ink", palette.cardInk,
                    readLayout(compose.onNodeWithTag("word")).layoutInput.style.color)
                categories.forEach { candidate ->
                    val markers = compose.onAllNodesWithTag("category-${candidate.id}", useUnmergedTree = true)
                    markers.assertCountEquals(if (candidate == category) 2 else 0)
                    if (candidate == category) repeat(2) { assertVectorPainter(markers[it]) }
                }
            }
        }
    }

    @Test
    fun actualCorrectAndPassButtonsRenderMatchingFeedback_withoutHiddenOrRepeatedScoring() {
        useTouchControls()
        onModel { it.changeTheme("Day") }
        chooseDeck("Wild World")
        val first = startAndAwaitWord(60)
        // Keep feedback inspectable without sleeping or racing its 650ms lifetime.
        // The real engine ignores earlier timestamps; only this test advances it.
        // No replacement engine, production hook, direct feedback assignment, or
        // change to the existing real-time/pause/tilt coverage is involved.
        var inspectionTime = AppModel.now() + 20_000L
        onModel { model -> checkNotNull(model.round).tick(inspectionTime); model.tick() }
        val expectedAnswers = mutableListOf<Answer>()
        var word = first
        for ((tag, outcome) in listOf("correct" to Outcome.CORRECT, "pass" to Outcome.PASS)) {
            val otherTag = if (tag == "correct") "pass" else "correct"
            val click = checkNotNull(compose.onNodeWithTag(tag).assertIsEnabled()
                .fetchSemanticsNode().config[SemanticsActions.OnClick].action)
            val otherClick = checkNotNull(compose.onNodeWithTag(otherTag).assertIsEnabled()
                .fetchSemanticsNode().config[SemanticsActions.OnClick].action)
            val palette = if (onModel { it.theme } == "Night") Night else Day
            val button = compose.onNodeWithTag(tag)
            assertEquals(palette.onOutcome,
                readLayout(compose.onNode(hasText(if (tag == "correct") "Correct" else "Pass") and
                    hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true)).layoutInput.style.color)
            assertTrue("Enabled button must use its outcome color",
                fractionNear(button.captureToImage().toPixelMap(), if (outcome == Outcome.CORRECT) palette.correct else palette.pass) > .35f)
            expectedAnswers += Answer(word, outcome)
            button.performSemanticsAction(SemanticsActions.OnClick) { action ->
                action(); click(); otherClick(); action()
            }
            for ((theme, p) in listOf("Day" to Day, "Night" to Night)) {
                onModel { it.changeTheme(theme) }
                val label = if (outcome == Outcome.CORRECT) "Correct" else "Pass"
                val ink = if (outcome == Outcome.CORRECT) p.correct else p.pass
                val feedback = compose.onNodeWithTag("feedback-$tag").assertIsDisplayed().assertTextEquals(label)
                assertEquals("$theme feedback must use the matching outcome ink", ink,
                    readLayout(feedback).layoutInput.style.color)
                compose.onNodeWithTag("word").assertDoesNotExist()
                compose.onNodeWithTag("correct").assertIsNotEnabled()
                compose.onNodeWithTag("pass").assertIsNotEnabled()
                val card = compose.onNodeWithTag("playing-card").captureToImage().toPixelMap()
                assertTrue("Feedback must retain the cream face", fractionNear(card, p.cardFace) > .55f)
                assertFeedbackBorder(card, ink)
                assertLiveScore(1)
                onModel { model ->
                    assertNull(model.round?.currentWord)
                    assertEquals(expectedAnswers, model.round?.answers)
                    assertEquals(outcome, model.round?.feedback)
                    assertTrue(model.history.isEmpty())
                }
                assertEquals(expectedAnswers.map { it.word }.toSet(), seenWords("wild-world"))
            }
            // Stale callbacks must remain harmless throughout the hidden-card window.
            compose.runOnUiThread { click(); otherClick() }
            onModel { assertEquals(expectedAnswers, it.round?.answers) }
            inspectionTime += 700L
            onModel { model -> checkNotNull(model.round).tick(inspectionTime); model.tick() }
            word = awaitWord(excluding = expectedAnswers.map { it.word }.toSet())
        }
        assertLiveScore(1)
        finishThroughPause()
        assertSingleSavedRound("Wild World", 60, expectedAnswers + Answer(word, Outcome.UNANSWERED))
    }

    @Test
    fun compactNativeCardFitsLongWordsAtDoubleFontScale_withoutCoveringTimerScoreOrCategoryMarks() {
        fontScale = 2f
        val words = categories.associate { category -> category.id to longestWord(category.id) }
        val shown = mutableListOf<String>()
        installIsolatedContent {
            val p = LocalPress.current
            PageBackground(Modifier.size(400.dp, 180.dp).testTag("compact-stage")) {
                PlayingCard(selected.id, Modifier.fillMaxSize().testTag("compact-card"),
                    topEnd = { Text("120s", Modifier.testTag("sample-timer"), color = p.cardInk,
                        fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 24.sp) },
                    bottomStart = {
                        Row(Modifier.testTag("sample-score"), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Sharp.Check, contentDescription = null, tint = p.correct, modifier = Modifier.size(24.dp))
                            Text("7", Modifier.testTag("sample-score-text"), color = p.correct,
                                fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                        }
                    }) {
                    AutoWord(words.getValue(selected.id), p.cardInk) { shown += it }
                }
            }
        }
        categories.forEach { category ->
            compose.runOnIdle {
                // The first category was laid out during installation. Do not erase
                // its acknowledgement and expect unchanged state to cause a new layout.
                if (selected != category) { shown.clear(); selected = category }
            }
            val stage = compose.onNodeWithTag("compact-stage").fetchSemanticsNode()
            assertEquals("Actual compact width", 400, stage.size.width)
            assertEquals("Actual compact height", 180, stage.size.height)
            val word = words.getValue(category.id)
            val node = compose.onNodeWithTag("word").assertIsDisplayed().assertTextEquals(word.uppercase())
            val layout = readLayout(node)
            assertEquals(2f, layout.layoutInput.density.fontScale, 0f)
            assertEquals(Day.cardInk, layout.layoutInput.style.color)
            assertFalse("Entire long clue must fit: $word", layout.hasVisualOverflow)
            assertTrue(layout.lineCount in 1..3)
            assertEquals(word.uppercase().length, layout.getLineEnd(layout.lineCount - 1, visibleEnd = true))
            val scoreLayout = assertTextFitsActualNode(compose.onNodeWithTag("sample-score-text"))
            assertEquals("Live score must remain a single line", 1, scoreLayout.lineCount)
            val reserved = textLineBounds(compose.onNodeWithTag("sample-timer")) +
                listOf(compose.onNodeWithTag("sample-score").assertIsDisplayed().fetchSemanticsNode().boundsInRoot) +
                compose.onAllNodesWithTag("category-${category.id}", useUnmergedTree = true)
                    .assertCountEquals(2).fetchSemanticsNodes().map { it.boundsInRoot }
            val wordLines = textLineBounds(node)
            wordLines.forEach { line ->
                assertTrue("Clue must stay inside the actual card", contains(stage.boundsInRoot, line))
                reserved.forEach { corner ->
                    assertFalse("$word at double font size overlaps a timer, score or category mark: $line / $corner",
                        line.overlaps(corner))
                }
            }
            compose.runOnIdle {
                assertTrue("Only a fitted visible word may be acknowledged", shown.isNotEmpty())
                assertTrue("No stale deck word may be acknowledged", shown.all { it == word })
            }
        }
    }

    private fun installIsolatedContent(content: @Composable () -> Unit) {
        // Replace this test activity's content, disposing GameApp's sensor/clock effects.
        // Do not call compose.setContent over MainActivity's already-installed content.
        compose.runOnUiThread {
            compose.activity.setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                    PressTheme(mode, content)
                }
            }
        }
    }

    private fun assertVectorPainter(node: SemanticsNodeInteraction) {
        node.assertIsDisplayed().assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Text))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription))
        val layout = node.fetchSemanticsNode().layoutInfo
        compose.runOnIdle {
            assertTrue("Category must really paint a vector, not merely expose its tag", layout.getModifierInfo().any { info ->
                (info.modifier as? InspectableValue)?.inspectableElements?.any {
                    it.name == "painter" && it.value is VectorPainter
                } == true
            })
        }
    }

    private fun assertSamePrintedVector(actual: PixelMap, expected: PixelMap, ink: Color, stock: Color, mirrored: Boolean, label: String) {
        // Padding can belong to the semantics node; the actual 30px paint area is centered.
        assertTrue("$label paint area must not be clipped", actual.width >= expected.width && actual.height >= expected.height)
        val xOffset = (actual.width - expected.width) / 2
        val yOffset = (actual.height - expected.height) / 2
        var actualInk = 0
        var expectedInk = 0
        var sharedInk = 0
        for (y in 0 until expected.height) for (x in 0 until expected.width) {
            val a = actual[x + xOffset, y + yOffset]
            val e = expected[if (mirrored) expected.width - 1 - x else x, if (mirrored) expected.height - 1 - y else y]
            val aPrinted = distanceSquared(a, ink) < distanceSquared(a, stock)
            val ePrinted = distanceSquared(e, ink) < distanceSquared(e, stock)
            if (aPrinted) actualInk++
            if (ePrinted) expectedInk++
            if (aPrinted && ePrinted) sharedInk++
        }
        assertTrue("$label reference must contain visible artwork", expectedInk > 20)
        assertTrue("$label must paint the original shape, not a blank, suit or different category",
            2f * sharedInk / (actualInk + expectedInk) >= .85f)
        assertTrue("$label must use its printed ink color", fractionNear(actual, ink) > .025f)
    }

    private fun assertExposedPageIsFlat(pixels: PixelMap, paper: Color) {
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
            if (x < 8 || y < 8 || x >= pixels.width - 8 || y >= pixels.height - 8) {
                assertTrue("No poker-table outline, gradient or hatch on exposed page at $x,$y",
                    near(pixels[x, y], paper))
            }
        }
    }

    private fun assertFeedbackBorder(pixels: PixelMap, ink: Color) {
        // Inspect a long straight part of the LEFT border, away from category icons,
        // central feedback text and the red back on the right. A red back alone cannot pass.
        val middle = (pixels.height * .4f).toInt()..(pixels.height * .6f).toInt()
        val matchingRows = middle.count { y ->
            (0 until (pixels.width * .12f).toInt()).any { x -> near(pixels[x, y], ink) }
        }
        assertTrue("The printed card border must change with feedback", matchingRows >= middle.count() * .9f)
    }

    private fun assertNoSuitOrGlyphText() {
        val glyphs = Regex("[✓↷↑↓→←‹›]|[\\x{1F000}-\\x{1FAFF}\\x{2600}-\\x{27BF}\\x{FE0F}\\x{20E3}]")
        compose.onAllNodes(SemanticsMatcher("No emoji or suit text") { node ->
            node.config.contains(SemanticsProperties.Text) && node.config[SemanticsProperties.Text].any { glyphs.containsMatchIn(it.text) }
        }, useUnmergedTree = true).assertCountEquals(0)
    }

    private fun readLayout(node: SemanticsNodeInteraction): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { assertTrue(it(layouts)) }
        assertEquals("Exactly one actual text layout", 1, layouts.size)
        return layouts.single()
    }

    private fun assertTextFitsActualNode(node: SemanticsNodeInteraction): TextLayoutResult {
        val actualNode = node.assertIsDisplayed().fetchSemanticsNode()
        val layout = readLayout(node)
        val input = layout.layoutInput
        val fullText = input.text.text
        val lines = (0 until layout.lineCount).map { line ->
            Rect(layout.getLineLeft(line), layout.getLineTop(line),
                layout.getLineRight(line), layout.getLineBottom(line))
        }
        val details = "text=${fullText.replace("\n", "\\n")}; nodeSize=${actualNode.size}; " +
            "nodeBounds=${actualNode.boundsInRoot}; layoutSize=${layout.size}; constraints=${input.constraints}; " +
            "multiParagraph=${layout.multiParagraph.width}x${layout.multiParagraph.height}; " +
            "lineCount=${layout.lineCount}; maxLines=${input.maxLines}; " +
            "didExceedMaxLines=${layout.multiParagraph.didExceedMaxLines}; " +
            "density=${input.density}; style=${input.style}; lineBounds=$lines"

        // Compose 1.7's plain-string semantics rebuilds MultiParagraph with the
        // original maximum width, but retains the smaller drawn layout size.
        // didOverflowWidth/hasVisualOverflow can therefore be false alarms for
        // these start-aligned labels. Check line extents against the actual node,
        // not the reconstructed paragraph width. AutoWord's full-layout overflow
        // assertion above stays strict and its collision rectangles stay exact.
        val rasterSlack = 1f // One physical pixel for line-metric/raster rounding only.
        assertTrue("Text must have visible lines within maxLines: $details", layout.lineCount in 1..input.maxLines)
        assertFalse("Text must not exceed maxLines: $details", layout.multiParagraph.didExceedMaxLines)
        assertEquals("Reported text layout size must match the actual node: $details", actualNode.size, layout.size)
        assertTrue("Full text height must fit the actual node: $details",
            layout.multiParagraph.height <= actualNode.size.height + rasterSlack)
        lines.forEachIndexed { line, bounds ->
            assertFalse("Line $line must not hide text behind an ellipsis: $details", layout.isLineEllipsized(line))
            assertTrue("Line $line must fit the actual text node: $details",
                bounds.left >= -rasterSlack && bounds.top >= -rasterSlack &&
                    bounds.right <= actualNode.size.width + rasterSlack &&
                    bounds.bottom <= actualNode.size.height + rasterSlack)
        }
        assertEquals("The last character must remain visible: $details", fullText.length,
            layout.getLineEnd(layout.lineCount - 1, visibleEnd = true))
        return layout
    }

    private fun textLineBounds(node: SemanticsNodeInteraction): List<Rect> {
        val layout = assertTextFitsActualNode(node)
        val origin = node.fetchSemanticsNode().boundsInRoot.topLeft
        return (0 until layout.lineCount).map { line ->
            Rect(origin.x + layout.getLineLeft(line), origin.y + layout.getLineTop(line),
                origin.x + layout.getLineRight(line), origin.y + layout.getLineBottom(line))
        }
    }

    private fun longestWord(id: String): String = InstrumentationRegistry.getInstrumentation().targetContext.assets
        .open("decks/$id.txt").bufferedReader().useLines { lines ->
            lines.map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.maxBy { it.length }
        }

    private fun contains(outer: Rect, inner: Rect): Boolean =
        inner.left >= outer.left && inner.top >= outer.top && inner.right <= outer.right && inner.bottom <= outer.bottom

    private fun fractionNear(pixels: PixelMap, color: Color): Float {
        var matched = 0
        // A two-pixel grid bounds image work even on a high-density emulator.
        var count = 0
        for (y in 0 until pixels.height step 2) for (x in 0 until pixels.width step 2) {
            count++
            if (near(pixels[x, y], color)) matched++
        }
        return matched.toFloat() / count
    }

    private fun near(a: Color, b: Color): Boolean = a.alpha >= .99f && distanceSquared(a, b) <= .0004f

    private fun distanceSquared(a: Color, b: Color): Float =
        (a.red - b.red) * (a.red - b.red) + (a.green - b.green) * (a.green - b.green) + (a.blue - b.blue) * (a.blue - b.blue)
}