package dev.bananajeans.eduschedule.wear

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import dev.bananajeans.eduschedule.sync.WearLesson
import dev.bananajeans.eduschedule.sync.WearSnapshot
import java.time.LocalDate
import java.time.LocalTime

/** No network or phone request is made from a watch face update. */
class ScheduleComplication : SuspendingComplicationDataSourceService() {
    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? =
        complicationData(this, WearCache.read(this), request.complicationType)

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        complicationData(this, null, type)
}

internal fun nextLesson(snapshot: WearSnapshot?, today: LocalDate, now: LocalTime): WearLesson? =
    snapshot?.days?.filter { it.date == today }?.flatMap { it.lessons }
        ?.filter { it.end?.isAfter(now) == true }
        ?.minByOrNull { it.start ?: LocalTime.MAX }

private fun complicationData(context: Context, snapshot: WearSnapshot?, type: ComplicationType): ComplicationData? {
    val today = LocalDate.now(snapshot?.settings?.zone ?: java.time.ZoneId.systemDefault())
    val lesson = nextLesson(snapshot, today, LocalTime.now(snapshot?.settings?.zone ?: java.time.ZoneId.systemDefault()))
    val title = lesson?.subjects?.firstOrNull()?.ifBlank { null }
        ?: context.getString(if (snapshot?.days?.any { it.date == today } == true)
            R.string.complication_empty else R.string.complication_sync)
    val label = lesson?.start?.toString().orEmpty()
    val open = PendingIntent.getActivity(context, 0, Intent(context, WearActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    val text = PlainComplicationText.Builder(title).build()
    val description = PlainComplicationText.Builder(listOf(label, title).filter(String::isNotBlank).joinToString(" ")).build()
    return when (type) {
        ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(text, description)
            .setTitle(PlainComplicationText.Builder(label).build()).setTapAction(open).build()
        ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(
            PlainComplicationText.Builder(listOf(label, title).filter(String::isNotBlank).joinToString(" · ")).build(),
            description).setTapAction(open).build()
        else -> null
    }
}

internal fun updateComplication(context: Context) {
    ComplicationDataSourceUpdateRequester.create(context, ComponentName(context, ScheduleComplication::class.java))
        .requestUpdateAll()
}
