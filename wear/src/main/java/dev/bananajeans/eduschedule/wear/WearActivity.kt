package dev.bananajeans.eduschedule.wear

import android.content.BroadcastReceiver
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Build
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.google.android.gms.wearable.Wearable
import dev.bananajeans.eduschedule.sync.WearSnapshot
class WearActivity : ComponentActivity() {
    companion object { const val ACTION_CHANGED = "dev.bananajeans.eduschedule.wear.SNAPSHOT_CHANGED" }
    private var snapshot by mutableStateOf<WearSnapshot?>(null)
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            snapshot = WearCache.read(this@WearActivity)
            requestReminderPermission()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        snapshot = WearCache.read(this)
        setContent { WearSchedule(snapshot, ::requestSync) }
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(this, receiver, IntentFilter(ACTION_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        snapshot = WearCache.read(this)
        requestReminderPermission()
        WearCache.loadLatest(this) {
            snapshot = WearCache.read(this)
            requestReminderPermission()
        }
    }

    override fun onStop() { unregisterReceiver(receiver); super.onStop() }

    private fun requestReminderPermission() {
        if (Build.VERSION.SDK_INT < 33 || snapshot?.settings?.reminders != true ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        val prefs = getSharedPreferences("wear_permissions", MODE_PRIVATE)
        if (!prefs.getBoolean("asked_notifications", false)) {
            prefs.edit().putBoolean("asked_notifications", true).apply()
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    private fun requestSync() {
        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            nodes.forEach { Wearable.getMessageClient(this).sendMessage(it.id, "/eduschedule/refresh", byteArrayOf()) }
        }
    }
}
