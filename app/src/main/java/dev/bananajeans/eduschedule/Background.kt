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
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.work.*
import kotlinx.coroutines.CancellationException
import java.security.MessageDigest
import java.time.*
import java.util.concurrent.TimeUnit

object Background {
    private const val PERIODIC_REFRESH = "timetable-refresh"
    private const val IMMEDIATE_REFRESH = "timetable-refresh-now"

    fun configure(context: Context, enabled: Boolean) {
        val manager = WorkManager.getInstance(context)
        if (!enabled) {
            manager.cancelUniqueWork(PERIODIC_REFRESH)
            manager.cancelUniqueWork(IMMEDIATE_REFRESH)
            ClassReminders.cancelAll(context)
            return
        }
        manager.enqueueUniquePeriodicWork(
            PERIODIC_REFRESH,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RefreshWorker>(1, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
        )
    }

    fun refreshNow(context: Context) {
        val preferences = Preferences(context)
        if (!preferences.notifications || preferences.home.isBlank() || preferences.host.isBlank()) return
        WorkManager.getInstance(context).enqueueUniqueWork(
            IMMEDIATE_REFRESH,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<RefreshWorker>().build()
        )
    }
}

class RefreshWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val preferences = Preferences(applicationContext)
        if (!preferences.notifications || preferences.home.isBlank()) return Result.success()
        return try {
            val zone = ZoneId.of(preferences.zone)
            val today = LocalDate.now(zone)
            val repository = Repository(applicationContext)
            val snapshot = repository.load(preferences.host, today)
            ScheduleWidgets.refresh(applicationContext)

            ClassReminders.scheduleDay(
                applicationContext,
                today,
                snapshot.timetable,
                preferences.home,
                preferences.hiddenGroups,
                preferences.cycleWeek,
                zone,
                preferences.classReminderLeadMinutes
            )

            // Keep tomorrow armed as well so a sleeping/closed app does not depend on an overnight
            // WorkManager execution before the first class. Repository caching keeps this cheap.
            try {
                val tomorrow = today.plusDays(1)
                val tomorrowSnapshot = repository.load(preferences.host, tomorrow)
                ClassReminders.scheduleDay(
                    applicationContext,
                    tomorrow,
                    tomorrowSnapshot.timetable,
                    preferences.home,
                    preferences.hiddenGroups,
                    preferences.cycleWeek,
                    zone,
                    preferences.classReminderLeadMinutes
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Today's reminders are still valid; the next refresh can retry tomorrow.
            }

            if (snapshot.offline) return Result.retry()

            val lessons = snapshot.timetable.lessons.filter {
                it.belongsTo(Selection(ScheduleKind.CLASS, preferences.home))
            }
            val signature = MessageDigest.getInstance("SHA-256")
                .digest(lessons.toString().toByteArray())
                .joinToString("") { "%02x".format(it) }
            val state = applicationContext.getSharedPreferences("background", Context.MODE_PRIVATE)
            val key = "${preferences.host}:${preferences.home}"
            val previous = state.getString(key, null)
            if (previous != null && previous != signature) {
                notify(
                    AppLocale.string(applicationContext, preferences.language, R.string.timetable_updated),
                    AppLocale.string(applicationContext, preferences.language, R.string.timetable_updated_body),
                    1
                )
            }
            state.edit { putString(key, signature) }

            if (System.currentTimeMillis() - state.getLong("updateCheck", 0) > TimeUnit.DAYS.toMillis(1)) {
                try {
                    val release = Updates.check()
                    if (release != null && state.getString("notifiedRelease", "") != release.version) {
                        notify(
                            "${AppLocale.string(applicationContext, preferences.language, R.string.app_name)} ${release.version}",
                            AppLocale.string(applicationContext, preferences.language, R.string.app_update_available),
                            2,
                            openUpdates = true
                        )
                        state.edit { putString("notifiedRelease", release.version) }
                    }
                    state.edit { putLong("updateCheck", System.currentTimeMillis()) }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Timetable refresh and class reminders still succeeded.
                }
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private fun notify(title: String, text: String, id: Int, openUpdates: Boolean = false) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val channel = "timetable_changes"
        applicationContext.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(
                NotificationChannel(
                    channel,
                    AppLocale.string(applicationContext, Preferences(applicationContext).language, R.string.timetable_updates_channel),
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        val intent = Intent()
            .setClass(applicationContext, MainActivity::class.java)
            .setPackage(applicationContext.packageName)
            .putExtra(MainActivity.EXTRA_OPEN_SETTINGS, openUpdates)
        val pending = PendingIntent.getActivity(
            applicationContext,
            id,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(applicationContext, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        if (NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()) {
            NotificationManagerCompat.from(applicationContext).notify(id, notification)
        }
    }
}

class ReminderRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> Background.refreshNow(context)
            else -> return
        }
    }
}
