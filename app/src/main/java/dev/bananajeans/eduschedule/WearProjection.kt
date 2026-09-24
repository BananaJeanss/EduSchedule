package dev.bananajeans.eduschedule

import dev.bananajeans.eduschedule.sync.WearDay
import dev.bananajeans.eduschedule.sync.WearLesson
import java.time.LocalDate

/** Keep the phone's revision, group and week interpretation authoritative. */
internal fun Snapshot.forWear(date: LocalDate, home: String, hidden: Set<String>, cycle: Int): WearDay =
    WearDay(date, timetable.lessonBlocksOn(date, Selection(ScheduleKind.CLASS, home), hidden, cycle).map { block ->
        WearLesson(block.id, block.subjects, block.start, block.end,
            block.lessons.map { it.roomNames }.filter(String::isNotBlank).distinct().joinToString(" / "),
            block.lessons.map { it.teacherNames }.filter(String::isNotBlank).distinct().joinToString(" / "),
            block.lessons.map { it.group }.filter(String::isNotBlank).distinct().joinToString(" / "))
    }, fetched)
