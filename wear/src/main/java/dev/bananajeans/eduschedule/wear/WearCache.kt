package dev.bananajeans.eduschedule.wear

import android.content.Context
import android.content.Intent
import android.util.AtomicFile
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import dev.bananajeans.eduschedule.sync.WearSnapshot
import dev.bananajeans.eduschedule.sync.WearSnapshotCodec
import dev.bananajeans.eduschedule.sync.shouldAcceptSnapshot
import java.io.File

object WearCache {
    const val PATH = "/eduschedule/snapshot/v1"
    private fun file(context: Context) = AtomicFile(File(context.filesDir, "schedule.json"))

    fun read(context: Context): WearSnapshot? = runCatching {
        WearSnapshotCodec.decode(file(context).openRead().use { it.readBytes() })
    }.getOrNull()

    @Synchronized fun accept(context: Context, bytes: ByteArray): Boolean {
        val incoming = runCatching { WearSnapshotCodec.decode(bytes) }.getOrNull() ?: return false
        val old = read(context)
        if (!shouldAcceptSnapshot(old, incoming)) return false
        val atomic = file(context)
        val out = atomic.startWrite()
        try { out.write(bytes); atomic.finishWrite(out) }
        catch (error: Exception) { atomic.failWrite(out); return false }
        WearReminders.reschedule(context, incoming)
        updateComplication(context)
        return true
    }

    fun loadLatest(context: Context, onLoaded: () -> Unit) {
        Wearable.getDataClient(context).dataItems.addOnSuccessListener { items ->
            try {
                for (item in items) {
                    val bytes = item.data ?: continue
                    if (item.uri.path == PATH && accept(context, bytes)) onLoaded()
                }
            } finally { items.release() }
        }
    }
}

class WearDataReceiver : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        for (event in events) {
            val bytes = event.dataItem.data ?: continue
            if (event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path == WearCache.PATH &&
                WearCache.accept(this, bytes))
                sendBroadcast(Intent(WearActivity.ACTION_CHANGED).setPackage(packageName))
        }
    }
}
