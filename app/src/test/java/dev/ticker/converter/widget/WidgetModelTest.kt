package dev.ticker.converter.widget

import dev.ticker.converter.core.math.AmountFormatter
import dev.ticker.converter.core.math.Converter
import dev.ticker.converter.core.math.Fee
import dev.ticker.converter.core.model.Settings
import dev.ticker.converter.fakes.testCatalog
import dev.ticker.converter.fixtures.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Duration
import java.time.ZoneOffset

class WidgetModelTest {
    private val catalog = testCatalog()
    private val formatter = AmountFormatter('.')
    private val fetched = Fixtures.snapshot.fetchedAt
    private val favs = Settings(favorites = listOf("EUR", "USD", "HUF", "VND"), initialized = true)

    private fun build(s: Settings = favs, snap: Boolean = true, age: Duration = Duration.ofMinutes(5)) =
        WidgetModel.build(s, if (snap) Fixtures.snapshot else null, fetched.plus(age), ZoneOffset.UTC, catalog, formatter)

    @Test fun noSnapshotIsNoRates() = assertEquals(WidgetUi.NoRates, build(snap = false))

    @Test fun oneFavouriteNeedsFavourites() =
        assertEquals(WidgetUi.NeedFavourites, build(favs.copy(favorites = listOf("EUR"))))

    @Test fun rowsAreOneBaseInEachOtherFavourite() {
        val ui = build() as WidgetUi.Rates
        assertEquals("EUR", ui.baseCode)
        assertEquals(listOf("USD", "HUF", "VND"), ui.rows.map { it.code })
        val usd = Converter.convert(BigDecimal.ONE, "EUR", "USD", Fixtures.snapshot)!!
        assertEquals(formatter.format(usd, catalog.get("USD")!!), ui.rows[0].amount)
        assertNull(ui.feeLabel)
    }

    @Test fun feeAppliedWhenOn() {
        val ui = build(favs.copy(feeOn = true, feeBps = 200)) as WidgetUi.Rates
        val usd = Converter.convert(BigDecimal.ONE, "EUR", "USD", Fixtures.snapshot)!!
        assertEquals(formatter.format(Fee.apply(usd, 200), catalog.get("USD")!!), ui.rows[0].amount)
        assertEquals("2%", ui.feeLabel)
    }

    @Test fun staleAfterTwoHours() {
        assertTrue((build(age = Duration.ofMinutes(121)) as WidgetUi.Rates).stale)
        assertFalse((build(age = Duration.ofMinutes(119)) as WidgetUi.Rates).stale)
    }

    @Test fun updatedIsLocalTime() {
        val expected = java.time.format.DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneOffset.UTC).format(fetched)
        assertEquals(expected, (build() as WidgetUi.Rates).updated)
    }

    @Test fun missingRateRowShowsDash() {
        val ui = build() as WidgetUi.Rates
        val vnd = ui.rows.first { it.code == "VND" }
        assertEquals("—", vnd.amount) // the fixture has no VND rate
        assertNull(vnd.change)
    }

    @Test fun changeUsesArrows() {
        val ui = build() as WidgetUi.Rates
        val usd = ui.rows.first { it.code == "USD" }
        val pct = Converter.dailyChangePct("USD", "EUR", Fixtures.snapshot)!!
        assertEquals(dev.ticker.converter.ui.components.changeArrowText(pct), usd.change)
        assertEquals(pct < 0, usd.down)
    }
    // Final review: the widget redraws when what it shows changes, not on every keypad press.
    @Test fun redrawKeyIgnoresTypingButNotFeeFavouritesOrRates() {
        val k = WidgetModel.redrawKey(favs, Fixtures.snapshot)
        assertEquals(k, WidgetModel.redrawKey(favs.copy(expression = "123+4", activeCode = "USD"), Fixtures.snapshot))
        assertTrue(k != WidgetModel.redrawKey(favs.copy(feeOn = true), Fixtures.snapshot))
        assertTrue(k != WidgetModel.redrawKey(favs.copy(feeBps = 175), Fixtures.snapshot))
        assertTrue(k != WidgetModel.redrawKey(favs.copy(favorites = favs.favorites.reversed()), Fixtures.snapshot))
        assertTrue(k != WidgetModel.redrawKey(favs, Fixtures.snapshot.copy(fetchedAt = fetched.plusSeconds(60))))
        assertTrue(k != WidgetModel.redrawKey(favs, null))
    }
}
