package dev.bananajeans.eduschedule
import org.junit.Assert.*
import org.junit.Test
import java.time.*
class ExportsTest {
    private val date = LocalDate.of(2026,9,14)
    private val headers = listOf("Date", "Start", "End", "Subject", "Room", "Teacher", "Class", "Group")
    private val description = "Published timetable; check EduPage for substitutions."
    private fun lesson() = EduPageParser.parse(javaClass.getResource("/timetable.json")!!.readText(),Revision("226","Test",date)).lessons.first()
    @Test fun writesEscapedUtcEventsWithStableUids() {
        val events = listOf(DatedLesson(date,lesson()))
        val first = Exports.ics("school.edupage.org","Class",events,ZoneId.of("Europe/Tallinn"),description,Instant.EPOCH)
        assertTrue(first.contains("DTSTART:20260914T100000Z"))
        assertTrue(first.contains("SUMMARY:Art\\, design\\; studio"))
        assertTrue(first.endsWith("END:VCALENDAR\r\n"))
        val second = Exports.ics("school.edupage.org","Class",events,ZoneId.of("Europe/Tallinn"),description,Instant.now())
        assertEquals(first.lines().first { it.startsWith("UID:") },second.lines().first { it.startsWith("UID:") })
    }
    @Test fun injectsLocalizedExportCopy() {
        val localizedHeaders = listOf("Kuupäev", "Algus", "Lõpp", "Aine", "Ruum", "Õpetaja", "Klass", "Rühm")
        val event = DatedLesson(date, lesson())
        val ics = Exports.ics("school.edupage.org", "Klass", listOf(event), ZoneId.of("Europe/Tallinn"), "Avaldatud tunniplaan")
        val csv = Exports.csv(listOf(event), localizedHeaders)
        assertTrue(ics.contains("Avaldatud tunniplaan"))
        assertTrue(csv.startsWith("\"Kuupäev\",\"Algus\""))
    }

    @Test fun foldsUtf8WithoutBreakingCharacters() {
        val text = "DESCRIPTION:" + "õ😀".repeat(60)
        val folded = Exports.fold(text)
        assertTrue(folded.split("\r\n").all { it.toByteArray().size <= 75 })
        assertEquals(text,folded.replace("\r\n ",""))
    }
    @Test fun skipsUnknownTimes() {
        assertFalse(Exports.ics("school","Class",listOf(DatedLesson(date,lesson().copy(start=null,end=null))),ZoneOffset.UTC,description).contains("BEGIN:VEVENT"))
    }
    @Test fun handlesDstUsingSchoolZone() {
        assertEquals(Instant.parse("2026-10-26T06:45:00Z"),Exports.instant(LocalDate.of(2026,10,26),LocalTime.of(8,45),ZoneId.of("Europe/Tallinn")))
    }
    @Test fun protectsCsvFormulaAndQuotes() {
        val csv = Exports.csv(listOf(DatedLesson(date,lesson().copy(subject="=HYPERLINK(\"x\")"))), headers)
        assertTrue(csv.contains("\"'=HYPERLINK(\"\"x\"\")\""))
    }
}
