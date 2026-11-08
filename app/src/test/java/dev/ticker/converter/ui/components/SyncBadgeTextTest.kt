package dev.ticker.converter.ui.components

import dev.ticker.converter.core.model.RateSource
import dev.ticker.converter.core.model.SyncStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class SyncBadgeTextTest {
    private val zone = ZoneId.of("Europe/Budapest")
    private val now = Instant.parse("2026-11-07T08:41:07Z") // 09:41:07 in Budapest

    @Test fun live() = assertEquals("SYNCED 09:41:07", syncBadgeText(SyncStatus.Live(now), now, zone))
    @Test fun partial() = assertEquals(
        "PARTIAL · SYNC 09:41:07",
        syncBadgeText(SyncStatus.Partial(now, setOf(RateSource.FAWAZ)), now, zone),
    )
    @Test fun offlineRelative() = assertEquals(
        "OFFLINE · last sync 2h ago",
        syncBadgeText(SyncStatus.Offline(now.minusSeconds(2 * 3600 + 5)), now, zone),
    )
    @Test fun offlineMinutes() = assertEquals("OFFLINE · last sync 5m ago", syncBadgeText(SyncStatus.Offline(now.minusSeconds(300)), now, zone))
    @Test fun offlineDays() = assertEquals("OFFLINE · last sync 3d ago", syncBadgeText(SyncStatus.Offline(now.minusSeconds(3 * 86_400)), now, zone))
    @Test fun offlineJustNow() = assertEquals("OFFLINE · last sync just now", syncBadgeText(SyncStatus.Offline(now.minusSeconds(20)), now, zone))
    @Test fun noData() = assertEquals("NO DATA", syncBadgeText(SyncStatus.NoData, now, zone))
    @Test fun syncing() = assertEquals("SYNCING…", syncBadgeText(SyncStatus.Syncing, now, zone))
}
