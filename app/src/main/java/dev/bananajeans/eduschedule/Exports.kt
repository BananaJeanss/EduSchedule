package dev.bananajeans.eduschedule

import java.security.MessageDigest
import java.time.*
import java.time.format.DateTimeFormatter

data class DatedLesson(val date: LocalDate, val lesson: Lesson)
object Exports {
    private val utcFormat = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)

    fun instant(date: LocalDate, time: LocalTime, zone: ZoneId): Instant =
        date.atTime(time).atZone(zone).toInstant()

    fun ics(
        host: String,
        title: String,
        lessons: List<DatedLesson>,
        zone: ZoneId,
        description: String,
        now: Instant = Instant.now()
    ): String {
        val lines = mutableListOf(
            "BEGIN:VCALENDAR",
            "VERSION:2.0",
            "PRODID:-//EduSchedule//Android//",
            "CALSCALE:GREGORIAN",
            "METHOD:PUBLISH",
            "X-WR-CALNAME:${escape(title)}"
        )
        lessons.filter { it.lesson.start != null && it.lesson.end != null }.forEach { (date, lesson) ->
            val uid = MessageDigest.getInstance("SHA-256")
                .digest("$host|$date|${lesson.id}".toByteArray())
                .joinToString("") { "%02x".format(it) }
            lines += listOf(
                "BEGIN:VEVENT",
                "UID:$uid@eduschedule",
                "DTSTAMP:${utcFormat.format(now)}",
                "DTSTART:${utcFormat.format(instant(date, lesson.start!!, zone))}",
                "DTEND:${utcFormat.format(instant(date, lesson.end!!, zone))}",
                "SUMMARY:${escape(lesson.subject)}",
                "LOCATION:${escape(lesson.roomNames)}",
                "DESCRIPTION:${escape(listOf(lesson.teacherNames, lesson.classNames, lesson.group, description).filter(String::isNotBlank).joinToString("\n"))}",
                "END:VEVENT"
            )
        }
        lines += "END:VCALENDAR"
        return lines.joinToString("\r\n", postfix = "\r\n") { fold(it) }
    }

    fun csv(lessons: List<DatedLesson>, headers: List<String>): String {
        require(headers.size == 8) { "CSV export requires exactly eight headers." }
        return (listOf(headers) + lessons.map { (date, l) ->
            listOf(
                date.toString(),
                l.start?.toString().orEmpty(),
                l.end?.toString().orEmpty(),
                l.subject,
                l.roomNames,
                l.teacherNames,
                l.classNames,
                l.group
            )
        }).joinToString("\r\n", postfix = "\r\n") { row ->
            row.joinToString(",") { value ->
                val safe = if (value.trimStart().firstOrNull() in listOf('=', '+', '-', '@', '\t', '\r')) "'$value" else value
                "\"${safe.replace("\"", "\"\"")}\""
            }
        }
    }

    internal fun escape(s: String) = s.replace("\\", "\\\\")
        .replace("\r\n", "\n")
        .replace("\r", "\n")
        .replace("\n", "\\n")
        .replace(";", "\\;")
        .replace(",", "\\,")

    internal fun fold(line: String): String {
        val out = StringBuilder()
        var bytes = 0
        line.codePoints().forEach { cp ->
            val text = String(Character.toChars(cp))
            val size = text.toByteArray(Charsets.UTF_8).size
            if (bytes + size > 75) {
                out.append("\r\n ")
                bytes = 1
            }
            out.append(text)
            bytes += size
        }
        return out.toString()
    }
}
