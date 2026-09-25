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
    val previews: Map<LocalDate, Snapshot?> = emptyMap(),
    val previewGeneration: Int = 0,
    val loading: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val theme: String = "System",
    val palette: String = "Default",
    val customColors: ThemeColors = ThemeColors(),
    val dynamic: Boolean = true,
    val language: AppLanguage = AppLanguage.SYSTEM,
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
            palette = preferences.palette,
            customColors = preferences.customColors,
            dynamic = preferences.dynamic,
            language = preferences.language,
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
    private val previewJobs = mutableMapOf<LocalDate, Job>()

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

    /** Adjacent pages only read validated disk snapshots; a peek never starts network work. */
    fun preview(dates: List<LocalDate>) {
        val host = mutable.value.host
        val generation = mutable.value.previewGeneration
        if (host.isBlank()) return
        dates.forEach { date ->
            if (date in mutable.value.previews || previewJobs[date]?.isActive == true) return@forEach
            previewJobs[date] = viewModelScope.launch {
                try {
                    val cached = dayCache[date] ?: mutable.value.week[date] ?: repository.loadCached(host, date)
                    if (host == mutable.value.host && generation == mutable.value.previewGeneration) {
                        mutable.update { state ->
                            state.copy(previews = (state.previews + (date to cached)).entries
                                .toList().takeLast(42).associate { it.toPair() })
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    if (host == mutable.value.host && generation == mutable.value.previewGeneration) {
                        mutable.update { it.copy(previews = it.previews + (date to null)) }
                    }
                } finally {
                    if (generation == mutable.value.previewGeneration) previewJobs.remove(date)
                }
            }
        }
    }

    private fun invalidatePreviews() {
        previewJobs.values.toList().forEach { it.cancel() }
        previewJobs.clear()
        mutable.update { it.copy(previews = emptyMap(), previewGeneration = it.previewGeneration + 1) }
    }

    fun select(value: Selection) {
        val name = mutable.value.snapshot?.timetable?.entities?.get(value.kind)?.find { it.id == value.id }?.name
        mutable.update { it.copy(selection = value, selectionName = name ?: it.selectionName) }
    }

    fun home(value: String) {
        preferences.home = value
        mutable.update { it.copy(home = value) }
        scheduleReminders()
        ScheduleWidgets.refresh(getApplication())
        WearPublisher.enqueue(getApplication())
    }

    fun theme(value: String) {
        preferences.theme = value
        mutable.update { it.copy(theme = value) }
        WearPublisher.enqueue(getApplication())
        ScheduleWidgets.refresh(getApplication())
    }

    fun palette(value: String) {
        preferences.palette = value
        mutable.update { it.copy(palette = value) }
        WearPublisher.enqueue(getApplication())
        ScheduleWidgets.refresh(getApplication())
    }

    fun customColors(value: ThemeColors) {
        preferences.customColors = value
        preferences.palette = "Custom"
        mutable.update { it.copy(customColors = value, palette = "Custom") }
        WearPublisher.enqueue(getApplication())
        ScheduleWidgets.refresh(getApplication())
    }

    fun dynamic(value: Boolean) {
        preferences.dynamic = value
        mutable.update { it.copy(dynamic = value) }
        WearPublisher.enqueue(getApplication())
        ScheduleWidgets.refresh(getApplication())
    }

    fun language(value: AppLanguage) {
        preferences.language = value
        mutable.update { it.copy(language = value) }
        scheduleReminders()
        WearPublisher.enqueue(getApplication())
    }

    fun cycle(value: Int) {
        preferences.cycleWeek = value
        mutable.update { it.copy(cycleWeek = value) }
        scheduleReminders()
        ScheduleWidgets.refresh(getApplication())
        WearPublisher.enqueue(getApplication())
    }

    fun groups(value: Set<String>) {
        preferences.hiddenGroups = value
        mutable.update { it.copy(hidden = value) }
        scheduleReminders()
        ScheduleWidgets.refresh(getApplication())
        WearPublisher.enqueue(getApplication())
    }

    fun notifications(value: Boolean) {
        preferences.notifications = value
        mutable.update { it.copy(notifications = value) }
        Background.configure(getApplication(), value)
        if (value) scheduleReminders()
        WearPublisher.enqueue(getApplication())
    }

    fun classReminderLeadMinutes(value: Int) {
        val minutes = value.coerceIn(0, 60)
        preferences.classReminderLeadMinutes = minutes
        mutable.update { it.copy(classReminderLeadMinutes = minutes) }
        scheduleReminders()
        WearPublisher.enqueue(getApplication())
    }

    fun school(value: String, zone: String) {
        try {
            val zoneId = ZoneId.of(zone)
            val host = Preferences.normalizeHost(value)
            ClassReminders.cancelAll(getApplication())
            preferences.host = host
            preferences.zone = zoneId.id
            preferences.cycleWeek = 0
            invalidatePreviews()
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
            WearPublisher.enqueue(getApplication())
        } catch (_: Exception) {
            message(localized(R.string.check_address_timezone))
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
                invalidatePreviews()
                scheduleReminders()
                ScheduleWidgets.refresh(getApplication())
                WearPublisher.enqueue(getApplication())
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                mutable.update { it.copy(error = localized(R.string.timetable_load_failed)) }
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
        if (!state.notifications || state.home.isBlank() || state.host.isBlank()) {
            ClassReminders.cancelAll(getApplication())
            return
        }
        val zone = ZoneId.of(state.zone)
        val today = LocalDate.now(zone)
        listOf(today, today.plusDays(1)).forEach { date ->
            val snapshot = dayCache[date]
                ?: state.week[date]
                ?: state.snapshot?.takeIf { state.date == date }
                ?: return@forEach
            ClassReminders.scheduleDay(
                getApplication(),
                date,
                snapshot.timetable,
                state.home,
                state.hidden,
                state.cycleWeek,
                zone,
                state.classReminderLeadMinutes
            )
        }
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
                    else if (release == null) localized(R.string.up_to_date)
                    else localized(R.string.version_available, release.version)
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            if (showResult) message(localized(R.string.release_check_failed))
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
                        message = localized(R.string.update_ready)
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.update {
                    it.copy(
                        updateDownloading = false,
                        updateProgress = null,
                        message = localized(R.string.update_prepare_failed)
                    )
                }
            }
        }
    }

    private fun localized(resource: Int, vararg args: Any): String =
        AppLocale.string(getApplication(), preferences.language, resource, *args)

    fun exportLessons(): List<DatedLesson> {
        val state = mutable.value
        val selection = state.selection ?: return emptyList()
        return state.week.toSortedMap().flatMap { (date, snapshot) ->
            snapshot.timetable.lessonsOn(date, selection, state.hidden, state.cycleWeek).map { DatedLesson(date, it) }
        }
    }
}

