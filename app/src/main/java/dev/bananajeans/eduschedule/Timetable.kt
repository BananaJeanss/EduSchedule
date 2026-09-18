package dev.bananajeans.eduschedule

import org.json.JSONArray
import org.json.JSONObject
import java.time.*

data class Entity(val id: String, val name: String)
enum class ScheduleKind(val table: String, val label: String) { CLASS("classes", "Classes"), TEACHER("teachers", "Teachers"), ROOM("classrooms", "Rooms") }
data class Selection(val kind: ScheduleKind, val id: String)
data class Revision(val id: String, val name: String, val from: LocalDate)
data class Lesson(
    val id: String, val subject: String, val day: Int, val period: String,
    val start: LocalTime?, val end: LocalTime?, val classes: List<String>,
    val teachers: List<String>, val rooms: List<String>, val groupIds: List<String>,
    val group: String, val teacherNames: String, val roomNames: String, val classNames: String,
    val weeks: String
) {
    fun belongsTo(selection: Selection) = selection.id in when (selection.kind) {
        ScheduleKind.CLASS -> classes; ScheduleKind.TEACHER -> teachers; ScheduleKind.ROOM -> rooms
    }
}
data class Timetable(
    val revision: Revision, val entities: Map<ScheduleKind, List<Entity>>, val lessons: List<Lesson>,
    val groups: Map<String, List<Entity>>, val weekNames: List<String>, val school: String
) {
    fun lessonsOn(date: LocalDate, selection: Selection, hiddenGroups: Set<String> = emptySet(), week: Int = 0): List<Lesson> =
        lessons.filter { it.day == date.dayOfWeek.value - 1 && it.belongsTo(selection) &&
            (it.groupIds.isEmpty() || it.groupIds.any { id -> id !in hiddenGroups }) &&
            (it.weeks.isEmpty() || it.weeks.getOrNull(week) == '1') }
            .sortedWith(compareBy<Lesson> { it.start ?: LocalTime.MAX }.thenBy { it.subject }.thenBy { it.id })
}

object EduPageParser {
    private fun result(raw: String): JSONObject {
        val root = JSONObject(raw)
        require(!root.has("error") && !root.has("e")) { "The school did not publish readable timetable data." }
        return root.optJSONObject("r") ?: error("Unrecognized EduPage response. Your saved timetable is safe.")
    }
    fun revisions(raw: String): List<Revision> = result(raw).getJSONObject("regular").getJSONArray("timetables").objects()
        .filter { !it.optBoolean("hidden") }
        .map {
            val id = it.getString("tt_num")
            require(Regex("[0-9]{1,10}").matches(id)) { "Unrecognized timetable identifier." }
            Revision(id, it.getString("text"), LocalDate.parse(it.getString("datefrom")))
        }
        .sortedWith(compareBy<Revision> { it.from }.thenBy { it.id.toIntOrNull() ?: 0 })

    fun revisionFor(revisions: List<Revision>, date: LocalDate): Revision? = revisions.lastOrNull { !it.from.isAfter(date) }

    fun parse(raw: String, revision: Revision): Timetable {
        val result = result(raw)
        val rights = result.optJSONObject("rights") ?: JSONObject()
        val tables = result.getJSONObject("dbiAccessorRes").getJSONArray("tables").objects()
            .associate { it.getString("id") to it.getJSONArray("data_rows").objects() }
        fun rows(name: String) = tables[name].orEmpty()
        fun index(name: String) = rows(name).associateBy { it.getString("id") }
        fun name(row: JSONObject) = row.optString("name").ifBlank { row.optString("short") }.ifBlank { row.getString("id") }
        val entities = ScheduleKind.entries.associateWith { kind ->
            if (!rights.optBoolean(kind.table, true)) emptyList() else rows(kind.table).map { Entity(it.getString("id"), name(it)) }
                .sortedWith(compareBy<Entity> { Regex("^\\d+").find(it.name)?.value?.toIntOrNull() ?: Int.MAX_VALUE }.thenBy { it.name.lowercase() })
        }
        val classes = index("classes"); val teachers = index("teachers"); val rooms = index("classrooms")
        val groups = index("groups"); val subjects = index("subjects"); val lessons = index("lessons")
        val periodRows = rows("periods")
        val periodKeys = periodRows.map { it.optString("period", it.getString("id")) }
        val periods = periodRows.associateBy { it.optString("period", it.getString("id")) }
        val bells = index("bells")
        fun names(ids: List<String>, source: Map<String, JSONObject>) = ids.map { source[it]?.let(::name) ?: it }.joinToString(", ")
        fun time(period: String, bell: String, day: Int, field: String): LocalTime? {
            val base = periods[period] ?: return null
            val override = bells[bell]?.optJSONObject("perioddata")?.optJSONObject(period)
            val rawTime = override?.optJSONObject("daydata")?.optJSONObject(day.toString())?.optString(field)?.takeIf { it.isNotBlank() }
                ?: override?.optString(field)?.takeIf { it.isNotBlank() }
                ?: base.optJSONObject("daydata")?.optJSONObject(day.toString())?.optString(field)?.takeIf { it.isNotBlank() }
                ?: base.optString(field)
            return runCatching { LocalTime.parse(rawTime) }.getOrNull()
        }
        val cards = rows("cards").flatMap { card ->
            val lesson = lessons[card.getString("lessonid")] ?: error("Timetable has an unknown lesson reference.")
            val groupIds = lesson.strings("groupids").distinct()
            val classIds = (lesson.strings("classids") + groupIds.mapNotNull { groups[it]?.optString("classid") }).distinct()
            val teacherIds = lesson.strings("teacherids").distinct()
            val roomIds = card.strings("classroomids").distinct()
            val bell = lesson.optString("bell").ifBlank { classIds.firstNotNullOfOrNull { classes[it]?.optString("bell")?.takeIf(String::isNotBlank) }
                ?: teacherIds.firstNotNullOfOrNull { teachers[it]?.optString("bell")?.takeIf(String::isNotBlank) }.orEmpty() }
            val period = card.getString("period")
            val duration = card.optInt("durationperiods", lesson.optInt("durationperiods", 1)).coerceAtLeast(1)
            val periodIndex = periodKeys.indexOf(period)
            val endPeriod = if (periodIndex >= 0) periodKeys.getOrNull(periodIndex + duration - 1) ?: period else period
            val splitGroups = groupIds.filter { groups[it]?.optBoolean("entireclass") != true }
            card.getString("days").mapIndexedNotNull { day, enabled ->
                if (enabled != '1' || day > 6) null else {
                    val start = time(period, bell, day, "starttime")
                    val baseEnd = time(period, bell, day, "endtime")
                    val extendedEnd = time(endPeriod, bell, day, "endtime")
                    val baseMinutes = if (start != null && baseEnd != null && baseEnd > start) Duration.between(start, baseEnd).toMinutes() else 0
                    // Some schools publish long teaching blocks (for example 75 minutes) while lesson.durationperiods
                    // still reflects the editor's underlying grid. Extending those blocks creates phantom late endings.
                    // Only extend short, conventional periods; otherwise the published period end is authoritative.
                    val end = if (duration > 1 && baseMinutes in 1..60) extendedEnd else baseEnd
                    val validTimes = start != null && end != null && end > start
                    Lesson(card.getString("id") + ":" + day, subjects[lesson.optString("subjectid")]?.let(::name) ?: "Untitled lesson",
                        day, period, start.takeIf { validTimes }, end.takeIf { validTimes }, classIds, teacherIds, roomIds,
                        splitGroups, names(splitGroups, groups), names(teacherIds, teachers), names(roomIds, rooms), names(classIds, classes), card.optString("weeks", "1"))
                }
            }
        }
        require(entities[ScheduleKind.CLASS].orEmpty().isNotEmpty()) { "No public classes found. This school may require a login." }
        return Timetable(revision, entities, cards, rows("groups").filter { !it.optBoolean("entireclass") }
            .groupBy { it.getString("classid") }.mapValues { (_, rows) -> rows.map { Entity(it.getString("id"), name(it)) } },
            rows("weeks").map(::name), rows("globals").firstOrNull()?.optString("reg_name").orEmpty())
    }
}
internal fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
internal fun JSONObject.strings(key: String): List<String> = optJSONArray(key)?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
