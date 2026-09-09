package com.nicgames.offthetop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Isolated rendered pixels: no activity model, sensors, game clock, or preferences. */
@RunWith(AndroidJUnit4::class)
class BackgroundAppearanceTest {
    @get:Rule
    val compose = createComposeRule()

    private val corners = listOf(0f to 0f, 1f to 0f, 0f to 1f, 1f to 1f)
    private val grid = listOf(.1f, .5f, .9f).flatMap { x ->
        listOf(.1f, .9f).map { y -> x to y }
    }
    private val samples = corners + grid
    private val tolerance = .01f // Allows 8-bit capture rounding, not a different tint.

    @Test
    fun dayBackdropIsUniformOpaqueStaticPaperWithReadableContrast() {
        assertFlatStaticBackdrop("Day", Day)
    }

    @Test
    fun nightBackdropIsUniformOpaqueStaticPaperWithReadableContrast() {
        assertFlatStaticBackdrop("Night", Night)
    }

    @Test
    fun solidNightCardFaceIsOpaqueCreamAndDistinctFromSolidGreenPage() {
        renderBackdrop("Night", withCard = true)
        val backdrop = captureBackdrop()
        val card = compose.onNodeWithTag("solid-card").captureToImage().toPixelMap()
        assertUniformSurface("Night card face", card, Night.cardFace)
        samples.forEach { point ->
            // All these samples are outside the centered card.
            assertColorNear("Exposed Night paper at $point", Night.paper, backdrop.at(point))
            assertEquals("Exposed page must be flat at $point", backdrop[0, 0], backdrop.at(point))
        }
        assertColorNear("Cream face covers the page center", Night.cardFace, backdrop.at(.5f to .5f))
        assertTrue("Card must be distinct from the exposed backdrop",
            colorDistance(card.at(.5f to .5f), backdrop.at(0f to 0f)) > .5f)
        listOf(Night.cardInk, Night.cardMuted, Night.correct, Night.pass).forEach { foreground ->
            assertTrue("Printed text must contrast with the actual cream face",
                contrastRatio(foreground, card.at(.5f to .5f)) >= 4.5f)
        }
    }

    private fun assertFlatStaticBackdrop(mode: String, palette: PressColors) {
        // Freeze before rendering; a reintroduced animation must not prevent idling.
        // This isolated frame clock never advances a round or changes game state.
        compose.mainClock.autoAdvance = false
        renderBackdrop(mode)
        compose.mainClock.advanceTimeByFrame()
        val before = captureBackdrop()
        assertUniformSurface("$mode paper", before, palette.paper)
        samples.forEach { point ->
            listOf("ink" to palette.ink, "muted" to palette.muted, "accent" to palette.accent)
                .forEach { (name, foreground) ->
                    val contrast = contrastRatio(foreground, before.at(point))
                    assertTrue("$mode $name contrast at $point was $contrast, must be at least 4.5",
                        contrast >= 4.5f)
                }
        }

        // Check several frames, not only the end of a possible one-second cycle.
        for (elapsed in listOf(250L, 750L, 1000L)) {
            compose.mainClock.advanceTimeBy(elapsed)
            val after = captureBackdrop()
            assertEquals("Backdrop width must stay fixed", before.width, after.width)
            assertEquals("Backdrop height must stay fixed", before.height, after.height)
            for (y in 0 until before.height) for (x in 0 until before.width) {
                if (before[x, y] != after[x, y]) {
                    assertEquals("$mode backdrop animated at ($x, $y)", before[x, y], after[x, y])
                }
            }
        }
    }

    private fun assertUniformSurface(label: String, pixels: PixelMap, expected: Color) {
        assertColorNear(label, expected, pixels[0, 0])
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
            if (pixels[x, y] != pixels[0, 0]) {
                assertEquals("$label must be uniform at ($x, $y)", pixels[0, 0], pixels[x, y])
            }
        }
    }

    private fun renderBackdrop(mode: String, withCard: Boolean = false) {
        compose.setContent {
            PressTheme(mode) {
                PageBackground(Modifier.size(300.dp, 180.dp).testTag("backdrop")) {
                    // Empty in the paper tests: no text or other UI can pollute samples.
                    if (withCard) {
                        Box(Modifier.align(Alignment.Center).size(120.dp, 80.dp)
                            .background(LocalPress.current.cardFace).testTag("solid-card"))
                    }
                }
            }
        }
    }

    private fun captureBackdrop(): PixelMap =
        compose.onNodeWithTag("backdrop").captureToImage().toPixelMap()

    private fun PixelMap.at(point: Pair<Float, Float>): Color =
        this[((width - 1) * point.first).roundToInt(), ((height - 1) * point.second).roundToInt()]

    private fun assertColorNear(label: String, expected: Color, actual: Color) {
        assertTrue("$label: expected $expected, got $actual", colorDistance(expected, actual) <= tolerance)
        assertEquals("$label must be opaque", 1f, actual.alpha, tolerance)
    }

    private fun colorDistance(a: Color, b: Color): Float {
        val red = a.red - b.red
        val green = a.green - b.green
        val blue = a.blue - b.blue
        return sqrt(red * red + green * green + blue * blue)
    }

    private fun contrastRatio(foreground: Color, background: Color): Float {
        val a = foreground.luminance()
        val b = background.luminance()
        return (max(a, b) + .05f) / (min(a, b) + .05f)
    }
}