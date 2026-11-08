package dev.ticker.converter.data.remote

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertFailsWith

class RemoteSourcesTest {
    private val server = MockWebServer()
    private val server2 = MockWebServer()

    private fun fixture(name: String) =
        javaClass.classLoader!!.getResource("fixtures/$name")!!.readText()

    private fun ok(name: String) = MockResponse.Builder().body(fixture(name)).build()

    @Before fun setUp() { server.start(); server2.start() }
    @After fun tearDown() { server.close(); server2.close() }

    private fun ecb() = RetrofitEcbSource(Network.frankfurter(server.url("/").toString()))

    @Test fun ecbLatestParses() = runTest {
        server.enqueue(ok("frankfurter_latest.json"))
        val r = ecb().latest()
        val raw = kotlinx.serialization.json.Json.parseToJsonElement(fixture("frankfurter_latest.json"))
        val expectedHuf = raw.jsonObjectGet("rates").jsonObjectGet("HUF").toString()
        assertEquals(0, BigDecimal(expectedHuf).compareTo(r.unitsPerUsd["HUF"]))
        assertEquals(LocalDate.parse(raw.jsonObjectGet("date").toString().trim('"')), r.date)
        assertTrue(server.takeRequest().target.startsWith("/v1/latest?base=USD"))
    }

    @Test fun ecbSeriesAscending() = runTest {
        server.enqueue(ok("frankfurter_series.json"))
        val r = ecb().series(LocalDate.parse("2026-10-29"), LocalDate.parse("2026-11-06"), listOf("EUR", "HUF"))
        assertTrue(r.size > 3)
        assertTrue(r.zipWithNext().all { (a, b) -> a.date < b.date })
        assertTrue(server.takeRequest().target.startsWith("/v1/2026-10-29..2026-11-06?base=USD&symbols=EUR,HUF"))
    }

    @Test fun fawazUppercasesAndFallsBack() = runTest {
        server.enqueue(MockResponse.Builder().code(500).build())
        server2.enqueue(ok("fawaz_usd.json"))
        val src = HttpFawazSource(
            Network.okHttp(), Network.json(),
            listOf(server.url("/a/").toString() + "{v}/usd.json", server2.url("/b/").toString() + "{v}/usd.json"),
        )
        val r = src.latest()
        assertNotNull(r.unitsPerUsd["BTC"])
        assertEquals("/b/latest/usd.json", server2.takeRequest().target)
    }

    @Test fun fawazDatedUsesDateInUrl() = runTest {
        server.enqueue(ok("fawaz_usd.json"))
        val src = HttpFawazSource(Network.okHttp(), Network.json(), listOf(server.url("/").toString() + "{v}/usd.json"))
        src.on(LocalDate.parse("2025-01-01"))
        assertEquals("/2025-01-01/usd.json", server.takeRequest().target)
    }

    @Test fun malformedJsonThrowsIOException() = runTest {
        server.enqueue(MockResponse.Builder().body("{").build())
        assertFailsWith<IOException> { ecb().latest() }
    }

    @Test fun httpErrorThrowsIOException() = runTest {
        server.enqueue(MockResponse.Builder().code(451).build())
        assertFailsWith<IOException> { ecb().latest() }
    }

    private fun kotlinx.serialization.json.JsonElement.jsonObjectGet(k: String) =
        (this as kotlinx.serialization.json.JsonObject)[k]!!
}
