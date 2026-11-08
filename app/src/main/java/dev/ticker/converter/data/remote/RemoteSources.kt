package dev.ticker.converter.data.remote

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.HttpException
import java.io.IOException
import java.math.BigDecimal
import java.time.LocalDate

/** Rates as units of each currency per 1 USD, uppercase codes. */
data class DatedRates(val date: LocalDate, val unitsPerUsd: Map<String, BigDecimal>)

interface EcbSource {
    suspend fun latest(): DatedRates
    /** Ascending by date. Empty [codes] means every ECB currency. */
    suspend fun series(start: LocalDate, end: LocalDate, codes: Collection<String>): List<DatedRates>
}

interface FawazSource {
    suspend fun latest(): DatedRates
    suspend fun on(date: LocalDate): DatedRates
}

/** Every remote failure surfaces as an [IOException]. */
internal suspend fun <T> io(block: suspend () -> T): T = try {
    block()
} catch (e: IOException) {
    throw e
} catch (e: HttpException) {
    throw IOException("HTTP ${e.code()}", e)
} catch (e: SerializationException) {
    throw IOException("Malformed response", e)
} catch (e: IllegalArgumentException) {
    throw IOException("Malformed response", e)
} catch (e: NumberFormatException) {
    throw IOException("Malformed number", e)
}

class RetrofitEcbSource(private val api: FrankfurterApi) : EcbSource {
    override suspend fun latest(): DatedRates = io {
        val dto = api.latest()
        DatedRates(LocalDate.parse(dto.date), dto.rates.mapValues { BigDecimal(it.value) })
    }

    override suspend fun series(start: LocalDate, end: LocalDate, codes: Collection<String>): List<DatedRates> = io {
        val dto = api.series("$start..$end", symbols = codes.takeIf { it.isNotEmpty() }?.joinToString(","))
        dto.rates.map { (d, r) -> DatedRates(LocalDate.parse(d), r.mapValues { BigDecimal(it.value) }) }
            .sortedBy { it.date }
    }
}

val FAWAZ_URLS = listOf(
    "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@{v}/v1/currencies/usd.json",
    "https://{v}.currency-api.pages.dev/v1/currencies/usd.json",
)

class HttpFawazSource(
    private val client: OkHttpClient,
    private val json: Json,
    private val baseUrls: List<String> = FAWAZ_URLS,
) : FawazSource {
    override suspend fun latest() = fetch("latest")
    override suspend fun on(date: LocalDate) = fetch(date.toString())

    private suspend fun fetch(version: String): DatedRates = io {
        var last: IOException? = null
        for (template in baseUrls) {
            try {
                return@io parse(get(template.replace("{v}", version)))
            } catch (e: IOException) {
                last = e
            } catch (e: SerializationException) {
                last = IOException("Malformed response", e)
            } catch (e: IllegalArgumentException) {
                last = IOException("Malformed response", e)
            }
        }
        throw last ?: IOException("No fawazahmed0 URL configured")
    }

    private suspend fun get(url: String): String = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        client.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code} for $url")
            r.body.string()
        }
    }

    private fun parse(text: String): DatedRates {
        val root = json.parseToJsonElement(text) as JsonObject
        val date = LocalDate.parse(root["date"]!!.jsonPrimitive.content)
        val rates = (root["usd"] as JsonObject).mapNotNull { (k, v) ->
            (v as? JsonPrimitive)?.content?.toBigDecimalOrNull()?.takeIf { it.signum() > 0 }?.let { k.uppercase() to it }
        }.toMap()
        return DatedRates(date, rates)
    }
}
