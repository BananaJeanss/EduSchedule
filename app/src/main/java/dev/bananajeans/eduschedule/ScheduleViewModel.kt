package dev.bananajeans.eduschedule

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.*
import java.time.temporal.TemporalAdjusters

data class ScheduleState(
    val date: LocalDate = LocalDate.now(ZoneId.of("Europe/Tallinn")), val selection: Selection? = null,
    val snapshot: Snapshot? = null, val week: Map<LocalDate, Snapshot> = emptyMap(),
    val loading: Boolean = false, val error: String? = null, val message: String? = null,
    val theme: String = "System", val dynamic: Boolean = true, val hidden: Set<String> = emptySet(),
    val cycleWeek: Int = 0, val release: AppRelease? = null, val updateChecking: Boolean = false,
    val home: String = "", val host: String = "kunst.edupage.org", val notifications: Boolean = false,
    val zone: String = "Europe/Tallinn"
)
class ScheduleViewModel(app: Application) : AndroidViewModel(app) {
    val preferences = Preferences(app)
    private val repository = Repository(app)
    private val mutable = MutableStateFlow(ScheduleState(date = LocalDate.now(ZoneId.of(preferences.zone)), theme = preferences.theme,
        dynamic = preferences.dynamic, hidden = preferences.hiddenGroups, home = preferences.home,
        selection = preferences.home.takeIf(String::isNotBlank)?.let { Selection(ScheduleKind.CLASS, it) },
        host = preferences.host, notifications = preferences.notifications, zone = preferences.zone))
    val state = mutable.asStateFlow()
    private var job: Job? = null
    init { refresh(); Background.configure(app, preferences.notifications) }
    fun date(value: LocalDate) { mutable.update { it.copy(date = value) }; refresh() }
    fun select(value: Selection) { mutable.update { it.copy(selection = value) } }
    fun home(value: String) { preferences.home = value; mutable.update { it.copy(home = value) } }
    fun theme(value: String) { preferences.theme = value; mutable.update { it.copy(theme = value) } }
    fun dynamic(value: Boolean) { preferences.dynamic = value; mutable.update { it.copy(dynamic = value) } }
    fun cycle(value: Int) { mutable.update { it.copy(cycleWeek = value) } }
    fun groups(value: Set<String>) { preferences.hiddenGroups = value; mutable.update { it.copy(hidden = value) } }
    fun notifications(value: Boolean) {
        preferences.notifications = value; mutable.update { it.copy(notifications = value) }
        Background.configure(getApplication(), value)
    }
    fun school(value: String, zone: String) {
        try {
            ZoneId.of(zone); val host = Preferences.normalizeHost(value)
            preferences.host = host; preferences.zone = zone
            mutable.update { it.copy(host = host, zone = zone, home = "", selection = null, snapshot = null, week = emptyMap(), hidden = emptySet()) }
            refresh(true)
        } catch (e: Exception) { message(e.message ?: "Check your school address and time zone.") }
    }
    fun message(value: String?) { mutable.update { it.copy(message = value) } }
    fun refresh(force: Boolean = false) {
        job?.cancel()
        job = viewModelScope.launch {
            val date = mutable.value.date; val host = mutable.value.host
            mutable.update { it.copy(loading = true, error = null, snapshot = null, week = emptyMap()) }
            try {
                val snapshot = repository.load(host, date, force)
                mutable.update { s -> s.copy(snapshot = snapshot, selection = s.selection?.takeIf { sel -> snapshot.timetable.entities[sel.kind].orEmpty().any { it.id == sel.id } }) }
                val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val week = mutableMapOf<LocalDate, Snapshot>()
                for (offset in 0L..6L) {
                    val d = monday.plusDays(offset)
                    try { week[d] = if (d == date) snapshot else repository.load(host, d) }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { /* Missing dates are shown explicitly, never replaced with another revision. */ }
                }
                mutable.update { it.copy(week = week) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(error = e.message ?: "Could not load the timetable.") } }
            finally { mutable.update { it.copy(loading = false) } }
        }
    }
    fun updates() = viewModelScope.launch {
        mutable.update { it.copy(updateChecking = true) }
        try { val release = Updates.check(); mutable.update { it.copy(release = release, message = if (release == null) "You're up to date." else "${release.version} is available.") } }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { message("No release could be checked. Try again later.") }
        finally { mutable.update { it.copy(updateChecking = false) } }
    }
    fun exportLessons(): List<DatedLesson> {
        val s = mutable.value; val selection = s.selection ?: return emptyList()
        return s.week.toSortedMap().flatMap { (date, snapshot) -> snapshot.timetable.lessonsOn(date, selection, s.hidden, s.cycleWeek).map { DatedLesson(date, it) } }
    }
}
