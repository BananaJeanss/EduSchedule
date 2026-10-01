package dev.bananajeans.eduschedule

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.util.UUID

/** Independent of the live cache: later refreshes cannot erase a notification's comparison. */
class ChangeStore(context: Context) {
    private val directory = File(context.filesDir, "timetable-changes").apply { mkdirs() }
    private fun read(file: File): String? = runCatching {
        AtomicFile(file).openRead().bufferedReader().use { it.readText() }
    }.getOrNull()
    private fun write(file: File, raw: String) {
        val atomic = AtomicFile(file)
        val output = atomic.startWrite()
        try { output.write(raw.toByteArray(Charsets.UTF_8)); atomic.finishWrite(output) }
        catch (e: Exception) { atomic.failWrite(output); throw e }
    }

    suspend fun record(host: String, home: String, table: Timetable, hidden: Set<String>, week: Int): ChangeReport? =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val file = File(directory, "baseline-${Repository.cacheFileName(host, home)}")
                val previous = read(file)?.let { runCatching { ChangeJson.baseline(it) }.getOrNull() }
                val current = TimetableChanges.baseline(table, home)
                val changes = previous?.let { TimetableChanges.compare(it, current, hidden, week) }.orEmpty()
                val report = if (changes.isEmpty()) null else ChangeReport(
                    UUID.randomUUID().toString(), current.school, current.className, Instant.now().toString(),
                    current.effectiveFrom, table.weekNames.getOrNull(week).orEmpty(), changes
                )
                if (report != null) write(File(directory, "report-${report.id}.json"), ChangeJson.report(report))
                write(file, ChangeJson.baseline(current))
                // Keep a bounded offline history. Old notifications receive an explicit unavailable state.
                directory.listFiles()?.filter { it.name.startsWith("report-") && it.extension == "json" }
                    ?.sortedByDescending { it.lastModified() }?.drop(20)?.forEach { it.delete() }
                directory.listFiles()?.filter { it.name.startsWith("baseline-") && it.extension == "json" }
                    ?.sortedByDescending { it.lastModified() }?.drop(12)?.forEach { it.delete() }
                report
            }
        }

    suspend fun load(id: String): ChangeReport? = withContext(Dispatchers.IO) {
        // Notification extras never become arbitrary filesystem paths.
        if (!Regex("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}").matches(id)) return@withContext null
        read(File(directory, "report-$id.json"))?.let { runCatching { ChangeJson.report(it) }.getOrNull() }
            ?.takeIf { it.id == id }
    }
    companion object { private val mutex = Mutex() }
}
