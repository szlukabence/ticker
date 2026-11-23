package dev.ticker.converter.trips

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

object TripMath {
    /** What the entry counts as: the bank's real charge when known, else the saved estimate. */
    fun counted(e: Entry): BigDecimal = e.actualHome ?: e.homeAmount

    /** A sum of the counted (already rounded) amounts, never a rounded sum of raw values. */
    fun total(es: List<Entry>): BigDecimal = es.fold(BigDecimal.ZERO) { acc, e -> acc + counted(e) }

    /** Days newest first, entries newest first inside a day. Days are cut in [zone], the phone's current zone. */
    fun byDay(es: List<Entry>, zone: ZoneId): List<Pair<LocalDate, List<Entry>>> =
        es.groupBy { it.at.atZone(zone).toLocalDate() }
            .map { (d, list) -> d to list.sortedByDescending { it.at } }
            .sortedByDescending { it.first }

    /** Days from the start to today (or to the end of an ended trip), inclusive, at least 1. */
    fun days(trip: Trip, today: LocalDate, zone: ZoneId): Int {
        val first = trip.startedAt.atZone(zone).toLocalDate()
        val last = trip.endedAt?.atZone(zone)?.toLocalDate() ?: today
        return (ChronoUnit.DAYS.between(first, last) + 1).toInt().coerceAtLeast(1)
    }

    fun dailyAverage(trip: Trip, es: List<Entry>, today: LocalDate, zone: ZoneId, homeDecimals: Int): BigDecimal =
        total(es).divide(BigDecimal(days(trip, today, zone)), homeDecimals, RoundingMode.HALF_UP)

    /** Null without a budget; negative when over it. */
    fun budgetLeft(trip: Trip, total: BigDecimal): BigDecimal? = trip.budgetHome?.subtract(total)

    /** Share of the budget spent, clamped to 0..1 for the bar. */
    fun budgetFraction(trip: Trip, total: BigDecimal): Float? {
        val b = trip.budgetHome?.takeIf { it.signum() > 0 } ?: return null
        return total.divide(b, 4, RoundingMode.HALF_UP).toFloat().coerceIn(0f, 1f)
    }
}
