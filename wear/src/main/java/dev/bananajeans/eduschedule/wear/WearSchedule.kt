package dev.bananajeans.eduschedule.wear

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.*
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import dev.bananajeans.eduschedule.sync.WearLesson
import dev.bananajeans.eduschedule.sync.WearSettings
import dev.bananajeans.eduschedule.sync.WearSnapshot
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private fun localized(context: Context, settings: WearSettings?): Context {
    val tag = settings?.language?.takeIf { it == "en" || it == "et" } ?: return context
    return context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) })
}

@Composable internal fun WearSchedule(snapshot: WearSnapshot?, sync: () -> Unit, clock: Instant? = null) {
    val context = LocalContext.current
    val strings = remember(context, snapshot?.settings?.language) { localized(context, snapshot?.settings) }
    val locale = strings.resources.configuration.locales[0]
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var instant by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(lifecycle, clock) {
        if (clock == null) lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                instant = Instant.now()
                delay(60_000 - instant.toEpochMilli() % 60_000)
            }
        }
    }
    val time = (clock ?: instant).atZone(snapshot?.settings?.zone ?: ZoneId.systemDefault())
    val today = time.toLocalDate()
    var screen by rememberSaveable { mutableStateOf("day") }
    var dateEpoch by rememberSaveable { mutableStateOf<Long?>(null) }
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    var showPast by rememberSaveable(dateEpoch, today.toEpochDay()) { mutableStateOf(false) }
    val shown = dateEpoch?.let(LocalDate::ofEpochDay) ?: today
    val day = snapshot?.days?.firstOrNull { it.date == shown }
    val detail = day?.lessons?.firstOrNull { it.id == detailId }
    val states = lessonStates(day?.lessons.orEmpty(), shown, today, time.toLocalTime())
    fun goToday() { detailId = null; dateEpoch = null; screen = "day" }
    fun back() {
        if (detailId != null) detailId = null else if (screen != "day") screen = "day" else goToday()
    }
    BackHandler(detailId != null || screen != "day" || dateEpoch != null, onBack = ::back)
    WearTheme(snapshot?.settings) {
        Box(Modifier.fillMaxSize()) {
        AppScaffold(timeText = {}) {
            SwipeToDismissBox(onDismissed = {
                if (detailId != null || screen != "day" || dateEpoch != null) back() else (context as? Activity)?.finish()
            }) { isBackground ->
                if (isBackground) Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) else {
                    // Each destination/date owns its scroll state; entering details always starts at its title.
                    key(screen, shown, detailId) {
                        val focusOnOpen = detail != null || (screen == "day" && detailId == null &&
                            states.values.any { it == LessonState.Current || it == LessonState.Next })
                        WearList(initialAnchor = if (focusOnOpen) 1 else -1) {
                            when {
                                snapshot == null || snapshot.settings.homeClass.isBlank() -> {
                                    heading(strings.getString(R.string.day))
                                    message(strings.getString(R.string.setup_phone))
                                    action(strings.getString(R.string.sync_now), "sync", sync)
                                }
                                detailId != null -> {
                                    heading(strings.getString(R.string.lesson))
                                    if (detail == null) message(strings.getString(R.string.lesson_unavailable))
                                    else {
                                        item {
                                            val spec = rememberTransformationSpec()
                                            Card(modifier = Modifier.fillMaxWidth().transformedHeight(this, spec).minimumVerticalContentPadding(CardDefaults.minimumVerticalListContentPadding), transformation = SurfaceTransformation(spec)) {
                                                Text(detail.subjects.joinToString(" / "), style = MaterialTheme.typography.titleMedium)
                                                Text(lessonTime(detail), style = MaterialTheme.typography.bodyMedium)
                                                Text(shown.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)), style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                        if (detail.room.isNotBlank()) metadata(strings.getString(R.string.room), detail.room)
                                        if (detail.teacher.isNotBlank()) metadata(strings.getString(R.string.teacher), detail.teacher)
                                        if (detail.group.isNotBlank()) metadata(strings.getString(R.string.group), detail.group)
                                    }
                                    action(strings.getString(R.string.back), "back", { detailId = null })
                                }
                                screen == "status" -> {
                                    heading(strings.getString(R.string.status))
                                    metadata(strings.getString(R.string.school), snapshot.settings.school)
                                    metadata(strings.getString(R.string.home_class), snapshot.settings.homeClass)
                                    message(strings.getString(R.string.last_sync, snapshot.publishedAt.atZone(snapshot.settings.zone)
                                        .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(locale))))
                                    message(strings.getString(R.string.regular_timetable))
                                    message(strings.getString(R.string.settings_on_phone))
                                    action(strings.getString(R.string.sync_now), "sync", sync)
                                    action(strings.getString(R.string.today), "today", ::goToday)
                                }
                                screen == "week" -> {
                                    heading(strings.getString(R.string.week))
                                    snapshot.days.sortedBy { it.date }.forEach { choice ->
                                        item(key = choice.date.toEpochDay()) {
                                            val spec = rememberTransformationSpec()
                                            Button(onClick = { dateEpoch = choice.date.toEpochDay(); screen = "day" },
                                                modifier = Modifier.fillMaxWidth().transformedHeight(this, spec).minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding).testTag("date-${choice.date}"),
                                                colors = if (choice.date == shown) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors(),
                                                transformation = SurfaceTransformation(spec),
                                                secondaryLabel = { Text(strings.resources.getQuantityString(R.plurals.lesson_count, choice.lessons.size, choice.lessons.size)) },
                                                label = { Text(choice.date.format(DateTimeFormatter.ofPattern("EEE d MMM", locale))) })
                                        }
                                    }
                                    action(strings.getString(R.string.today), "today", ::goToday)
                                }
                                else -> {
                                    heading(shown.format(DateTimeFormatter.ofPattern("EEE d MMM", locale)), snapshot.settings.homeClass)
                                    val lessons = day?.lessons.orEmpty().sortedWith(compareBy(nullsLast()) { it.start })
                                    val focus = lessons.firstOrNull { states[it.id] == LessonState.Current }
                                        ?: lessons.firstOrNull { states[it.id] == LessonState.Next }
                                    if (day == null) {
                                        message(strings.getString(R.string.not_synced))
                                        action(strings.getString(R.string.sync_now), "sync", sync)
                                    } else if (lessons.isEmpty()) message(strings.getString(R.string.no_lessons))
                                    else {
                                        if (focus != null) lesson(focus, states.getValue(focus.id), strings) { detailId = focus.id }
                                        val past = if (shown == today) lessons.filter { states[it.id] == LessonState.Past } else emptyList()
                                        lessons.filter { it.id != focus?.id && (shown != today || showPast || states[it.id] != LessonState.Past) }.forEach { lesson ->
                                            lesson(lesson, states.getValue(lesson.id), strings) { detailId = lesson.id }
                                        }
                                        if (past.isNotEmpty()) action(strings.getString(if (showPast) R.string.hide_earlier else R.string.show_earlier), "earlier", { showPast = !showPast })
                                        if (shown == today && past.size == lessons.size) message(strings.getString(R.string.finished_today))
                                    }
                                    if (dateEpoch != null) action(strings.getString(R.string.today), "today", ::goToday)
                                    action(strings.getString(R.string.week), "week", { screen = "week" })
                                    action(strings.getString(R.string.status), "status", { screen = "status" })
                                }
                            }
                        }
                    }
                }
            }
        }
        // A fixed native clock stays legible while the initially centered list/header scrolls.
        TimeText()
        }
    }
}

@Composable private fun WearList(initialAnchor: Int = -1, content: TransformingLazyColumnScope.() -> Unit) {
    val state = rememberTransformingLazyColumnState(initialAnchorItemIndex = initialAnchor)
    ScreenScaffold(scrollState = state) { padding ->
        // Reserve the clock's band even when an initially centered lesson pushes the header up.
        TransformingLazyColumn(modifier = Modifier.fillMaxSize().padding(top = 24.dp).clipToBounds().testTag("wear-list"), state = state,
            contentPadding = padding, verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

private fun TransformingLazyColumnScope.heading(text: String, secondary: String? = null) = item {
    val spec = rememberTransformationSpec()
    ListHeader(modifier = Modifier.fillMaxWidth().transformedHeight(this, spec).minimumVerticalContentPadding(ListHeaderDefaults.minimumTopListContentPadding), transformation = SurfaceTransformation(spec)) {
        Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
            Text(text, textAlign = TextAlign.Center)
            if (secondary != null) Text(secondary, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
        }
    }
}

private fun TransformingLazyColumnScope.message(text: String) = item {
    // ListHeader supplies transformation and centered, round-safe text padding.
    val spec = rememberTransformationSpec()
    ListHeader(modifier = Modifier.fillMaxWidth().transformedHeight(this, spec), transformation = SurfaceTransformation(spec)) {
        Text(text, style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onBackground)
    }
}

private fun TransformingLazyColumnScope.metadata(label: String, value: String) = item {
    val spec = rememberTransformationSpec()
    Card(modifier = Modifier.fillMaxWidth().transformedHeight(this, spec).minimumVerticalContentPadding(CardDefaults.minimumVerticalListContentPadding), transformation = SurfaceTransformation(spec)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun TransformingLazyColumnScope.action(label: String, tag: String, onClick: () -> Unit) = item {
    val spec = rememberTransformationSpec()
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().transformedHeight(this, spec).minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding).testTag(tag),
        colors = ButtonDefaults.filledTonalButtonColors(), transformation = SurfaceTransformation(spec), label = { Text(label) })
}

private fun lessonTime(lesson: WearLesson): String = "${lesson.start ?: "?"}–${lesson.end ?: "?"}"

private fun TransformingLazyColumnScope.lesson(lesson: WearLesson, state: LessonState, strings: Context, onClick: () -> Unit) = item(key = lesson.id) {
    val spec = rememberTransformationSpec()
    val scheme = MaterialTheme.colorScheme
    val emphasized = state == LessonState.Current || state == LessonState.Next
    val container = if (state == LessonState.Current) scheme.primaryContainer else if (state == LessonState.Next) scheme.secondaryContainer else scheme.surfaceContainer
    val foreground = if (state == LessonState.Current) scheme.onPrimaryContainer else if (state == LessonState.Next) scheme.onSecondaryContainer else scheme.onSurface
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().transformedHeight(this, spec).minimumVerticalContentPadding(CardDefaults.minimumVerticalListContentPadding).testTag("lesson-${lesson.id}"),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = foreground, titleColor = foreground),
        transformation = SurfaceTransformation(spec)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (emphasized || state == LessonState.Past) Text(strings.getString(when (state) {
                LessonState.Current -> R.string.now
                LessonState.Next -> R.string.next
                else -> R.string.earlier
            }), style = MaterialTheme.typography.labelSmall)
            Text(lesson.subjects.joinToString(" / "), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(lessonTime(lesson), style = MaterialTheme.typography.bodyMedium)
            if (lesson.room.isNotBlank()) Text(strings.getString(R.string.room_value, lesson.room), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
