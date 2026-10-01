package dev.bananajeans.eduschedule.wear

import dev.bananajeans.eduschedule.sync.WearLesson
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class WearPresentationTest {
    private val today = LocalDate.of(2026, 10, 1)
    private fun lesson(id: String, start: String?, end: String?) = WearLesson(id, listOf("Example"), start?.let(LocalTime::parse), end?.let(LocalTime::parse), "", "", "")
    private val lessons = listOf(lesson("later", "10:00", "10:45"), lesson("first", "09:00", "09:45"), lesson("unknown", null, null))
    @Test fun currentLessonUsesInclusiveStartAndExclusiveEnd() {
        assertEquals(LessonState.Current, lessonStates(lessons, today, today, LocalTime.of(9, 0))["first"])
        val after = lessonStates(lessons, today, today, LocalTime.of(9, 45))
        assertEquals(LessonState.Past, after["first"])
        assertEquals(LessonState.Next, after["later"])
        assertEquals(LessonState.Upcoming, after["unknown"])
    }
    @Test fun nextLessonUsesTimeRatherThanPayloadOrder() {
        assertEquals(LessonState.Next, lessonStates(lessons, today, today, LocalTime.of(8, 0))["first"])
        assertEquals(LessonState.Upcoming, lessonStates(lessons, today, today, LocalTime.of(9, 20))["later"])
    }
    @Test fun otherDatesNeverGetCurrentOrNextLabels() {
        assertEquals(setOf(LessonState.Past), lessonStates(lessons, today.minusDays(1), today, LocalTime.of(9, 10)).values.toSet())
        assertEquals(setOf(LessonState.Upcoming), lessonStates(lessons, today.plusDays(1), today, LocalTime.of(9, 10)).values.toSet())
    }
    @Test fun parallelCurrentLessonsRemainCurrent() {
        val parallel = lessons + lesson("parallel", "09:00", "09:45")
        val states = lessonStates(parallel, today, today, LocalTime.of(9, 20))
        assertEquals(LessonState.Current, states["first"])
        assertEquals(LessonState.Current, states["parallel"])
    }
}
