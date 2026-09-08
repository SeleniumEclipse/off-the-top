package com.nicgames.offthetop

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class DecksTest {
    private data class Entry(val file: String, val line: Int, val word: String)

    // Android Gradle runs local unit tests from the app module, not the repository root.
    private fun deckFiles(): List<File> {
        val directory = File("src/main/assets/decks")
        assertTrue("Missing bundled decks: ${directory.absolutePath}", directory.isDirectory)
        val files = directory.listFiles()?.filter { it.isFile && it.extension.equals("txt", true) }
            ?.sortedBy { it.name }.orEmpty()
        assertEquals("The offline game must bundle exactly three text decks", 3, files.size)
        return files
    }

    private fun entries(file: File): List<Entry> = file.readLines(Charsets.UTF_8)
        .mapIndexedNotNull { index, raw ->
            val word = raw.trim()
            if (word.isEmpty() || word.startsWith("#")) null else Entry(file.name, index + 1, word)
        }

    private fun duplicates(entries: List<Entry>): String = entries
        .groupBy { it.word.lowercase() }
        .filterValues { it.size > 1 }
        .entries.joinToString("\n") { (word, copies) ->
            "$word: ${copies.joinToString { "${it.file}:${it.line}" }}"
        }

    @Test fun bundlesExactlyThreeTextDecksWithAtLeast500PlayableEntriesEach() {
        for (file in deckFiles()) {
            val count = entries(file).size
            println("${file.name}: $count playable entries")
            assertTrue("${file.name} has $count entries; at least 500 required", count >= 500)
        }
    }

    @Test fun eachDeckHasNoCaseInsensitiveDuplicateEntries() {
        for (file in deckFiles()) {
            val duplicates = duplicates(entries(file))
            assertTrue("Duplicates in ${file.name}:\n$duplicates", duplicates.isEmpty())
        }
    }

    @Test fun noCaseInsensitiveDuplicateEntriesAcrossAnyDecks() {
        val duplicates = duplicates(deckFiles().flatMap(::entries))
        assertTrue("Shared or repeated words across bundled decks:\n$duplicates", duplicates.isEmpty())
    }

    @Test fun deckEntriesAreCleanUtf8SingleLineCardsNotCommentsOrControlCharacters() {
        for (file in deckFiles()) {
            file.readLines(Charsets.UTF_8).forEachIndexed { index, raw ->
                val trimmed = raw.trim()
                if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                    val location = "${file.name}:${index + 1}"
                    assertEquals("Surrounding whitespace at $location", trimmed, raw)
                    assertFalse("Invalid UTF-8, byte-order mark, or control character at $location",
                        raw.any { it == '\uFFFD' || it == '\uFEFF' || it.isISOControl() })
                }
            }
        }
    }

    @Test fun everyBundledDeckCanBePlayedOfflineToExhaustionWithoutRepeats() {
        for (file in deckFiles()) {
            val words = entries(file).map { it.word }
            val engine = RoundEngine(words, Int.MAX_VALUE)
            engine.begin(0L)
            engine.tick(3_000L)
            words.forEachIndexed { index, word ->
                val now = 3_000L + index * 650L
                engine.tick(now)
                assertEquals("${file.name} card ${index + 1}", word, engine.currentWord)
                assertTrue("Unable to play ${file.name} card ${index + 1}",
                    engine.mark(Outcome.CORRECT, now))
            }
            assertEquals(Phase.FINISHED, engine.phase)
            assertEquals(words, engine.answers.map { it.word })
            assertEquals(words.size, engine.score)
            assertNull(engine.currentWord)
            assertFalse(engine.mark(Outcome.CORRECT, Long.MAX_VALUE))
        }
    }
}