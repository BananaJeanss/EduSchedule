package dev.bananajeans.eduschedule

import org.junit.Assert.assertEquals
import org.junit.Test

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
}
