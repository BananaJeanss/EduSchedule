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
    val date: LocalDate = LocalDate.now(),
    val selection: Selection? = null,
    val selectionName: String? = null,
    val snapshot: Snapshot? = null,
    val week: Map<LocalDate, Snapshot> = emptyMap(),
    val loading: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val theme: String = "System",
    val dynamic: Boolean = true,
    val hidden: Set<String> = emptySet(),
    val cycleWeek: Int = 0,
    val release: AppRelease? = null,
    val updateChecking: Boolean = false,
    val updateDownloading: Boolean = false,
    val updateProgress: Int? = null,
    val home: String = "",
    val host: String = "",
    val notifications: Boolean = false,
    val classReminderLeadMinutes: Int = 10,
    val zone: String = ZoneId.systemDefault().id
)

class ScheduleViewModel(app: Application) : AndroidViewModel(app) {
    val preferences = Preferences(app)
    private val repository = Repository(app)
    private val dayCache = object : LinkedHashMap<LocalDate, Snapshot>(42, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<LocalDate, Snapshot>?): Boolean = size > 42
    }
    private val mutable = MutableStateFlow(
        ScheduleState(
            date = LocalDate.now(ZoneId.of(preferences.zone)),
            theme = preferences.theme,
            dynamic = preferences.dynamic,
            hidden = preferences.hiddenGroups,
            cycleWeek = preferences.cycleWeek,
            home = preferences.home,
            selection = preferences.home.takeIf(String::isNotBlank)?.let { Selection(ScheduleKind.CLASS, it) },
            host = preferences.host,
            notifications = preferences.notifications,
            classReminderLeadMinutes = preferences.classReminderLeadMinutes,
            zone = preferences.zone
        )
    )
    val state = mutable.asStateFlow()
    private var job: Job? = null

    init {
        Background.configure(app, preferences.notifications)
        if (preferences.host.isNotBlank()) refresh()
        checkUpdates(showResult = false)
    }

    fun date(value: LocalDate) {
        job?.cancel()
        val hot = dayCache[value] ?: mutable.value.week[value]
        mutable.update { it.copy(date = value, snapshot = hot, loading = hot == null, error = null) }
        if (hot != null) applySnapshot(hot)
        refresh()
    }

    fun select(value: Selection) {
        val name = mutable.value.snapshot?.timetable?.entities?.get(value.kind)?.find { it.id == value.id }?.name
        mutable.update { it.copy(selection = value, selectionName = name ?: it.selectionName) }
    }

    fun home(value: String) {
        preferences.home = value
        mutable.update { it.copy(home = value) }
        scheduleReminders()
    }

    fun theme(value: String) {
        preferences.theme = value
        mutable.update { it.copy(theme = value) }
    }

    fun dynamic(value: Boolean) {
        preferences.dynamic = value
        mutable.update { it.copy(dynamic = value) }
    }

    fun cycle(value: Int) {
        preferences.cycleWeek = value
        mutable.update { it.copy(cycleWeek = value) }
        scheduleReminders()
    }

    fun groups(value: Set<String>) {
        preferences.hiddenGroups = value
        mutable.update { it.copy(hidden = value) }
        scheduleReminders()
    }

    fun notifications(value: Boolean) {
        preferences.notifications = value
        mutable.update { it.copy(notifications = value) }
        Background.configure(getApplication(), value)
        if (value) scheduleReminders()
    }

    fun classReminderLeadMinutes(value: Int) {
        val minutes = value.coerceIn(0, 60)
        preferences.classReminderLeadMinutes = minutes
        mutable.update { it.copy(classReminderLeadMinutes = minutes) }
        scheduleReminders()
    }

    fun school(value: String, zone: String) {
        try {
            val zoneId = ZoneId.of(zone)
            val host = Preferences.normalizeHost(value)
            preferences.host = host
            preferences.zone = zoneId.id
            preferences.cycleWeek = 0
            dayCache.clear()
            mutable.update {
                it.copy(
                    date = LocalDate.now(zoneId),
                    host = host,
                    zone = zoneId.id,
                    home = "",
                    selection = null,
                    selectionName = null,
                    snapshot = null,
                    week = emptyMap(),
                    hidden = emptySet(),
                    cycleWeek = 0,
                    loading = true,
                    error = null
                )
            }
            refresh(true)
        } catch (e: Exception) {
            message(e.message ?: "Check the EduPage address and time zone.")
        }
    }

    fun message(value: String?) {
        mutable.update { it.copy(message = value) }
    }

    fun refresh(force: Boolean = false) {
        val initial = mutable.value
        if (initial.host.isBlank()) {
            mutable.update { it.copy(loading = false) }
            return
        }

        job?.cancel()
        job = viewModelScope.launch {
            val date = mutable.value.date
            val host = mutable.value.host
            val hot = if (force) null else dayCache[date] ?: mutable.value.week[date]

            if (hot != null) {
                mutable.update { it.copy(snapshot = hot, loading = false, error = null) }
                applySnapshot(hot)
            } else {
                mutable.update { it.copy(loading = true, error = null) }
            }

            try {
                val snapshot = hot ?: repository.load(host, date, force)
                dayCache[date] = snapshot
                applySnapshot(snapshot)

                val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val week = linkedMapOf<LocalDate, Snapshot>()
                for (offset in 0L..6L) {
                    val d = monday.plusDays(offset)
                    try {
                        val cached = dayCache[d]
                        val item = when {
                            d == date -> snapshot
                            cached != null && !force -> cached
                            else -> repository.load(host, d, force)
                        }
                        dayCache[d] = item
                        week[d] = item
                        if (d == mutable.value.date) applySnapshot(item)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        // Missing dates remain absent instead of borrowing another revision.
                    }
                }
                mutable.update { it.copy(week = week) }
                scheduleReminders()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.update { it.copy(error = e.message ?: "Could not load the timetable.") }
            } finally {
                mutable.update { it.copy(loading = false) }
            }
        }
    }

    private fun applySnapshot(snapshot: Snapshot) {
        mutable.update { state ->
            val selection = state.selection?.takeIf { selected ->
                snapshot.timetable.entities[selected.kind].orEmpty().any { it.id == selected.id }
            }
            val name = selection?.let { selected ->
                snapshot.timetable.entities[selected.kind].orEmpty().find { it.id == selected.id }?.name
            }
            state.copy(
                snapshot = snapshot,
                selection = selection,
                selectionName = name ?: state.selectionName.takeIf { selection != null },
                error = null
            )
        }
    }

    private fun scheduleReminders() {
        val state = mutable.value
        if (!state.notifications || state.home.isBlank() || state.host.isBlank()) return
        val zone = ZoneId.of(state.zone)
        val today = LocalDate.now(zone)
        val snapshot = dayCache[today] ?: state.week[today] ?: state.snapshot?.takeIf { state.date == today } ?: return
        ClassReminders.scheduleDay(
            getApplication(),
            today,
            snapshot.timetable,
            state.home,
            state.hidden,
            state.cycleWeek,
            zone,
            state.classReminderLeadMinutes
        )
    }

    fun updates() {
        checkUpdates(showResult = true)
    }

    private fun checkUpdates(showResult: Boolean) = viewModelScope.launch {
        mutable.update { it.copy(updateChecking = true) }
        try {
            val release = Updates.check()
            mutable.update {
                it.copy(
                    release = release,
                    message = if (!showResult) it.message
                    else if (release == null) "You're up to date."
                    else "${release.version} is available."
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            if (showResult) message("No release could be checked. Try again later.")
        } finally {
            mutable.update { it.copy(updateChecking = false) }
        }
    }

    fun installUpdate() {
        val release = mutable.value.release ?: return
        if (mutable.value.updateDownloading) return
        viewModelScope.launch {
            mutable.update { it.copy(updateDownloading = true, updateProgress = 0) }
            try {
                UpdateInstaller.downloadVerifyAndInstall(getApplication(), release) { progress ->
                    mutable.update { state -> state.copy(updateProgress = progress) }
                }
                mutable.update {
                    it.copy(
                        updateDownloading = false,
                        updateProgress = null,
                        message = "Update verified. Confirm installation with Android."
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.update {
                    it.copy(
                        updateDownloading = false,
                        updateProgress = null,
                        message = e.message ?: "Could not prepare the update."
                    )
                }
            }
        }
    }

    fun exportLessons(): List<DatedLesson> {
        val state = mutable.value
        val selection = state.selection ?: return emptyList()
        return state.week.toSortedMap().flatMap { (date, snapshot) ->
            snapshot.timetable.lessonsOn(date, selection, state.hidden, state.cycleWeek).map { DatedLesson(date, it) }
        }
    }
}
