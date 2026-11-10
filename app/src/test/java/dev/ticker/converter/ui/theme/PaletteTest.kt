package dev.ticker.converter.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import dev.ticker.converter.core.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaletteTest {
    private fun contrast(fg: Color, bg: Color): Double {
        val a = fg.compositeOver(bg).luminance() + 0.05
        val b = bg.luminance() + 0.05
        return maxOf(a, b) / minOf(a, b)
    }

    private fun assertReadable(p: TickerPalette, name: String) {
        val backgrounds = mapOf("bg" to p.bg, "tile" to p.tile.compositeOver(p.bg), "surface" to p.surface)
        for ((bgName, bg) in backgrounds) {
            for ((fgName, fg) in mapOf("text" to p.text, "accent" to p.accent, "negative" to p.negative)) {
                val c = contrast(fg, bg)
                assertTrue("$name $fgName on $bgName is $c:1", c >= 4.5)
            }
            val dim = contrast(p.dim, bg)
            assertTrue("$name dim on $bgName is $dim:1", dim >= 3.0)
        }
        val key = contrast(p.onAccent, p.accent)
        assertTrue("$name onAccent on accent is $key:1", key >= 4.5)
        val active = contrast(p.text, p.activeTile.compositeOver(p.bg))
        assertTrue("$name text on active tile is $active:1", active >= 4.5)
    }

    @Test fun darkPaletteIsReadable() = assertReadable(DarkPalette, "dark")

    @Test fun lightPaletteIsReadable() = assertReadable(LightPalette, "light")

    @Test fun darkPaletteKeepsTheNeonLook() {
        assertEquals(Color(0xFF000000), DarkPalette.bg)
        assertEquals(Color(0xFF2BFF88), DarkPalette.accent)
        assertTrue(DarkPalette.glows)
        assertTrue(!LightPalette.glows)
    }

    @Test fun themeModeResolves() {
        assertTrue(ThemeMode.SYSTEM.isDark(systemDark = true))
        assertTrue(!ThemeMode.SYSTEM.isDark(systemDark = false))
        assertTrue(ThemeMode.DARK.isDark(systemDark = false))
        assertTrue(!ThemeMode.LIGHT.isDark(systemDark = true))
    }
}
