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
        zone: ZoneId
    ) {
        if (home.isBlank()) return
        val manager = WorkManager.getInstance(context)
        manager.cancelAllWorkByTag(TAG)
        val now = Instant.now()
        timetable.lessonBlocksOn(date, Selection(ScheduleKind.CLASS, home), hiddenGroups, cycleWeek).forEach { block ->
            val start = block.start ?: return@forEach
            val at = date.atTime(start).atZone(zone).toInstant()
            if (!at.isAfter(now)) return@forEach
            val request = OneTimeWorkRequestBuilder<ClassReminderWorker>()
                .setInitialDelay(Duration.between(now, at).toMillis(), TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(
                    "id" to block.id,
                    "title" to block.title,
                    "detail" to reminderDetail(block)
                ))
                .addTag(TAG)
                .build()
            manager.enqueueUniqueWork(
                "class-reminder-$date-${block.id.hashCode()}",
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }

    private fun reminderDetail(block: LessonBlock): String {
        if (block.lessons.size == 1) {
            val lesson = block.lessons.first()
            return listOf(lesson.roomNames, lesson.teacherNames).filter(String::isNotBlank).joinToString(" · ")
        }
        val subjects = block.subjects.take(3).joinToString(" / ")
        return if (subjects.isNotBlank()) subjects else "${block.lessons.size} group options"
    }

    internal fun mutedUntil(context: Context): Long =
        context.getSharedPreferences("background", Context.MODE_PRIVATE).getLong("classMuteUntil", 0L)

    internal fun mute(context: Context, until: Instant) {
        context.getSharedPreferences("background", Context.MODE_PRIVATE).edit {
            putLong("classMuteUntil", until.toEpochMilli())
        }
    }

    internal fun show(context: Context, title: String, detail: String, notificationId: Int) {
        if (System.currentTimeMillis() < mutedUntil(context)) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Class reminders", NotificationManager.IMPORTANCE_DEFAULT))

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
            .setContentTitle("$title starts now")
            .setContentText(detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .addAction(0, "Mute 1h", muteIntent(ReminderActionReceiver.MUTE_HOUR, notificationId * 10 + 1))
            .addAction(0, "Mute today", muteIntent(ReminderActionReceiver.MUTE_TODAY, notificationId * 10 + 2))
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
        ClassReminders.show(applicationContext, title, detail, id)
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
