package dev.bananajeans.eduschedule.sync

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class WearSnapshotTest {
    private val settings = WearSettings("Example School", "9.C", ZoneId.of("Europe/Tallinn"),
        "et", "System", "Custom", "#123456", "#abcdef", "#ffffff", true, 10)

    @Test fun roundTripDistinguishesEmptyDayFromMissingDay() {
        val date = LocalDate.of(2026, 9, 24)
        val snapshot = WearSnapshot(settings, listOf(WearDay(date, emptyList(), Instant.EPOCH),
            WearDay(date.plusDays(1), listOf(WearLesson("card:1", listOf("Math", "Science"),
                LocalTime.of(9, 0), null, "101", "Teacher", "A / B")), Instant.EPOCH)), Instant.EPOCH)
        val result = WearSnapshotCodec.decode(WearSnapshotCodec.encode(snapshot))
        assertEquals(snapshot, result)
        assertTrue(result.days.first().lessons.isEmpty())
        assertFalse(result.days.any { it.date == date.plusDays(2) })
    }

    @Test fun rejectsUnknownVersionAndDuplicateDays() {
        val snapshot = WearSnapshot(settings, emptyList(), Instant.EPOCH)
        val invalid = String(WearSnapshotCodec.encode(snapshot)).replace("\"version\":1", "\"version\":2")
        assertThrows(IllegalArgumentException::class.java) { WearSnapshotCodec.decode(invalid.toByteArray()) }
        assertThrows(IllegalArgumentException::class.java) {
            WearSnapshot(settings, listOf(WearDay(LocalDate.now(), emptyList(), Instant.EPOCH),
                WearDay(LocalDate.now(), emptyList(), Instant.EPOCH)), Instant.EPOCH)
        }
    }

    @Test fun newerSchoolReplacementCannotBeUndoneByDelayedPacket() {
        val before = WearSnapshot(settings, emptyList(), Instant.ofEpochSecond(10))
        val changed = WearSnapshot(settings.copy(school = "other.edupage.org", homeClass = "10.A"),
            emptyList(), Instant.ofEpochSecond(11))
        assertTrue(shouldAcceptSnapshot(before, changed))
        assertFalse(shouldAcceptSnapshot(changed, before))
    }
}
