package dev.ticker.converter.data.local

import dev.ticker.converter.core.model.PricePoint
import dev.ticker.converter.core.model.RateSnapshot
import dev.ticker.converter.core.model.RateSource
import dev.ticker.converter.core.model.Settings
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.time.Instant

// coerceInputValues: an enum value this version doesn't know (say, from a newer release) falls back
// to the field's default instead of failing the whole decode and losing every other setting.
private val codecJson = Json { ignoreUnknownKeys = true; coerceInputValues = true }

object SnapshotCodec {
    @Serializable
    private data class Dto(
        val usdPer: Map<String, String>,
        val usdPerPrev: Map<String, String>,
        val sourceFor: Map<String, RateSource>,
        val fetchedAt: Long,
    )

    fun encode(s: RateSnapshot): String = codecJson.encodeToString(
        Dto.serializer(),
        Dto(
            s.usdPer.mapValues { it.value.toPlainString() },
            s.usdPerPrev.mapValues { it.value.toPlainString() },
            s.sourceFor,
            s.fetchedAt.toEpochMilli(),
        ),
    )

    fun decode(json: String): RateSnapshot = codecJson.decodeFromString(Dto.serializer(), json).let { d ->
        RateSnapshot(
            d.usdPer.mapValues { BigDecimal(it.value) },
            d.usdPerPrev.mapValues { BigDecimal(it.value) },
            d.sourceFor,
            Instant.ofEpochMilli(d.fetchedAt),
        )
    }
}

object SeriesCodec {
    @Serializable
    private data class P(val t: Long, val v: String)

    fun encode(p: List<PricePoint>): String =
        codecJson.encodeToString(kotlinx.serialization.builtins.ListSerializer(P.serializer()), p.map { P(it.time.toEpochMilli(), it.value.toPlainString()) })

    fun decode(json: String): List<PricePoint> =
        codecJson.decodeFromString(kotlinx.serialization.builtins.ListSerializer(P.serializer()), json)
            .map { PricePoint(Instant.ofEpochMilli(it.t), BigDecimal(it.v)) }
}

object SettingsCodec {
    fun encode(s: Settings): String = codecJson.encodeToString(Settings.serializer(), s)

    /** Missing or corrupt JSON yields defaults. */
    fun decode(json: String?): Settings =
        json?.let { runCatching { codecJson.decodeFromString(Settings.serializer(), it) }.getOrNull() } ?: Settings()
}
