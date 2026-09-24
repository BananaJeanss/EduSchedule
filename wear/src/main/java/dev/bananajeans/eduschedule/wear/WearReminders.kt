package dev.bananajeans.eduschedule.wear

import android.Manifest
import android.app.AlarmManager
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
import com.google.android.gms.wearable.Wearable
import dev.bananajeans.eduschedule.sync.WearSnapshot
import java.time.Instant
import java.time.LocalDate

/** Only alarms for the next two dates are retained. A new snapshot replaces the previous set. */
object WearReminders {
    private const val PREFS = "wear_reminder_alarms"
    private const val KEYS = "keys"
    private const val CHANNEL = "classes"
    const val ACTION_FIRE = "dev.bananajeans.eduschedule.wear.REMINDER"
    const val ACTION_ROLLOVER = "dev.bananajeans.eduschedule.wear.ROLLOVER"

    fun reschedule(context: Context, snapshot: WearSnapshot?) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val manager = context.getSystemService(AlarmManager::class.java)
        prefs.getStringSet(KEYS, emptySet()).orEmpty().forEach { key ->
            pending(context, key, PendingIntent.FLAG_NO_CREATE)?.let { manager.cancel(it); it.cancel() }
        }
        val rollover = PendingIntent.getBroadcast(context, 0, Intent(context, WearClockReceiver::class.java)
            .setAction(ACTION_ROLLOVER), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        manager.cancel(rollover)
        val keys = mutableSetOf<String>()
        if (snapshot != null && snapshot.settings.reminders) {
            val now = Instant.now()
            val today = LocalDate.now(snapshot.settings.zone)
            val nextDay = today.plusDays(1).atTime(0, 5).atZone(snapshot.settings.zone).toInstant()
            manager.set(AlarmManager.RTC_WAKEUP, nextDay.toEpochMilli(), rollover)
            snapshot.days.filter { !it.date.isBefore(today) && !it.date.isAfter(today.plusDays(1)) }
                .forEach { day -> day.lessons.forEach { lesson ->
                    val start = lesson.start ?: return@forEach
                    val startAt = day.date.atTime(start).atZone(snapshot.settings.zone).toInstant()
                    val offsets = listOf(snapshot.settings.leadMinutes.coerceIn(0, 60), 0).distinct()
                    offsets.forEach { lead ->
                        val fireAt = startAt.minusSeconds(lead * 60L)
                        if (!fireAt.isAfter(now)) return@forEach
                        val key = "${day.date}|${lesson.id}|$lead"
                        val intent = Intent(context, WearReminderReceiver::class.java).apply {
                            action = ACTION_FIRE
                            data = android.net.Uri.parse("eduschedule://reminder/${android.net.Uri.encode(key)}")
                            putExtra("key", key)
                            putExtra("title", lesson.subjects.joinToString(" / "))
                            putExtra("detail", listOf(lesson.room, lesson.teacher).filter(String::isNotBlank).joinToString(" · "))
                            putExtra("lead", lead)
                            putExtra("fireAt", fireAt.toEpochMilli())
                            putExtra("validUntil", if (lead > 0) startAt.toEpochMilli() else startAt.plusSeconds(300).toEpochMilli())
                        }
                        val alarm = PendingIntent.getBroadcast(context, key.hashCode(), intent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                        // Inexact alarms avoid requesting exact alarm access on a battery constrained device.
                        manager.set(AlarmManager.RTC_WAKEUP, fireAt.toEpochMilli(), alarm)
                        keys += key
                    }
                } }
        }
        prefs.edit { putStringSet(KEYS, keys) }
    }

    private fun pending(context: Context, key: String, flag: Int) = PendingIntent.getBroadcast(context,
        key.hashCode(), Intent(context, WearReminderReceiver::class.java).apply {
            action = ACTION_FIRE
            data = android.net.Uri.parse("eduschedule://reminder/${android.net.Uri.encode(key)}")
        }, flag or PendingIntent.FLAG_IMMUTABLE)

    fun delivered(context: Context, key: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit { putStringSet(KEYS, prefs.getStringSet(KEYS, emptySet()).orEmpty() - key) }
    }

    fun notify(context: Context, intent: Intent) {
        val snapshot = WearCache.read(context) ?: return
        if (!snapshot.settings.reminders || (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)) return
        val now = System.currentTimeMillis()
        if (now !in intent.getLongExtra("fireAt", -1L)..intent.getLongExtra("validUntil", -1L)) return
        val title = intent.getStringExtra("title").orEmpty().ifBlank { return }
        val language = snapshot.settings.language
        val locale = if (language == "et") java.util.Locale.forLanguageTag("et") else java.util.Locale.ENGLISH
        val strings = context.createConfigurationContext(android.content.res.Configuration(context.resources.configuration).apply { setLocale(locale) })
        val lead = intent.getIntExtra("lead", 0)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, strings.getString(R.string.reminders_channel), NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(context, 0, Intent(context, WearActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(if (lead > 0) strings.getString(R.string.starts_in, title, lead)
                else strings.getString(R.string.starts_now, title))
            .setContentText(intent.getStringExtra("detail").orEmpty())
            .setContentIntent(open).setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER).build()
        NotificationManagerCompat.from(context).notify(intent.getStringExtra("key").hashCode(), notification)
    }
}

class WearReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != WearReminders.ACTION_FIRE || intent.data?.scheme != "eduschedule") return
        WearReminders.delivered(context, intent.getStringExtra("key").orEmpty())
        // Connected phones already bridge their class notification to the watch.
        val pending = goAsync()
        Wearable.getNodeClient(context).connectedNodes
            .addOnCompleteListener { task ->
                try { if (!task.isSuccessful || task.result.isNullOrEmpty()) WearReminders.notify(context, intent) }
                finally { pending.finish() }
            }
    }
}

class WearClockReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIMEZONE_CHANGED,
                Intent.ACTION_TIME_CHANGED, WearReminders.ACTION_ROLLOVER))
            WearReminders.reschedule(context, WearCache.read(context))
    }
}
