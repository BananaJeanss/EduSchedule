package dev.bananajeans.eduschedule

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

class ChangeStoreTest {
    @Test fun firstRefreshIsSilentAndComparisonSurvivesReopening() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val lesson = Lesson("a:0", "Math", 0, "1", LocalTime.of(8,45), LocalTime.of(9,30),
            listOf("class"), emptyList(), emptyList(), emptyList(), "", "Teacher A", "101", "9.X", "")
        val table = Timetable(Revision("1", "Test", LocalDate.of(2026,9,14)),
            mapOf(ScheduleKind.CLASS to listOf(Entity("class", "9.X"))), listOf(lesson), emptyMap(), emptyList(), "School")
        val host = "${UUID.randomUUID()}.edupage.org"
        val store = ChangeStore(context)
        assertNull(store.record(host, "class", table, emptySet(), 0))
        val changed = table.copy(lessons = listOf(lesson.copy(roomNames = "202")))
        val report = store.record(host, "class", changed, emptySet(), 0)!!
        assertEquals("101", report.changes.single().before!!.room)
        assertEquals("202", report.changes.single().after!!.room)
        assertEquals(report, ChangeStore(context).load(report.id))
        assertNull(store.record(host, "class", changed, emptySet(), 0))
        assertNull(store.load("../../settings"))
        assertNull(store.load(UUID.randomUUID().toString()))
    }
}
