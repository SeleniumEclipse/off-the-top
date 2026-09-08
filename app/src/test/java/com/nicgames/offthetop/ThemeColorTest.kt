package com.nicgames.offthetop

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeColorTest {
    private fun contrast(a: Color, b: Color): Float {
        val first = a.luminance()
        val second = b.luminance()
        return (maxOf(first, second) + .05f) / (minOf(first, second) + .05f)
    }

    @Test fun bodyAndMutedTextRemainReadableOnBothPaperColors() {
        for (p in listOf(Day, Night)) for (background in listOf(p.paper, p.card)) {
            assertTrue(contrast(p.ink, background) >= 4.5f)
            assertTrue(contrast(p.muted, background) >= 4.5f)
            assertTrue(contrast(p.accent, background) >= 4.5f)
        }
    }

    @Test fun CorrectAndPassFeedbackHaveReadableContrastingText() {
        for (p in listOf(Day, Night)) {
            assertTrue(contrast(p.accent, p.onAccent) >= 4.5f)
            assertTrue(contrast(p.ink, p.paper) >= 4.5f)
        }
    }

    @Test fun TintedDeckIconsAndPanelsRetainContrast() {
        for (p in listOf(Day, Night)) {
            assertTrue(contrast(p.accent, p.wash) >= 3f)
            assertTrue(contrast(p.ink, p.wash) >= 4.5f)
        }
    }
}