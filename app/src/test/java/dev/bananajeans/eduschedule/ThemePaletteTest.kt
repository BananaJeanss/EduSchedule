package dev.bananajeans.eduschedule

import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test

class ThemePaletteTest {
    @Test fun customColorsAcceptOnlySixDigitHex() {
        assertTrue(validHexColor("#12abEF"))
        assertFalse(validHexColor("#123"))
        assertFalse(validHexColor("#12345678"))
        assertFalse(validHexColor("#gggggg"))
        assertEquals(Color(0xFF12ABEF), parseHexColor("#12abEF"))
        assertNull(parseHexColor("#bad"))
    }

    @Test fun customAndPresetSchemesKeepForegroundReadable() {
        val bright = themeScheme("Custom", false, ThemeColors("#FFFF00", "#FF0000", "#FFFFFF"))
        val dark = themeScheme("Custom", true, ThemeColors("#FFFF00", "#FF0000", "#FFFFFF"))
        assertEquals(Color.Black, bright.onPrimary)
        assertEquals(Color.Black, bright.onSurface)
        assertEquals(Color.White, dark.onSurface)
        assertEquals(Color(0xFFEFF1F5), themeScheme("Catppuccin", false, ThemeColors()).surface)
        assertEquals(Color(0xFF1E1E2E), themeScheme("Catppuccin", true, ThemeColors()).surface)
    }
}
