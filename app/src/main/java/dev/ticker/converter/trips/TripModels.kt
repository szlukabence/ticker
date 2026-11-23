package dev.ticker.converter.trips

import dev.ticker.converter.core.model.Payment
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.math.BigDecimal
import java.time.Instant

/** Money is a plain decimal string in JSON so the scale survives a round trip. */
object BigDecimalAsString : KSerializer<BigDecimal> {
    override val descriptor = PrimitiveSerialDescriptor("BigDecimalAsString", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: BigDecimal) = encoder.encodeString(value.toPlainString())
    override fun deserialize(decoder: Decoder): BigDecimal = BigDecimal(decoder.decodeString())
}

object InstantAsEpochMs : KSerializer<Instant> {
    override val descriptor = PrimitiveSerialDescriptor("InstantAsEpochMs", PrimitiveKind.LONG)
    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeLong(value.toEpochMilli())
    override fun deserialize(decoder: Decoder): Instant = Instant.ofEpochMilli(decoder.decodeLong())
}

/** Which source the rate came from: both currencies ECB, both Fawaz, or one of each. */
@Serializable
enum class RateOrigin { ECB, FAWAZ, MIXED }

@Serializable
enum class EntrySource { CONVERT, RECEIPT, MANUAL }

@Serializable
data class Trip(
    val id: String,
    val name: String,
    val homeCode: String,
    @Serializable(with = InstantAsEpochMs::class) val startedAt: Instant,
    @Serializable(with = InstantAsEpochMs::class) val endedAt: Instant?,
    @Serializable(with = BigDecimalAsString::class) val budgetHome: BigDecimal?,
) {
    val active: Boolean get() = endedAt == null
}

/** One spending line. It keeps the rate and fee it was made with, so later rate moves never change it. */
@Serializable
data class Entry(
    val id: String,
    val tripId: String,
    @Serializable(with = InstantAsEpochMs::class) val at: Instant,
    @Serializable(with = BigDecimalAsString::class) val amount: BigDecimal,
    val code: String,
    /** One unit of [code] in the trip's home currency, before the fee. */
    @Serializable(with = BigDecimalAsString::class) val rateToHome: BigDecimal,
    @Serializable(with = InstantAsEpochMs::class) val rateAt: Instant,
    val rateSource: RateOrigin,
    val feeBps: Int,
    @Serializable(with = BigDecimalAsString::class) val homeAmount: BigDecimal,
    @Serializable(with = BigDecimalAsString::class) val actualHome: BigDecimal?,
    val payment: Payment,
    val note: String?,
    val source: EntrySource,
)
