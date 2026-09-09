package com.nicgames.offthetop

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
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

    private fun uiSources(): List<File> = listOf("MainActivity.kt", "Theme.kt", "CardTable.kt").map { name ->
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
            assertFalse("${file.name}: use Sharp action vectors or the three approved category vectors, not glyphs/suits",
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
    fun actionIconsStaySharp_andOnlyCardTableMayLoadTheApprovedCategoryVectors() {
        val main = uiSources().single { it.name == "MainActivity.kt" }.readText()
        val importPattern = Regex("""(?m)^import\s+androidx\.compose\.material\.icons\.([^\s]+)""")
        val imports = importPattern.findAll(main).map { it.groupValues[1] }.filter { it != "Icons" }.toList()
        assertTrue("Screen icons must import the official Material vector set", imports.isNotEmpty())
        imports.forEach { name ->
            assertTrue("Use Sharp vectors, not mixed icon styles: $name",
                Regex("""(?:automirrored\.)?sharp\.[A-Z]\w*""").matches(name))
        }
        uiSources().forEach { file ->
            val source = file.readText()
            assertTrue("${file.name}: icons must render through Compose Icon",
                Regex("""\bIcon\s*\(""").containsMatchIn(source))
            // Category artwork is the explicit exception, not permission to mix
            // random vector sets, bitmap decorations, emoji or playing-card suits.
            if (file.name == "CardTable.kt") {
                val category = functionSource(source, "CategoryIcon")
                assertTrue("CategoryIcon must paint its selected bundled vector",
                    Regex("""\bIcon\s*\(\s*painterResource\s*\(\s*asset\s*\)""").containsMatchIn(category))
                val mappings = mapOf("wild-world" to "category_wild", "everyday" to "category_everyday",
                    "do-your-thing" to "category_actions")
                mappings.forEach { (id, asset) ->
                    assertTrue("$id must select $asset",
                        Regex("\"$id\"\\s*->\\s*R\\.drawable\\.$asset\\b").containsMatchIn(category))
                }
                assertEquals("No substitute category assets",
                    mappings.values.toSet(), Regex("""R\.drawable\.(\w+)""").findAll(source).map { it.groupValues[1] }.toSet())
                assertEquals("Only the category component may use painterResource", 1,
                    Regex("""\bpainterResource\s*\(""").findAll(source).count())
            } else {
                assertFalse("${file.name}: custom category painters belong only in CategoryIcon",
                    source.contains("painterResource"))
                assertTrue("${file.name}: action icon parameters must use ImageVector, not font strings",
                    Regex("""\bimport\s+androidx\.compose\.ui\.graphics\.vector\.ImageVector\b""")
                        .containsMatchIn(source))
            }
            importPattern.findAll(source).map { it.groupValues[1] }.filter { it != "Icons" }.forEach { name ->
                assertTrue("${file.name}: no mixed action-icon styles: $name",
                    Regex("""(?:automirrored\.)?sharp\.[A-Z]\w*""").matches(name))
            }
        }
    }

    @Test
    fun categoryResourcesRetainTheOriginalLeafMugAndMotionArtwork() {
        // Frozen equivalents of design/icons/{wild-world,everyday-things,do-your-thing}.svg.
        // SVG implicit lines/relative moves and its circle are expanded for Android.
        // Together with rendered pixel checks, this rejects blank/replaced/suit artwork.
        val originals = mapOf(
            "category_wild" to "M10,32C7,15 19,7 38,7c1,19 -7,31 -22,28M8,40L31,17M17,31V22m0,9h10M24,24v-8m0,8h8",
            "category_everyday" to "M8,16h24v18a5,5 0,0 1,-5 5H13a5,5 0,0 1,-5 -5V16ZM32,19h4a6,6 0,0 1,0 12h-4M6,43h32M15,6v5m10,-5v5",
            "category_actions" to "M26,8a4,4 0,1 0,8 0a4,4 0,1 0,-8 0M15,17l9,-2 8,9h9M24,15l-7,15 12,4 4,9M17,30l-6,10H4M15,17l-5,8",
        )
        val drawableRoot = File("src/main/res/drawable")
        assertEquals("Only the three original category XML resources are approved", originals.keys,
            drawableRoot.listFiles().orEmpty().filter { it.name.startsWith("category_") }.map { it.nameWithoutExtension }.toSet())
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        val android = "http://schemas.android.com/apk/res/android"
        originals.forEach { (name, path) ->
            val file = File(drawableRoot, "$name.xml")
            assertTrue("Missing original category resource $name", file.isFile)
            val root = factory.newDocumentBuilder().parse(file).documentElement
            assertEquals("$name must be a vector, not a bitmap", "vector", root.tagName)
            listOf("viewportWidth", "viewportHeight").forEach { attribute ->
                assertEquals("$name $attribute", 48f, root.getAttributeNS(android, attribute).toFloat(), 0f)
            }
            val paths = root.getElementsByTagName("path")
            assertEquals("$name must retain its original path only", 1, paths.length)
            val element = paths.item(0) as org.w3c.dom.Element
            assertEquals("$name artwork changed", path.replace(Regex("[\\s,]+"), ""),
                element.getAttributeNS(android, "pathData").replace(Regex("[\\s,]+"), ""))
            assertEquals("$name must remain an outline", "@android:color/transparent", element.getAttributeNS(android, "fillColor"))
            assertEquals("$name stroke", 3f, element.getAttributeNS(android, "strokeWidth").toFloat(), 0f)
            assertEquals("square", element.getAttributeNS(android, "strokeLineCap"))
            assertEquals("miter", element.getAttributeNS(android, "strokeLineJoin"))
        }
    }

    @Test
    fun flatPageAndApprovedPreviewDoNotRestoreTheRejectedTableOutline() {
        val theme = File(sourceRoot, "Theme.kt").readText()
        val background = functionSource(theme, "PageBackground")
        assertEquals("PageBackground must paint one flat background", 1,
            Regex("""\.background\s*\(""").findAll(background).count())
        assertTrue("PageBackground must use the theme's opaque paper", background.contains("LocalPress.current.paper"))
        val decoration = Regex("""\b(?:border|drawRoundRect|drawRect|drawPath|drawLine|Canvas|drawBehind|drawWithCache|drawWithContent)\s*\(""")
        assertFalse("No table edge or effects around PageBackground", decoration.containsMatchIn(background))
        val game = functionSource(File(sourceRoot, "MainActivity.kt").readText(), "GameApp")
        assertFalse("GameApp must not add a border outside its cards", decoration.containsMatchIn(game))
        val html = File("../design/card-table.html")
        assertTrue("The approved preview must remain available", html.isFile)
        assertFalse("The rejected table-edge element must not return to the preview",
            Regex("""(?i)<[^>]+(?:class|id)\s*=\s*["'][^"']*\btable-edge\b""").containsMatchIn(html.readText()))
    }

    @Test
    fun crosshatchingIsConfinedToRedCardBacks_notPageOrCreamFaces() {
        val source = File(sourceRoot, "CardTable.kt").readText()
        val back = functionSource(source, "RedCardBack")
        assertTrue("Approved card backs retain their printed pattern", back.contains("drawPath(hatch"))
        assertTrue("The card-back base must be red", back.contains("drawRect(p.pass)"))
        val pattern = Regex("""\b(?:Canvas|drawPath|drawLine)\s*\(""")
        listOf("CardFace", "PlayingCard", "DeckStack", "CategoryIcon").forEach { name ->
            assertFalse("$name must not paint a hatch pattern", pattern.containsMatchIn(functionSource(source, name)))
        }
        assertEquals("Only the back uses Canvas", 1, Regex("""\bCanvas\s*\(""").findAll(source).count())
    }

    private fun functionSource(source: String, name: String): String {
        val start = Regex("""\bfun\s+$name\s*\(""").find(source)
        assertTrue("Missing production function $name", start != null)
        return source.substring(checkNotNull(start).range.first).substringBefore("\n@Composable")
    }
}