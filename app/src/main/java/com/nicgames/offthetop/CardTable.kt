package com.nicgames.offthetop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Original category artwork, transferred from the approved SVG study without card suits. */
@Composable internal fun CategoryIcon(deckId: String, modifier: Modifier = Modifier, tint: Color = LocalPress.current.cardInk) {
    val asset = when (deckId) {
        "wild-world" -> R.drawable.category_wild
        "everyday" -> R.drawable.category_everyday
        "do-your-thing" -> R.drawable.category_actions
        else -> error("Unknown category: $deckId")
    }
    Icon(painterResource(asset), contentDescription = null, tint = tint, modifier = modifier.testTag("category-$deckId"))
}

/** Crosshatching belongs only on the red card back, never the table or text surface. */
@Composable internal fun RedCardBack(modifier: Modifier = Modifier) {
    val p = LocalPress.current
    Canvas(modifier.clip(RoundedCornerShape(4.dp)).drawWithCache {
        val inset = 10.dp.toPx()
        val spacing = 11.dp.toPx()
        val hatch = Path()
        var x = -size.height
        while (x < size.width + size.height) {
            hatch.moveTo(x, 0f); hatch.lineTo(x + size.height, size.height)
            hatch.moveTo(x, 0f); hatch.lineTo(x - size.height, size.height)
            x += spacing
        }
        val center = Rect(size.width * .32f, size.height * .33f, size.width * .68f, size.height * .67f)
        onDrawBehind {
            drawRect(p.pass)
            drawPath(hatch, p.cardFace.copy(alpha = .4f), style = Stroke(0.75.dp.toPx()))
            drawRect(p.cardFace, style = Stroke(4.dp.toPx()))
            drawRect(p.cardFace, Offset(inset, inset), Size((size.width - inset * 2).coerceAtLeast(0f), (size.height - inset * 2).coerceAtLeast(0f)), style = Stroke(1.dp.toPx()))
            drawRect(p.pass, center.topLeft, center.size)
            drawRect(p.cardFace, center.topLeft, center.size, style = Stroke(1.5.dp.toPx()))
            repeat(4) { index ->
                val y = center.top + center.height * (.32f + index * .12f)
                drawLine(p.cardFace, Offset(center.left + 8.dp.toPx(), y), Offset(center.right - 8.dp.toPx(), y), 1.5.dp.toPx())
            }
        }
    }) { }
}

/** Stack and printed surface only; intentionally no outer poker-table outline. */
@Composable internal fun CardFace(
    modifier: Modifier = Modifier,
    borderColor: Color = LocalPress.current.cardInk,
    content: @Composable BoxScope.() -> Unit,
) {
    val p = LocalPress.current
    val shape = RoundedCornerShape(4.dp)
    Box(modifier.padding(end = 5.dp, bottom = 5.dp)) {
        Box(Modifier.matchParentSize().offset(5.dp, 5.dp).background(p.shadow, shape))
        Box(Modifier.matchParentSize().offset(3.dp, 3.dp).background(p.cardFace, shape).border(1.dp, p.cardInk, shape))
        Box(Modifier.fillMaxSize().background(p.cardFace, shape).border(2.dp, borderColor, shape), content = content)
    }
}

@Composable internal fun DeckStack(deck: Deck, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPress.current
    BoxWithConstraints(modifier.padding(start = 10.dp, top = 8.dp, end = 6.dp, bottom = 9.dp)
        .clickable(role = Role.Button, onClick = onClick)
        .semantics(mergeDescendants = true) { contentDescription = "Choose ${deck.title}, ${deck.words.size} cards" }
        .testTag("deck-${deck.id}")) {
        val compact = maxHeight < 160.dp
        val availableWidth = maxWidth
        val availableHeight = maxHeight
        RedCardBack(Modifier.matchParentSize().graphicsLayer { rotationZ = -5f; translationX = -5.dp.toPx() })
        CardFace(Modifier.fillMaxSize()) {
            val markSize = if (compact) 24.dp else 30.dp
            val markColor = if (deck.id == "everyday") p.pass else p.cardInk
            CategoryIcon(deck.id, Modifier.align(Alignment.TopStart).padding(10.dp).size(markSize), markColor)
            CategoryIcon(deck.id, Modifier.align(Alignment.BottomEnd).padding(10.dp).size(markSize).graphicsLayer { rotationZ = 180f }, markColor)
            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = if (compact) 32.dp else 42.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                val title = when (deck.id) { "wild-world" -> "WILD\nWORLD"; "everyday" -> "EVERYDAY\nTHINGS"; else -> "DO YOUR\nTHING" }
                var fontSize by remember(deck.id, availableWidth, availableHeight) { mutableStateOf(if (compact) 21 else 27) }
                Text(title, fontFamily = Headline, fontSize = fontSize.sp, lineHeight = (fontSize * 1.08f).sp,
                    textAlign = TextAlign.Center, color = p.cardInk, maxLines = 2,
                    onTextLayout = { if (it.hasVisualOverflow && fontSize > 12) fontSize -= 1 })
                Spacer(Modifier.height(if (compact) 5.dp else 12.dp))
                Text("${deck.words.size} cards", fontFamily = Body, fontSize = 13.sp, color = p.cardMuted)
            }
        }
    }
}

@Composable internal fun PlayingCard(
    deckId: String,
    modifier: Modifier = Modifier,
    feedback: Outcome? = null,
    topEnd: @Composable () -> Unit = {},
    bottomStart: @Composable () -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    val p = LocalPress.current
    val borderColor = when (feedback) { Outcome.CORRECT -> p.correct; Outcome.PASS -> p.pass; else -> p.cardInk }
    BoxWithConstraints(modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
        val small = maxHeight < 170.dp
        RedCardBack(Modifier.align(Alignment.CenterEnd).fillMaxHeight(.90f).width(135.dp).graphicsLayer { rotationZ = 5f; translationX = 5.dp.toPx() })
        CardFace(Modifier.fillMaxSize().padding(end = 8.dp), borderColor) {
            Box(Modifier.matchParentSize().padding(9.dp).border(if (feedback == null) 1.dp else 3.dp,
                if (feedback == null) p.cardInk.copy(alpha = .2f) else borderColor, RoundedCornerShape(2.dp)))
            // Measure the corners first so large system fonts cannot collide with the clue.
            SubcomposeLayout(Modifier.fillMaxSize()) { constraints ->
                val width = constraints.maxWidth
                val height = constraints.maxHeight
                val inset = 14.dp.roundToPx()
                val gutter = 8.dp.roundToPx()
                val cornerBounds = constraints.copy(minWidth = 0, minHeight = 0,
                    maxWidth = (width - inset * 2).coerceAtLeast(0), maxHeight = (height - inset * 2).coerceAtLeast(0))
                val topMark = subcompose("top-mark") { CategoryIcon(deckId, Modifier.size(if (small) 25.dp else 32.dp), borderColor) }.single().measure(cornerBounds)
                val bottomMark = subcompose("bottom-mark") { CategoryIcon(deckId, Modifier.size(if (small) 25.dp else 32.dp).graphicsLayer { rotationZ = 180f }, borderColor) }.single().measure(cornerBounds)
                val timer = subcompose("timer") { Box { topEnd() } }.single().measure(cornerBounds)
                val score = subcompose("score") { Box { bottomStart() } }.single().measure(cornerBounds)
                val topClearance = inset + maxOf(topMark.height, timer.height) + gutter
                val bottomClearance = inset + maxOf(bottomMark.height, score.height) + gutter
                val sideClearance = (if (small) 52.dp else 65.dp).roundToPx()
                val clueWidth = (width - sideClearance * 2).coerceAtLeast(0)
                val clueHeight = (height - topClearance - bottomClearance).coerceAtLeast(0)
                val clue = subcompose("clue") {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center, content = content)
                }.single().measure(androidx.compose.ui.unit.Constraints.fixed(clueWidth, clueHeight))
                layout(width, height) {
                    topMark.placeRelative(inset, inset)
                    timer.placeRelative(width - inset - timer.width, inset)
                    score.placeRelative(inset, height - inset - score.height)
                    bottomMark.placeRelative(width - inset - bottomMark.width, height - inset - bottomMark.height)
                    clue.placeRelative(sideClearance, topClearance)
                }
            }
        }
    }
}