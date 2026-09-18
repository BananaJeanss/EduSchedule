package dev.bananajeans.eduschedule

import org.json.JSONArray
import org.json.JSONObject
import java.time.*

data class Entity(val id: String, val name: String)
enum class ScheduleKind(val table: String, val label: String) { CLASS("classes", "Classes"), TEACHER("teachers", "Teachers"), ROOM("classrooms", "Rooms") }
data class Selection(val kind: ScheduleKind, val id: String)
data class ScheduleLink(val kind: ScheduleKind, val entity: Entity)
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

    fun linkedSchedules(lesson: Lesson): List<ScheduleLink> {
        val ids = mapOf(
            ScheduleKind.CLASS to lesson.classes,
            ScheduleKind.TEACHER to lesson.teachers,
            ScheduleKind.ROOM to lesson.rooms
        )
        return listOf(ScheduleKind.ROOM, ScheduleKind.TEACHER, ScheduleKind.CLASS).flatMap { kind ->
            val byId = entities[kind].orEmpty().associateBy(Entity::id)
            ids[kind].orEmpty().distinct().mapNotNull { id -> byId[id]?.let { ScheduleLink(kind, it) } }
        }
    }
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
        fun parseClock(value: String): LocalTime? {
            val parts = value.trim().split(":")
            if (parts.size != 2) return null
            val hour = parts[0].toIntOrNull() ?: return null
            val minute = parts[1].toIntOrNull() ?: return null
            return runCatching { LocalTime.of(hour, minute) }.getOrNull()
        }
        fun objectRange(value: JSONObject?): Pair<LocalTime, LocalTime>? {
            value ?: return null
            val start = parseClock(value.optString("starttime")) ?: return null
            val end = parseClock(value.optString("endtime")) ?: return null
            return (start to end).takeIf { end > start }
        }
        fun labelRange(base: JSONObject): Pair<LocalTime, LocalTime>? {
            val range = Regex("""(?<!\d)(\d{1,2}:\d{2})\s*[-–—]\s*(\d{1,2}:\d{2})(?!\d)""")
            for (label in listOf(base.optString("short"), base.optString("name")).distinct()) {
                val match = range.find(label) ?: continue
                val start = parseClock(match.groupValues[1]) ?: continue
                val end = parseClock(match.groupValues[2]) ?: continue
                if (end > start) return start to end
            }
            return null
        }
        fun timeRange(period: String, bell: String, day: Int): Pair<LocalTime, LocalTime>? {
            val base = periods[period] ?: return null
            val override = bells[bell]?.optJSONObject("perioddata")?.optJSONObject(period)

            // A day-specific override is the most explicit source available.
            objectRange(override?.optJSONObject("daydata")?.optJSONObject(day.toString()))?.let { return it }
            objectRange(base.optJSONObject("daydata")?.optJSONObject(day.toString()))?.let { return it }

            // Some EduPage exports contain stale/misaligned structured bell times while the period's
            // user-visible label has the intended time range. Prefer an explicit valid label range
            // before the generic structured values so we match what the published timetable says.
            labelRange(base)?.let { return it }

            return objectRange(override) ?: objectRange(base)
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
                    val baseRange = timeRange(period, bell, day)
                    val endRange = timeRange(endPeriod, bell, day)
                    val start = baseRange?.first
                    val baseEnd = baseRange?.second
                    val extendedEnd = endRange?.second
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
