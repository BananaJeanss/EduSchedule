package dev.bananajeans.eduschedule

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp

/** Persisted independently of the light/dark preference. Invalid stored values fall back safely. */
data class ThemeColors(
    val primary: String = "#426437",
    val secondary: String = "#546B91",
    val surface: String = "#F9FAF4"
)

internal fun validHexColor(value: String): Boolean = Regex("^#[0-9a-fA-F]{6}$").matches(value)
internal fun parseHexColor(value: String): Color? =
    value.takeIf(::validHexColor)?.substring(1)?.toLongOrNull(16)?.let { Color((0xFF000000L or it).toInt()) }

private fun foreground(background: Color): Color = if (background.luminance() > 0.179f) Color.Black else Color.White

internal fun themeScheme(palette: String, dark: Boolean, custom: ThemeColors): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    val (primary, secondary, surface) = when (palette) {
        "Catppuccin" -> if (dark) Triple(Color(0xFFCBA6F7), Color(0xFF89B4FA), Color(0xFF1E1E2E))
            else Triple(Color(0xFF8839EF), Color(0xFF1E66F5), Color(0xFFEFF1F5))
        "Ocean" -> if (dark) Triple(Color(0xFF80CBC4), Color(0xFF90CAF9), Color(0xFF101F2D))
            else Triple(Color(0xFF006B69), Color(0xFF275C9D), Color(0xFFF2F8FA))
        "Custom" -> {
            val p = parseHexColor(custom.primary) ?: Color(0xFF426437)
            val s = parseHexColor(custom.secondary) ?: Color(0xFF546B91)
            val bg = parseHexColor(custom.surface) ?: Color(0xFFF9FAF4)
            if (dark) Triple(lerp(p, Color.White, 0.35f), lerp(s, Color.White, 0.35f), lerp(Color(0xFF10131A), bg, 0.18f))
            else Triple(p, s, bg)
        }
        else -> if (dark) Triple(Color(0xFFB5CEA8), Color(0xFFAFCCB5), Color(0xFF111511))
            else Triple(Color(0xFF426437), Color(0xFF546B91), Color(0xFFF9FAF4))
    }
    val primaryContainer = lerp(primary, surface, if (dark) 0.74f else 0.82f)
    val secondaryContainer = lerp(secondary, surface, if (dark) 0.74f else 0.82f)
    val onSurface = foreground(surface)
    return base.copy(
        primary = primary, onPrimary = foreground(primary),
        primaryContainer = primaryContainer, onPrimaryContainer = foreground(primaryContainer),
        secondary = secondary, onSecondary = foreground(secondary),
        secondaryContainer = secondaryContainer, onSecondaryContainer = foreground(secondaryContainer),
        surface = surface, onSurface = onSurface,
        surfaceContainer = lerp(surface, onSurface, if (dark) 0.08f else 0.04f),
        surfaceContainerLow = lerp(surface, onSurface, if (dark) 0.04f else 0.02f),
        surfaceContainerHigh = lerp(surface, onSurface, if (dark) 0.12f else 0.07f),
        background = surface, onBackground = onSurface,
        onSurfaceVariant = lerp(onSurface, surface, if (dark) 0.25f else 0.32f),
        outline = lerp(onSurface, surface, 0.48f)
    )
}
