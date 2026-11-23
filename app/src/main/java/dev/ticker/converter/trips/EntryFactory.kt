package dev.ticker.converter.trips

import dev.ticker.converter.core.math.Converter
import dev.ticker.converter.core.math.Fee
import dev.ticker.converter.core.model.Payment
import dev.ticker.converter.core.model.RateSnapshot
import dev.ticker.converter.core.model.RateSource
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.UUID

enum class Reason { NO_RATES, NO_RATE_FOR_PAIR, ZERO_AMOUNT }

sealed interface EntryResult {
    data class Ok(val entry: Entry) : EntryResult
    data class Unavailable(val reason: Reason) : EntryResult
}

object EntryFactory {
    fun fromConversion(
        amount: BigDecimal,
        code: String,
        trip: Trip,
        homeDecimals: Int,
        snap: RateSnapshot?,
        feeOn: Boolean,
        feeBps: Int,
        payment: Payment,
        source: EntrySource,
        now: Instant,
        note: String? = null,
        newId: () -> String = { UUID.randomUUID().toString() },
    ): EntryResult {
        if (snap == null) return EntryResult.Unavailable(Reason.NO_RATES)
        if (amount.signum() <= 0) return EntryResult.Unavailable(Reason.ZERO_AMOUNT)
        val home = trip.homeCode
        val rate = if (code == home) BigDecimal.ONE
        else Converter.convert(BigDecimal.ONE, code, home, snap) ?: return EntryResult.Unavailable(Reason.NO_RATE_FOR_PAIR)
        val fee = if (payment == Payment.CARD && feeOn) feeBps else 0
        val raw = amount.multiply(rate)
        val homeAmount = (if (fee > 0) Fee.apply(raw, fee) else raw).setScale(homeDecimals, RoundingMode.HALF_UP)
        return EntryResult.Ok(
            Entry(
                id = newId(), tripId = trip.id, at = now, amount = amount, code = code,
                rateToHome = rate, rateAt = snap.fetchedAt, rateSource = origin(code, home, snap),
                feeBps = fee, homeAmount = homeAmount, actualHome = null, payment = payment, note = note, source = source,
            ),
        )
    }

    private fun origin(code: String, home: String, snap: RateSnapshot): RateOrigin {
        if (code == home) return RateOrigin.ECB
        val a = snap.sourceFor[code]
        val b = snap.sourceFor[home]
        return when {
            a == RateSource.FRANKFURTER && b == RateSource.FRANKFURTER -> RateOrigin.ECB
            a == RateSource.FAWAZ && b == RateSource.FAWAZ -> RateOrigin.FAWAZ
            else -> RateOrigin.MIXED
        }
    }
}
