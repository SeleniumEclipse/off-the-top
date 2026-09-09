package com.nicgames.offthetop

import android.content.Context
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Locale
import java.util.UUID
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android preferences, a unique file per test, and no activity, sensor or clock. */
@RunWith(AndroidJUnit4::class)
class SharedSeenCardsTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var name: String
    private lateinit var prefs: SharedPreferences

    @Before fun createIsolatedPreferences() {
        name = "shared-seen-test-${UUID.randomUUID()}"
        prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        assertTrue(prefs.all.isEmpty())
        assertEquals("Persisted migration key is a compatibility contract", "seen-shared-v1", SeenCards.KEY)
    }

    @After fun removeIsolatedPreferences() {
        if (::prefs.isInitialized) assertTrue(prefs.edit().clear().commit())
        if (::name.isInitialized) context.deleteSharedPreferences(name)
    }

    @Test fun firstInitializationUnionsAllLegacyStringSets_withTrimAndLocaleIndependentCase() {
        val legacy = mapOf(
            "seen-wild-world" to setOf("  IGUANA  ", "Apple"),
            "seen-everyday" to setOf(" APPLE ", " Iced Tea "),
            "seen-do-your-thing" to setOf("JUGGLING"),
            "seen-retired-deck" to setOf("  Bicycle "),
        )
        val editor = prefs.edit().putString("seen-not-a-set", "not a card")
            .putStringSet("unrelated-set", setOf("Do not migrate me"))
        legacy.forEach { (key, words) -> editor.putStringSet(key, words) }
        assertTrue(editor.commit())
        assertFalse(prefs.contains(SeenCards.KEY))
        val previousLocale = Locale.getDefault()
        try {
            // Default-locale lowercase would incorrectly turn I into dotless ı here.
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            val seen = SeenCards(prefs)
            assertEquals(setOf("iguana", "apple", "iced tea", "juggling", "bicycle"), stored())
            assertEquals(listOf("Unplayed"), seen.unseen(listOf("Iguana", " APPLE", "ICED TEA ", "Juggling", "Bicycle", "Unplayed")))
            assertEquals("iced tea", SeenCards.identity("  ICED TEA "))
        } finally {
            Locale.setDefault(previousLocale)
        }
        assertTrue(prefs.contains(SeenCards.KEY))
        legacy.forEach { (key, words) -> assertEquals("Migration must leave $key intact", words, prefs.getStringSet(key, null)) }
        assertEquals("not a card", prefs.getString("seen-not-a-set", null))
        assertEquals(setOf("Do not migrate me"), prefs.getStringSet("unrelated-set", null))
    }

    @Test fun existingGlobalMemoryIsAuthoritative_andResetStaysEmptyAfterRecreation() {
        assertTrue(prefs.edit().putStringSet(SeenCards.KEY, setOf("pear"))
            .putStringSet("seen-everyday", setOf("Apple")).putString("history", "kept").commit())
        val seen = SeenCards(prefs)
        assertEquals(listOf("Apple"), seen.unseen(listOf("Apple", "PEAR")))
        seen.remember("  Iced Tea ")
        seen.remember("ICED TEA")
        assertEquals(setOf("pear", "iced tea"), stored())
        assertEquals(listOf("Apple"), SeenCards(prefs).unseen(listOf("Apple", "Pear", "Iced Tea")))
        assertEquals(setOf("Apple"), prefs.getStringSet("seen-everyday", null))
        assertEquals(setOf("seen-everyday", SeenCards.KEY), prefs.all.keys.filter { it.startsWith("seen-") }.toSet())

        seen.reset()
        assertTrue("An empty global key must remain present to block migration", prefs.contains(SeenCards.KEY))
        assertTrue(stored().isEmpty())
        assertTrue(prefs.edit().putStringSet("seen-late-legacy", setOf("Banana")).commit())
        val recreated = SeenCards(context.getSharedPreferences(name, Context.MODE_PRIVATE))
        val words = listOf("Apple", "Pear", "Iced Tea", "Banana")
        assertEquals(words, recreated.unseen(words))
        assertTrue(stored().isEmpty())
        assertEquals("kept", prefs.getString("history", null))
        assertEquals(setOf("Apple"), prefs.getStringSet("seen-everyday", null))
    }

    @Test fun sharedTitleIsExcludedAcrossDecks_andRestartRemovesOnlySelectedDeckIdentities() {
        val source = listOf("Shared Card", "Source Only")
        val themed = listOf(" Shared Card ", "Themed Only")
        val seen = SeenCards(prefs)
        seen.remember("SHARED CARD")
        assertEquals(listOf("Source Only"), seen.unseen(source))
        assertEquals(listOf("Themed Only"), seen.unseen(themed))
        // Similar phrasing is not the same exact title.
        assertEquals(listOf("Shared Cards"), seen.unseen(listOf("Shared Cards")))
        seen.remember("Source Only")
        seen.remember("Themed Only")
        seen.remember("Unrelated Character")
        assertTrue(seen.unseen(source).isEmpty())
        assertTrue(seen.unseen(themed).isEmpty())

        seen.restart(source)
        assertEquals(setOf("themed only", "unrelated character"), stored())
        assertEquals(source, seen.unseen(source))
        assertEquals(listOf(" Shared Card "), seen.unseen(themed))
        val recreated = SeenCards(prefs)
        assertEquals(listOf(" Shared Card "), recreated.unseen(themed))
        assertTrue(recreated.unseen(listOf("Unrelated Character")).isEmpty())
        recreated.remember("shared card")
        assertEquals(listOf("Source Only"), recreated.unseen(source))
        assertTrue(recreated.unseen(themed).isEmpty())
        assertEquals(setOf("shared card", "themed only", "unrelated character"), stored())
    }

    @Test fun rememberRestartAndResetNeverMutateTheStringSetReturnedByPreferences() {
        val legacy = setOf(" Legacy Word ")
        assertTrue(prefs.edit().putStringSet("seen-old", legacy)
            .putStringSet(SeenCards.KEY, setOf("shared", "outside")).commit())
        val seen = SeenCards(prefs)
        val beforeRemember = checkNotNull(prefs.getStringSet(SeenCards.KEY, null))
        seen.remember(" New Word ")
        assertEquals("remember must copy the borrowed set", setOf("shared", "outside"), beforeRemember)
        assertEquals(setOf("shared", "outside", "new word"), stored())

        val beforeRestart = checkNotNull(prefs.getStringSet(SeenCards.KEY, null))
        val input = listOf(" SHARED ", "New Word")
        seen.restart(input)
        assertEquals("restart must copy the borrowed set", setOf("shared", "outside", "new word"), beforeRestart)
        assertEquals(listOf(" SHARED ", "New Word"), input)
        assertEquals(setOf("outside"), stored())

        val beforeReset = checkNotNull(prefs.getStringSet(SeenCards.KEY, null))
        seen.reset()
        assertEquals("reset must not clear the borrowed set", setOf("outside"), beforeReset)
        assertTrue(stored().isEmpty())
        assertEquals(legacy, prefs.getStringSet("seen-old", null))
    }

    private fun stored(): Set<String> = checkNotNull(prefs.getStringSet(SeenCards.KEY, null)).toSet()
}