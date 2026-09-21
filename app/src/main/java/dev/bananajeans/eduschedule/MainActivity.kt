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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    override fun attachBaseContext(newBase: Context) {
        val language = Preferences(newBase).language
        super.attachBaseContext(AppLocale.wrap(newBase, language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        val openSettings = intent.getBooleanExtra(EXTRA_OPEN_SETTINGS, false)
        setContent {
            val vm: ScheduleViewModel = viewModel()
            val state by vm.state.collectAsStateWithLifecycle()
            EduTheme(state.theme, state.dynamic) { ScheduleApp(vm, state, openSettings) }
        }
    }

    companion object {
        const val EXTRA_OPEN_SETTINGS = "open_settings"
    }
}
@Composable fun EduTheme(theme: String = "System", dynamic: Boolean = true, content: @Composable () -> Unit) {
    val dark = theme == "Dark" || (theme == "System" && isSystemInDarkTheme())
    val context = LocalContext.current
    val scheme = if (dynamic && Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) darkColorScheme(primary = Color(0xFFB5CEA8), secondaryContainer = Color(0xFF35452F))
    else lightColorScheme(primary = Color(0xFF426437), primaryContainer = Color(0xFFC3EBAF), surface = Color(0xFFF9FAF4), secondaryContainer = Color(0xFFE0E9D7))
    MaterialTheme(colorScheme = scheme, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ScheduleApp(vm: ScheduleViewModel, s: ScheduleState, openSettingsInitially: Boolean = false) {
    if (s.host.isBlank()) { SetupScreen(vm, s.zone, s.language); return }
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
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
                vm.message(context.getString(R.string.export_saved))
            } catch (_: Exception) {
                vm.message(context.getString(R.string.export_save_failed))
            }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.notifications(granted)
        if (!granted) vm.message(context.getString(R.string.notifications_disabled))
    }
    val installPermission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (context.packageManager.canRequestPackageInstalls()) vm.installUpdate()
        else vm.message(context.getString(R.string.allow_installs))
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
            vm.message(context.getString(R.string.allow_installs_settings))
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
            vm.message(context.getString(R.string.no_app_for_link))
        }
    }
    LaunchedEffect(s.message) { s.message?.let { snack.showSnackbar(it); vm.message(null) } }
    BackHandler(settings || tab != "Day") { if (settings) settings = false else tab = "Day" }
    val timetable = s.snapshot?.timetable
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
                                    vm.message(context.getString(R.string.default_class_saved))
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
                if (s.error != null) EmptyState(
                    stringResource(R.string.couldnt_load_date),
                    s.error,
                    stringResource(R.string.retry)
                ) { vm.refresh(true) }
                else if (timetable == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else {
                    if (s.snapshot.offline) Surface(color = MaterialTheme.colorScheme.tertiaryContainer) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Glyph("offline", stringResource(R.string.using_saved_offline_timetable))
                            Text(
                                stringResource(
                                    R.string.offline_saved_at,
                                    s.snapshot.fetched.atZone(ZoneId.of(s.zone))
                                        .format(DateTimeFormatter.ofPattern("d MMM, HH:mm", locale))
                                ),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                    when {
                        tab == "Browse" || s.selection == null -> BrowseScreen(timetable, s.selection, s.home, { vm.select(it); if (s.home.isBlank() && it.kind == ScheduleKind.CLASS) vm.home(it.id); tab = "Day" })
                        else -> {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { vm.date(s.date.minusDays(if (tab == "Week") 7 else 1)) }) {
                                    Glyph("back", stringResource(if (tab == "Week") R.string.previous_week else R.string.previous_day))
                                }
                                TextButton(onClick = { showDate = true }, modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(s.date.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy", locale)))
                                        if (s.loading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                    }
                                }
                                IconButton(onClick = { vm.date(s.date.plusDays(if (tab == "Week") 7 else 1)) }) {
                                    Glyph("next", stringResource(if (tab == "Week") R.string.next_week else R.string.next_day))
                                }
                                TextButton(onClick = { vm.date(LocalDate.now(ZoneId.of(s.zone))) }) {
                                    Text(stringResource(R.string.today))
                                }
                            }
                            if (timetable.weekNames.size > 1) Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                timetable.weekNames.forEachIndexed { i, name -> FilterChip(s.cycleWeek == i, { vm.cycle(i) }, { Text(name) }) }
                            }
                            if (tab == "Day") DayScreen(timetable.lessonBlocksOn(s.date, s.selection, s.hidden, s.cycleWeek), s.date, s.zone,
                                timetable.revision.name, { detail = DatedLesson(s.date, it) })
                            else WeekScreen(s) { detail = it }
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
                        selectedName ?: context.getString(R.string.timetable),
                        vm.exportLessons(),
                        ZoneId.of(s.zone),
                        description = calendarDescription
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
        ModalBottomSheet(onDismissRequest = { detail = null }) {
            Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                    val entityLinks = links.filter { it.kind == kind }
                    if (entityLinks.isNotEmpty()) {
                        entityLinks.forEach { link ->
                            ListItem(
                                overlineContent = { Text(label) },
                                headlineContent = { Text(link.entity.name, fontWeight = FontWeight.Medium) },
                                trailingContent = { Glyph("next", stringResource(R.string.open_schedule, label)) },
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
                            "${l.teacherNames}\n${l.classNames} ${l.group}\n${context.getString(R.string.calendar_event_description)}"
                        )
                        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, Exports.instant(dated.date,l.start!!,ZoneId.of(s.zone)).toEpochMilli())
                        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, Exports.instant(dated.date,l.end!!,ZoneId.of(s.zone)).toEpochMilli())
                    try {
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        vm.message(context.getString(R.string.install_calendar_or_export))
                    }
                }) { Text(stringResource(R.string.add_to_calendar)) }
            }
        }
    }
}

@Composable fun SetupScreen(vm: ScheduleViewModel, defaultZone: String, language: AppLanguage) {
    val context = LocalContext.current
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
                        error = context.getString(R.string.check_address_timezone)
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
    val emptyLines = listOf("Nothing on the board.", "No lessons today. Enjoy the gap.", "Clear schedule. Nice.", "No published lessons today.")
    val doneLines = listOf("That’s your day.", "Done for today.", "You’re finished.", "Schedule cleared.")
    val markerIndex = when {
        !today || blocks.isEmpty() -> -1
        current != null -> blocks.indexOf(current)
        next != null -> blocks.indexOf(next)
        else -> blocks.size
    }

    LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            val focus = current ?: next
            Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        when {
                            current != null -> "HAPPENING NOW"
                            next != null -> "UP NEXT"
                            blocks.isEmpty() -> "CLEAR SCHEDULE"
                            today -> "ALL DONE"
                            else -> date.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, locale).uppercase(locale)
                        },
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        focus?.let { if (it.isSplit && it.subjects.size > 1) "${it.lessons.size} group lessons" else it.title }
                            ?: if (blocks.isEmpty()) emptyLines[date.dayOfYear % emptyLines.size]
                            else if (today) doneLines[date.dayOfYear % doneLines.size]
                            else "${blocks.size} ${if (blocks.size == 1) "lesson" else "lessons"}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        focus?.let { block ->
                            val rooms = block.lessons.map { it.roomNames }.filter(String::isNotBlank).distinct()
                            "${block.start ?: "?"}–${block.end ?: "?"}${if (rooms.isNotEmpty()) " · ${rooms.joinToString(" / ")}" else ""}"
                        } ?: if (blocks.isEmpty()) "No published lessons for this selection."
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
                "Published timetable · $revision\nDaily substitutions and holidays may differ.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable fun CurrentTimeMarker(time: LocalTime) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.primary)
        Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Text("${time.format(DateTimeFormatter.ofPattern("HH:mm"))} now", Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium)
        }
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.primary)
    }
}

@Composable fun LessonBlockCard(block: LessonBlock, active: Boolean = false, onLesson: (Lesson) -> Unit) {
    if (block.lessons.size == 1) {
        LessonCard(block.lessons.first(), active) { onLesson(block.lessons.first()) }
        return
    }
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer),
        border = if (active) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.width(48.dp)) {
                Text(block.start?.toString() ?: "—", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(block.end?.toString() ?: "—", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${block.lessons.size} groups", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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
        shape = RoundedCornerShape(20.dp),
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
@Composable fun WeekScreen(s: ScheduleState, onLesson: (DatedLesson) -> Unit) {
    val monday = s.date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    // Wide, independently scrollable day columns keep real lesson names readable on phones.
    Row(Modifier.fillMaxSize().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        (0L..6L).forEach { offset ->
            val date = monday.plusDays(offset); val snapshot = s.week[date]
            val blocks = snapshot?.timetable?.lessonBlocksOn(date,s.selection!!,s.hidden,s.cycleWeek).orEmpty()
            if (offset < 5 || blocks.isNotEmpty()) Column(Modifier.width(272.dp)) {
                Row(
                    Modifier.padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(date.format(DateTimeFormatter.ofPattern("EEEE · d")), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (snapshot?.offline == true) Glyph("offline", "Using saved offline timetable")
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                    if (snapshot == null) item { Text(if (s.loading) "Loading…" else "Not available offline. Refresh to retry.") }
                    else if (blocks.isEmpty()) item { Text("No published lessons", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(blocks, key = { it.id }) { block -> LessonBlockCard(block) { onLesson(DatedLesson(date,it)) } }
                }
            }
        }
    }
}
@Composable fun SettingsScreen(s: ScheduleState, vm: ScheduleViewModel, enableNotifications: () -> Unit, installUpdate: () -> Unit, open: (String) -> Unit) {
    var host by remember(s.host) { mutableStateOf(s.host) }
    var zone by remember(s.zone) { mutableStateOf(s.zone) }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Appearance", style = MaterialTheme.typography.titleLarge) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("System", "Light", "Dark").forEach { FilterChip(s.theme == it, { vm.theme(it) }, { Text(it) }) } } }
        item { SettingSwitch("Wallpaper colors", "Use your Android accent colors", s.dynamic, vm::dynamic) }
        item { HorizontalDivider(); Spacer(Modifier.height(16.dp)); Text("Your school", style = MaterialTheme.typography.titleLarge) }
        item { OutlinedTextField(host, { host = it }, label = { Text("EduPage address") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(zone, { zone = it }, label = { Text("School time zone") }, supportingText = { Text("For example, Europe/Tallinn") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
        item { FilledTonalButton(onClick = { vm.school(host,zone) }, enabled = host != s.host || zone != s.zone) { Text("Save school") } }
        item { HorizontalDivider(); Spacer(Modifier.height(16.dp)); Text("Notifications", style = MaterialTheme.typography.titleLarge) }
        item { SettingSwitch("Class reminders", "Notify for your saved class. Alerts include Mute 1h and Mute today; timetable-change and app-update alerts are included too. Android may delay background work.", s.notifications) { if (it) enableNotifications() else vm.notifications(false) } }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Early reminder", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Choose how long before class to get a heads-up. The start-time alert still fires separately.",
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
                            label = { Text(if (minutes == 0) "Off" else "$minutes min") },
                            enabled = s.notifications
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)); Text("Updates", style = MaterialTheme.typography.titleLarge) }
        item {
            OutlinedButton(
                onClick = { vm.updates() },
                enabled = !s.updateChecking && !s.updateDownloading
            ) { Text(if (s.updateChecking) "Checking…" else "Check for app updates") }
        }
        s.release?.let { release ->
            item {
                Card {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("EduSchedule ${release.version}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (s.updateDownloading) "Downloading and verifying the signed APK…"
                            else "Download the verified APK here. Android will ask you to confirm the update.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (s.updateDownloading) {
                            val progress = s.updateProgress
                            if (progress == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                            else {
                                LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                                Text(
                                    if (progress < 100) "$progress%" else "Verifying…",
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
                            Text(if (s.updateDownloading) "Preparing update…" else "Download & install")
                        }
                    }
                }
            }
        }
        val groups = s.snapshot?.timetable?.groups?.get(s.selection?.id).orEmpty()
        if (s.selection?.kind == ScheduleKind.CLASS && groups.isNotEmpty()) {
            item { HorizontalDivider(); Spacer(Modifier.height(16.dp)); Text("Visible groups", style = MaterialTheme.typography.titleLarge); Text("Hide groups you don't attend. Whole-class lessons stay visible.", style = MaterialTheme.typography.bodyMedium) }
            items(groups, key = { it.id }) { group -> SettingSwitch(group.name, "", group.id !in s.hidden) { enabled -> vm.groups(if (enabled) s.hidden - group.id else s.hidden + group.id) } }
        }
        item { HorizontalDivider(); Text("EduSchedule ${BuildConfig.VERSION_NAME}", Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleMedium)
            Text("Independent reader for public EduPage timetables. No account, ads, or analytics. Timetables are stored on this device. Regular schedules may not include substitutions or holidays.", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { TextButton(onClick = { open("https://github.com/${Updates.REPOSITORY}") }) { Text("Source, help & privacy") } }
    }
}
@Composable fun SettingSwitch(title: String, subtitle: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium); if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Switch(checked, change)
    }
}
