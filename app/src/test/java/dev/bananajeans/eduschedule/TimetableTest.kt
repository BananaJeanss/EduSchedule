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
    @Test fun prefersPublishedLabelRangeOverConflictingGenericTimes() {
        val monday = timetable().lessons.first { it.day == 0 && it.period == "4" }
        assertEquals(LocalTime.of(13,0), monday.start)
        assertEquals(LocalTime.of(14,40), monday.end)
        assertEquals("Teacher One", monday.teacherNames)
    }
    @Test fun daySpecificOverrideStillWinsOverLabelRange() {
        val tuesday = timetable().lessons.first { it.day == 1 && it.period == "4" }
        assertEquals(LocalTime.of(13,25), tuesday.start)
        assertEquals(LocalTime.of(14,40), tuesday.end)
    }
    @Test fun singlePeriodUsesItsExplicitPublishedRange() {
        val lesson = timetable().lessons.first { it.day == 2 && it.period == "5" }
        assertEquals(LocalTime.of(13,55), lesson.start)
        assertEquals(LocalTime.of(14,40), lesson.end)
    }
    @Test fun filtersGroupsAndCycleWithoutHidingWholeClass() {
        val t = timetable(); val selection = Selection(ScheduleKind.CLASS,"*1")
        assertEquals(1,t.lessonsOn(revision.from,selection).size)
        assertTrue(t.lessonsOn(revision.from,selection,setOf("g1")).isEmpty())
        assertEquals(1,t.lessonsOn(revision.from,selection,setOf("g1"),1).size)
        assertTrue(t.lessonsOn(revision.from.plusDays(6),selection).isEmpty())
    }
    @Test fun mergesParallelClassGroupsIntoOneDisplayBlock() {
        val t = timetable()
        val base = t.lessons.first { it.day == 0 && it.period == "4" }
        val alt = base.copy(id = "alt", subject = "Design", groupIds = listOf("g2"), group = "Group 2")
        val blocks = t.copy(lessons = listOf(base, alt)).lessonBlocksOn(revision.from, Selection(ScheduleKind.CLASS, "*1"))
        assertEquals(1, blocks.size)
        assertEquals(2, blocks.single().lessons.size)
        assertEquals(listOf("Art, design; studio", "Design"), blocks.single().subjects)
    }
    @Test fun duplicateGroupIdsDoNotRepeatLabels() {
        val raw = javaClass.getResource("/timetable.json")!!.readText().replace("\"groupids\":[\"g1\"]", "\"groupids\":[\"g1\",\"g1\",\"g1\"]")
        val lesson = EduPageParser.parse(raw, revision).lessons.first { it.period == "4" }
        assertEquals("Group 1", lesson.group)
        assertEquals(listOf("g1"), lesson.groupIds)
    }
    @Test fun distinctGroupIdsWithSameNamePrintOnceButRemainFilterable() {
        val raw = javaClass.getResource("/timetable.json")!!.readText()
            .replace("\"groupids\":[\"g1\"]", "\"groupids\":[\"g1\",\"g2\"]")
            .replace("\"entireclass\":false}]", "\"entireclass\":false},{\"id\":\"g2\",\"classid\":\"*1\",\"name\":\"Group 1\",\"entireclass\":false}]")
        val table = EduPageParser.parse(raw, revision)
        val lesson = table.lessons.first { it.period == "4" }
        assertEquals(listOf("g1", "g2"), lesson.groupIds)
        assertEquals("Group 1", lesson.group)
        assertEquals(1, table.lessonsOn(revision.from, Selection(ScheduleKind.CLASS, "*1"), setOf("g1")).size)
        assertTrue(table.lessonsOn(revision.from, Selection(ScheduleKind.CLASS, "*1"), setOf("g1", "g2")).isEmpty())
    }
    @Test fun cacheFileNamesAreDeterministicAndDoNotExposeInputs() {
        val name = Repository.cacheFileName("school.edupage.org", "227")
        assertTrue(name.matches(Regex("[0-9a-f]{64}\\.json")))
        assertEquals(name, Repository.cacheFileName("school.edupage.org", "227"))
        assertFalse(name.contains("school"))
        assertFalse(name.contains("227"))
    }
    @Test fun supportsTeacherAndRoomSchedules() {
        assertEquals(1,timetable().lessonsOn(revision.from,Selection(ScheduleKind.TEACHER,"-1")).size)
        assertEquals(1,timetable().lessonsOn(revision.from,Selection(ScheduleKind.ROOM,"-8")).size)
    }
    @Test fun lessonDetailLinksResolveExactSchedules() {
        val t = timetable()
        val lesson = t.lessons.first { it.day == 0 && it.period == "4" }
        val links = t.linkedSchedules(lesson)
        assertTrue(links.any { it.kind == ScheduleKind.CLASS && it.entity.id == "*1" })
        assertTrue(links.any { it.kind == ScheduleKind.TEACHER && it.entity.id == "-1" })
        assertTrue(links.any { it.kind == ScheduleKind.ROOM && it.entity.id == "-8" })
        assertEquals(links.size, links.distinctBy { it.kind to it.entity.id }.size)
    }
    @Test fun invalidTimesRemainUnknown() { assertNull(timetable().lessons.first { it.period == "7" }.start) }
    @Test(expected = IllegalArgumentException::class) fun rejectsErrorEnvelope() { EduPageParser.parse("""{"error":"login required"}""",revision) }
    @Test fun hostValidationRejectsRedirectAndCredentialTricks() {
        assertEquals("school.edupage.org",Preferences.normalizeHost("https://school.edupage.org/timetable/"))
        listOf("https://school.edupage.org.evil.test", "http://school.edupage.org", "https://user@school.edupage.org", "localhost", "https://school.edupage.org:443").forEach { assertTrue(runCatching { Preferences.normalizeHost(it) }.isFailure) }
    }
    @Test fun comparesReleaseNumbersNotStrings() {
        assertTrue(Updates.isNewer("v1.10.0","1.9.0")); assertFalse(Updates.isNewer("v1.0.0-beta","0.1.0"))
        assertFalse(Updates.isNewer("v1.0.0","1.0.0")); assertFalse(Updates.isNewer("v0.9.0","1.0.0"))
    }
    @Test fun updateAssetsArePinnedAndChecksumsAreParsedExactly() {
        val release = AppRelease("v0.3.0")
        assertEquals("EduSchedule-0.3.0.apk", release.apkName)
        assertEquals(
            "https://github.com/BananaJeanss/EduSchedule/releases/download/v0.3.0/EduSchedule-0.3.0.apk",
            Updates.assetUri(release, release.apkName).toString()
        )
        val hash = "a".repeat(64)
        val manifest = "$hash  EduSchedule-0.3.0.apk\nbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb  EduSchedule-0.3.0.aab\n"
        assertEquals(hash, Updates.checksumFor(manifest, release.apkName))
        assertNull(Updates.checksumFor(manifest, "EduSchedule-0.4.0.apk"))
        assertTrue(runCatching { Updates.assetUri(release, "../other.apk") }.isFailure)
        assertTrue(runCatching { Updates.assetUri(AppRelease("../../bad"), "SHA256SUMS") }.isFailure)
    }
}
