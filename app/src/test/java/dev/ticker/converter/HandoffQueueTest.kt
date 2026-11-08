package dev.ticker.converter

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class HandoffQueueTest {
    private val h = CameraHandoff("EUR", BigDecimal("12.99"))

    // Spec extras §4: "Open in Ticker" can start the app cold, before Convert collects.
    @Test fun deliversValueSentBeforeCollection() = runTest {
        val q = HandoffQueue()
        assertTrue(q.send(h))
        assertEquals(h, q.flow.first())
    }

    @Test fun deliversOnlyOnce() = runTest {
        val q = HandoffQueue()
        q.send(h)
        q.flow.first()
        assertNull(withTimeoutOrNull(100) { q.flow.first() })
    }
}
