package dev.bananajeans.eduschedule.sync

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** The phone publishes display-ready regular lessons. No EduPage payload or credentials cross devices. */
data class WearLesson(
    val id: String,
    val subjects: List<String>,
    val start: LocalTime?,
    val end: LocalTime?,
    val room: String,
    val teacher: String,
    val group: String
)

/** A present empty day means no published lessons; an absent date means that date was not synced. */
data class WearDay(val date: LocalDate, val lessons: List<WearLesson>, val fetchedAt: Instant)

data class WearSettings(
    val school: String,
    val homeClass: String,
    val zone: ZoneId,
    val language: String,
    val theme: String,
    val palette: String,
    val primary: String,
    val secondary: String,
    val surface: String,
    val reminders: Boolean,
    val leadMinutes: Int
)

data class WearSnapshot(val settings: WearSettings, val days: List<WearDay>, val publishedAt: Instant) {
    init {
        require(days.size <= 14 && days.map { it.date }.distinct().size == days.size)
        require(days.all { it.lessons.size <= 80 })
        require(settings.leadMinutes in 0..60)
    }
}

object WearSnapshotCodec {
    const val VERSION = 1
    const val MAX_BYTES = 256 * 1024

    fun encode(snapshot: WearSnapshot): ByteArray {
        val s = snapshot.settings
        val root = JSONObject().put("version", VERSION).put("publishedAt", snapshot.publishedAt.toString())
            .put("settings", JSONObject().put("school", s.school).put("homeClass", s.homeClass)
                .put("zone", s.zone.id).put("language", s.language).put("theme", s.theme)
                .put("palette", s.palette).put("primary", s.primary).put("secondary", s.secondary)
                .put("surface", s.surface).put("reminders", s.reminders).put("leadMinutes", s.leadMinutes))
        root.put("days", JSONArray().apply {
            snapshot.days.forEach { day -> put(JSONObject().put("date", day.date.toString())
                .put("fetchedAt", day.fetchedAt.toString()).put("lessons", JSONArray().apply {
                    day.lessons.forEach { lesson -> put(JSONObject().put("id", lesson.id)
                        .put("subjects", JSONArray(lesson.subjects))
                        .put("start", lesson.start?.toString() ?: JSONObject.NULL)
                        .put("end", lesson.end?.toString() ?: JSONObject.NULL)
                        .put("room", lesson.room).put("teacher", lesson.teacher).put("group", lesson.group)) }
                })) }
        })
        return root.toString().toByteArray(Charsets.UTF_8).also { require(it.size <= MAX_BYTES) }
    }

    fun decode(bytes: ByteArray): WearSnapshot {
        require(bytes.size <= MAX_BYTES)
        val root = JSONObject(bytes.toString(Charsets.UTF_8))
        require(root.getInt("version") == VERSION) { "Unsupported Wear snapshot version" }
        val raw = root.getJSONObject("settings")
        val settings = WearSettings(raw.getString("school"), raw.getString("homeClass"),
            ZoneId.of(raw.getString("zone")), raw.getString("language"), raw.getString("theme"),
            raw.getString("palette"), raw.getString("primary"), raw.getString("secondary"),
            raw.getString("surface"), raw.getBoolean("reminders"), raw.getInt("leadMinutes"))
        val rawDays = root.getJSONArray("days")
        require(rawDays.length() <= 14)
        val days = (0 until rawDays.length()).map { index ->
            val day = rawDays.getJSONObject(index)
            val rawLessons = day.getJSONArray("lessons")
            require(rawLessons.length() <= 80)
            WearDay(LocalDate.parse(day.getString("date")), (0 until rawLessons.length()).map { i ->
                val lesson = rawLessons.getJSONObject(i)
                val subjects = lesson.getJSONArray("subjects")
                WearLesson(lesson.getString("id"), (0 until subjects.length()).map(subjects::getString),
                    lesson.optString("start").takeIf(String::isNotBlank)?.let(LocalTime::parse),
                    lesson.optString("end").takeIf(String::isNotBlank)?.let(LocalTime::parse),
                    lesson.getString("room"), lesson.getString("teacher"), lesson.getString("group"))
            }, Instant.parse(day.getString("fetchedAt")))
        }
        return WearSnapshot(settings, days, Instant.parse(root.getString("publishedAt")))
    }
}
