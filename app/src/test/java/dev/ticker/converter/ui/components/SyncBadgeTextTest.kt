package dev.ticker.converter.ui.components

import dev.ticker.converter.R
import dev.ticker.converter.core.model.RateSource
import dev.ticker.converter.core.model.SyncStatus
import dev.ticker.converter.testutil.StringsXml
import dev.ticker.converter.ui.text.UiText
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class SyncBadgeTextTest {
    private val zone = ZoneId.of("Europe/Budapest")
    private val now = Instant.parse("2026-11-07T08:41:07Z") // 09:41:07 in Budapest

    private fun offline(ago: UiText) = UiText.Res(R.string.sync_offline, listOf(ago))

    @Test fun live() = assertEquals(UiText.Res(R.string.sync_live, listOf("09:41:07")), syncBadgeText(SyncStatus.Live(now), now, zone))
    @Test fun partial() = assertEquals(
        UiText.Res(R.string.sync_partial, listOf("09:41:07")),
        syncBadgeText(SyncStatus.Partial(now, setOf(RateSource.FAWAZ)), now, zone),
    )
    @Test fun offlineHours() = assertEquals(
        offline(UiText.Plural(R.plurals.sync_hours_ago, 2)),
        syncBadgeText(SyncStatus.Offline(now.minusSeconds(2 * 3600 + 5)), now, zone),
    )
    @Test fun offlineMinutes() = assertEquals(
        offline(UiText.Plural(R.plurals.sync_minutes_ago, 5)),
        syncBadgeText(SyncStatus.Offline(now.minusSeconds(300)), now, zone),
    )
    @Test fun offlineDays() = assertEquals(
        offline(UiText.Plural(R.plurals.sync_days_ago, 3)),
        syncBadgeText(SyncStatus.Offline(now.minusSeconds(3 * 86_400)), now, zone),
    )
    @Test fun offlineJustNow() = assertEquals(
        offline(UiText.Res(R.string.sync_just_now)),
        syncBadgeText(SyncStatus.Offline(now.minusSeconds(20)), now, zone),
    )
    @Test fun noData() = assertEquals(UiText.Res(R.string.sync_no_data), syncBadgeText(SyncStatus.NoData, now, zone))
    @Test fun syncing() = assertEquals(UiText.Res(R.string.sync_syncing), syncBadgeText(SyncStatus.Syncing, now, zone))

    @Test fun syncStringsKeepEnglishWording() {
        val en = StringsXml.load("src/main/res/values/strings.xml")
        assertEquals("SYNCED %1\$s", en.strings["sync_live"])
        assertEquals("OFFLINE · last sync %1\$s", en.strings["sync_offline"])
        assertEquals("PARTIAL · SYNC %1\$s", en.strings["sync_partial"])
        assertEquals("%dm ago", en.plurals["sync_minutes_ago"]?.get("other"))
    }
}
