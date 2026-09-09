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
        for ((mode, p) in listOf("Day" to Day, "Night" to Night)) {
            for ((label, outcome) in listOf("Correct" to p.correct, "Pass" to p.pass)) {
                assertTrue("$mode $label text on a cream card must meet normal-text contrast",
                    contrast(outcome, p.cardFace) >= 4.5f)
                assertTrue("$mode $label button label must meet normal-text contrast",
                    contrast(p.onOutcome, outcome) >= 4.5f)
            }
            assertTrue("$mode correct must stay green", p.correct.green > p.correct.red && p.correct.green > p.correct.blue)
            assertTrue("$mode pass must stay red", p.pass.red > p.pass.green && p.pass.red > p.pass.blue)
            assertTrue(contrast(p.accent, p.onAccent) >= 4.5f)
            assertTrue(contrast(p.ink, p.paper) >= 4.5f)
        }
    }

    @Test fun CreamCardTextCategoryMarksAndGreenPanelsRetainContrast() {
        for (p in listOf(Day, Night)) {
            assertTrue("Clues and titles must be readable on cream", contrast(p.cardInk, p.cardFace) >= 4.5f)
            assertTrue("Small card counts must be readable on cream", contrast(p.cardMuted, p.cardFace) >= 4.5f)
            assertTrue("Cream cards must remain distinct from green panels", contrast(p.cardFace, p.card) >= 4.5f)
            assertTrue(contrast(p.accent, p.wash) >= 3f)
            assertTrue(contrast(p.ink, p.wash) >= 4.5f)
        }
    }
}