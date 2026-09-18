package dev.bananajeans.eduschedule

import java.time.LocalTime

data class LessonBlock(val lessons: List<Lesson>) {
    init { require(lessons.isNotEmpty()) }
    val id: String = lessons.map { it.id }.sorted().joinToString("|")
    val start: LocalTime? = lessons.mapNotNull { it.start }.minOrNull()
    val end: LocalTime? = lessons.mapNotNull { it.end }.maxOrNull()
    val subjects: List<String> = lessons.map { it.subject }.distinct()
    val isSplit: Boolean = lessons.size > 1
    val title: String = when {
        subjects.size == 1 -> subjects.first()
        isSplit -> "Group lesson"
        else -> subjects.first()
    }
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
