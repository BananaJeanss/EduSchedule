package dev.bananajeans.eduschedule.wear

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.dynamicColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import dev.bananajeans.eduschedule.sync.WearSettings

private fun foreground(color: Color) = if (color.luminance() > .179f) Color.Black else Color.White
private fun parse(value: String, fallback: Color): Color =
    value.takeIf { Regex("^#[0-9a-fA-F]{6}$").matches(it) }
        ?.substring(1)?.toLongOrNull(16)?.let { Color((0xFF000000L or it).toInt()) } ?: fallback

/** Map every surface/accent role together, rather than mixing a phone palette with Wear defaults. */
internal fun wearColorScheme(settings: WearSettings?, dark: Boolean): ColorScheme {
    val (primary, secondary, background) = when (settings?.palette) {
        "Catppuccin" -> if (dark) Triple(Color(0xFFCBA6F7), Color(0xFF89B4FA), Color(0xFF1E1E2E))
            else Triple(Color(0xFF8839EF), Color(0xFF1E66F5), Color(0xFFEFF1F5))
        "Ocean" -> if (dark) Triple(Color(0xFF80CBC4), Color(0xFF90CAF9), Color(0xFF101F2D))
            else Triple(Color(0xFF006B69), Color(0xFF275C9D), Color(0xFFF2F8FA))
        "Custom" -> {
            val p = parse(settings.primary, Color(0xFF426437))
            val s = parse(settings.secondary, Color(0xFF546B91))
            val bg = parse(settings.surface, Color(0xFFF9FAF4))
            if (dark) Triple(lerp(p, Color.White, .35f), lerp(s, Color.White, .35f), lerp(Color(0xFF10131A), bg, .18f))
            else Triple(p, s, bg)
        }
        else -> if (dark) Triple(Color(0xFFB5CEA8), Color(0xFFAFCCB5), Color(0xFF111511))
            else Triple(Color(0xFF426437), Color(0xFF546B91), Color(0xFFF9FAF4))
    }
    val onBackground = foreground(background)
    val primaryContainer = lerp(primary, background, if (dark) .74f else .82f)
    val secondaryContainer = lerp(secondary, background, if (dark) .74f else .82f)
    val tertiary = secondary
    val error = if (dark) Color(0xFFFFB4AB) else Color(0xFFBA1A1A)
    val errorContainer = lerp(error, background, .8f)
    return ColorScheme(
        primary = primary, primaryDim = lerp(primary, background, .15f),
        primaryContainer = primaryContainer, onPrimary = foreground(primary), onPrimaryContainer = foreground(primaryContainer),
        secondary = secondary, secondaryDim = lerp(secondary, background, .15f),
        secondaryContainer = secondaryContainer, onSecondary = foreground(secondary), onSecondaryContainer = foreground(secondaryContainer),
        tertiary = tertiary, tertiaryDim = lerp(tertiary, background, .15f), tertiaryContainer = secondaryContainer,
        onTertiary = foreground(tertiary), onTertiaryContainer = foreground(secondaryContainer),
        background = background, onBackground = onBackground,
        surfaceContainerLow = lerp(background, onBackground, if (dark) .04f else .02f),
        surfaceContainer = lerp(background, onBackground, if (dark) .08f else .04f),
        surfaceContainerHigh = lerp(background, onBackground, if (dark) .12f else .07f),
        onSurface = onBackground, onSurfaceVariant = lerp(onBackground, background, if (dark) .25f else .32f),
        outline = lerp(onBackground, background, .48f), outlineVariant = lerp(onBackground, background, .7f),
        error = error, errorDim = lerp(error, background, .15f), errorContainer = errorContainer,
        onError = foreground(error), onErrorContainer = foreground(errorContainer)
    )
}

@Composable internal fun WearTheme(settings: WearSettings?, content: @Composable () -> Unit) {
    val dark = when (settings?.theme) { "Light" -> false; "Dark" -> true; else -> isSystemInDarkTheme() }
    val dynamic = if (settings?.palette == "Default" && settings.theme != "Light") dynamicColorScheme(LocalContext.current) else null
    MaterialTheme(colorScheme = dynamic ?: wearColorScheme(settings, dark), content = content)
}
