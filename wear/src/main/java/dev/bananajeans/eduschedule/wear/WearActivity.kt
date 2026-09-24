package dev.bananajeans.eduschedule.wear

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import com.google.android.gms.wearable.Wearable

/** A minimal launchable shell; the Wear day/week interface lands in the next PR. */
class WearActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showCache()
        WearCache.loadLatest(this, ::showCache)
        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            nodes.forEach { Wearable.getMessageClient(this).sendMessage(it.id, "/eduschedule/refresh", byteArrayOf()) }
        }
    }

    private fun showCache() {
        val snapshot = WearCache.read(this)
        setContentView(TextView(this).apply {
            text = snapshot?.let { "EduSchedule · ${it.settings.homeClass}\n${it.days.size} days saved" }
                ?: "Open EduSchedule on your phone to sync"
            textSize = 18f
            setPadding(36, 48, 36, 36)
        })
    }
}
