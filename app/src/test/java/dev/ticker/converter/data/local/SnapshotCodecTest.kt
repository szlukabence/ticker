package dev.ticker.converter.data.local

import dev.ticker.converter.core.model.PricePoint
import dev.ticker.converter.fixtures.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class SnapshotCodecTest {
    @Test fun roundTrip() {
        val s = Fixtures.snapshot
        val back = SnapshotCodec.decode(SnapshotCodec.encode(s))
        assertEquals(s, back)
        assertEquals(7, back.usdPer["HUF"]!!.scale()) // 0.0030681 keeps its scale
    }
}

class SeriesCodecTest {
    @Test fun roundTrip() {
        val p = listOf(PricePoint(Instant.ofEpochMilli(1000), BigDecimal("1.50")), PricePoint(Instant.ofEpochMilli(2000), BigDecimal("2")))
        assertEquals(p, SeriesCodec.decode(SeriesCodec.encode(p)))
    }
}

class SettingsCodecTest {
    @Test fun corruptJsonGivesDefaults() {
        assertEquals(dev.ticker.converter.core.model.Settings(), SettingsCodec.decode("{not json"))
    }

    @Test fun oldSettingsJsonGetsFeeDefaults() {
        val s = SettingsCodec.decode("""{"favorites":["EUR","USD"]}""")
        assertEquals(200, s.feeBps)
        assertEquals(false, s.feeOn)
    }

    @Test fun feeRoundTrips() {
        val s = SettingsCodec.decode(SettingsCodec.encode(dev.ticker.converter.core.model.Settings(feeBps = 175, feeOn = true)))
        assertEquals(175, s.feeBps)
        assertEquals(true, s.feeOn)
    }

    @Test fun oldSettingsJsonGetsPriceMode() {
        val s = SettingsCodec.decode("""{"favorites":["EUR","USD"]}""")
        assertEquals(dev.ticker.converter.core.model.CameraMode.PRICE, s.cameraMode)
    }

    @Test fun cameraModeRoundTrips() {
        val s = SettingsCodec.decode(SettingsCodec.encode(dev.ticker.converter.core.model.Settings(cameraMode = dev.ticker.converter.core.model.CameraMode.RECEIPT)))
        assertEquals(dev.ticker.converter.core.model.CameraMode.RECEIPT, s.cameraMode)
    }

    /** The JSON stored on the phone before cameraTo existed must keep every other field. */
    @Test fun settingsFromBeforeCameraToKeepEverything() {
        val stored = """{"favorites":["USD","EUR","HUF","GBP","RON"],"activeCode":"USD","expression":"30634","cameraFrom":"HUF","chartBase":"RON","chartQuote":"HUF","savedPairs":[{"base":"RON","quote":"HUF"}],"initialized":true}"""
        val s = SettingsCodec.decode(stored)
        assertEquals(listOf("USD", "EUR", "HUF", "GBP", "RON"), s.favorites)
        assertEquals("HUF", s.cameraFrom)
        assertEquals(null, s.cameraTo)
        assertEquals(true, s.initialized)
    }
}
