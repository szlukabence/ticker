package dev.ticker.converter.widget

import androidx.compose.ui.graphics.Color
import dev.ticker.converter.core.model.ThemeMode
import dev.ticker.converter.ui.theme.DarkPalette
import dev.ticker.converter.ui.theme.LightPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetThemeTest {
    @Test fun systemIsDayNight() {
        val c = WidgetTheme.colors(ThemeMode.SYSTEM)
        assertTrue(c.dayNight)
        assertEquals(WidgetTheme.Light, c.light)
        assertEquals(WidgetTheme.Dark, c.dark)
    }

    @Test fun lightIsFixedLight() {
        val c = WidgetTheme.colors(ThemeMode.LIGHT)
        assertFalse(c.dayNight)
        assertEquals(WidgetTheme.Light, c.light)
        assertEquals(WidgetTheme.Light, c.dark)
    }

    @Test fun darkIsFixedDark() {
        val c = WidgetTheme.colors(ThemeMode.DARK)
        assertFalse(c.dayNight)
        assertEquals(WidgetTheme.Dark, c.light)
        assertEquals(WidgetTheme.Dark, c.dark)
    }

    @Test fun lightBgIsWhiteCard() = assertEquals(LightPalette.surface, WidgetTheme.Light.bg)

    @Test fun darkBgIsBlackAndRolesCopyPalette() {
        assertEquals(Color.Black, WidgetTheme.Dark.bg)
        assertEquals(DarkPalette.accent, WidgetTheme.Dark.accent)
        assertEquals(LightPalette.text, WidgetTheme.Light.text)
    }
}
