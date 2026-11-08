package dev.ticker.converter.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class ChangeArrowTextTest {
    @Test fun upShowsUpArrow() = assertEquals("▲ 0.21%", changeArrowText(0.2149))
    @Test fun downShowsDownArrow() = assertEquals("▼ 1.85%", changeArrowText(-1.851))
    @Test fun roundsToFlatAsUp() = assertEquals("▲ 0.00%", changeArrowText(-0.001))
}
