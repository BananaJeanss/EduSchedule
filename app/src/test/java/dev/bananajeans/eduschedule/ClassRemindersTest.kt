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
    fun notificationTitleReflectsReminderTiming() {
        assertEquals("Chemistry starts in 15 min", ClassReminders.notificationTitle("Chemistry", 15))
        assertEquals("Chemistry starts now", ClassReminders.notificationTitle("Chemistry", 0))
    }
}
