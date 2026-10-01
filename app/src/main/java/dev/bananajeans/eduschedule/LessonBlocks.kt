package dev.bananajeans.eduschedule

import java.time.LocalTime

data class LessonBlock(val lessons: List<Lesson>) {
    init { require(lessons.isNotEmpty()) }
    val id: String = lessons.map { it.id }.sorted().joinToString("|")
    val start: LocalTime? = lessons.mapNotNull { it.start }.minOrNull()
    val end: LocalTime? = lessons.mapNotNull { it.end }.maxOrNull()
    val subjects: List<String> = lessons.map { it.subject }.distinct()
    val isSplit: Boolean = lessons.size > 1
    val title: String = subjects.firstOrNull().orEmpty()
}

fun Timetable.lessonBlocksOn(
    date: java.time.LocalDate,
    selection: Selection,
    hiddenGroups: Set<String> = emptySet(),
    week: Int = 0
): List<LessonBlock> {
    val visible = lessonsOn(date, selection, hiddenGroups, week)
    if (selection.kind != ScheduleKind.CLASS) return visible.map { LessonBlock(listOf(it)) }

    val blocks = mutableListOf<LessonBlock>()
    val splitBySlot = linkedMapOf<String, MutableList<Lesson>>()
    visible.forEach { lesson ->
        if (lesson.groupIds.isEmpty()) {
            blocks += LessonBlock(listOf(lesson))
        } else {
            val key = listOf(lesson.period, lesson.start, lesson.end).joinToString("|")
            splitBySlot.getOrPut(key) { mutableListOf() } += lesson
        }
    }
    blocks += splitBySlot.values.map { LessonBlock(it.sortedBy { lesson -> lesson.group }) }
    return blocks.sortedWith(compareBy<LessonBlock> { it.start ?: LocalTime.MAX }.thenBy { it.title }.thenBy { it.id })
}

// Use the unfiltered block so a previously hidden alternative can be selected again.
fun Timetable.hiddenGroupsForDefault(
    date: java.time.LocalDate,
    selection: Selection,
    lesson: Lesson,
    hiddenGroups: Set<String>,
    week: Int = 0
): Set<String>? {
    if (selection.kind != ScheduleKind.CLASS || lesson.groupIds.isEmpty()) return null
    val block = lessonBlocksOn(date, selection, week = week)
        .firstOrNull { block -> block.lessons.any { it.id == lesson.id } } ?: return null
    val classGroups = groups[selection.id].orEmpty()
    fun expand(ids: Set<String>): Set<String> {
        val names = classGroups.filter { it.id in ids }.map { it.name }.toSet()
        return ids + classGroups.filter { it.name in names }.map { it.id }
    }
    val chosen = expand(lesson.groupIds.toSet())
    val alternatives = expand(block.lessons.flatMap { it.groupIds }.toSet()) - chosen
    if (alternatives.isEmpty()) return null
    return (hiddenGroups + alternatives) - chosen
}
