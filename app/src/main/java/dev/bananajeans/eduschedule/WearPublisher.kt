package dev.bananajeans.eduschedule

import android.content.Context
import androidx.core.content.edit
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import dev.bananajeans.eduschedule.sync.WearSettings
import dev.bananajeans.eduschedule.sync.WearSnapshot
import dev.bananajeans.eduschedule.sync.WearSnapshotCodec
import kotlinx.coroutines.CancellationException
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

object WearPublisher {
    private const val PATH = "/eduschedule/snapshot/v1"
    private const val WORK = "wear-snapshot"

    fun enqueue(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<WearSyncWorker>().build()
        )
    }

    internal suspend fun publishCached(context: Context, force: Boolean = false): Boolean {
        val prefs = Preferences(context)
        val zone = ZoneId.of(prefs.zone)
        val monday = LocalDate.now(zone).with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        val days = if (prefs.host.isBlank() || prefs.home.isBlank()) emptyList() else {
            val repository = Repository(context)
            (0L..13L).mapNotNull { offset ->
                val date = monday.plusDays(offset)
                val saved = repository.loadCached(prefs.host, date) ?: return@mapNotNull null
                if (saved.timetable.entities[ScheduleKind.CLASS].orEmpty().none { it.id == prefs.home })
                    return@mapNotNull null
                saved.forWear(date, prefs.home, prefs.hiddenGroups, prefs.cycleWeek)
            }
        }
        val colors = prefs.customColors
        val settings = WearSettings(prefs.host, prefs.home, zone, prefs.language.preferenceValue,
            prefs.theme, prefs.palette, colors.primary, colors.secondary, colors.surface,
            prefs.notifications, prefs.classReminderLeadMinutes)
        val now = Instant.now()
        val snapshot = WearSnapshot(settings, days, now)
        // Fresh fetch timestamps alone do not justify waking the watch every hour.
        val semantic = WearSnapshotCodec.encode(snapshot.copy(publishedAt = Instant.EPOCH,
            days = days.map { it.copy(fetchedAt = Instant.EPOCH) }))
        val digest = MessageDigest.getInstance("SHA-256").digest(semantic)
            .joinToString("") { "%02x".format(it) }
        val state = context.getSharedPreferences("wear-publisher", Context.MODE_PRIVATE)
        if (!force && state.getString("digest", null) == digest &&
            now.toEpochMilli() - state.getLong("lastPublished", 0) < 24 * 60 * 60 * 1000L) return true
        return try {
            val data = WearSnapshotCodec.encode(snapshot)
            val request = PutDataRequest.create(PATH).setData(data)
            // Update the persisted digest only after Play services accepts the item.
            val result = kotlinx.coroutines.suspendCancellableCoroutine<Boolean> { continuation ->
                Wearable.getDataClient(context).putDataItem(request)
                    .addOnSuccessListener { if (continuation.isActive) continuation.resume(true, null) }
                    .addOnFailureListener { if (continuation.isActive) continuation.resume(false, null) }
            }
            if (result) state.edit { putString("digest", digest); putLong("lastPublished", now.toEpochMilli()) }
            result
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { false }
    }
}

class WearSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        if (WearPublisher.publishCached(applicationContext, inputData.getBoolean("force", false))) Result.success()
        else Result.retry()
}

class WearSyncRequests : WearableListenerService() {
    override fun onMessageReceived(message: MessageEvent) {
        if (message.path != "/eduschedule/refresh") return
        WorkManager.getInstance(this).enqueueUniqueWork("wear-snapshot",
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<WearSyncWorker>()
                .setInputData(androidx.work.workDataOf("force" to true)).build())
    }
}
