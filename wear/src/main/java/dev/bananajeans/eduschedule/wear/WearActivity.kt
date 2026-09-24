package dev.bananajeans.eduschedule.wear

import android.content.BroadcastReceiver
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Bundle
import android.os.Build
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.google.android.gms.wearable.Wearable
import dev.bananajeans.eduschedule.sync.WearLesson
import dev.bananajeans.eduschedule.sync.WearSettings
import dev.bananajeans.eduschedule.sync.WearSnapshot
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

class WearActivity : ComponentActivity() {
    companion object { const val ACTION_CHANGED = "dev.bananajeans.eduschedule.wear.SNAPSHOT_CHANGED" }
    private var snapshot by mutableStateOf<WearSnapshot?>(null)
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
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
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun requestSync() {
        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            nodes.forEach { Wearable.getMessageClient(this).sendMessage(it.id, "/eduschedule/refresh", byteArrayOf()) }
        }
    }
}

private fun localized(context: Context, settings: WearSettings?): Context {
    val tag = settings?.language?.takeIf { it == "en" || it == "et" } ?: return context
    val config = Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }
    return context.createConfigurationContext(config)
}

private fun colors(settings: WearSettings?): Triple<Color, Color, Color> {
    val dark = settings?.theme != "Light"
    fun parse(value: String, fallback: Color): Color =
        runCatching { Color(android.graphics.Color.parseColor(value)) }.getOrDefault(fallback)
    return when (settings?.palette) {
        "Catppuccin" -> Triple(if (dark) Color(0xFFCBA6F7) else Color(0xFF8839EF),
            if (dark) Color(0xFF1E1E2E) else Color(0xFFEFF1F5), Color(0xFF89B4FA))
        "Ocean" -> Triple(if (dark) Color(0xFF80CBC4) else Color(0xFF006B69),
            if (dark) Color(0xFF101F2D) else Color(0xFFF2F8FA), Color(0xFF90CAF9))
        "Custom" -> Triple(parse(settings.primary, Color(0xFFB5CEA8)),
            parse(settings.surface, Color(0xFF111511)), parse(settings.secondary, Color(0xFFAFCCB5)))
        else -> Triple(Color(0xFFB5CEA8), Color(0xFF111511), Color(0xFFAFCCB5))
    }
}

@Composable private fun WearSchedule(snapshot: WearSnapshot?, sync: () -> Unit) {
    val context = LocalContext.current
    val strings = remember(context, snapshot?.settings?.language) { localized(context, snapshot?.settings) }
    val locale = strings.resources.configuration.locales[0]
    var screen by remember { mutableStateOf("today") }
    var date by remember { mutableStateOf<LocalDate?>(null) }
    var detail by remember { mutableStateOf<WearLesson?>(null) }
    BackHandler(screen != "today" || detail != null) { if (detail != null) detail = null else screen = "today" }

    val today = LocalDate.now(snapshot?.settings?.zone ?: ZoneId.systemDefault())
    val shown = date ?: today
    val day = snapshot?.days?.firstOrNull { it.date == shown }
    val (primary, background, secondary) = colors(snapshot?.settings)
    val foreground = if (background.luminance() > .179f) Color.Black else Color.White
    val onPrimary = if (primary.luminance() > .179f) Color.Black else Color.White
    val scheme = MaterialTheme.colorScheme.copy(primary = primary, secondary = secondary,
        onPrimary = onPrimary, background = background, onBackground = foreground, onSurface = foreground)

    MaterialTheme(colorScheme = scheme) {
        Column(Modifier.fillMaxSize().background(background).verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                snapshot == null || snapshot.settings.homeClass.isBlank() -> {
                    Text(strings.getString(R.string.setup_phone), style = MaterialTheme.typography.titleMedium)
                    Button(onClick = sync, modifier = Modifier.fillMaxWidth(), label = { Text(strings.getString(R.string.sync_now)) })
                }
                detail != null -> {
                    Button(onClick = { detail = null }, label = { Text(strings.getString(R.string.back)) })
                    Text(detail!!.subjects.joinToString(" / "), style = MaterialTheme.typography.titleMedium)
                    Text("${detail!!.start ?: "?"}–${detail!!.end ?: "?"}")
                    if (detail!!.room.isNotBlank()) Text(detail!!.room)
                    if (detail!!.teacher.isNotBlank()) Text(detail!!.teacher)
                    if (detail!!.group.isNotBlank()) Text(detail!!.group)
                }
                screen == "status" -> {
                    Text(strings.getString(R.string.status), style = MaterialTheme.typography.titleMedium)
                    Text(snapshot.settings.school + " · " + snapshot.settings.homeClass)
                    Text(strings.getString(R.string.last_sync, snapshot.publishedAt.atZone(snapshot.settings.zone)
                        .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(locale))))
                    Text(strings.getString(R.string.settings_on_phone))
                    Button(onClick = sync, modifier = Modifier.fillMaxWidth(), label = { Text(strings.getString(R.string.sync_now)) })
                    Button(onClick = { screen = "today" }, modifier = Modifier.fillMaxWidth(), label = { Text(strings.getString(R.string.today)) })
                }
                screen == "week" -> {
                    Text(strings.getString(R.string.week), style = MaterialTheme.typography.titleMedium)
                    snapshot.days.sortedBy { it.date }.forEach { choice ->
                        Button(onClick = { date = choice.date; screen = "today" }, modifier = Modifier.fillMaxWidth(),
                            label = { Text("${choice.date.format(DateTimeFormatter.ofPattern("EEE d MMM", locale))} · ${choice.lessons.size}") })
                    }
                    Button(onClick = { screen = "today" }, modifier = Modifier.fillMaxWidth(), label = { Text(strings.getString(R.string.today)) })
                }
                else -> {
                    Text(shown.format(DateTimeFormatter.ofPattern("EEEE d MMM", locale)), style = MaterialTheme.typography.titleMedium)
                    Text(snapshot.settings.homeClass)
                    if (day == null) Text(strings.getString(R.string.not_synced))
                    else if (day.lessons.isEmpty()) Text(strings.getString(R.string.no_lessons))
                    else day.lessons.forEach { lesson ->
                        Button(onClick = { detail = lesson }, modifier = Modifier.fillMaxWidth(), label = {
                            val now = LocalTime.now(snapshot.settings.zone)
                            val current = shown == today && lesson.start != null && lesson.end != null &&
                                !now.isBefore(lesson.start) && now.isBefore(lesson.end)
                            Text("${lesson.start ?: "?"} ${lesson.subjects.joinToString(" / ")}" +
                                if (current) " · ${strings.getString(R.string.now)}" else "")
                        })
                    }
                    Button(onClick = { screen = "week" }, modifier = Modifier.fillMaxWidth(), label = { Text(strings.getString(R.string.week)) })
                    Button(onClick = { screen = "status" }, modifier = Modifier.fillMaxWidth(), label = { Text(strings.getString(R.string.status)) })
                }
            }
        }
    }
}
