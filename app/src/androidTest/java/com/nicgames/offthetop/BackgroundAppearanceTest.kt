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
    fun dayBackdropIsUniformPaperWithoutAccentTint() {
        renderBackdrop("Day")
        val pixels = captureBackdrop()
        samples.forEach { point ->
            val actual = pixels.at(point)
            assertColorNear("Day paper at $point", Day.paper, actual)
            assertEquals("Day must be flat at $point", pixels[0, 0], actual)
        }
    }

    @Test
    fun nightBackdropHasVisibleStaticVariationAndReadableContrast() {
        // Freeze frames before rendering so an accidental animation cannot run away
        // during test synchronization. Advancing this clock never touches a game.
        compose.mainClock.autoAdvance = false
        renderBackdrop("Night")
        compose.mainClock.advanceTimeByFrame()
        val before = captureBackdrop()
        assertTrue("Night corners must visibly differ",
            colorDistance(before.at(0f to 0f), before.at(1f to 1f)) > .01f)
        for (y in listOf(.1f, .9f)) {
            assertTrue("Night must vary across the page at y=$y",
                colorDistance(before.at(.1f to y), before.at(.9f to y)) > .01f)
        }
        // Bound the rendered colors by the intended palette, without reconstructing
        // the gradient or its glow. This also catches a default purple/accent tint.
        samples.forEach { point -> assertNightPalette(before.at(point), point) }
        grid.forEach { point ->
            val background = before.at(point)
            listOf("ink" to Night.ink, "muted" to Night.muted, "accent" to Night.accent)
                .forEach { (name, foreground) ->
                    val contrast = contrastRatio(foreground, background)
                    assertTrue("Night $name contrast at $point was $contrast, must exceed 4.5",
                        contrast > 4.5f)
                }
        }

        compose.mainClock.advanceTimeBy(1000)
        val after = captureBackdrop()
        assertEquals("Backdrop width must stay fixed", before.width, after.width)
        assertEquals("Backdrop height must stay fixed", before.height, after.height)
        for (y in 0 until before.height) for (x in 0 until before.width) {
            if (before[x, y] != after[x, y]) {
                assertEquals("Backdrop animated at ($x, $y) after one second", before[x, y], after[x, y])
            }
        }
    }

    @Test
    fun solidCardCoversNightGradientWithOpaqueCardSurface() {
        renderBackdrop("Night", withCard = true)
        val backdrop = captureBackdrop()
        val card = compose.onNodeWithTag("solid-card").captureToImage().toPixelMap()
        samples.forEach { point ->
            assertColorNear("Solid card at $point", Night.card, card.at(point))
        }
        assertColorNear("Card covers the page center", Night.card, backdrop.at(.5f to .5f))
        assertTrue("The exposed backdrop must still have a gradient",
            colorDistance(backdrop.at(0f to 0f), backdrop.at(1f to 1f)) > .01f)
        assertTrue("Card must be distinct from the exposed backdrop",
            colorDistance(card.at(.5f to .5f), backdrop.at(0f to 0f)) > .01f)
    }

    private fun renderBackdrop(mode: String, withCard: Boolean = false) {
        compose.setContent {
            PressTheme(mode) {
                PageBackground(Modifier.size(300.dp, 180.dp).testTag("backdrop")) {
                    // Empty in the paper tests: no text or other UI can pollute samples.
                    if (withCard) {
                        Box(Modifier.align(Alignment.Center).size(120.dp, 80.dp)
                            .background(LocalPress.current.card).testTag("solid-card"))
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

    private fun assertNightPalette(actual: Color, point: Pair<Float, Float>) {
        val palette = listOf(Night.paper, Night.backgroundEnd, Night.backgroundGlow)
        val channels = listOf<(Color) -> Float>({ it.red }, { it.green }, { it.blue })
        channels.forEachIndexed { index, channel ->
            val lower = palette.minOf { channel(it) } - tolerance
            val upper = palette.maxOf { channel(it) } + tolerance
            assertTrue("Unexpected Night tint at $point, channel $index: $actual",
                channel(actual) in lower..upper)
        }
        assertEquals("Night paper must be opaque at $point", 1f, actual.alpha, tolerance)
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