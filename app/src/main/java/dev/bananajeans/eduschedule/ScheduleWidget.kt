package dev.bananajeans.eduschedule

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal enum class WidgetMode { TODAY, NEXT, WEEK }
internal data class WidgetOptions(val mode: WidgetMode = WidgetMode.TODAY, val rooms: Boolean = true, val teachers: Boolean = false)

internal object ScheduleWidgets {
    private const val SETTINGS = "schedule_widgets"
    fun options(context: Context, id: Int): WidgetOptions {
        val prefs = context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE)
        return WidgetOptions(
            runCatching { WidgetMode.valueOf(prefs.getString("$id.mode", "TODAY")!!) }.getOrDefault(WidgetMode.TODAY),
            prefs.getBoolean("$id.rooms", true), prefs.getBoolean("$id.teachers", false)
        )
    }
    fun save(context: Context, id: Int, options: WidgetOptions) {
        context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE).edit {
            putString("$id.mode", options.mode.name)
            putBoolean("$id.rooms", options.rooms)
            putBoolean("$id.teachers", options.teachers)
        }
    }
    fun remove(context: Context, ids: IntArray) {
        context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE).edit {
            ids.forEach { id -> remove("$id.mode"); remove("$id.rooms"); remove("$id.teachers") }
        }
    }
    fun refresh(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, ScheduleWidgetProvider::class.java))
        if (ids.isNotEmpty()) ScheduleWidgetProvider.update(context.applicationContext, manager, ids)
    }
}

class ScheduleWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        update(context, manager, ids) { pending.finish() }
    }
    override fun onDeleted(context: Context, ids: IntArray) = ScheduleWidgets.remove(context, ids)

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
                    val title = when (options.mode) {
                        WidgetMode.TODAY -> localized.getString(R.string.widget_today)
                        WidgetMode.NEXT -> localized.getString(R.string.widget_next)
                        WidgetMode.WEEK -> localized.getString(R.string.widget_week)
                    }
                    views.setTextViewText(R.id.widget_title, title)
                    val rows = runCatching {
                        if (prefs.host.isBlank() || prefs.home.isBlank()) emptyList()
                        else widgetRows(repository, prefs, today, now, options, localized)
                    }.getOrDefault(emptyList())
                    val empty = if (prefs.home.isBlank()) R.string.widget_choose_class else R.string.widget_no_lessons
                    val text = rows.ifEmpty { listOf(localized.getString(empty)) }
                    listOf(R.id.widget_row_1, R.id.widget_row_2, R.id.widget_row_3, R.id.widget_row_4).forEachIndexed { index, viewId ->
                        views.setViewVisibility(viewId, if (index < text.size) View.VISIBLE else View.GONE)
                        if (index < text.size) views.setTextViewText(viewId, text[index])
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
                WidgetMode.NEXT -> {
                    // Include the current lesson, then look ahead across the week.
                    (0L..6L).firstNotNullOfOrNull { offset ->
                        val day = date.plusDays(offset)
                        blocks(day)?.firstOrNull { offset > 0 || it.end == null || it.end > now }
                            ?.let { listOf(if (offset == 0L) line(it) else "${day.format(DateTimeFormatter.ofPattern("EEE d", context.resources.configuration.locales[0]))} · ${line(it)}") }
                    }.orEmpty()
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

class ScheduleWidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        setResult(RESULT_CANCELED)
        val prefs = Preferences(this)
        val localized = AppLocale.wrap(this, prefs.language)
        setContent {
            var options by remember { mutableStateOf(ScheduleWidgets.options(this, id)) }
            EduTheme(prefs.theme, prefs.dynamic, prefs.palette, prefs.customColors) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(localized.getString(R.string.widget_configure), style = MaterialTheme.typography.headlineMedium)
                    Text(localized.getString(R.string.widget_home_class_hint), style = MaterialTheme.typography.bodyMedium)
                    WidgetMode.entries.forEach { mode ->
                        val label = when (mode) {
                            WidgetMode.TODAY -> R.string.widget_today
                            WidgetMode.NEXT -> R.string.widget_next
                            WidgetMode.WEEK -> R.string.widget_week
                        }
                        FilterChip(selected = options.mode == mode, onClick = { options = options.copy(mode = mode) }, label = { Text(localized.getString(label)) })
                    }
                    Row { Text(localized.getString(R.string.widget_show_rooms), Modifier.weight(1f)); Switch(options.rooms, { options = options.copy(rooms = it) }) }
                    Row { Text(localized.getString(R.string.widget_show_teachers), Modifier.weight(1f)); Switch(options.teachers, { options = options.copy(teachers = it) }) }
                    Button(onClick = {
                        ScheduleWidgets.save(this@ScheduleWidgetConfigActivity, id, options)
                        ScheduleWidgetProvider.update(this@ScheduleWidgetConfigActivity, AppWidgetManager.getInstance(this@ScheduleWidgetConfigActivity), intArrayOf(id))
                        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
                        finish()
                    }, modifier = Modifier.fillMaxWidth()) { Text(localized.getString(R.string.widget_add)) }
                }
            }
        }
    }
}
