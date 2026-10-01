package dev.bananajeans.eduschedule

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

@Composable
fun ChangesScreen(reportId: String, zone: String) {
    val context = LocalContext.current
    val result by produceState<Pair<Boolean, ChangeReport?>>(false to null, reportId) {
        value = true to ChangeStore(context).load(reportId)
    }
    if (!result.first) Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
        CircularProgressIndicator()
    } else ChangesContent(result.second, zone)
}

@Composable
fun ChangesContent(report: ChangeReport?, zone: String) {
    val locale = LocalConfiguration.current.locales[0]
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (report == null) {
            item {
                Text(stringResource(R.string.changes_unavailable), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.changes_unavailable_body), style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(report.className, style = MaterialTheme.typography.headlineSmall)
                    if (report.school.isNotBlank()) Text(report.school, style = MaterialTheme.typography.bodyMedium)
                    Text(pluralStringResource(R.plurals.timetable_change_count, report.changes.size, report.changes.size),
                        style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.changes_detected_at,
                        Instant.parse(report.detectedAt).atZone(ZoneId.of(zone))
                            .format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", locale))),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.changes_effective_from,
                        LocalDate.parse(report.effectiveFrom).format(DateTimeFormatter.ofPattern("d MMM yyyy", locale))),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (report.cycleName.isNotBlank()) Text(stringResource(R.string.changes_cycle, report.cycleName),
                        style = MaterialTheme.typography.labelLarge)
                }
            }
            itemsIndexed(report.changes) { _, change -> ChangeCard(change) }
        }
        item {
            Text(stringResource(R.string.changes_regular_notice), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ChangeCard(change: LessonChange) {
    val label = when {
        change.before == null -> R.string.lesson_added
        change.after == null -> R.string.lesson_removed
        else -> R.string.lesson_changed
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(label), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary)
            Text((change.after ?: change.before)!!.subject, style = MaterialTheme.typography.titleLarge)
            change.before?.let { LessonVersion(stringResource(R.string.changes_before), it, change.after) }
            if (change.before != null && change.after != null) HorizontalDivider()
            change.after?.let { LessonVersion(stringResource(R.string.changes_now), it, change.before) }
        }
    }
}

@Composable
private fun LessonVersion(label: String, lesson: ChangeLesson, other: ChangeLesson?) {
    val locale = LocalConfiguration.current.locales[0]
    val notPublished = stringResource(R.string.changes_not_published)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        val day = DayOfWeek.of(lesson.day + 1).getDisplayName(TextStyle.FULL, locale)
        DetailValue(stringResource(R.string.changes_day), day, other != null && lesson.day != other.day)
        if (other != null && lesson.subject != other.subject)
            DetailValue(stringResource(R.string.changes_subject), lesson.subject, true)
        DetailValue(stringResource(R.string.changes_time),
            if (lesson.start == null || lesson.end == null) notPublished else "${lesson.start} – ${lesson.end}",
            other != null && (lesson.start != other.start || lesson.end != other.end))
        DetailValue(stringResource(R.string.changes_period), lesson.period.ifBlank { notPublished },
            other != null && lesson.period != other.period)
        DetailValue(stringResource(R.string.csv_room), lesson.room.ifBlank { notPublished }, other != null && lesson.room != other.room)
        DetailValue(stringResource(R.string.csv_teacher), lesson.teacher.ifBlank { notPublished }, other != null && lesson.teacher != other.teacher)
        if (lesson.group.isNotBlank() || other?.group?.isNotBlank() == true)
            DetailValue(stringResource(R.string.group), lesson.group.ifBlank { stringResource(R.string.changes_whole_class) },
                other != null && lesson.group != other.group)
    }
}

@Composable
private fun DetailValue(label: String, value: String, changed: Boolean) {
    // Stack labels and values so long teacher names and large fonts stay readable.
    Column {
        Text(if (changed) stringResource(R.string.changes_field_changed, label) else label,
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = if (changed) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge)
    }
}
