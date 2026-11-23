package dev.ticker.converter.i18n

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Every user-visible string belongs in strings.xml, so it can be translated. */
class NoHardcodedTextTest {
    private val patterns = listOf(
        Regex("""Text\(\s*"[\p{L}]"""),
        Regex("""(contentDescription|label|title|placeholder)\s*=\s*"[\p{L}]"""),
        Regex("""NeonIconButton\([^,]+,\s*"[\p{L}]"""),
    )

    @Test fun noLiteralTextInUi() {
        val hits = mutableListOf<String>()
        File("src/main/java").walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { f ->
            f.readLines().forEachIndexed { i, raw ->
                val line = raw.substringBefore("//")
                if ("AsState(" in line) return@forEachIndexed // animation labels
                if (line.contains("\"TICKER\"")) return@forEachIndexed
                if (patterns.any { it.containsMatchIn(line) }) hits += "${f.path}:${i + 1}"
            }
        }
        assertTrue("${hits.size} hardcoded strings:\n" + hits.joinToString("\n"), hits.isEmpty())
    }
}
