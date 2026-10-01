package dev.bananajeans.eduschedule.wear

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import dev.bananajeans.eduschedule.sync.WearSettings
import java.time.ZoneId
import org.junit.Assert.assertTrue
import org.junit.Test

class WearThemeTest {
    private fun contrast(a: Color, b: Color): Float {
        val x = a.luminance(); val y = b.luminance()
        return (maxOf(x, y) + .05f) / (minOf(x, y) + .05f)
    }
    @Test fun everyPaletteKeepsTextReadableOnItsSurfaces() {
        for (palette in listOf("Default", "Catppuccin", "Ocean", "Custom")) for (dark in listOf(false, true)) {
            val settings = WearSettings("Example", "9.X", ZoneId.of("UTC"), "en", "System", palette,
                "#eacc00", "#ffccee", "#ffffff", false, 0)
            val s = wearColorScheme(settings, dark)
            val pairs = listOf(s.background to s.onBackground, s.surfaceContainerLow to s.onSurface,
                s.surfaceContainer to s.onSurface, s.surfaceContainerHigh to s.onSurface,
                s.primary to s.onPrimary, s.primaryContainer to s.onPrimaryContainer,
                s.secondary to s.onSecondary, s.secondaryContainer to s.onSecondaryContainer)
            pairs.forEach { (background, foreground) ->
                assertTrue("$palette / dark=$dark text contrast", contrast(background, foreground) >= 4.5f)
            }
        }
    }
}
