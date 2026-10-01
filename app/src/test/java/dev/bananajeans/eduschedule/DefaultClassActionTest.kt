package dev.bananajeans.eduschedule

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultClassActionTest {
    @Test fun savedClassCannotBeSetAsDefaultAgain() {
        assertFalse(canMakeDefaultClass(Selection(ScheduleKind.CLASS, "9.C"), "9.C"))
    }

    @Test fun anotherClassCanBecomeDefault() {
        assertTrue(canMakeDefaultClass(Selection(ScheduleKind.CLASS, "9.B"), "9.C"))
        assertTrue(canMakeDefaultClass(Selection(ScheduleKind.CLASS, "9.C"), ""))
    }

    @Test fun nonClassAndMissingSelectionsCannotBecomeDefault() {
        assertFalse(canMakeDefaultClass(null, "9.C"))
        assertFalse(canMakeDefaultClass(Selection(ScheduleKind.TEACHER, "9.C"), "9.C"))
        assertFalse(canMakeDefaultClass(Selection(ScheduleKind.ROOM, "100"), "9.C"))
    }
}
