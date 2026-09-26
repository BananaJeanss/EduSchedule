package dev.bananajeans.eduschedule

/** Only a different class can replace the saved home class. */
internal fun canMakeDefaultClass(selection: Selection?, home: String): Boolean =
    selection?.kind == ScheduleKind.CLASS && selection.id != home
