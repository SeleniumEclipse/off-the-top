package com.nicgames.offthetop

import android.content.res.Configuration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsFlowTest : OffTheTopUiTest() {
    @Test
    fun allFourTogglesPersistAcrossRecreation_andCanBeChangedBack() {
        tapText("Settings", scroll = true)
        assertToggleSettings(sound = true, vibration = true, touch = false, gentle = false, saved = false)

        setToggle("Sound effects", false)
        setToggle("Vibration", false)
        setToggle("Touch-only mode", true)
        setToggle("Gentle tilts", true)
        assertToggleSettings(sound = false, vibration = false, touch = true, gentle = true)
        compose.activityRule.scenario.recreate()
        awaitText("Make it yours")
        assertToggleSettings(sound = false, vibration = false, touch = true, gentle = true)

        setToggle("Sound effects", true)
        setToggle("Vibration", true)
        setToggle("Touch-only mode", false)
        setToggle("Gentle tilts", false)
        tapText("‹ Back")
        tapText("Settings", scroll = true)
        compose.activityRule.scenario.recreate()
        awaitText("Make it yours")
        assertToggleSettings(sound = true, vibration = true, touch = false, gentle = false)
        onModel { assertTrue(it.history.isEmpty()) }
    }

    @Test
    fun allRoundLengthsAndPaperThemesPersist_andRenderTheirSelection() {
        tapText("Settings", scroll = true)
        tapText("Day", scroll = true)
        assertPaperTheme("Day", Day.ink)

        for (seconds in listOf(30, 60, 90, 120)) {
            tapText("${seconds}s", scroll = true)
            assertDuration(seconds)
            compose.activityRule.scenario.recreate()
            awaitText("Make it yours")
            assertDuration(seconds)
            assertPaperTheme("Day", Day.ink)
        }

        for (theme in listOf("Night", "Day", "System")) {
            tapText(theme, scroll = true)
            val expectedInk = when (theme) {
                "Night" -> Night.ink
                "Day" -> Day.ink
                else -> compose.runOnUiThread {
                    val nightMode = compose.activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                    if (nightMode == Configuration.UI_MODE_NIGHT_YES) Night.ink else Day.ink
                }
            }
            assertPaperTheme(theme, expectedInk)
            compose.activityRule.scenario.recreate()
            awaitText("Make it yours")
            assertPaperTheme(theme, expectedInk)
            onModel { assertEquals(120, it.seconds) }
        }

        tapText("‹ Back")
        tapText("30s")
        chooseDeck("Everyday Things")
        val deckSize = onModel { it.selected.words.size }
        compose.onNodeWithText("$deckSize UNSEEN · 30 SECOND ROUND").performScrollTo().assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        awaitText("Start round  →")
        compose.onNodeWithText("$deckSize UNSEEN · 30 SECOND ROUND").performScrollTo().assertIsDisplayed()
        withReloadedModel { assertEquals(30, it.seconds) }
        onModel { assertTrue(it.history.isEmpty()) }
    }

    @Test
    fun helpAndEmptyHistoryRemainUsableAfterRecreation() {
        tapText("How to play", scroll = true)
        awaitText("A little help up top")
        compose.onNodeWithText("01 / GATHER ROUND").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("02 / NO PEEKING").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("03 / SAY IT OUT LOUD").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("04 / TILT TO SCORE").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("05 / PASS THE PHONE").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("GOOD TO KNOW").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Leaving the app pauses the timer.", substring = true)
            .performScrollTo().assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        awaitText("A little help up top")
        tapText("‹ Back")
        tapText("Recent rounds  →", scroll = true)
        awaitText("Recent rounds")
        compose.onNodeWithText(EMPTY_HISTORY).assertIsDisplayed()
        compose.onNodeWithText("REVIEW +").assertDoesNotExist()
        compose.activityRule.scenario.recreate()
        awaitText("Recent rounds")
        compose.onNodeWithText(EMPTY_HISTORY).assertIsDisplayed()
        withReloadedModel { assertTrue(it.history.isEmpty()) }
        tapText("‹ Back")
        chooseDeck("Do Your Thing")
        compose.onNodeWithText("Start round  →").assertIsEnabled()
        onModel { assertTrue(it.history.isEmpty()) }
    }

    private fun assertToggleSettings(
        sound: Boolean,
        vibration: Boolean,
        touch: Boolean,
        gentle: Boolean,
        saved: Boolean = true,
    ) {
        assertToggle("Sound effects", sound)
        assertToggle("Vibration", vibration)
        assertToggle("Touch-only mode", touch)
        assertToggle("Gentle tilts", gentle)
        fun check(model: AppModel) {
            assertEquals(sound, model.sound)
            assertEquals(vibration, model.haptics)
            assertEquals(touch, model.touchOnly)
            assertEquals(gentle, model.gentle)
        }
        onModel { check(it) }
        withReloadedModel { check(it) }
        if (saved) {
            val prefs = preferences()
            assertEquals(sound, prefs.getBoolean("sound", !sound))
            assertEquals(vibration, prefs.getBoolean("haptics", !vibration))
            assertEquals(touch, prefs.getBoolean("touch", !touch))
            assertEquals(gentle, prefs.getBoolean("gentle", !gentle))
        }
    }

    private fun assertDuration(seconds: Int) {
        onModel { assertEquals(seconds, it.seconds) }
        assertEquals(seconds, preferences().getInt("seconds", -1))
        withReloadedModel { assertEquals(seconds, it.seconds) }
        // The picker exposes no Selected semantics. Check the actual rendered text
        // colors as well as model/prefs, rather than asserting that every label exists.
        for (value in listOf(30, 60, 90, 120)) {
            compose.onNodeWithText("${value}s").performScrollTo().assertIsDisplayed()
            assertTextColor("${value}s", if (value == seconds) Day.paper else Day.ink)
        }
    }

    private fun assertPaperTheme(theme: String, expectedInk: Color) {
        onModel { assertEquals(theme, it.theme) }
        assertEquals(theme, preferences().getString("theme", null))
        withReloadedModel { assertEquals(theme, it.theme) }
        assertTextColor("Make it yours", expectedInk)
    }

    private fun assertTextColor(text: String, expected: Color) {
        compose.onNodeWithText(text).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { getLayout ->
            val layouts = mutableListOf<TextLayoutResult>()
            assertTrue("Text must expose its actual layout", getLayout(layouts))
            assertEquals("Rendered color of $text", expected, layouts.single().layoutInput.style.color)
        }
    }
}