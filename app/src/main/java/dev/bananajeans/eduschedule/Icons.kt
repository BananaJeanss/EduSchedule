package dev.bananajeans.eduschedule

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

// Official Material Symbols Rounded vector assets are vendored in res/drawable.
// Keep this mapping explicit so every icon used by the app is reviewable.
@DrawableRes
private fun glyphResource(name: String): Int = when (name) {
    "back" -> R.drawable.ic_arrow_back_24
    "next" -> R.drawable.ic_chevron_right_24
    "more" -> R.drawable.ic_more_vert_24
    "day" -> R.drawable.ic_calendar_view_day_24
    "week" -> R.drawable.ic_calendar_view_week_24
    "browse" -> R.drawable.ic_search_24
    "refresh" -> R.drawable.ic_refresh_24
    "check" -> R.drawable.ic_check_24
    "offline" -> R.drawable.ic_cloud_off_24
    else -> error("Unknown Material Symbol: $name")
}

@Composable
fun Glyph(name: String, description: String? = null) {
    Icon(
        painter = painterResource(glyphResource(name)),
        contentDescription = description,
        modifier = Modifier.size(24.dp)
    )
}
