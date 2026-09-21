package dev.bananajeans.eduschedule

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.work.*
import java.time.*
import java.util.concurrent.TimeUnit

object ClassReminders {
    private const val TAG = "class-reminders"
    private const val CHANNEL = "class_reminders"

    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(TAG)
    }

    fun scheduleDay(
        context: Context,
        date: LocalDate,
        timetable: Timetable,
        home: String,
        hiddenGroups: Set<String>,
        cycleWeek: Int,
        zone: ZoneId,
        preReminderMinutes: Int
    ) {
        if (home.isBlank()) return
        val manager = WorkManager.getInstance(context)
        manager.cancelAllWorkByTag(TAG)
        val now = Instant.now()
        timetable.lessonBlocksOn(date, Selection(ScheduleKind.CLASS, home), hiddenGroups, cycleWeek).forEach blockLoop@ { block ->
            val start = block.start ?: return@blockLoop
            val startAt = date.atTime(start).atZone(zone).toInstant()
            reminderOffsets(preReminderMinutes).forEach reminderLoop@ { minutesBefore ->
                val fireAt = startAt.minusSeconds(minutesBefore * 60L)
                if (!fireAt.isAfter(now)) return@reminderLoop
                val request = OneTimeWorkRequestBuilder<ClassReminderWorker>()
                    .setInitialDelay(Duration.between(now, fireAt).toMillis(), TimeUnit.MILLISECONDS)
                    .setInputData(workDataOf(
                        "id" to block.id,
                        "title" to reminderBlockTitle(context, block),
                        "detail" to reminderDetail(context, block),
                        "minutesBefore" to minutesBefore
                    ))
                    .addTag(TAG)
                    .build()
                manager.enqueueUniqueWork(
                    "class-reminder-$date-${block.id.hashCode()}-$minutesBefore",
                    ExistingWorkPolicy.REPLACE,
                    request
                )
            }
        }
    }

    internal fun reminderOffsets(preReminderMinutes: Int): List<Int> {
        val lead = preReminderMinutes.coerceIn(0, 60)
        return if (lead == 0) listOf(0) else listOf(lead, 0)
    }

    internal fun notificationTitle(context: Context, title: String, minutesBefore: Int): String {
        val language = Preferences(context).language
        return if (minutesBefore > 0) {
            AppLocale.string(context, language, R.string.class_starts_in, title, minutesBefore)
        } else {
            AppLocale.string(context, language, R.string.class_starts_now, title)
        }
    }

    private fun reminderBlockTitle(context: Context, block: LessonBlock): String {
        if (block.subjects.size == 1) return block.subjects.first()
        val localized = AppLocale.wrap(context, Preferences(context).language)
        return localized.resources.getQuantityString(
            R.plurals.group_lessons,
            block.lessons.size,
            block.lessons.size
        )
    }

    private fun reminderDetail(context: Context, block: LessonBlock): String {
        if (block.lessons.size == 1) {
            val lesson = block.lessons.first()
            return listOf(lesson.roomNames, lesson.teacherNames).filter(String::isNotBlank).joinToString(" · ")
        }
        val subjects = block.subjects.take(3).joinToString(" / ")
        if (subjects.isNotBlank()) return subjects
        val localized = AppLocale.wrap(context, Preferences(context).language)
        return localized.resources.getQuantityString(
            R.plurals.group_options,
            block.lessons.size,
            block.lessons.size
        )
    }

    internal fun mutedUntil(context: Context): Long =
        context.getSharedPreferences("background", Context.MODE_PRIVATE).getLong("classMuteUntil", 0L)

    internal fun mute(context: Context, until: Instant) {
        context.getSharedPreferences("background", Context.MODE_PRIVATE).edit {
            putLong("classMuteUntil", until.toEpochMilli())
        }
    }

    internal fun show(context: Context, title: String, detail: String, notificationId: Int, minutesBefore: Int) {
        if (System.currentTimeMillis() < mutedUntil(context)) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return

        val manager = context.getSystemService(NotificationManager::class.java)
        val language = Preferences(context).language
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                AppLocale.string(context, language, R.string.class_reminders_channel),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )

        val open = PendingIntent.getActivity(
            context, notificationId,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        fun muteIntent(action: String, requestCode: Int) = PendingIntent.getBroadcast(
            context, requestCode,
            Intent(context, ReminderActionReceiver::class.java).setAction(action).putExtra("notificationId", notificationId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(notificationTitle(context, title, minutesBefore))
            .setContentText(detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .addAction(
                0,
                AppLocale.string(context, language, R.string.mute_one_hour),
                muteIntent(ReminderActionReceiver.MUTE_HOUR, notificationId * 10 + 1)
            )
            .addAction(
                0,
                AppLocale.string(context, language, R.string.mute_today),
                muteIntent(ReminderActionReceiver.MUTE_TODAY, notificationId * 10 + 2)
            )
            .build()
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        }
    }
}

class ClassReminderWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
    override fun doWork(): Result {
        if (!Preferences(applicationContext).notifications) return Result.success()
        val title = inputData.getString("title") ?: return Result.failure()
        val detail = inputData.getString("detail").orEmpty()
        val id = (inputData.getString("id") ?: title).hashCode().and(Int.MAX_VALUE)
        val minutesBefore = inputData.getInt("minutesBefore", 0)
        ClassReminders.show(applicationContext, title, detail, id, minutesBefore)
        return Result.success()
    }
}

class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val zone = runCatching { ZoneId.of(Preferences(context).zone) }.getOrDefault(ZoneId.systemDefault())
        val now = ZonedDateTime.now(zone)
        val until = when (intent.action) {
            MUTE_HOUR -> now.plusHours(1).toInstant()
            MUTE_TODAY -> now.toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()
            else -> return
        }
        ClassReminders.mute(context, until)
        NotificationManagerCompat.from(context).cancel(intent.getIntExtra("notificationId", 0))
    }

    companion object {
        const val MUTE_HOUR = "dev.bananajeans.eduschedule.MUTE_HOUR"
        const val MUTE_TODAY = "dev.bananajeans.eduschedule.MUTE_TODAY"
    }
}
