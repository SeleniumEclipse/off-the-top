package com.nicgames.offthetop

import android.content.SharedPreferences
import java.util.Locale

/** Shared topics use one memory across themed decks; legacy deck history is preserved. */
internal class SeenCards(private val prefs: SharedPreferences) {
    init {
        if (!prefs.contains(KEY)) {
            val legacy = prefs.all.filterKeys { it.startsWith("seen-") }.values
                .flatMap { (it as? Set<*>)?.filterIsInstance<String>().orEmpty() }
                .map(::identity).toSet()
            prefs.edit().putStringSet(KEY, legacy).apply()
        }
    }

    fun unseen(words: List<String>): List<String> {
        val seen = prefs.getStringSet(KEY, emptySet()).orEmpty()
        return words.filterNot { identity(it) in seen }
    }

    fun remember(word: String) {
        val seen = prefs.getStringSet(KEY, emptySet()).orEmpty().toMutableSet()
        if (seen.add(identity(word))) prefs.edit().putStringSet(KEY, seen).apply()
    }

    /** Start another cycle only for an exhausted deck; keep all unrelated memories. */
    fun restart(words: List<String>) {
        val next = prefs.getStringSet(KEY, emptySet()).orEmpty() - words.map(::identity).toSet()
        prefs.edit().putStringSet(KEY, next).apply()
    }

    fun reset() { prefs.edit().putStringSet(KEY, emptySet()).apply() }

    companion object {
        const val KEY = "seen-shared-v1"
        fun identity(word: String): String = word.trim().lowercase(Locale.ROOT)
    }
}