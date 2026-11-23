package dev.ticker.converter.trips

import dev.ticker.converter.core.model.Payment
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

class TripCsvTest {
    private val zone = ZoneId.of("Europe/Budapest")
    private val start = Instant.parse("2026-11-18T08:00:00Z")
    private val trip = Trip("t", "Wien", "HUF", start, null, BigDecimal("200000"))
    private val oldLocale = Locale.getDefault()

    @After fun restore() = Locale.setDefault(oldLocale)

    private fun e(
        id: String, at: String, home: String, actual: String? = null, note: String? = null,
        amount: String = "42.50", bps: Int = 150,
    ) = Entry(
        id, "t", Instant.parse(at), BigDecimal(amount), "EUR", BigDecimal("396.02"), Instant.parse("2026-11-18T08:41:00Z"),
        RateOrigin.ECB, bps, BigDecimal(home), actual?.let(::BigDecimal), Payment.CARD, note, EntrySource.CONVERT,
    )

    private fun lines(csv: String) = csv.removePrefix("﻿").split("\r\n").filter { it.isNotEmpty() }

    /** Small RFC 4180 splitter for one line. */
    private fun split(line: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var q = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                q && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> { sb.append('"'); i++ }
                c == '"' -> q = !q
                c == ',' && !q -> { out += sb.toString(); sb.clear() }
                else -> sb.append(c)
            }
            i++
        }
        out += sb.toString()
        return out
    }

    @Test fun startsWithBomAndHeader() {
        val csv = TripCsv.write(trip, emptyList(), zone)
        assertTrue(csv.startsWith("﻿" + TripCsv.HEADER))
    }

    @Test fun crlfLines() {
        val csv = TripCsv.write(trip, listOf(e("a", "2026-11-18T10:00:00Z", "100.00")), zone)
        assertTrue(csv.endsWith("\r\n"))
        assertEquals(2, lines(csv).size)
        assertTrue(!csv.replace("\r\n", "").contains('\n'))
    }

    @Test fun oldestFirst() {
        val es = listOf(e("b", "2026-11-19T10:00:00Z", "2.00"), e("a", "2026-11-18T10:00:00Z", "1.00"))
        val rows = lines(TripCsv.write(trip, es, zone)).drop(1).map(::split)
        assertEquals(listOf("2026-11-18", "2026-11-19"), rows.map { it[0] })
    }

    @Test fun columnsAndFormats() {
        val row = split(lines(TripCsv.write(trip, listOf(e("a", "2026-11-18T22:30:00Z", "17083.31", "17120.00", "Taxi")), zone))[1])
        assertEquals(
            listOf("2026-11-18", "23:30", "Taxi", "42.50", "EUR", "396.02", "2026-11-18T09:41:00+01:00", "ECB", "1.5", "17083.31", "17120.00", "17120.00", "HUF", "CARD", "CONVERT"),
            row,
        )
    }

    @Test fun quotesNoteWithCommaAndQuote() {
        val csv = TripCsv.write(trip, listOf(e("a", "2026-11-18T10:00:00Z", "1.00", note = "Lunch, \"Naschmarkt\"")), zone)
        assertTrue(csv.contains(",\"Lunch, \"\"Naschmarkt\"\"\","))
    }

    @Test fun bankChargedEmptyWhenUnset() {
        val row = split(lines(TripCsv.write(trip, listOf(e("a", "2026-11-18T10:00:00Z", "17083.31")), zone))[1])
        assertEquals("", row[10])
        assertEquals("17083.31", row[11])
    }

    @Test fun countedColumnSumsToTotal() {
        val es = listOf(
            e("a", "2026-11-18T10:00:00Z", "100.10"), e("b", "2026-11-18T11:00:00Z", "50.55", "60.05"),
            e("c", "2026-11-19T10:00:00Z", "0.35"), e("d", "2026-11-19T11:00:00Z", "7.77", "8.01"), e("e", "2026-11-20T10:00:00Z", "1234.56"),
        )
        val sum = lines(TripCsv.write(trip, es, zone)).drop(1).map { BigDecimal(split(it)[11]) }.fold(BigDecimal.ZERO, BigDecimal::add)
        assertEquals(TripMath.total(es), sum)
    }

    @Test fun decimalPointNotLocale() {
        Locale.setDefault(Locale.forLanguageTag("hu-HU"))
        val csv = TripCsv.write(trip, listOf(e("a", "2026-11-18T10:00:00Z", "17083.31", amount = "42.50")), zone)
        assertTrue(csv.contains("42.50"))
        assertTrue(csv.contains("17083.31"))
        assertTrue(!csv.contains("42,50"))
    }
}
