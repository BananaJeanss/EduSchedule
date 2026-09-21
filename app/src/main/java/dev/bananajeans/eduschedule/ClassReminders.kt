package dev.bananajeans.eduschedule

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.time.*

object ClassReminders {
    private const val LEGACY_TAG = "class-reminders"
    private const val CHANNEL = "class_reminders"
    private const val ALARM_PREFS = "class-reminder-alarms"
    private const val ALARM_KEYS = "keys"
    private const val ACTION_FIRE = "dev.bananajeans.eduschedule.CLASS_REMINDER"
    private const val START_GRACE_MINUTES = 5L

    fun cancelAll(context: Context) {
        // Cancel jobs created by versions that used WorkManager for class-time delivery.
        WorkManager.getInstance(context).cancelAllWorkByTag(LEGACY_TAG)

        val prefs = context.getSharedPreferences(ALARM_PREFS, Context.MODE_PRIVATE)
        val keys = prefs.getStringSet(ALARM_KEYS, emptySet()).orEmpty().toSet()
        keys.forEach { cancelAlarm(context, it) }
        prefs.edit { remove(ALARM_KEYS) }
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
        // Remove persisted WorkManager reminders left behind by pre-AlarmManager releases.
        WorkManager.getInstance(context).cancelAllWorkByTag(LEGACY_TAG)
        cancelDate(context, date)
        if (home.isBlank()) return

        val now = Instant.now()
        val prefs = context.getSharedPreferences(ALARM_PREFS, Context.MODE_PRIVATE)
        val keys = prefs.getStringSet(ALARM_KEYS, emptySet()).orEmpty().toMutableSet()

        timetable.lessonBlocksOn(date, Selection(ScheduleKind.CLASS, home), hiddenGroups, cycleWeek).forEach blockLoop@ { block ->
            val start = block.start ?: return@blockLoop
            val startAt = date.atTime(start).atZone(zone).toInstant()

            reminderOffsets(preReminderMinutes).forEach reminderLoop@ { minutesBefore ->
                val fireAt = startAt.minusSeconds(minutesBefore * 60L)
                if (!fireAt.isAfter(now)) return@reminderLoop

                val validUntil = reminderValidUntil(startAt, minutesBefore)
                val key = reminderKey(date, block.id, minutesBefore)
                val notificationId = block.id.hashCode().and(Int.MAX_VALUE)
                val intent = reminderIntent(context, key)
                    .putExtra("key", key)
                    .putExtra("id", block.id)
                    .putExtra("title", reminderBlockTitle(context, block))
                    .putExtra("detail", reminderDetail(context, block))
                    .putExtra("notificationId", notificationId)
                    .putExtra("minutesBefore", minutesBefore)
                    .putExtra("fireAt", fireAt.toEpochMilli())
                    .putExtra("validUntil", validUntil.toEpochMilli())

                val pending = PendingIntent.getBroadcast(
                    context,
                    requestCode(key),
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                scheduleAlarm(context, fireAt, minutesBefore, pending)
                keys += key
            }
        }

        prefs.edit { putStringSet(ALARM_KEYS, keys.toSet()) }
    }

    internal fun reminderOffsets(preReminderMinutes: Int): List<Int> {
        val lead = preReminderMinutes.coerceIn(0, 60)
        return if (lead == 0) listOf(0) else listOf(lead, 0)
    }

    internal fun reminderValidUntil(startAt: Instant, minutesBefore: Int): Instant =
        if (minutesBefore > 0) startAt else startAt.plusSeconds(START_GRACE_MINUTES * 60)

    internal fun shouldDeliver(nowMillis: Long, fireAtMillis: Long, validUntilMillis: Long): Boolean =
        fireAtMillis > 0L &&
            validUntilMillis >= fireAtMillis &&
            nowMillis in fireAtMillis..validUntilMillis

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

    private fun reminderKey(date: LocalDate, blockId: String, minutesBefore: Int): String =
        "$date|$blockId|$minutesBefore"

    private fun requestCode(key: String): Int = key.hashCode().and(Int.MAX_VALUE)

    private fun reminderIntent(context: Context, key: String): Intent =
        Intent(context, ClassReminderReceiver::class.java)
            .setAction(ACTION_FIRE)
            .setData(
                Uri.Builder()
                    .scheme("eduschedule")
                    .authority("class-reminder")
                    .appendPath(key)
                    .build()
            )

    private fun scheduleAlarm(context: Context, fireAt: Instant, minutesBefore: Int, pending: PendingIntent) {
        val manager = context.getSystemService(AlarmManager::class.java)
        val triggerAt = fireAt.toEpochMilli()
        val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()

        if (exactAllowed) {
            try {
                if (minutesBefore == 0) {
                    manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
                } else {
                    manager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pending)
                }
                return
            } catch (_: SecurityException) {
                // Fall through to an inexact alarm if exact-alarm access was revoked concurrently.
            }
        }

        if (minutesBefore == 0) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        } else {
            manager.set(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    private fun cancelDate(context: Context, date: LocalDate) {
        val prefs = context.getSharedPreferences(ALARM_PREFS, Context.MODE_PRIVATE)
        val keys = prefs.getStringSet(ALARM_KEYS, emptySet()).orEmpty().toMutableSet()
        val prefix = "$date|"
        val removed = keys.filter { it.startsWith(prefix) }
        removed.forEach { cancelAlarm(context, it) }
        if (removed.isNotEmpty()) {
            keys.removeAll(removed.toSet())
            prefs.edit { putStringSet(ALARM_KEYS, keys.toSet()) }
        }
    }

    private fun cancelAlarm(context: Context, key: String) {
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode(key),
            reminderIntent(context, key),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        ) ?: return
        context.getSystemService(AlarmManager::class.java).cancel(pending)
        pending.cancel()
    }

    internal fun consume(context: Context, key: String) {
        if (key.isBlank()) return
        val prefs = context.getSharedPreferences(ALARM_PREFS, Context.MODE_PRIVATE)
        val keys = prefs.getStringSet(ALARM_KEYS, emptySet()).orEmpty().toMutableSet()
        if (keys.remove(key)) prefs.edit { putStringSet(ALARM_KEYS, keys.toSet()) }
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

class ClassReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra("key").orEmpty()
        try {
            if (!Preferences(context).notifications) return

            val now = System.currentTimeMillis()
            val fireAt = intent.getLongExtra("fireAt", -1L)
            val validUntil = intent.getLongExtra("validUntil", -1L)
            if (!ClassReminders.shouldDeliver(now, fireAt, validUntil)) return

            val title = intent.getStringExtra("title") ?: return
            val detail = intent.getStringExtra("detail").orEmpty()
            val notificationId = intent.getIntExtra(
                "notificationId",
                (intent.getStringExtra("id") ?: title).hashCode().and(Int.MAX_VALUE)
            )
            val minutesBefore = intent.getIntExtra("minutesBefore", 0)
            ClassReminders.show(context, title, detail, notificationId, minutesBefore)
        } finally {
            ClassReminders.consume(context, key)
        }
    }
}

/**
 * Kept temporarily so WorkManager can safely instantiate and drain reminders persisted by older
 * app versions. New class reminders are scheduled exclusively through AlarmManager.
 */
class ClassReminderWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
    override fun doWork(): Result = Result.success()
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
