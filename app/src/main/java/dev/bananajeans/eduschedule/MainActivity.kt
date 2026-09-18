package dev.bananajeans.eduschedule

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.CalendarContract
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
import androidx.compose.ui.platform.LocalContext
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
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        setContent {
            val vm: ScheduleViewModel = viewModel()
            val state by vm.state.collectAsStateWithLifecycle()
            EduTheme(state.theme, state.dynamic) { ScheduleApp(vm, state) }
        }
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
@Composable fun ScheduleApp(vm: ScheduleViewModel, s: ScheduleState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableStateOf("Day") }
    var menu by remember { mutableStateOf(false) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<DatedLesson?>(null) }
    var pendingExport by remember { mutableStateOf("") }
    val snack = remember { SnackbarHostState() }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        if (uri != null) scope.launch {
            try {
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(pendingExport.toByteArray()) } ?: error("Could not open the destination.") }
                vm.message("Export saved.")
            } catch (_: Exception) { vm.message("Couldn't save the export. Try another location.") }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.notifications(granted); if (!granted) vm.message("Notifications are off. You can enable them in Android settings.")
    }
    fun open(url: String) { try { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) } catch (_: Exception) { vm.message("No app can open this link.") } }
    LaunchedEffect(s.message) { s.message?.let { snack.showSnackbar(it); vm.message(null) } }
    BackHandler(settings || tab != "Day") { if (settings) settings = false else tab = "Day" }
    val timetable = s.snapshot?.timetable
    val selectedName = timetable?.entities?.get(s.selection?.kind)?.find { it.id == s.selection?.id }?.name
    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(title = { Column {
                Text(if (settings) "Settings" else selectedName ?: "Your timetable", fontWeight = FontWeight.SemiBold)
                if (!settings) Text(s.host.substringBefore('.'), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } }, navigationIcon = { if (settings) IconButton(onClick = { settings = false }) { Glyph("back", "Back") } },
                actions = {
                    if (!settings) {
                        IconButton(onClick = { vm.refresh(true) }, enabled = !s.loading) { Glyph("refresh", "Refresh timetable") }
                        Box {
                            IconButton(onClick = { menu = true }) { Glyph("more", "More options") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("Choose schedule") }, onClick = { menu = false; tab = "Browse" })
                                if (s.home.isNotBlank()) DropdownMenuItem(text = { Text("My class") }, onClick = { vm.select(Selection(ScheduleKind.CLASS,s.home)); menu = false; tab = "Day" })
                                if (s.selection?.kind == ScheduleKind.CLASS) DropdownMenuItem(text = { Text("Make this my class") }, onClick = { vm.home(s.selection.id); menu = false; vm.message("Default class saved.") })
                                DropdownMenuItem(text = { Text("Export week") }, enabled = s.selection != null && s.week.isNotEmpty() && !s.loading, onClick = { menu = false; showExport = true })
                                DropdownMenuItem(text = { Text("Open school timetable") }, onClick = { menu = false; open("https://${s.host}/timetable/") })
                                DropdownMenuItem(text = { Text("Settings") }, onClick = { menu = false; settings = true })
                            }
                        }
                    }
                })
        },
        bottomBar = {
            if (!settings) NavigationBar {
                listOf("Day" to "day", "Week" to "week", "Browse" to "browse").forEach { (label, icon) ->
                    NavigationBarItem(selected = tab == label, onClick = { tab = label }, icon = { Glyph(icon) }, label = { Text(label) })
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (settings) SettingsScreen(s, vm, {
                if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS) else vm.notifications(true)
            }, ::open)
            else Column {
                if (s.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (s.error != null) EmptyState("Couldn't load this date", s.error, "Retry") { vm.refresh(true) }
                else if (timetable == null) EmptyState("Loading timetable", "Getting the school's published schedule.")
                else {
                    if (s.snapshot.offline) Surface(color = MaterialTheme.colorScheme.tertiaryContainer) {
                        Text("Offline · saved ${s.snapshot.fetched.atZone(ZoneId.of(s.zone)).format(DateTimeFormatter.ofPattern("d MMM, HH:mm"))}", Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium)
                    }
                    when {
                        tab == "Browse" || s.selection == null -> BrowseScreen(timetable, s.selection, s.home, { vm.select(it); if (s.home.isBlank() && it.kind == ScheduleKind.CLASS) vm.home(it.id); tab = "Day" })
                        else -> {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { vm.date(s.date.minusDays(if (tab == "Week") 7 else 1)) }) { Glyph("back", "Previous ${tab.lowercase()}") }
                                TextButton(onClick = { showDate = true }, modifier = Modifier.weight(1f)) { Text(s.date.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy"))) }
                                IconButton(onClick = { vm.date(s.date.plusDays(if (tab == "Week") 7 else 1)) }) { Glyph("next", "Next ${tab.lowercase()}") }
                                TextButton(onClick = { vm.date(LocalDate.now(ZoneId.of(s.zone))) }) { Text("Today") }
                            }
                            if (timetable.weekNames.size > 1) Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                timetable.weekNames.forEachIndexed { i, name -> FilterChip(s.cycleWeek == i, { vm.cycle(i) }, { Text(name) }) }
                            }
                            if (tab == "Day") DayScreen(timetable.lessonsOn(s.date, s.selection, s.hidden, s.cycleWeek), s.date, s.zone,
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
        }) { Text("Go") } }, dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } }) { DatePicker(picker) }
    }
    if (showExport) AlertDialog(onDismissRequest = { showExport = false }, title = { Text("Export this week") },
        text = { Text("A dated snapshot of the visible lessons. Google Calendar can import the .ics file on the web. Exports do not update automatically. ${if (s.week.size < 7) "Some dates couldn't be loaded; refresh before exporting." else ""}") },
        confirmButton = { TextButton(enabled = s.week.size == 7, onClick = {
            pendingExport = Exports.ics(s.host, selectedName ?: "Timetable", vm.exportLessons(), ZoneId.of(s.zone)); export.launch("EduSchedule-${s.date}.ics"); showExport = false
        }) { Text("Calendar (.ics)") } },
        dismissButton = { TextButton(enabled = s.week.size == 7, onClick = { pendingExport = Exports.csv(vm.exportLessons()); export.launch("EduSchedule-${s.date}.csv"); showExport = false }) { Text("Spreadsheet (.csv)") } })
    detail?.let { dated ->
        val l = dated.lesson
        ModalBottomSheet(onDismissRequest = { detail = null }) {
            Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(l.subject, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Text("${dated.date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM"))} · ${l.start ?: "?"}–${l.end ?: "?"}", style = MaterialTheme.typography.titleMedium)
                listOf("Room" to l.roomNames, "Teacher" to l.teacherNames, "Class" to l.classNames, "Group" to l.group).filter { it.second.isNotBlank() }.forEach { (label, value) ->
                    Text("$label · $value", style = MaterialTheme.typography.bodyLarge)
                }
                Button(enabled = l.start != null && l.end != null, modifier = Modifier.fillMaxWidth(), onClick = {
                    val intent = Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
                        .putExtra(CalendarContract.Events.TITLE, l.subject)
                        .putExtra(CalendarContract.Events.EVENT_LOCATION, l.roomNames)
                        .putExtra(CalendarContract.Events.DESCRIPTION, "${l.teacherNames}\n${l.classNames} ${l.group}\nPublished timetable; check EduPage for substitutions.")
                        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, Exports.instant(dated.date,l.start!!,ZoneId.of(s.zone)).toEpochMilli())
                        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, Exports.instant(dated.date,l.end!!,ZoneId.of(s.zone)).toEpochMilli())
                    try { context.startActivity(intent) } catch (_: Exception) { vm.message("Install a calendar app, or export the week as .ics.") }
                }) { Text("Add to calendar") }
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
        if (home.isBlank()) { Text("Make it yours", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold); Text("Choose your class to get started.", Modifier.padding(top = 4.dp, bottom = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ScheduleKind.entries.forEach { k -> FilterChip(kind == k, { kind = k; query = "" }, { Text(k.label) }) }
        }
        OutlinedTextField(query, { query = it }, label = { Text("Search ${kind.label.lowercase()}") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), leadingIcon = { Glyph("browse") })
        val entities = t.entities[kind].orEmpty().filter { it.name.contains(query, ignoreCase = true) }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
            if (entities.isEmpty()) item { EmptyState("No matches", "Try another name or schedule type.") }
            items(entities, key = { it.id }) { entity ->
                ListItem(headlineContent = { Text(entity.name, fontWeight = FontWeight.Medium) },
                    supportingContent = if (kind == ScheduleKind.CLASS && entity.id == home) ({ Text("My class") }) else null,
                    trailingContent = { if (selection == Selection(kind,entity.id)) Glyph("check", "Selected") else Glyph("next") },
                    modifier = Modifier.clickable { choose(Selection(kind,entity.id)) },
                    colors = ListItemDefaults.colors(containerColor = if (selection == Selection(kind,entity.id)) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface))
            }
        }
    }
}
@Composable fun DayScreen(lessons: List<Lesson>, date: LocalDate, zone: String, revision: String, onLesson: (Lesson) -> Unit) {
    var now by remember { mutableStateOf(ZonedDateTime.now(ZoneId.of(zone))) }
    LaunchedEffect(zone) { while (true) { now = ZonedDateTime.now(ZoneId.of(zone)); delay(30_000) } }
    val current = if (date == now.toLocalDate()) lessons.firstOrNull { it.start != null && it.end != null && now.toLocalTime() >= it.start && now.toLocalTime() < it.end } else null
    val next = if (date == now.toLocalDate()) lessons.firstOrNull { it.start != null && it.start > now.toLocalTime() } else null
    LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            val focus = current ?: next
            Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(when { current != null -> "HAPPENING NOW"; next != null -> "UP NEXT"; lessons.isEmpty() -> "CLEAR SCHEDULE"; date == now.toLocalDate() -> "ALL DONE"; else -> date.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault()).uppercase() }, style = MaterialTheme.typography.labelMedium)
                    Text(focus?.subject ?: if (lessons.isEmpty()) "A little breathing room." else if (date == now.toLocalDate()) "That's your day." else "${lessons.size} lessons", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                    Text(focus?.let { "${it.start}–${it.end}${if (it.roomNames.isNotBlank()) " · ${it.roomNames}" else ""}" } ?: if (lessons.isEmpty()) "No published lessons for this selection." else "${lessons.first().start ?: "?"}–${lessons.last().end ?: "?"}", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        items(lessons, key = { it.id }) { lesson -> LessonCard(lesson, lesson == current) { onLesson(lesson) } }
        item { Text("Published timetable · $revision\nDaily substitutions and holidays may differ.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp)) }
    }
}
@Composable fun LessonCard(lesson: Lesson, active: Boolean = false, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.width(48.dp)) { Text(lesson.start?.toString() ?: "—", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold); Text(lesson.end?.toString() ?: "—", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(lesson.subject, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(listOf(lesson.roomNames, lesson.teacherNames).filter(String::isNotBlank).joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            val lessons = snapshot?.timetable?.lessonsOn(date,s.selection!!,s.hidden,s.cycleWeek).orEmpty()
            if (offset < 5 || lessons.isNotEmpty()) Column(Modifier.width(272.dp)) {
                Text(date.format(DateTimeFormatter.ofPattern("EEEE · d")), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 14.dp), fontWeight = FontWeight.SemiBold)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                    if (snapshot == null) item { Text(if (s.loading) "Loading…" else "Not available offline. Refresh to retry.") }
                    else if (lessons.isEmpty()) item { Text("No published lessons", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(lessons, key = { it.id }) { l -> LessonCard(l) { onLesson(DatedLesson(date,l)) } }
                }
            }
        }
    }
}
@Composable fun SettingsScreen(s: ScheduleState, vm: ScheduleViewModel, enableNotifications: () -> Unit, open: (String) -> Unit) {
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
        item { HorizontalDivider(); Spacer(Modifier.height(16.dp)); Text("Updates", style = MaterialTheme.typography.titleLarge) }
        item { SettingSwitch("Notify me", "Timetable changes and new app releases. Android may delay background checks.", s.notifications) { if (it) enableNotifications() else vm.notifications(false) } }
        item { OutlinedButton(onClick = { vm.updates() }, enabled = !s.updateChecking) { Text(if (s.updateChecking) "Checking…" else "Check for app updates") } }
        s.release?.let { release -> item { Button(onClick = { open(release.url) }) { Text("View ${release.version}") } } }
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
