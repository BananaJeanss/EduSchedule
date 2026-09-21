package dev.bananajeans.eduschedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ClassRemindersTest {
    @Test
    fun reminderOffsetsIncludePreReminderAndStart() {
        assertEquals(listOf(10, 0), ClassReminders.reminderOffsets(10))
    }

    @Test
    fun zeroLeadSchedulesOnlyStartReminder() {
        assertEquals(listOf(0), ClassReminders.reminderOffsets(0))
    }

    @Test
    fun reminderOffsetsClampToSupportedRange() {
        assertEquals(listOf(60, 0), ClassReminders.reminderOffsets(999))
        assertEquals(listOf(0), ClassReminders.reminderOffsets(-5))
    }

    @Test
    fun preReminderExpiresWhenClassStarts() {
        val start = Instant.parse("2026-09-21T10:00:00Z")
        assertEquals(start, ClassReminders.reminderValidUntil(start, 10))
    }

    @Test
    fun startReminderHasShortGraceWindow() {
        val start = Instant.parse("2026-09-21T10:00:00Z")
        assertEquals(start.plusSeconds(5 * 60), ClassReminders.reminderValidUntil(start, 0))
    }

    @Test
    fun staleReminderIsNeverDelivered() {
        assertTrue(ClassReminders.shouldDeliver(1_000L, 1_000L, 2_000L))
        assertTrue(ClassReminders.shouldDeliver(2_000L, 1_000L, 2_000L))
        assertFalse(ClassReminders.shouldDeliver(999L, 1_000L, 2_000L))
        assertFalse(ClassReminders.shouldDeliver(2_001L, 1_000L, 2_000L))
    }
}
