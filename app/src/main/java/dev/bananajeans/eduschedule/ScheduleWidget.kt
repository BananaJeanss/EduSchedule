package dev.bananajeans.eduschedule

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.os.Build
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal enum class WidgetMode { TODAY, TOMORROW, NEXT, UPCOMING, WEEK }
internal enum class WidgetBorder { NONE, SOLID, DASHED }
internal enum class WidgetPalette { FOLLOW_APP, WALLPAPER, DEFAULT, CATPPUCCIN, OCEAN }
internal data class WidgetOptions(
    val mode: WidgetMode = WidgetMode.TODAY,
    val rooms: Boolean = true,
    val teachers: Boolean = false,
    val maxRows: Int = 4,
    val border: WidgetBorder = WidgetBorder.NONE,
    val palette: WidgetPalette = WidgetPalette.FOLLOW_APP
)

internal fun WidgetMode.label(): Int = when (this) {
    WidgetMode.TODAY -> R.string.widget_today
    WidgetMode.TOMORROW -> R.string.widget_tomorrow
    WidgetMode.NEXT -> R.string.widget_next
    WidgetMode.UPCOMING -> R.string.widget_upcoming
    WidgetMode.WEEK -> R.string.widget_week
}

internal data class WidgetColors(val surface: Int, val text: Int, val accent: Int, val outline: Int)

internal fun widgetColors(context: Context, prefs: Preferences, options: WidgetOptions): WidgetColors {
    val dark = prefs.theme == "Dark" ||
        (prefs.theme == "System" && context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES)
    val wallpaper = options.palette == WidgetPalette.WALLPAPER ||
        (options.palette == WidgetPalette.FOLLOW_APP && prefs.palette == "Default" && prefs.dynamic)
    val scheme = if (wallpaper && Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        val palette = when (options.palette) {
            WidgetPalette.CATPPUCCIN -> "Catppuccin"
            WidgetPalette.OCEAN -> "Ocean"
            WidgetPalette.DEFAULT, WidgetPalette.WALLPAPER -> "Default"
            WidgetPalette.FOLLOW_APP -> prefs.palette
        }
        themeScheme(palette, dark, prefs.customColors)
    }
    return WidgetColors(scheme.surfaceContainer.toArgb(), scheme.onSurface.toArgb(),
        scheme.primary.toArgb(), scheme.outline.toArgb())
}

/** Launcher-safe rounded surface, sized to the widget; never fetches a timetable. */
internal fun widgetBackground(width: Int, height: Int, colors: WidgetColors, border: WidgetBorder, density: Float): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val radius = 28f * density
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors.surface }
    canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radius, radius, paint)
    if (border != WidgetBorder.NONE) {
        val inset = 2f * density
        paint.color = colors.outline
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f * density
        if (border == WidgetBorder.DASHED) paint.pathEffect = DashPathEffect(floatArrayOf(8f * density, 6f * density), 0f)
        canvas.drawRoundRect(inset, inset, width - inset, height - inset, radius - inset, radius - inset, paint)
    }
    return bitmap
}

internal object ScheduleWidgets {
    private const val SETTINGS = "schedule_widgets"
    private val providers = listOf(
        ScheduleWidgetProvider::class.java,
        NextScheduleWidgetProvider::class.java,
        UpcomingScheduleWidgetProvider::class.java,
        WeekScheduleWidgetProvider::class.java
    )

    private fun initialMode(context: Context, id: Int): WidgetMode {
        val name = AppWidgetManager.getInstance(context).getAppWidgetInfo(id)?.provider?.className
        return when (name) {
            NextScheduleWidgetProvider::class.java.name -> WidgetMode.NEXT
            UpcomingScheduleWidgetProvider::class.java.name -> WidgetMode.UPCOMING
            WeekScheduleWidgetProvider::class.java.name -> WidgetMode.WEEK
            else -> WidgetMode.TODAY
        }
    }

    fun options(context: Context, id: Int): WidgetOptions {
        val prefs = context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE)
        return WidgetOptions(
            runCatching { WidgetMode.valueOf(prefs.getString("$id.mode", null) ?: initialMode(context, id).name) }
                .getOrDefault(initialMode(context, id)),
            prefs.getBoolean("$id.rooms", true), prefs.getBoolean("$id.teachers", false),
            prefs.getInt("$id.maxRows", 4).coerceIn(1, 4),
            runCatching { WidgetBorder.valueOf(prefs.getString("$id.border", "NONE")!!) }.getOrDefault(WidgetBorder.NONE),
            runCatching { WidgetPalette.valueOf(prefs.getString("$id.palette", "FOLLOW_APP")!!) }.getOrDefault(WidgetPalette.FOLLOW_APP)
        )
    }
    fun save(context: Context, id: Int, options: WidgetOptions) {
        context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE).edit {
            putString("$id.mode", options.mode.name)
            putBoolean("$id.rooms", options.rooms)
            putBoolean("$id.teachers", options.teachers)
            putInt("$id.maxRows", options.maxRows.coerceIn(1, 4))
            putString("$id.border", options.border.name)
            putString("$id.palette", options.palette.name)
        }
    }
    fun remove(context: Context, ids: IntArray) {
        context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE).edit {
            ids.forEach { id ->
                listOf("mode", "rooms", "teachers", "maxRows", "border", "palette").forEach { remove("$id.$it") }
            }
        }
    }
    fun publishPreviews(context: Context) {
        if (Build.VERSION.SDK_INT < 35) return
        val settings = context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE)
        val configuration = context.resources.configuration
        val signature = "v2:${BuildConfig.VERSION_CODE}:${configuration.locales.toLanguageTags()}:${configuration.uiMode}"
        val manager = AppWidgetManager.getInstance(context)
        listOf(
            ScheduleWidgetProvider::class.java to R.layout.widget_preview_today,
            NextScheduleWidgetProvider::class.java to R.layout.widget_preview_next,
            UpcomingScheduleWidgetProvider::class.java to R.layout.widget_preview_upcoming,
            WeekScheduleWidgetProvider::class.java to R.layout.widget_preview_week
        ).forEach { (provider, layout) ->
            val key = "preview_${provider.simpleName}"
            if (settings.getString(key, null) != signature) {
                // Record success only. A throttled provider can retry on the next launch;
                // complete previewImage artwork is available immediately either way.
                val accepted = runCatching {
                    manager.setWidgetPreview(ComponentName(context, provider),
                        android.appwidget.AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN,
                        RemoteViews(context.packageName, layout))
                }.getOrDefault(false)
                if (accepted) settings.edit { putString(key, signature) }
            }
        }
    }
    fun refresh(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = providers.flatMap { manager.getAppWidgetIds(ComponentName(context, it)).toList() }
        if (ids.isNotEmpty()) ScheduleWidgetProvider.update(context.applicationContext, manager, ids.toIntArray())
    }
}

open class ScheduleWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        update(context, manager, ids) { pending.finish() }
    }
    override fun onDeleted(context: Context, ids: IntArray) = ScheduleWidgets.remove(context, ids)
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) {
        super.onAppWidgetOptionsChanged(context, manager, id, newOptions)
        update(context, manager, intArrayOf(id))
    }

    companion object {
        fun update(context: Context, manager: AppWidgetManager, ids: IntArray, done: () -> Unit = {}) {
            // Render cached timetables off the broadcast/main thread. The launcher can request
            // many instances at once; each keeps its own display configuration.
            CoroutineScope(Dispatchers.IO).launch {
              try {
                val prefs = Preferences(context)
                val localized = AppLocale.wrap(context, prefs.language)
                val today = runCatching { LocalDate.now(ZoneId.of(prefs.zone)) }.getOrDefault(LocalDate.now())
                val now = runCatching { LocalTime.now(ZoneId.of(prefs.zone)) }.getOrDefault(LocalTime.now())
                val repository = Repository(context)
                ids.forEach { id -> runCatching {
                    val options = ScheduleWidgets.options(context, id)
                    val views = RemoteViews(context.packageName, R.layout.widget_schedule)
                    val title = localized.getString(options.mode.label())
                    val colors = widgetColors(localized, prefs, options)
                    val size = manager.getAppWidgetOptions(id)
                    val width = size.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 180).coerceIn(100, 400)
                    val heightDp = size.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 180).coerceIn(80, 300)
                    // A one-pixel-per-dp bitmap stays below the launcher RemoteViews bitmap budget.
                    views.setImageViewBitmap(R.id.widget_background,
                        widgetBackground(width, heightDp, colors, options.border, 1f))
                    views.setTextColor(R.id.widget_title, colors.accent)
                    listOf(R.id.widget_row_1, R.id.widget_row_2, R.id.widget_row_3, R.id.widget_row_4).forEach {
                        views.setTextColor(it, colors.text)
                    }
                    views.setTextViewText(R.id.widget_title, title)
                    val rows = runCatching {
                        if (prefs.host.isBlank() || prefs.home.isBlank()) emptyList()
                        else widgetRows(repository, prefs, today, now, options, localized)
                    }.getOrDefault(emptyList())
                    val empty = if (prefs.home.isBlank()) R.string.widget_choose_class else R.string.widget_no_lessons
                    val text = rows.ifEmpty { listOf(localized.getString(empty)) }
                    val fittingRows = when {
                        heightDp < 120 -> 1
                        heightDp < 160 -> 2
                        heightDp < 210 -> 3
                        else -> 4
                    }
                    val visible = text.take(minOf(options.maxRows, fittingRows))
                    listOf(R.id.widget_row_1, R.id.widget_row_2, R.id.widget_row_3, R.id.widget_row_4).forEachIndexed { index, viewId ->
                        views.setViewVisibility(viewId, if (index < visible.size) View.VISIBLE else View.GONE)
                        if (index < visible.size) views.setTextViewText(viewId, visible[index])
                    }
                    val intent = Intent(context, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    views.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(
                        context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    ))
                    manager.updateAppWidget(id, views)
                } }
              } finally { done() }
            }
        }

        private suspend fun widgetRows(
            repository: Repository, prefs: Preferences, date: LocalDate, now: LocalTime,
            options: WidgetOptions, context: Context
        ): List<String> {
            val selection = Selection(ScheduleKind.CLASS, prefs.home)
            suspend fun blocks(day: LocalDate) = repository.loadCached(prefs.host, day)?.timetable
                ?.lessonBlocksOn(day, selection, prefs.hiddenGroups, prefs.cycleWeek)
            fun line(block: LessonBlock): String {
                val first = block.lessons.first()
                val subject = if (block.isSplit) context.getString(R.string.widget_group_count, block.lessons.size) else first.subject
                val details = buildList {
                    if (options.rooms && first.roomNames.isNotBlank() && !block.isSplit) add(first.roomNames)
                    if (options.teachers && first.teacherNames.isNotBlank() && !block.isSplit) add(first.teacherNames)
                }.joinToString(" · ")
                return "${block.start ?: "—"}  $subject${if (details.isBlank()) "" else " · $details"}"
            }
            return when (options.mode) {
                WidgetMode.TODAY -> blocks(date)?.take(4)?.map(::line)
                    ?: listOf(context.getString(R.string.widget_open_to_save))
                WidgetMode.TOMORROW -> blocks(date.plusDays(1))?.take(4)?.map(::line)
                    ?: listOf(context.getString(R.string.widget_open_to_save))
                WidgetMode.NEXT -> {
                    // Include the current lesson, then look ahead across the week.
                    (0L..6L).firstNotNullOfOrNull { offset ->
                        val day = date.plusDays(offset)
                        blocks(day)?.firstOrNull { offset > 0 || it.end == null || it.end > now }
                            ?.let { listOf(if (offset == 0L) line(it) else "${day.format(DateTimeFormatter.ofPattern("EEE d", context.resources.configuration.locales[0]))} · ${line(it)}") }
                    }.orEmpty()
                }
                WidgetMode.UPCOMING -> {
                    val upcoming = (0L..6L).flatMap { offset ->
                        val day = date.plusDays(offset)
                        blocks(day).orEmpty().filter { offset > 0 || it.end == null || it.end > now }.map { block ->
                            if (offset == 0L) line(block)
                            else "${day.format(DateTimeFormatter.ofPattern("EEE d", context.resources.configuration.locales[0]))} · ${line(block)}"
                        }
                    }.take(4)
                    upcoming.ifEmpty { listOf(context.getString(R.string.widget_open_to_save)) }
                }
                WidgetMode.WEEK -> (0L..3L).map { offset ->
                    val day = date.plusDays(offset)
                    val count = blocks(day)?.size
                    "${day.format(DateTimeFormatter.ofPattern("EEE d", context.resources.configuration.locales[0]))} · ${if (count == null) context.getString(R.string.widget_not_saved) else context.getString(R.string.widget_lessons_count, count)}"
                }
            }
        }
    }
}

class NextScheduleWidgetProvider : ScheduleWidgetProvider()
class UpcomingScheduleWidgetProvider : ScheduleWidgetProvider()
class WeekScheduleWidgetProvider : ScheduleWidgetProvider()

class ScheduleWidgetConfigActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase, Preferences(newBase).language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        setResult(RESULT_CANCELED)
        val prefs = Preferences(this)
        setContent {
            var options by remember { mutableStateOf(ScheduleWidgets.options(this, id)) }
            EduTheme(prefs.theme, prefs.dynamic, prefs.palette, prefs.customColors) {
                WidgetConfigScreen(options, { options = it }) {
                    ScheduleWidgets.save(this@ScheduleWidgetConfigActivity, id, options)
                    ScheduleWidgetProvider.update(this@ScheduleWidgetConfigActivity,
                        AppWidgetManager.getInstance(this@ScheduleWidgetConfigActivity), intArrayOf(id))
                    setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
                    finish()
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WidgetConfigScreen(options: WidgetOptions, onChange: (WidgetOptions) -> Unit, onSave: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember(context) { Preferences(context) }
    val colors = widgetColors(context, prefs, options)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(stringResource(R.string.widget_configure), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.widget_home_class_hint),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Text(stringResource(R.string.widget_preview), style = MaterialTheme.typography.titleMedium)
        val shape = MaterialTheme.shapes.extraLarge
        val previewBorder = if (options.border == WidgetBorder.SOLID) BorderStroke(2.dp, Color(colors.outline)) else null
        Surface(Modifier.fillMaxWidth().heightIn(min = 128.dp), shape = shape,
            color = Color(colors.surface), border = previewBorder) {
            Box {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(options.mode.label()), color = Color(colors.accent),
                        style = MaterialTheme.typography.labelLarge)
                    Text(stringResource(R.string.widget_open_to_save), color = Color(colors.text),
                        style = MaterialTheme.typography.bodyMedium)
                }
                if (options.border == WidgetBorder.DASHED) {
                    Box(Modifier.matchParentSize().drawWithContent {
                        drawContent()
                        val inset = 2.dp.toPx()
                        drawRoundRect(
                            color = Color(colors.outline),
                            topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                            size = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2),
                            cornerRadius = CornerRadius(26.dp.toPx()),
                            style = Stroke(width = 2.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())))
                        )
                    })
                }
            }
        }

        Text(stringResource(R.string.widget_display), style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WidgetMode.entries.forEach { mode ->
                FilterChip(selected = options.mode == mode, onClick = { onChange(options.copy(mode = mode)) },
                    label = { Text(stringResource(mode.label())) })
            }
        }

        Text(stringResource(R.string.widget_max_rows), style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..4).forEach { count ->
                FilterChip(selected = options.maxRows == count, onClick = { onChange(options.copy(maxRows = count)) },
                    label = { Text(count.toString()) })
            }
        }
        SettingSwitch(stringResource(R.string.widget_show_rooms), "", options.rooms) { onChange(options.copy(rooms = it)) }
        SettingSwitch(stringResource(R.string.widget_show_teachers), "", options.teachers) { onChange(options.copy(teachers = it)) }

        HorizontalDivider()
        Text(stringResource(R.string.widget_colors), style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WidgetPalette.entries.forEach { palette ->
                val label = when (palette) {
                    WidgetPalette.FOLLOW_APP -> R.string.widget_follow_app
                    WidgetPalette.WALLPAPER -> R.string.wallpaper_colors
                    WidgetPalette.DEFAULT -> R.string.palette_default
                    WidgetPalette.CATPPUCCIN -> R.string.palette_catppuccin
                    WidgetPalette.OCEAN -> R.string.palette_ocean
                }
                FilterChip(selected = options.palette == palette, onClick = { onChange(options.copy(palette = palette)) },
                    label = { Text(stringResource(label)) })
            }
        }
        Text(stringResource(R.string.widget_border), style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WidgetBorder.entries.forEach { border ->
                val label = when (border) {
                    WidgetBorder.NONE -> R.string.widget_border_none
                    WidgetBorder.SOLID -> R.string.widget_border_solid
                    WidgetBorder.DASHED -> R.string.widget_border_dashed
                }
                FilterChip(selected = options.border == border, onClick = { onChange(options.copy(border = border)) },
                    label = { Text(stringResource(label)) })
            }
        }
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.widget_save)) }
    }
}

