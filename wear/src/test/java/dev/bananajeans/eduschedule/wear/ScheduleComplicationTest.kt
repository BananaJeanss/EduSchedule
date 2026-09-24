package dev.bananajeans.eduschedule.wear

import dev.bananajeans.eduschedule.sync.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class ScheduleComplicationTest {
    @Test fun choosesCurrentOrNextLessonAndNeverYesterday() {
        val today = LocalDate.of(2026, 9, 24)
        val settings = WearSettings("school", "9.A", ZoneId.of("UTC"), "en", "Dark", "Default",
            "#ffffff", "#ffffff", "#000000", true, 10)
        fun lesson(id: String, start: Int, end: Int) = WearLesson(id, listOf(id),
            LocalTime.of(start, 0), LocalTime.of(end, 0), "", "", "")
        val snapshot = WearSnapshot(settings, listOf(
            WearDay(today.minusDays(1), listOf(lesson("past", 12, 13)), Instant.EPOCH),
            WearDay(today, listOf(lesson("first", 9, 10), lesson("second", 11, 12)), Instant.EPOCH)), Instant.EPOCH)
        assertEquals("first", nextLesson(snapshot, today, LocalTime.of(9, 30))?.id)
        assertEquals("second", nextLesson(snapshot, today, LocalTime.of(10, 30))?.id)
        assertNull(nextLesson(snapshot, today, LocalTime.of(12, 0)))
    }
}
