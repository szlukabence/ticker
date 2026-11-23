package dev.ticker.converter.trips

import dev.ticker.converter.core.math.Fee
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One trip as a spreadsheet file: UTF-8 BOM, comma separated, CRLF, `.` as the decimal mark. */
object TripCsv {
    const val HEADER = "date,time,note,amount,currency,rate_to_home,rate_time,rate_source,fee_percent,home_amount,bank_charged,counted,home_currency,payment,source"

    private val DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT)
    private val TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)

    fun write(trip: Trip, entries: List<Entry>, zone: ZoneId): String {
        val sb = StringBuilder("﻿").append(HEADER).append("\r\n")
        for (e in entries.sortedBy { it.at }) {
            val at = e.at.atZone(zone)
            val fields = listOf(
                DATE.format(at), TIME.format(at), e.note.orEmpty(), e.amount.toPlainString(), e.code,
                e.rateToHome.toPlainString(), DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(e.rateAt.atZone(zone)),
                e.rateSource.name, Fee.label(e.feeBps).removeSuffix("%"), e.homeAmount.toPlainString(),
                e.actualHome?.toPlainString().orEmpty(), TripMath.counted(e).toPlainString(),
                trip.homeCode, e.payment.name, e.source.name,
            )
            sb.append(fields.joinToString(",") { quote(it) }).append("\r\n")
        }
        return sb.toString()
    }

    private fun quote(f: String): String =
        if (f.any { it == ',' || it == '"' || it == '\r' || it == '\n' }) "\"" + f.replace("\"", "\"\"") + "\"" else f
}
