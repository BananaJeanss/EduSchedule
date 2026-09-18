package dev.bananajeans.eduschedule
import org.junit.Assert.*
import org.junit.Test
import java.time.*
class ExportsTest {
    private val date = LocalDate.of(2026,9,14)
    private fun lesson() = EduPageParser.parse(javaClass.getResource("/timetable.json")!!.readText(),Revision("226","Test",date)).lessons.first()
    @Test fun writesEscapedUtcEventsWithStableUids() {
        val events = listOf(DatedLesson(date,lesson()))
        val first = Exports.ics("kunst.edupage.org","Class",events,ZoneId.of("Europe/Tallinn"),Instant.EPOCH)
        assertTrue(first.contains("DTSTART:20260914T102000Z"))
        assertTrue(first.contains("SUMMARY:Art\\, design\\; studio"))
        assertTrue(first.endsWith("END:VCALENDAR\r\n"))
        val second = Exports.ics("kunst.edupage.org","Class",events,ZoneId.of("Europe/Tallinn"),Instant.now())
        assertEquals(first.lines().first { it.startsWith("UID:") },second.lines().first { it.startsWith("UID:") })
    }
    @Test fun foldsUtf8WithoutBreakingCharacters() {
        val text = "DESCRIPTION:" + "õ😀".repeat(60)
        val folded = Exports.fold(text)
        assertTrue(folded.split("\r\n").all { it.toByteArray().size <= 75 })
        assertEquals(text,folded.replace("\r\n ",""))
    }
    @Test fun skipsUnknownTimes() {
        assertFalse(Exports.ics("school","Class",listOf(DatedLesson(date,lesson().copy(start=null,end=null))),ZoneOffset.UTC).contains("BEGIN:VEVENT"))
    }
    @Test fun handlesDstUsingSchoolZone() {
        assertEquals(Instant.parse("2026-10-26T06:45:00Z"),Exports.instant(LocalDate.of(2026,10,26),LocalTime.of(8,45),ZoneId.of("Europe/Tallinn")))
    }
    @Test fun protectsCsvFormulaAndQuotes() {
        val csv = Exports.csv(listOf(DatedLesson(date,lesson().copy(subject="=HYPERLINK(\"x\")"))))
        assertTrue(csv.contains("\"'=HYPERLINK(\"\"x\"\")\""))
    }
}
