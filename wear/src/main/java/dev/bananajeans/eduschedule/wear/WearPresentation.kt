package dev.bananajeans.eduschedule.wear

import dev.bananajeans.eduschedule.sync.WearLesson
import java.time.LocalDate
import java.time.LocalTime

enum class LessonState { Current, Next, Past, Upcoming }

internal fun lessonStates(lessons: List<WearLesson>, date: LocalDate, today: LocalDate, now: LocalTime): Map<String, LessonState> {
    val current = if (date == today) lessons.filter { it.start != null && it.end != null && !now.isBefore(it.start) && now.isBefore(it.end) } else emptyList()
    val next = if (date == today && current.isEmpty()) lessons.filter { it.start != null && now.isBefore(it.start) }.minByOrNull { it.start!! } else null
    return lessons.associate { lesson -> lesson.id to when {
        lesson in current -> LessonState.Current
        lesson == next -> LessonState.Next
        date < today || (date == today && lesson.end != null && !now.isBefore(lesson.end)) -> LessonState.Past
        else -> LessonState.Upcoming
    } }
}
