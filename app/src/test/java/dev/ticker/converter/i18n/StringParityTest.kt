package dev.ticker.converter.i18n

import dev.ticker.converter.testutil.StringsXml
import dev.ticker.converter.testutil.placeholders
import org.junit.Assert.assertTrue
import org.junit.Test

class StringParityTest {
    private val en = StringsXml.load("src/main/res/values/strings.xml")
    private val hu = StringsXml.load("src/main/res/values-hu/strings.xml")

    @Test fun huHasEveryKey() {
        val missing = (en.strings.keys - hu.strings.keys) + (en.plurals.keys - hu.plurals.keys)
        val extra = (hu.strings.keys - en.strings.keys) + (hu.plurals.keys - en.plurals.keys)
        assertTrue("missing in hu: $missing; extra in hu: $extra", missing.isEmpty() && extra.isEmpty())
    }

    @Test fun placeholdersMatch() {
        val bad = en.strings.filter { (k, v) -> hu.strings[k]?.let { placeholders(it) != placeholders(v) } == true }.keys +
            en.plurals.filter { (k, v) -> v["other"]?.let { o -> hu.plurals[k]?.get("other")?.let { placeholders(it) != placeholders(o) } } == true }.keys
        assertTrue("placeholder mismatch: $bad", bad.isEmpty())
    }

    @Test fun noEmptyHu() {
        val empty = hu.strings.filterValues { it.isBlank() }.keys + hu.plurals.filterValues { p -> p.values.any { it.isBlank() } }.keys
        assertTrue("empty hu values: $empty", empty.isEmpty())
    }

    @Test fun pluralsHaveOneAndOther() {
        val bad = listOf("en" to en, "hu" to hu).flatMap { (n, r) ->
            r.plurals.filterValues { !it.keys.containsAll(listOf("one", "other")) }.keys.map { "$n:$it" }
        }
        assertTrue("plurals missing one/other: $bad", bad.isEmpty())
    }
}
