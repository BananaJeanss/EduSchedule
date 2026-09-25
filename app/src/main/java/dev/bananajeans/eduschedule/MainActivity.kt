package dev.bananajeans.eduschedule

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URI
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

class MainActivity : ComponentActivity() {
    override fun onResume() {
        super.onResume()
        InstallResultRouter.resumed(this)
    }

    override fun onPause() {
        InstallResultRouter.paused(this)
        super.onPause()
    }

    override fun attachBaseContext(newBase: Context) {
        val language = Preferences(newBase).language
        super.attachBaseContext(AppLocale.wrap(newBase, language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        lifecycleScope.launch(Dispatchers.IO) { ScheduleWidgets.publishPreviews(applicationContext) }
        val openSettings = intent.getBooleanExtra(EXTRA_OPEN_SETTINGS, false)
        setContent {
            val vm: ScheduleViewModel = viewModel()
            val state by vm.state.collectAsStateWithLifecycle()
            EduTheme(state.theme, state.dynamic, state.palette, state.customColors) { ScheduleApp(vm, state, openSettings) }
        }
    }

    companion object {
        const val EXTRA_OPEN_SETTINGS = "open_settings"
    }
}
@Composable fun EduTheme(theme: String = "System", dynamic: Boolean = true, palette: String = "Default", customColors: ThemeColors = ThemeColors(), content: @Composable () -> Unit) {
    val dark = theme == "Dark" || (theme == "System" && isSystemInDarkTheme())
    val context = LocalContext.current
    val scheme = if (palette == "Default" && dynamic && Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else themeScheme(palette, dark, customColors)
    MaterialExpressiveTheme(
        colorScheme = scheme,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8.dp),
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(28.dp),
            extraLarge = RoundedCornerShape(32.dp)
        ),
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ScheduleApp(vm: ScheduleViewModel, s: ScheduleState, openSettingsInitially: Boolean = false) {
    if (s.host.isBlank()) { SetupScreen(vm, s.zone, s.language); return }
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val exportSavedMessage = stringResource(R.string.export_saved)
    val exportSaveFailedMessage = stringResource(R.string.export_save_failed)
    val notificationsDisabledMessage = stringResource(R.string.notifications_disabled)
    val allowInstallsMessage = stringResource(R.string.allow_installs)
    val allowInstallsSettingsMessage = stringResource(R.string.allow_installs_settings)
    val noAppForLinkMessage = stringResource(R.string.no_app_for_link)
    val defaultClassSavedMessage = stringResource(R.string.default_class_saved)
    val timetableLabel = stringResource(R.string.timetable)
    val calendarEventDescription = stringResource(R.string.calendar_event_description)
    val installCalendarOrExportMessage = stringResource(R.string.install_calendar_or_export)
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableStateOf("Day") }
    var menu by remember { mutableStateOf(false) }
    var settings by rememberSaveable { mutableStateOf(openSettingsInitially) }
    var showExport by remember { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<DatedLesson?>(null) }
    var pendingExport by remember { mutableStateOf("") }
    val snack = remember { SnackbarHostState() }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        if (uri != null) scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(pendingExport.toByteArray()) }
                        ?: error("Could not open the destination.")
                }
                vm.message(exportSavedMessage)
            } catch (_: Exception) {
                vm.message(exportSaveFailedMessage)
            }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.notifications(granted)
        if (!granted) vm.message(notificationsDisabledMessage)
    }
    val installPermission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (context.packageManager.canRequestPackageInstalls()) vm.installUpdate()
        else vm.message(allowInstallsMessage)
    }
    fun requestUpdateInstall() {
        if (context.packageManager.canRequestPackageInstalls()) {
            vm.installUpdate()
            return
        }
        try {
            installPermission.launch(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    "package:${context.packageName}".toUri()
                )
            )
        } catch (_: Exception) {
            vm.message(allowInstallsSettingsMessage)
        }
    }
    fun open(url: String) {
        try {
            val parsed = URI(url)
            val host = parsed.host?.lowercase().orEmpty()
            require(parsed.scheme == "https" && parsed.userInfo == null && parsed.port == -1 &&
                (host == "github.com" || Preferences.isEduPageHost(host))) {
                "Unexpected link destination."
            }
            context.startActivity(
                Intent(Intent.ACTION_VIEW, url.toUri()).addCategory(Intent.CATEGORY_BROWSABLE)
            )
        } catch (_: Exception) {
            vm.message(noAppForLinkMessage)
        }
    }
    LaunchedEffect(s.message) { s.message?.let { snack.showSnackbar(it); vm.message(null) } }
    BackHandler(settings || tab != "Day") { if (settings) settings = false else tab = "Day" }
    val selectedSnapshot = s.snapshot ?: s.week[s.date] ?: s.previews[s.date]
    val timetable = selectedSnapshot?.timetable
    val selectedName = s.selectionName
    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(title = { Column {
                Text(
                    if (settings) stringResource(R.string.settings) else selectedName ?: stringResource(R.string.your_timetable),
                    fontWeight = FontWeight.SemiBold
                )
                if (!settings) Text(s.host.substringBefore('.'), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } }, navigationIcon = {
                if (settings) IconButton(onClick = { settings = false }) {
                    Glyph("back", stringResource(R.string.back))
                }
            },
                actions = {
                    if (!settings) {
                        IconButton(onClick = { vm.refresh(true) }, enabled = !s.loading) {
                            if (s.loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            else Glyph("refresh", stringResource(R.string.refresh_timetable))
                        }
                        Box {
                            IconButton(onClick = { menu = true }) {
                                Glyph("more", stringResource(R.string.more_options))
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.choose_schedule)) }, onClick = { menu = false; tab = "Browse" })
                                if (s.home.isNotBlank()) DropdownMenuItem(text = { Text(stringResource(R.string.my_class)) }, onClick = { vm.select(Selection(ScheduleKind.CLASS,s.home)); menu = false; tab = "Day" })
                                if (s.selection?.kind == ScheduleKind.CLASS) DropdownMenuItem(text = { Text(stringResource(R.string.make_this_my_class)) }, onClick = {
                                    vm.home(s.selection.id)
                                    menu = false
                                    vm.message(defaultClassSavedMessage)
                                })
                                DropdownMenuItem(text = { Text(stringResource(R.string.export_week)) }, enabled = s.selection != null && s.week.isNotEmpty() && !s.loading, onClick = { menu = false; showExport = true })
                                DropdownMenuItem(text = { Text(stringResource(R.string.open_school_timetable)) }, onClick = { menu = false; open("https://${s.host}/timetable/") })
                                DropdownMenuItem(text = { Text(stringResource(R.string.settings)) }, onClick = { menu = false; settings = true })
                            }
                        }
                    }
                })
        },
        bottomBar = {
            if (!settings) NavigationBar {
                listOf(
                    Triple("Day", "day", R.string.day),
                    Triple("Week", "week", R.string.week),
                    Triple("Browse", "browse", R.string.browse)
                ).forEach { (key, icon, label) ->
                    NavigationBarItem(
                        selected = tab == key,
                        onClick = { tab = key },
                        icon = { Glyph(icon) },
                        label = { Text(stringResource(label)) }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (settings) SettingsScreen(s, vm, {
                if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS) else vm.notifications(true)
            }, ::requestUpdateInstall, ::open)
            else Column {
                if (s.error != null && s.selection == null) EmptyState(
                    stringResource(R.string.couldnt_load_date),
                    s.error,
                    stringResource(R.string.retry)
                ) { vm.refresh(true) }
                else if (timetable == null && s.selection == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else {
                    if (selectedSnapshot?.offline == true) Surface(color = MaterialTheme.colorScheme.tertiaryContainer) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Glyph("offline", stringResource(R.string.using_saved_offline_timetable))
                            Text(
                                stringResource(
                                    R.string.offline_saved_at,
                                    selectedSnapshot.fetched.atZone(ZoneId.of(s.zone))
                                        .format(DateTimeFormatter.ofPattern("d MMM, HH:mm", locale))
                                ),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                    when {
                        tab == "Browse" || s.selection == null -> {
                            if (timetable != null) BrowseScreen(timetable, s.selection, s.home, { vm.select(it); if (s.home.isBlank() && it.kind == ScheduleKind.CLASS) vm.home(it.id); tab = "Day" })
                            else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        }
                        else -> {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                FilledTonalIconButton(onClick = { vm.date(s.date.minusDays(if (tab == "Week") 7 else 1)) }) {
                                    Glyph("back", stringResource(if (tab == "Week") R.string.previous_week else R.string.previous_day))
                                }
                                TextButton(onClick = { showDate = true }, modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(s.date.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy", locale)))
                                        if (s.loading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                    }
                                }
                                FilledTonalIconButton(onClick = { vm.date(s.date.plusDays(if (tab == "Week") 7 else 1)) }) {
                                    Glyph("next", stringResource(if (tab == "Week") R.string.next_week else R.string.next_day))
                                }
                                TextButton(onClick = { vm.date(LocalDate.now(ZoneId.of(s.zone))) }) {
                                    Text(stringResource(R.string.today))
                                }
                            }
                            if (timetable != null && timetable.weekNames.size > 1) Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                timetable.weekNames.forEachIndexed { i, name -> FilterChip(s.cycleWeek == i, { vm.cycle(i) }, { Text(name) }) }
                            }
                            key(tab, s.host) {
                                val weekView = tab == "Week"
                                val selected = if (weekView) s.date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) else s.date
                                DatePager(selected, onDateChange = { pageDate ->
                                    vm.date(if (weekView) pageDate.plusDays(s.date.dayOfWeek.value - 1L) else pageDate)
                                }, stepDays = if (weekView) 7 else 1) { pageDate ->
                                    LaunchedEffect(pageDate, s.host, s.previewGeneration) {
                                        vm.preview(if (weekView) (0L..6L).map(pageDate::plusDays) else listOf(pageDate))
                                    }
                                    if (weekView) {
                                        val snapshots = s.previews.mapNotNull { (date, value) -> value?.let { date to it } }.toMap() + s.week + listOfNotNull(s.snapshot?.let { s.date to it }).toMap()
                                        WeekScreen(s.copy(date = pageDate, week = snapshots),
                                            startAtEnd = pageDate < selected, onLesson = { detail = it })
                                    } else {
                                        val snapshot = if (pageDate == s.date) selectedSnapshot else s.week[pageDate] ?: s.previews[pageDate]
                                        if (snapshot != null) DayScreen(
                                            snapshot.timetable.lessonBlocksOn(pageDate, s.selection, s.hidden, s.cycleWeek),
                                            pageDate, s.zone, snapshot.timetable.revision.name,
                                            onLesson = { detail = DatedLesson(pageDate, it) })
                                        else SchedulePagePlaceholder(pageDate,
                                            loading = (pageDate == s.date && s.loading) || pageDate !in s.previews,
                                            error = s.error.takeIf { pageDate == s.date },
                                            onRetry = { if (pageDate == s.date) vm.refresh(true) else vm.date(pageDate) })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showDate) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = s.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(onDismissRequest = { showDate = false }, confirmButton = { TextButton(onClick = {
            picker.selectedDateMillis?.let { vm.date(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }; showDate = false
        }) { Text(stringResource(R.string.go)) } }, dismissButton = {
            TextButton(onClick = { showDate = false }) { Text(stringResource(R.string.cancel)) }
        }) { DatePicker(picker) }
    }
    if (showExport) {
        val exportDescription = buildString {
            append(stringResource(R.string.export_description))
            if (s.week.size < 7) {
                append(" ")
                append(stringResource(R.string.export_partial_warning))
            }
        }
        val calendarDescription = stringResource(R.string.calendar_event_description)
        val csvHeaders = listOf(
            stringResource(R.string.csv_date),
            stringResource(R.string.csv_start),
            stringResource(R.string.csv_end),
            stringResource(R.string.csv_subject),
            stringResource(R.string.csv_room),
            stringResource(R.string.csv_teacher),
            stringResource(R.string.csv_class),
            stringResource(R.string.csv_group)
        )
        AlertDialog(
            onDismissRequest = { showExport = false },
            title = { Text(stringResource(R.string.export_this_week)) },
            text = { Text(exportDescription) },
            confirmButton = {
                TextButton(enabled = s.week.size == 7, onClick = {
                    pendingExport = Exports.ics(
                        s.host,
                        selectedName ?: timetableLabel,
                        vm.exportLessons(),
                        ZoneId.of(s.zone),
                        calendarDescription
                    )
                    export.launch("EduSchedule-${s.date}.ics")
                    showExport = false
                }) { Text(stringResource(R.string.calendar_ics)) }
            },
            dismissButton = {
                TextButton(enabled = s.week.size == 7, onClick = {
                    pendingExport = Exports.csv(vm.exportLessons(), csvHeaders)
                    export.launch("EduSchedule-${s.date}.csv")
                    showExport = false
                }) { Text(stringResource(R.string.spreadsheet_csv)) }
            }
        )
    }
    detail?.let { dated ->
        val l = dated.lesson
        val links = timetable?.linkedSchedules(l).orEmpty()
        ModalBottomSheet(
            onDismissRequest = { detail = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                Modifier.fillMaxWidth()
                    .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.85f).dp)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(l.subject, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "${dated.date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", locale))} · ${l.start ?: "?"}–${l.end ?: "?"}",
                    style = MaterialTheme.typography.titleMedium
                )

                listOf(
                    ScheduleKind.ROOM to l.roomNames,
                    ScheduleKind.TEACHER to l.teacherNames,
                    ScheduleKind.CLASS to l.classNames
                ).forEach { (kind, fallback) ->
                    val label = stringResource(kind.singularLabelResource())
                    val openDescription = stringResource(
                        when (kind) {
                            ScheduleKind.ROOM -> R.string.open_room_schedule
                            ScheduleKind.TEACHER -> R.string.open_teacher_schedule
                            ScheduleKind.CLASS -> R.string.open_class_schedule
                        }
                    )
                    val entityLinks = links.filter { it.kind == kind }
                    if (entityLinks.isNotEmpty()) {
                        entityLinks.forEach { link ->
                            ListItem(
                                overlineContent = { Text(label) },
                                headlineContent = { Text(link.entity.name, fontWeight = FontWeight.Medium) },
                                trailingContent = { Glyph("next", openDescription) },
                                modifier = Modifier.fillMaxWidth().clickable {
                                    detail = null
                                    vm.date(dated.date)
                                    vm.select(Selection(link.kind, link.entity.id))
                                    tab = "Day"
                                },
                                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                            )
                        }
                    } else if (fallback.isNotBlank()) {
                        Text("$label · $fallback", style = MaterialTheme.typography.bodyLarge)
                    }
                }
                if (l.group.isNotBlank()) {
                    Text("${stringResource(R.string.group)} · ${l.group}", style = MaterialTheme.typography.bodyLarge)
                }

                Button(enabled = l.start != null && l.end != null, modifier = Modifier.fillMaxWidth(), onClick = {
                    val intent = Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
                        .putExtra(CalendarContract.Events.TITLE, l.subject)
                        .putExtra(CalendarContract.Events.EVENT_LOCATION, l.roomNames)
                        .putExtra(
                            CalendarContract.Events.DESCRIPTION,
                            "${l.teacherNames}\n${l.classNames} ${l.group}\n$calendarEventDescription"
                        )
                        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, Exports.instant(dated.date,l.start!!,ZoneId.of(s.zone)).toEpochMilli())
                        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, Exports.instant(dated.date,l.end!!,ZoneId.of(s.zone)).toEpochMilli())
                    try {
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        vm.message(installCalendarOrExportMessage)
                    }
                }) { Text(stringResource(R.string.add_to_calendar)) }
            }
        }
    }
}

@Composable fun SetupScreen(vm: ScheduleViewModel, defaultZone: String, language: AppLanguage) {
    val context = LocalContext.current
    val checkAddressTimezoneMessage = stringResource(R.string.check_address_timezone)
    var host by rememberSaveable { mutableStateOf("") }
    var zone by rememberSaveable { mutableStateOf(defaultZone) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 48.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(R.string.setup_description),
                Modifier.padding(top = 8.dp, bottom = 28.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                host, { host = it; error = null },
                label = { Text(stringResource(R.string.edupage_address)) },
                placeholder = { Text(stringResource(R.string.edupage_placeholder)) },
                supportingText = { Text(stringResource(R.string.edupage_address_help)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                zone, { zone = it; error = null },
                label = { Text(stringResource(R.string.school_time_zone)) },
                supportingText = { Text(stringResource(R.string.timezone_example)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (error != null) Text(error!!, Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.error)
            Button(
                onClick = {
                    try {
                        Preferences.normalizeHost(host)
                        ZoneId.of(zone)
                        error = null
                        vm.school(host, zone)
                    } catch (_: Exception) {
                        error = checkAddressTimezoneMessage
                    }
                },
                enabled = host.isNotBlank() && zone.isNotBlank(),
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp)
            ) { Text(stringResource(R.string.continue_action)) }
            Text(
                stringResource(R.string.setup_privacy),
                Modifier.padding(top = 16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                stringResource(R.string.language),
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
                style = MaterialTheme.typography.titleMedium
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppLanguage.entries.forEach { option ->
                    FilterChip(
                        selected = language == option,
                        onClick = {
                            if (language != option) {
                                vm.language(option)
                                context.findActivity()?.recreate()
                            }
                        },
                        label = { Text(stringResource(option.labelResource())) }
                    )
                }
            }
        }
    }
}

@Composable fun EmptyState(title: String, body: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null) Button(onClick = onAction) { Text(action) }
    }
}
@Composable fun BrowseScreen(t: Timetable, selection: Selection?, home: String, choose: (Selection) -> Unit) {
    var kind by rememberSaveable { mutableStateOf(ScheduleKind.CLASS) }
    var query by rememberSaveable { mutableStateOf("") }
    Column(Modifier.padding(horizontal = 20.dp)) {
        if (home.isBlank()) {
            Text(stringResource(R.string.make_it_yours), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(R.string.choose_class_to_start),
                Modifier.padding(top = 4.dp, bottom = 12.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ScheduleKind.entries.forEach { k ->
                FilterChip(kind == k, { kind = k; query = "" }, { Text(stringResource(k.pluralLabelResource())) })
            }
        }
        OutlinedTextField(
            query,
            { query = it },
            label = { Text(stringResource(kind.searchLabelResource())) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            leadingIcon = { Glyph("browse") }
        )
        val entities = t.entities[kind].orEmpty().filter { it.name.contains(query, ignoreCase = true) }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
            if (entities.isEmpty()) item {
                EmptyState(stringResource(R.string.no_matches), stringResource(R.string.try_another_schedule))
            }
            items(entities, key = { it.id }) { entity ->
                ListItem(headlineContent = { Text(entity.name, fontWeight = FontWeight.Medium) },
                    supportingContent = if (kind == ScheduleKind.CLASS && entity.id == home) ({ Text(stringResource(R.string.my_class)) }) else null,
                    trailingContent = {
                        if (selection == Selection(kind,entity.id)) Glyph("check", stringResource(R.string.selected))
                        else Glyph("next")
                    },
                    modifier = Modifier.clickable { choose(Selection(kind,entity.id)) },
                    colors = ListItemDefaults.colors(containerColor = if (selection == Selection(kind,entity.id)) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface))
            }
        }
    }
}
@Composable fun DayScreen(blocks: List<LessonBlock>, date: LocalDate, zone: String, revision: String, onLesson: (Lesson) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    var now by remember { mutableStateOf(ZonedDateTime.now(ZoneId.of(zone))) }
    LaunchedEffect(zone) { while (true) { now = ZonedDateTime.now(ZoneId.of(zone)); delay(30_000) } }
    val today = date == now.toLocalDate()
    val current = if (today) blocks.firstOrNull { it.start != null && it.end != null && now.toLocalTime() >= it.start && now.toLocalTime() < it.end } else null
    val next = if (today) blocks.firstOrNull { it.start != null && it.start > now.toLocalTime() } else null
    val emptyLines = listOf(
        R.string.empty_day_1,
        R.string.empty_day_2,
        R.string.empty_day_3,
        R.string.empty_day_4
    )
    val doneLines = listOf(
        R.string.done_day_1,
        R.string.done_day_2,
        R.string.done_day_3,
        R.string.done_day_4
    )
    val markerIndex = when {
        !today || blocks.isEmpty() -> -1
        current != null -> blocks.indexOf(current)
        next != null -> blocks.indexOf(next)
        else -> blocks.size
    }

    LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            val focus = current ?: next
            Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        when {
                            current != null -> stringResource(R.string.happening_now)
                            next != null -> stringResource(R.string.up_next)
                            blocks.isEmpty() -> stringResource(R.string.clear_schedule)
                            today -> stringResource(R.string.all_done)
                            else -> date.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, locale).uppercase(locale)
                        },
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        focus?.let {
                            if (it.isSplit && it.subjects.size > 1) {
                                pluralStringResource(R.plurals.group_lessons, it.lessons.size, it.lessons.size)
                            } else {
                                it.title
                            }
                        } ?: if (blocks.isEmpty()) {
                            stringResource(emptyLines[date.dayOfYear % emptyLines.size])
                        } else if (today) {
                            stringResource(doneLines[date.dayOfYear % doneLines.size])
                        } else {
                            pluralStringResource(R.plurals.lesson_count, blocks.size, blocks.size)
                        },
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        focus?.let { block ->
                            val rooms = block.lessons.map { it.roomNames }.filter(String::isNotBlank).distinct()
                            "${block.start ?: "?"}–${block.end ?: "?"}${if (rooms.isNotEmpty()) " · ${rooms.joinToString(" / ")}" else ""}"
                        } ?: if (blocks.isEmpty()) stringResource(R.string.no_published_lessons_selection)
                        else "${blocks.first().start ?: "?"}–${blocks.last().end ?: "?"}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        blocks.forEachIndexed { index, block ->
            if (markerIndex == index) item(key = "now-marker-$index") { CurrentTimeMarker(now.toLocalTime()) }
            item(key = block.id) { LessonBlockCard(block, block == current, onLesson) }
        }
        if (markerIndex == blocks.size) item(key = "now-marker-end") { CurrentTimeMarker(now.toLocalTime()) }

        item {
            Text(
                stringResource(R.string.published_timetable_footer, revision),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable fun CurrentTimeMarker(time: LocalTime) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TimeWave(Modifier.weight(1f))
        Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Text(
                stringResource(R.string.time_now, time.format(DateTimeFormatter.ofPattern("HH:mm"))),
                Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium
            )
        }
        TimeWave(Modifier.weight(1f))
    }
}

@Composable private fun TimeWave(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier.height(8.dp)) {
        val wavelength = 18.dp.toPx()
        val amplitude = 2.dp.toPx()
        val path = Path().apply {
            moveTo(0f, size.height / 2)
            var x = 0f
            while (x <= size.width) {
                lineTo(x, size.height / 2 + kotlin.math.sin(x / wavelength * 2 * Math.PI).toFloat() * amplitude)
                x += 2.dp.toPx()
            }
        }
        drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable fun LessonBlockCard(block: LessonBlock, active: Boolean = false, onLesson: (Lesson) -> Unit) {
    if (block.lessons.size == 1) {
        LessonCard(block.lessons.first(), active) { onLesson(block.lessons.first()) }
        return
    }
    Card(
        shape = if (active) MaterialTheme.shapes.extraLarge else MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer),
        border = if (active) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.width(48.dp)) {
                Text(block.start?.toString() ?: "—", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(block.end?.toString() ?: "—", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    pluralStringResource(R.plurals.group_count, block.lessons.size, block.lessons.size),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                block.lessons.forEachIndexed { index, lesson ->
                    if (index > 0) HorizontalDivider()
                    Column(
                        Modifier.fillMaxWidth().clickable { onLesson(lesson) }.padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(lesson.subject, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text(
                            listOf(lesson.roomNames, lesson.teacherNames).filter(String::isNotBlank).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (lesson.group.isNotBlank()) Text(lesson.group, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable fun LessonCard(lesson: Lesson, active: Boolean = false, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = if (active) MaterialTheme.shapes.extraLarge else MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer),
        border = if (active) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.width(48.dp)) {
                Text(lesson.start?.toString() ?: "—", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(lesson.end?.toString() ?: "—", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(lesson.subject, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    listOf(lesson.roomNames, lesson.teacherNames).filter(String::isNotBlank).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (lesson.group.isNotBlank()) Text(lesson.group, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
@Composable fun WeekScreen(
    s: ScheduleState,
    startAtEnd: Boolean = false,
    onLesson: (DatedLesson) -> Unit
) {
    val locale = LocalConfiguration.current.locales[0]
    val monday = s.date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val scroll = remember(monday) { ScrollState(if (startAtEnd) Int.MAX_VALUE else 0) }
    LaunchedEffect(monday, startAtEnd) {
        if (startAtEnd) scroll.scrollTo(Int.MAX_VALUE)
    }
    // The child consumes scrolling through columns. At an edge, the pager receives the rest.
    Row(Modifier.fillMaxSize().horizontalScroll(scroll, overscrollEffect = null).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        (0L..6L).forEach { offset ->
            val date = monday.plusDays(offset); val snapshot = s.week[date]
            val blocks = snapshot?.timetable?.lessonBlocksOn(date,s.selection!!,s.hidden,s.cycleWeek).orEmpty()
            if (offset < 5 || blocks.isNotEmpty()) Column(Modifier.width(272.dp)) {
                Row(
                    Modifier.padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        date.format(DateTimeFormatter.ofPattern("EEEE · d", locale)),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (snapshot?.offline == true) {
                        Glyph("offline", stringResource(R.string.using_saved_offline_timetable))
                    }
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                    if (snapshot == null) item {
                        Text(if (s.loading) stringResource(R.string.loading) else stringResource(R.string.not_available_offline))
                    } else if (blocks.isEmpty()) item {
                        Text(stringResource(R.string.no_published_lessons), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    items(blocks, key = { it.id }) { block -> LessonBlockCard(block) { onLesson(DatedLesson(date,it)) } }
                }
            }
        }
    }
}
@Composable fun SettingsScreen(
    s: ScheduleState,
    vm: ScheduleViewModel,
    enableNotifications: () -> Unit,
    installUpdate: () -> Unit,
    open: (String) -> Unit
) {
    val context = LocalContext.current
    var host by remember(s.host) { mutableStateOf(s.host) }
    var zone by remember(s.zone) { mutableStateOf(s.zone) }
    var editColors by remember { mutableStateOf(false) }

    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text(stringResource(R.string.appearance), style = MaterialTheme.typography.titleLarge) }
        item {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "System" to R.string.theme_system,
                    "Light" to R.string.theme_light,
                    "Dark" to R.string.theme_dark
                ).forEach { (value, label) ->
                    FilterChip(
                        selected = s.theme == value,
                        onClick = { vm.theme(value) },
                        label = { Text(stringResource(label)) }
                    )
                }
            }
        }
        item {
            Text(stringResource(R.string.color_palette), style = MaterialTheme.typography.titleMedium)
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "Default" to R.string.palette_default,
                    "Catppuccin" to R.string.palette_catppuccin,
                    "Ocean" to R.string.palette_ocean,
                    "Custom" to R.string.palette_custom
                ).forEach { (value, label) ->
                    FilterChip(
                        selected = s.palette == value,
                        onClick = { if (value == "Custom") editColors = true else vm.palette(value) },
                        label = { Text(stringResource(label)) }
                    )
                }
            }
        }
        if (s.palette == "Default") item {
            SettingSwitch(
                stringResource(R.string.wallpaper_colors),
                stringResource(R.string.wallpaper_colors_description),
                s.dynamic,
                vm::dynamic
            )
        }
        if (s.palette == "Custom") item {
            TextButton(onClick = { editColors = true }) { Text(stringResource(R.string.edit_custom_colors)) }
        }
        item {
            Text(stringResource(R.string.language), style = MaterialTheme.typography.titleMedium)
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppLanguage.entries.forEach { option ->
                    FilterChip(
                        selected = s.language == option,
                        onClick = {
                            if (s.language != option) {
                                vm.language(option)
                                context.findActivity()?.recreate()
                            }
                        },
                        label = { Text(stringResource(option.labelResource())) }
                    )
                }
            }
        }

        item {
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.your_school), style = MaterialTheme.typography.titleLarge)
        }
        item {
            OutlinedTextField(
                host,
                { host = it },
                label = { Text(stringResource(R.string.edupage_address)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            OutlinedTextField(
                zone,
                { zone = it },
                label = { Text(stringResource(R.string.school_time_zone)) },
                supportingText = { Text(stringResource(R.string.timezone_example)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            FilledTonalButton(
                onClick = { vm.school(host, zone) },
                enabled = host != s.host || zone != s.zone
            ) { Text(stringResource(R.string.save_school)) }
        }

        item {
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.notifications), style = MaterialTheme.typography.titleLarge)
        }
        item {
            SettingSwitch(
                stringResource(R.string.class_reminders),
                stringResource(R.string.class_reminders_description),
                s.notifications
            ) {
                if (it) enableNotifications() else vm.notifications(false)
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.early_reminder), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.early_reminder_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(0, 5, 10, 15, 30).forEach { minutes ->
                        FilterChip(
                            selected = s.classReminderLeadMinutes == minutes,
                            onClick = { vm.classReminderLeadMinutes(minutes) },
                            label = {
                                Text(
                                    if (minutes == 0) stringResource(R.string.off)
                                    else stringResource(R.string.minutes_short, minutes)
                                )
                            },
                            enabled = s.notifications
                        )
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.updates), style = MaterialTheme.typography.titleLarge)
        }
        item {
            OutlinedButton(
                onClick = { vm.updates() },
                enabled = !s.updateChecking && !s.updateDownloading
            ) {
                Text(
                    if (s.updateChecking) stringResource(R.string.checking)
                    else stringResource(R.string.check_for_updates)
                )
            }
        }
        s.release?.let { release ->
            item {
                Card {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "${stringResource(R.string.app_name)} ${release.version}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            if (s.updateDownloading) stringResource(R.string.downloading_verifying)
                            else stringResource(R.string.download_update_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (s.updateDownloading) {
                            val progress = s.updateProgress
                            if (progress == null) {
                                LinearProgressIndicator(Modifier.fillMaxWidth())
                            } else {
                                LinearProgressIndicator(
                                    progress = { progress / 100f },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    if (progress < 100) "$progress%" else stringResource(R.string.verifying),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Button(
                            onClick = installUpdate,
                            enabled = !s.updateDownloading,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                if (s.updateDownloading) stringResource(R.string.preparing_update)
                                else stringResource(R.string.download_install)
                            )
                        }
                    }
                }
            }
        }

        val groups = s.snapshot?.timetable?.groups?.get(s.selection?.id).orEmpty().groupBy { it.name }
        if (s.selection?.kind == ScheduleKind.CLASS && groups.isNotEmpty()) {
            item {
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.visible_groups), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.visible_groups_description),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            items(groups.entries.toList(), key = { it.key }) { (name, entries) ->
                val ids = entries.map { it.id }.toSet()
                SettingSwitch(name, "", ids.any { it !in s.hidden }) { enabled ->
                    vm.groups(if (enabled) s.hidden - ids else s.hidden + ids)
                }
            }
        }

        item {
            HorizontalDivider()
            Text(
                "${stringResource(R.string.app_name)} ${BuildConfig.VERSION_NAME}",
                Modifier.padding(top = 16.dp),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                stringResource(R.string.about_description),
                Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            TextButton(onClick = { open("https://github.com/${Updates.REPOSITORY}") }) {
                Text(stringResource(R.string.source_help_privacy))
            }
        }
    }
    if (editColors) CustomColorsDialog(s.customColors, onDismiss = { editColors = false }) {
        vm.customColors(it)
        editColors = false
    }
}

@Composable private fun CustomColorsDialog(initial: ThemeColors, onDismiss: () -> Unit, onSave: (ThemeColors) -> Unit) {
    var primary by remember(initial) { mutableStateOf(initial.primary) }
    var secondary by remember(initial) { mutableStateOf(initial.secondary) }
    var surface by remember(initial) { mutableStateOf(initial.surface) }
    val colors = ThemeColors(primary.trim(), secondary.trim(), surface.trim())
    val valid = listOf(colors.primary, colors.secondary, colors.surface).all(::validHexColor)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_custom_colors)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.custom_colors_hint), style = MaterialTheme.typography.bodySmall)
                listOf(
                    Triple(R.string.color_primary, primary, { text: String -> primary = text }),
                    Triple(R.string.color_secondary, secondary, { text: String -> secondary = text }),
                    Triple(R.string.color_surface, surface, { text: String -> surface = text })
                ).forEach { (label, value, update) ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            Modifier.size(32.dp), shape = RoundedCornerShape(12.dp),
                            color = parseHexColor(value.trim()) ?: MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {}
                        OutlinedTextField(
                            value = value, onValueChange = { if (it.length <= 7) update(it) },
                            modifier = Modifier.weight(1f), label = { Text(stringResource(label)) },
                            singleLine = true, isError = !validHexColor(value.trim())
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onSave(colors) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
@Composable fun SettingSwitch(title: String, subtitle: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium); if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Switch(checked, change)
    }
}

