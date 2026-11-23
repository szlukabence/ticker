package dev.ticker.converter.i18n

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Every user-visible string belongs in strings.xml, so it can be translated. */
class NoHardcodedTextTest {
    private val patterns = listOf(
        Regex("""Text\(\s*"[\p{L}]"""),
        Regex("""(contentDescription|label|title|placeholder|onClickLabel|onLongClickLabel|stateDescription)\s*=\s*"[\p{L}]"""),
        Regex("""(NeonPillButton|StatTile|Heading|SectionHeader|Body|Message)\(\s*"[\p{L}]"""),
        Regex("""(Icon|CodeChip)\([^\n]*,\s*"[\p{L}]"""),
        Regex("""Text\(\s*if\s*\([^"]*\)\s*"[\p{L}]"""),
        Regex("""NeonIconButton\([^,]+,\s*"[\p{L}]"""),
    )

    @Test fun noLiteralTextInUi() {
        val hits = mutableListOf<String>()
        File("src/main/java").walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { f ->
            // Comments and animation labels are not shown to the user; blank them out but keep the line count.
            val text = f.readLines().joinToString("\n") { raw ->
                val line = raw.substringBefore("//")
                if ("AsState(" in line || line.contains("\"TICKER\"")) "" else line
            }
            for (p in patterns) for (m in p.findAll(text)) hits += "${f.path}:${text.substring(0, m.range.first).count { it == '\n' } + 1}"
        }
        assertTrue("${hits.size} hardcoded strings:\n" + hits.sorted().joinToString("\n"), hits.isEmpty())
    }
}
