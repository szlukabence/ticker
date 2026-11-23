package dev.ticker.converter.ui.text

import dev.ticker.converter.R
import org.junit.Assert.assertEquals
import org.junit.Test

class UiTextTest {
    @Test fun resKeepsIdAndArgs() {
        val t = UiText.Res(R.string.tab_convert, listOf("x"))
        assertEquals(R.string.tab_convert, t.id)
        assertEquals(listOf<Any>("x"), t.args)
    }
}
