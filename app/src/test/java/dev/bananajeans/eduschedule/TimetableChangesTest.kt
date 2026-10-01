package dev.bananajeans.eduschedule

import org.junit.Assert.*
import org.junit.Test

class TimetableChangesTest {
    private val lesson = ChangeLesson("a:0", "Math", 0, "1", "08:45", "09:30", "Teacher A", "101", "Group 1", listOf("g1"), "10")
    private fun baseline(vararg lessons: ChangeLesson) = ChangeBaseline("School", "9.X", "Test", "2026-09-14", lessons.toList())
    private fun compare(before: ChangeBaseline, after: ChangeBaseline, hidden: Set<String> = emptySet(), week: Int = 0) =
        TimetableChanges.compare(before, after, hidden, week)

    @Test fun changedTimeRoomAndTeacherKeepBeforeAndAfter() {
        val next = lesson.copy(start = "09:45", end = "10:30", room = "202", teacher = "Teacher B")
        assertEquals(listOf(LessonChange(lesson, next)), compare(baseline(lesson), baseline(next)))
    }
    @Test fun reorderedLessonsAndRegeneratedIdsDoNotNotify() {
        val second = lesson.copy(id = "b", subject = "Art", day = 1)
        assertTrue(compare(baseline(lesson, second), baseline(second.copy(id = "x"), lesson.copy(id = "y", groupIds = listOf("new")))).isEmpty())
    }
    @Test fun movedLessonWithNewCardIdIsOneChange() {
        val next = lesson.copy(id = "new:2", day = 2, period = "3")
        assertEquals(listOf(LessonChange(lesson, next)), compare(baseline(lesson), baseline(next)))
    }
    @Test fun addedRemovedAndSubjectReplacementAreExplicit() {
        assertEquals(listOf(LessonChange(null, lesson)), compare(baseline(), baseline(lesson)))
        assertEquals(listOf(LessonChange(lesson, null)), compare(baseline(lesson), baseline()))
        val next = lesson.copy(id = "replacement", subject = "Physics")
        assertEquals(listOf(LessonChange(lesson, next)), compare(baseline(lesson), baseline(next)))
    }
    @Test fun hiddenGroupsAndInactiveCyclesDoNotNotify() {
        assertTrue(compare(baseline(lesson), baseline(lesson.copy(room = "202")), setOf("g1")).isEmpty())
        assertTrue(compare(baseline(lesson), baseline(lesson.copy(room = "202")), week = 1).isEmpty())
        // The newly selected cycle is applied to both snapshots, not stored as an old filter.
        val other = lesson.copy(weeks = "01")
        assertTrue(compare(baseline(other), baseline(other), week = 1).isEmpty())
        assertEquals(listOf(LessonChange(lesson, null)), compare(baseline(lesson), baseline(lesson.copy(weeks = "01"))))
    }
    @Test fun ambiguousRepeatedLessonsAreNeverGuessedAsMoves() {
        val second = lesson.copy(id = "b", day = 1)
        val next = lesson.copy(id = "c", day = 2)
        val changes = compare(baseline(lesson, second), baseline(next))
        assertEquals(3, changes.size)
        assertTrue(changes.none { it.before != null && it.after != null })
    }
    @Test fun jsonRoundTripPreservesMissingTimesUnicodeAndBothVersions() {
        val before = lesson.copy(start = null, end = null, subject = "Kunst & disain", teacher = "Õpetaja")
        val after = lesson.copy(room = "202")
        val report = ChangeReport("uuid", "School", "9.X", "2026-10-01T08:00:00Z", "2026-09-14", "A",
            listOf(LessonChange(before, after), LessonChange(null, lesson), LessonChange(lesson, null)))
        assertEquals(report, ChangeJson.report(ChangeJson.report(report)))
        assertEquals(baseline(before), ChangeJson.baseline(ChangeJson.baseline(baseline(before))))
    }
}
