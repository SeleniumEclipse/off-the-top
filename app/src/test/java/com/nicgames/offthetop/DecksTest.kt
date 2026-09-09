package com.nicgames.offthetop

import java.io.File
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class DecksTest {
    private data class Entry(val file: String, val line: Int, val word: String)
    private val expectedSizes = mapOf(
        "wild-world" to 622, "everyday" to 617, "do-your-thing" to 605,
        "characters" to 580, "silent-acting" to 534, "food-drink" to 550,
    )
    private val originalIds = setOf("wild-world", "everyday", "do-your-thing")
    private fun identity(word: String) = word.trim().lowercase(Locale.ROOT)

    // Android Gradle runs local unit tests from the app module, not the repository root.
    private fun deckFiles(): List<File> {
        val directory = File("src/main/assets/decks")
        assertTrue("Missing bundled decks: ${directory.absolutePath}", directory.isDirectory)
        val files = directory.listFiles()?.filter { it.isFile && it.extension.equals("txt", true) }
            ?.sortedBy { it.name }.orEmpty()
        assertEquals("The offline game must bundle exactly six text decks", 6, files.size)
        assertEquals("No missing or substitute decks", expectedSizes.keys, files.map { it.nameWithoutExtension }.toSet())
        return files
    }

    private fun entries(file: File): List<Entry> = file.readLines(Charsets.UTF_8)
        .mapIndexedNotNull { index, raw ->
            val word = raw.trim()
            if (word.isEmpty() || word.startsWith("#")) null else Entry(file.name, index + 1, word)
        }

    private fun duplicates(entries: List<Entry>): String = entries
        .groupBy { identity(it.word) }
        .filterValues { it.size > 1 }
        .entries.joinToString("\n") { (word, copies) ->
            "$word: ${copies.joinToString { "${it.file}:${it.line}" }}"
        }

    @Test fun bundlesExactlySixTextDecksWithAtLeast500PlayableEntriesEach() {
        for (file in deckFiles()) {
            val count = entries(file).size
            println("${file.name}: $count playable entries")
            assertTrue("${file.name} has $count entries; at least 500 required", count >= 500)
            assertEquals("Approved entry count for ${file.name}", expectedSizes.getValue(file.nameWithoutExtension), count)
        }
    }

    @Test fun eachDeckHasNoCaseInsensitiveDuplicateEntries() {
        for (file in deckFiles()) {
            val duplicates = duplicates(entries(file))
            assertTrue("Duplicates in ${file.name}:\n$duplicates", duplicates.isEmpty())
        }
    }

    @Test fun onlyTheThreeApprovedSharedPairsOverlap_withExactCountsAndOriginalSpelling() {
        val decks = deckFiles().associate { it.nameWithoutExtension to entries(it).map { entry -> entry.word } }
        val allowed = mapOf(
            setOf("silent-acting", "do-your-thing") to 394,
            setOf("food-drink", "everyday") to 115,
            setOf("food-drink", "wild-world") to 66,
        )
        val ids = decks.keys.toList()
        ids.forEachIndexed { index, left ->
            ids.drop(index + 1).forEach { right ->
                val shared = decks.getValue(left).map(::identity).toSet()
                    .intersect(decks.getValue(right).map(::identity).toSet())
                assertEquals("Unexpected shared cards: $left / $right: $shared",
                    allowed[setOf(left, right)] ?: 0, shared.size)
                assertEquals("Approved reuse must retain the exact original titles: $left / $right",
                    shared.size, decks.getValue(left).toSet().intersect(decks.getValue(right).toSet()).size)
            }
        }
    }

    @Test fun originalThreeDecksRemainMutuallyDistinct() {
        val originals = deckFiles().filter { it.nameWithoutExtension in originalIds }.flatMap(::entries)
        assertEquals("Original catalog size must not change", 1844, originals.size)
        assertTrue("Original decks must remain distinct:\n${duplicates(originals)}", duplicates(originals).isEmpty())
    }

    @Test fun charactersShareNoTitlesWithAnyOtherDeck() {
        val files = deckFiles()
        val characters = entries(files.single { it.nameWithoutExtension == "characters" }).map { identity(it.word) }.toSet()
        val others = files.filterNot { it.nameWithoutExtension == "characters" }.flatMap(::entries).map { identity(it.word) }.toSet()
        assertTrue("Characters must be wholly new: ${characters.intersect(others)}", characters.intersect(others).isEmpty())
    }

    @Test fun expansionAdds1089UniqueCardsAcross3508EntriesAnd2933Identities() {
        val files = deckFiles()
        val all = files.flatMap(::entries)
        val originals = files.filter { it.nameWithoutExtension in originalIds }.flatMap(::entries).map { identity(it.word) }.toSet()
        val expansion = files.filterNot { it.nameWithoutExtension in originalIds }.flatMap(::entries).map { identity(it.word) }.toSet()
        assertEquals("All deck entries, including intentional reuse", 3508, all.size)
        assertEquals("Total distinct playable titles", 2933, all.map { identity(it.word) }.toSet().size)
        assertEquals("Genuinely new titles, not reused original cards", 1089, (expansion - originals).size)
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