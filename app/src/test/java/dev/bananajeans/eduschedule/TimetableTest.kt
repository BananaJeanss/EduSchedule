package dev.bananajeans.eduschedule

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class TimetableTest {
    private val revision = Revision("226", "14 September", LocalDate.of(2026,9,14))
    private fun timetable() = EduPageParser.parse(javaClass.getResource("/timetable.json")!!.readText(), revision)
    @Test fun selectsEffectiveRevisionAndNeverFuture() {
        val next = revision.copy(id="227", from=LocalDate.of(2026,9,21))
        assertEquals(revision,EduPageParser.revisionFor(listOf(revision,next),LocalDate.of(2026,9,18)))
        assertEquals(next,EduPageParser.revisionFor(listOf(revision,next),next.from))
        assertNull(EduPageParser.revisionFor(listOf(revision),revision.from.minusDays(1)))
    }
    @Test fun ignoresHiddenRevisionsAndSortsNumericTieBreaks() {
        val raw = """{"r":{"regular":{"timetables":[{"tt_num":"10","text":"ten","datefrom":"2026-09-01","hidden":false},{"tt_num":"11","text":"hidden","datefrom":"2026-09-02","hidden":true},{"tt_num":"9","text":"nine","datefrom":"2026-09-01"}]}}}"""
        assertEquals(listOf("9","10"),EduPageParser.revisions(raw).map { it.id })
    }
    @Test fun honorsBellOverridesDayOverridesAndDoubleLessons() {
        val t = timetable(); val monday = t.lessons.first { it.day == 0 && it.period == "4" }; val tuesday = t.lessons.first { it.day == 1 }
        assertEquals(LocalTime.of(13,20),monday.start)
        assertEquals(LocalTime.of(16,0),monday.end)
        assertEquals(LocalTime.of(13,25),tuesday.start)
        assertEquals("Teacher One",monday.teacherNames)
    }
    @Test fun filtersGroupsAndCycleWithoutHidingWholeClass() {
        val t = timetable(); val selection = Selection(ScheduleKind.CLASS,"*1")
        assertEquals(1,t.lessonsOn(revision.from,selection).size)
        assertTrue(t.lessonsOn(revision.from,selection,setOf("g1")).isEmpty())
        assertEquals(1,t.lessonsOn(revision.from,selection,setOf("g1"),1).size)
        assertTrue(t.lessonsOn(revision.from.plusDays(6),selection).isEmpty())
    }
    @Test fun supportsTeacherAndRoomSchedules() {
        assertEquals(1,timetable().lessonsOn(revision.from,Selection(ScheduleKind.TEACHER,"-1")).size)
        assertEquals(1,timetable().lessonsOn(revision.from,Selection(ScheduleKind.ROOM,"-8")).size)
    }
    @Test fun invalidTimesRemainUnknown() { assertNull(timetable().lessons.first { it.period == "7" }.start) }
    @Test(expected = IllegalArgumentException::class) fun rejectsErrorEnvelope() { EduPageParser.parse("""{"error":"login required"}""",revision) }
    @Test fun hostValidationRejectsRedirectAndCredentialTricks() {
        assertEquals("kunst.edupage.org",Preferences.normalizeHost("https://kunst.edupage.org/timetable/"))
        listOf("https://kunst.edupage.org.evil.test", "http://kunst.edupage.org", "https://user@kunst.edupage.org", "localhost", "https://kunst.edupage.org:443").forEach { assertTrue(runCatching { Preferences.normalizeHost(it) }.isFailure) }
    }
    @Test fun comparesReleaseNumbersNotStrings() {
        assertTrue(Updates.isNewer("v1.10.0","1.9.0")); assertFalse(Updates.isNewer("v1.0.0-beta","0.1.0"))
        assertFalse(Updates.isNewer("v1.0.0","1.0.0")); assertFalse(Updates.isNewer("v0.9.0","1.0.0"))
    }
}
