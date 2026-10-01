package dev.bananajeans.eduschedule

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/** A compact local copy of a class's recurring public lessons, independent of editor IDs. */
data class ChangeLesson(
    val id: String,
    val subject: String,
    val day: Int,
    val period: String,
    val start: String?,
    val end: String?,
    val teacher: String,
    val room: String,
    val group: String,
    val groupIds: List<String>,
    val weeks: String
) {
    internal fun visible(hidden: Set<String>, week: Int) =
        (groupIds.isEmpty() || groupIds.any { it !in hidden }) &&
            (weeks.isEmpty() || weeks.getOrNull(week) == '1')

    // Internal IDs, input order and inactive cycle bits are not visible changes.
    internal fun sameContent(other: ChangeLesson) = copy(id = "", groupIds = emptyList(), weeks = "") ==
        other.copy(id = "", groupIds = emptyList(), weeks = "")
}

data class ChangeBaseline(
    val school: String,
    val className: String,
    val revisionName: String,
    val effectiveFrom: String,
    val lessons: List<ChangeLesson>
)

data class LessonChange(val before: ChangeLesson?, val after: ChangeLesson?)
data class ChangeReport(
    val id: String,
    val school: String,
    val className: String,
    val detectedAt: String,
    val effectiveFrom: String,
    val cycleName: String,
    val changes: List<LessonChange>
)

object TimetableChanges {
    fun baseline(table: Timetable, home: String) = ChangeBaseline(
        table.school,
        table.entities[ScheduleKind.CLASS].orEmpty().firstOrNull { it.id == home }?.name ?: home,
        table.revision.name,
        table.revision.from.toString(),
        table.lessons.filter { it.belongsTo(Selection(ScheduleKind.CLASS, home)) }.map {
            ChangeLesson(it.id, it.subject, it.day, it.period, it.start?.toString(), it.end?.toString(),
                it.teacherNames, it.roomNames, it.group, it.groupIds, it.weeks)
        }
    )

    fun compare(before: ChangeBaseline, after: ChangeBaseline, hidden: Set<String>, week: Int): List<LessonChange> {
        val old = before.lessons.filter { it.visible(hidden, week) }.toMutableList()
        val new = after.lessons.filter { it.visible(hidden, week) }.toMutableList()
        val changes = mutableListOf<LessonChange>()
        // Cancel equal display content first, even when EduPage regenerates every card ID.
        old.toList().forEach { a ->
            val b = new.firstOrNull { a.sameContent(it) }
            if (b != null) { old.remove(a); new.remove(b) }
        }
        fun match(predicate: (ChangeLesson, ChangeLesson) -> Boolean) {
            old.toList().forEach { a ->
                val candidates = new.filter { predicate(a, it) }
                val b = candidates.singleOrNull()
                // Ambiguous parallel/repeated lessons stay as explicit additions/removals.
                if (b != null && old.count { predicate(it, b) } == 1) {
                    changes += LessonChange(a, b)
                    old.remove(a); new.remove(b)
                }
            }
        }
        match { a, b -> a.id == b.id && a.subject == b.subject }
        match { a, b -> a.subject == b.subject && a.group == b.group && a.day == b.day && a.period == b.period }
        match { a, b -> a.subject == b.subject && a.group == b.group }
        match { a, b -> a.day == b.day && a.period == b.period && a.group == b.group }
        changes += old.map { LessonChange(it, null) }
        changes += new.map { LessonChange(null, it) }
        return changes.sortedWith(compareBy<LessonChange> { (it.after ?: it.before)!!.day }
            .thenBy { (it.after ?: it.before)!!.start ?: "99:99" }
            .thenBy { (it.after ?: it.before)!!.subject })
    }
}

/** Versioned JSON is shared by disk persistence and JVM regression tests. */
object ChangeJson {
    private fun lesson(l: ChangeLesson) = JSONObject().put("id", l.id).put("subject", l.subject)
        .put("day", l.day).put("period", l.period).put("start", l.start ?: JSONObject.NULL)
        .put("end", l.end ?: JSONObject.NULL).put("teacher", l.teacher).put("room", l.room)
        .put("group", l.group).put("groupIds", JSONArray(l.groupIds)).put("weeks", l.weeks)
    private fun lesson(j: JSONObject) = ChangeLesson(j.getString("id"), j.getString("subject"),
        j.getInt("day").also { require(it in 0..6) }, j.getString("period"),
        if (j.isNull("start")) null else j.getString("start"),
        if (j.isNull("end")) null else j.getString("end"), j.getString("teacher"), j.getString("room"),
        j.getString("group"), j.strings("groupIds"), j.getString("weeks"))
    fun baseline(value: ChangeBaseline): String = JSONObject().put("schema", 1)
        .put("school", value.school).put("className", value.className).put("revisionName", value.revisionName)
        .put("effectiveFrom", value.effectiveFrom).put("lessons", JSONArray(value.lessons.map(::lesson))).toString()
    fun baseline(raw: String): ChangeBaseline {
        val j = JSONObject(raw); require(j.getInt("schema") == 1)
        return ChangeBaseline(j.getString("school"), j.getString("className"), j.getString("revisionName"),
            j.getString("effectiveFrom"), j.getJSONArray("lessons").objects().map { lesson(it) })
    }
    fun report(value: ChangeReport): String = JSONObject().put("schema", 1).put("id", value.id)
        .put("school", value.school).put("className", value.className).put("detectedAt", value.detectedAt)
        .put("effectiveFrom", value.effectiveFrom).put("cycleName", value.cycleName)
        .put("changes", JSONArray(value.changes.map { JSONObject()
            .put("before", it.before?.let(::lesson) ?: JSONObject.NULL)
            .put("after", it.after?.let(::lesson) ?: JSONObject.NULL) })).toString()
    fun report(raw: String): ChangeReport {
        val j = JSONObject(raw); require(j.getInt("schema") == 1)
        Instant.parse(j.getString("detectedAt"))
        return ChangeReport(j.getString("id"), j.getString("school"), j.getString("className"),
            j.getString("detectedAt"), j.getString("effectiveFrom"), j.getString("cycleName"),
            j.getJSONArray("changes").objects().map {
                val before = it.optJSONObject("before")?.let(::lesson)
                val after = it.optJSONObject("after")?.let(::lesson)
                require(before != null || after != null)
                LessonChange(before, after)
            })
    }
}
