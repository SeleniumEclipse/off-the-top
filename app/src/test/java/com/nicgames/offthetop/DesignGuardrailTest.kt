package com.nicgames.offthetop

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** This game's chosen visual direction, not a general judgement about UI design. */
class DesignGuardrailTest {
    // Gradle runs app unit tests from the app project directory. Never walk test
    // sources: the negative examples in this file must not police themselves.
    private val sourceRoot = File("src/main/java/com/nicgames/offthetop")

    private fun productionSources(): List<File> {
        assertTrue("Run from the app project; missing ${sourceRoot.path}", sourceRoot.isDirectory)
        return sourceRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
            .also { assertTrue("Production Kotlin sources must not be empty", it.isNotEmpty()) }
    }

    private fun uiSources(): List<File> = listOf("MainActivity.kt", "Theme.kt").map { name ->
        File(sourceRoot, name).also { assertTrue("Missing production UI source: ${it.path}", it.isFile) }
    }

    @Test
    fun runtimeSurfacesDoNotReintroduceGradientsOrGlowShaders() {
        // Case-sensitive API names, not prose such as "no gradients" in a comment.
        // Imports also catch aliases and directly imported gradient factory methods.
        val effectTypes = listOf(
            "Brush", "Shader", "ShaderBrush", "RuntimeShader", "RenderEffect", "BlurEffect", "BlurMaskFilter",
            "LinearGradient", "RadialGradient", "SweepGradient",
            "LinearGradientShader", "RadialGradientShader", "SweepGradientShader",
        ).joinToString("|")
        val effects = Regex(
            """(?m)^\s*import\s+(?:android\.graphics|androidx\.compose\.ui\.graphics)\.(?:$effectTypes)\b|\b(?:$effectTypes)\s*\(|\bBrush\s*\.\s*(?:Companion\s*\.\s*)?\w*Gradient\s*\(""",
        )
        productionSources().forEach { file ->
            val found = effects.find(file.readText())
            assertTrue("${file.name}: flat surfaces must not use ${found?.value}", found == null)
        }
    }

    @Test
    fun uiSourceDoesNotUseEmojiOrTextGlyphIcons() {
        // Restrict icon-like ranges, not all Unicode: curly quotes, apostrophes,
        // degree signs, middle dots in metadata and ordinary dashes remain valid.
        val icons = Regex("[✓↷↑↓→←‹›]|[\\x{1F000}-\\x{1FAFF}\\x{2600}-\\x{27BF}\\x{FE0F}\\x{20E3}]")
        val unicodeEscape = Regex("""\\u([0-9a-fA-F]{4})""")
        uiSources().forEach { file ->
            val decoded = unicodeEscape.replace(file.readText()) {
                it.groupValues[1].toInt(16).toChar().toString()
            }
            assertFalse("${file.name}: use an official vector icon instead of emoji or text glyphs",
                icons.containsMatchIn(decoded))
        }
    }

    @Test
    fun removedDecorationAndFillerDoNotReturn() {
        val removedCopy = listOf(
            "Nature", "Objects & food", "Actions & places", "Phone to forehead",
            "Hold still. Screen facing friends.", "Tap instead of tilting", "Smaller nods",
            "Tap to correct", "THE FOREHEAD GUESSING GAME", "ALL OFFLINE", "NO ADS",
        )
        uiSources().forEach { file ->
            val source = file.readText()
            assertFalse("${file.name}: decorative deck/phone illustrations were removed",
                Regex("""\b(?:DeckMark|PhoneMark)\b""").containsMatchIn(source))
            removedCopy.forEach { text ->
                // Match complete literals, not words inside essential help or metadata.
                assertFalse("${file.name}: removed filler returned: $text", source.contains("\"$text\""))
            }
        }
    }

    @Test
    fun uiIconsComeFromTheOfficialSharpVectorSet() {
        val main = uiSources().single { it.name == "MainActivity.kt" }.readText()
        val imports = Regex("""(?m)^import\s+androidx\.compose\.material\.icons\.([^\s]+)""")
            .findAll(main).map { it.groupValues[1] }.filter { it != "Icons" }.toList()
        assertTrue("Screen icons must import the official Material vector set", imports.isNotEmpty())
        imports.forEach { name ->
            assertTrue("Use Sharp vectors, not mixed icon styles: $name",
                Regex("""(?:automirrored\.)?sharp\.[A-Z]\w*""").matches(name))
        }
        uiSources().forEach { file ->
            val source = file.readText()
            assertTrue("${file.name}: icons must render through Compose Icon",
                Regex("""\bIcon\s*\(""").containsMatchIn(source))
            assertTrue("${file.name}: icon parameters must use ImageVector, not font strings",
                Regex("""\bimport\s+androidx\.compose\.ui\.graphics\.vector\.ImageVector\b""")
                    .containsMatchIn(source))
        }
    }
}