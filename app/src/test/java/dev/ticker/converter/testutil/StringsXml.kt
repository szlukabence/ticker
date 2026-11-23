package dev.ticker.converter.testutil

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

data class Res(val strings: Map<String, String>, val plurals: Map<String, Map<String, String>>)

object StringsXml {
    /** Parses a strings.xml. A missing file is an empty [Res]. Values are unescaped (`\'`, `\"`, `\n`). */
    fun load(path: String): Res {
        val f = File(path)
        if (!f.exists()) return Res(emptyMap(), emptyMap())
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f)
        val strings = LinkedHashMap<String, String>()
        val plurals = LinkedHashMap<String, Map<String, String>>()
        val nodes = doc.documentElement.childNodes
        for (i in 0 until nodes.length) {
            val e = nodes.item(i) as? Element ?: continue
            when (e.tagName) {
                "string" -> strings[e.getAttribute("name")] = unescape(e.textContent)
                "plurals" -> {
                    val items = LinkedHashMap<String, String>()
                    val kids = e.getElementsByTagName("item")
                    for (j in 0 until kids.length) {
                        val k = kids.item(j) as Element
                        items[k.getAttribute("quantity")] = unescape(k.textContent)
                    }
                    plurals[e.getAttribute("name")] = items
                }
            }
        }
        return Res(strings, plurals)
    }

    private fun unescape(s: String): String =
        s.replace("\\'", "'").replace("\\\"", "\"").replace("\\n", "\n").replace("\\@", "@").replace("\\?", "?")
}

private val PLACEHOLDER = Regex("%(\\d+\\$)?[sdf]|%%")

/** The `%1$s`-style tokens of [s]. */
fun placeholders(s: String): Set<String> = PLACEHOLDER.findAll(s).map { it.value }.toSet()
