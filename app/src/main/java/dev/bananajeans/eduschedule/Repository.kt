package dev.bananajeans.eduschedule

import android.content.Context
import android.util.AtomicFile
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.time.*

data class Snapshot(val timetable: Timetable, val fetched: Instant, val offline: Boolean, val warning: String? = null)
class Preferences(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    var host: String
        get() = prefs.getString("host", "kunst.edupage.org")!!
        set(value) { prefs.edit { putString("host", normalizeHost(value)); remove("home"); remove("hidden") } }
    var home: String
        get() = prefs.getString("home", "")!!
        set(value) { prefs.edit { putString("home", value) } }
    var hiddenGroups: Set<String>
        get() = prefs.getStringSet("hidden", emptySet())!!.toSet()
        set(value) { prefs.edit { putStringSet("hidden", value.toSet()) } }
    var theme: String
        get() = prefs.getString("theme", "System")!!
        set(value) { prefs.edit { putString("theme", value) } }
    var dynamic: Boolean
        get() = prefs.getBoolean("dynamic", true)
        set(value) { prefs.edit { putBoolean("dynamic", value) } }
    var notifications: Boolean
        get() = prefs.getBoolean("notifications", false)
        set(value) { prefs.edit { putBoolean("notifications", value) } }
    var zone: String
        get() = prefs.getString("zone", "Europe/Tallinn")!!
        set(value) { ZoneId.of(value); prefs.edit { putString("zone", value) } }
    companion object {
        fun normalizeHost(input: String): String {
            val value = input.trim().lowercase()
            val uri = URI(if (value.contains("://")) value else "https://$value")
            val host = uri.host.orEmpty()
            require(uri.scheme == "https" && uri.userInfo == null && uri.port == -1 &&
                Regex("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.edupage\\.org").matches(host)) {
                "Enter a public school address such as kunst.edupage.org."
            }
            return host
        }
    }
}
object Http {
    fun request(url: String, body: String? = null): String {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000; connection.readTimeout = 25_000
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("User-Agent", "EduSchedule/${BuildConfig.VERSION_NAME} (public timetable reader)")
        connection.setRequestProperty("Accept", "application/json")
        try {
            if (body != null) {
                connection.requestMethod = "POST"; connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toByteArray()) }
            }
            check(connection.responseCode in 200..299) { "Server returned ${connection.responseCode}. Try again later." }
            return connection.inputStream.use { stream ->
                val bytes = stream.readBytesLimited(12 * 1024 * 1024)
                bytes.toString(Charsets.UTF_8)
            }
        } finally { connection.disconnect() }
    }
    private fun java.io.InputStream.readBytesLimited(limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
        while (true) { val n = read(buffer); if (n < 0) break; check(output.size() + n <= limit) { "Timetable response is too large." }; output.write(buffer, 0, n) }
        return output.toByteArray()
    }
}
class Repository(context: Context) {
    private val directory = File(context.filesDir, "timetables").apply { mkdirs() }
    private fun file(host: String, key: String) = File(directory, "$host-$key.json")
    private fun cached(host: String, key: String): JSONObject? = runCatching {
        JSONObject(AtomicFile(file(host, key)).openRead().bufferedReader().use { it.readText() })
    }.getOrNull()
    private fun save(host: String, key: String, raw: String) {
        val atomic = AtomicFile(file(host, key)); val out = atomic.startWrite()
        try { out.write(JSONObject().put("fetched", Instant.now().toString()).put("raw", raw).toString().toByteArray()); atomic.finishWrite(out) }
        catch (e: Exception) { atomic.failWrite(out); throw e }
        directory.listFiles()?.filter { it.extension == "json" }?.sortedByDescending { it.lastModified() }?.drop(24)?.forEach { it.delete() }
    }
    private fun rpc(host: String, module: String, function: String, argument: Any): String = Http.request(
        "https://$host/timetable/server/$module.js?__func=$function",
        JSONObject().put("__args", JSONArray().put(JSONObject.NULL).put(argument)).put("__gsh", "00000000").toString())
    suspend fun load(hostInput: String, date: LocalDate, force: Boolean = false): Snapshot = withContext(Dispatchers.IO) {
        mutex.withLock {
            val host = Preferences.normalizeHost(hostInput)
            var offline = false
            var index = cached(host, "index")
            if (force || index == null || Duration.between(Instant.parse(index.getString("fetched")), Instant.now()).toMinutes() >= 30) {
                try {
                    val raw = rpc(host, "ttviewer", "getTTViewerData", if (date.monthValue >= 8) date.year else date.year - 1)
                    require(EduPageParser.revisions(raw).isNotEmpty()) { "No public timetable is available." }
                    save(host, "index", raw); index = cached(host, "index")
                } catch (e: Exception) { if (index == null) throw e; offline = true }
            }
            val revision = EduPageParser.revisionFor(EduPageParser.revisions(index!!.getString("raw")), date)
                ?: error("No published timetable covers this date.")
            var data = cached(host, revision.id)
            if (force || data == null || Duration.between(Instant.parse(data.getString("fetched")), Instant.now()).toHours() >= 6) {
                try {
                    val raw = rpc(host, "regulartt", "regularttGetData", revision.id)
                    EduPageParser.parse(raw, revision) // Validate before replacing a working snapshot.
                    save(host, revision.id, raw); data = cached(host, revision.id)
                } catch (e: Exception) { if (data == null) throw e; offline = true }
            }
            val timetable = EduPageParser.parse(data!!.getString("raw"), revision)
            Snapshot(timetable, Instant.parse(data.getString("fetched")), offline,
                if (offline) "Offline · showing saved timetable" else null)
        }
    }
    companion object { private val mutex = Mutex() }
}

data class AppRelease(val version: String, val url: String)
object Updates {
    const val REPOSITORY = "BananaJeanss/EduSchedule"
    fun version(value: String): List<Int>? = Regex("^v?(\\d+)\\.(\\d+)\\.(\\d+)$").matchEntire(value)?.groupValues?.drop(1)?.map { it.toIntOrNull() ?: return null }
    fun isNewer(remote: String, local: String): Boolean {
        val a = version(remote) ?: return false; val b = version(local.substringBefore('-')) ?: return false
        return a.zip(b).firstOrNull { it.first != it.second }?.let { it.first > it.second } ?: false
    }
    suspend fun check(): AppRelease? = withContext(Dispatchers.IO) {
        val raw = JSONObject(Http.request("https://api.github.com/repos/$REPOSITORY/releases/latest"))
        val tag = raw.getString("tag_name")
        val url = raw.getString("html_url")
        require(url.startsWith("https://github.com/$REPOSITORY/releases/tag/")) { "Unexpected release destination." }
        if (!raw.optBoolean("draft") && !raw.optBoolean("prerelease") && isNewer(tag, BuildConfig.VERSION_NAME)) AppRelease(tag, url) else null
    }
}
