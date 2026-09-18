package dev.bananajeans.eduschedule

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import kotlinx.coroutines.CancellationException
import java.security.MessageDigest
import java.time.*
import java.util.concurrent.TimeUnit

object Background {
    fun configure(context: Context, enabled: Boolean) {
        val manager = WorkManager.getInstance(context)
        if (!enabled) { manager.cancelUniqueWork("timetable-refresh"); return }
        manager.enqueueUniquePeriodicWork("timetable-refresh", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RefreshWorker>(1, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
    }
}
class RefreshWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val preferences = Preferences(applicationContext)
        if (!preferences.notifications || preferences.home.isBlank()) return Result.success()
        return try {
            val today = LocalDate.now(ZoneId.of(preferences.zone))
            val snapshot = Repository(applicationContext).load(preferences.host, today)
            if (snapshot.offline) return Result.retry()
            val lessons = snapshot.timetable.lessons.filter { it.belongsTo(Selection(ScheduleKind.CLASS, preferences.home)) }
            val signature = MessageDigest.getInstance("SHA-256").digest(lessons.toString().toByteArray()).joinToString("") { "%02x".format(it) }
            val state = applicationContext.getSharedPreferences("background", Context.MODE_PRIVATE)
            val key = "${preferences.host}:${preferences.home}"
            val previous = state.getString(key, null)
            if (previous != null && previous != signature) notify("Timetable updated", "Your saved class has a new timetable. Tap to check what changed.", 1)
            state.edit().putString(key, signature).apply()
            // Daily release checks share the opt-in background task. No install/download permission.
            if (System.currentTimeMillis() - state.getLong("updateCheck", 0) > TimeUnit.DAYS.toMillis(1)) {
                try {
                    val release = Updates.check()
                    if (release != null && state.getString("notifiedRelease", "") != release.version) {
                        notify("EduSchedule ${release.version}", "An update is available. Open Settings to view the release.", 2)
                        state.edit().putString("notifiedRelease", release.version).apply()
                    }
                    state.edit().putLong("updateCheck", System.currentTimeMillis()).apply()
                } catch (e: CancellationException) { throw e } catch (_: Exception) { /* Timetable refresh still succeeded. */ }
            }
            Result.success()
        } catch (e: CancellationException) { throw e } catch (_: Exception) { if (runAttemptCount < 3) Result.retry() else Result.failure() }
    }
    private fun notify(title: String, text: String, id: Int) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val channel = "timetable_changes"
        applicationContext.getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(channel, "Timetable and app updates", NotificationManager.IMPORTANCE_DEFAULT))
        val pending = PendingIntent.getActivity(applicationContext, 0, Intent(applicationContext, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(applicationContext, channel).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title).setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text)).setContentIntent(pending).setAutoCancel(true).build()
        if (NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()) NotificationManagerCompat.from(applicationContext).notify(id, notification)
    }
}
