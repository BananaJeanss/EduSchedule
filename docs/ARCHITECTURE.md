# Plan and architecture

## Product sequence

1. Verify public data and calendar semantics before building UI.
2. Implement pure parser and date-aware model; test revisions, bit masks and bell overrides.
3. Build Material 3 day/week/browse experience with home class, state restoration and accessible controls.
4. Add atomic offline snapshots, exports, opt-in notifications and release discovery.
5. Gate builds with tests, lint, emulator checks and CodeQL; sign published releases with a durable owner-managed key.

## Design

One Android module deliberately avoids a backend, DI framework, ORM or OAuth account system. First launch has no built-in school: the user supplies a public EduPage host and time zone before any timetable request is made. Compose observes an immutable `StateFlow` in an Android ViewModel. The repository runs HTTPS and file operations on IO, serializes disk-cache access with a process-wide mutex, validates payloads before atomic replacement, and returns a stale snapshot when refresh fails. The ViewModel also keeps a bounded in-memory day cache so revisiting prefetched dates is immediate without flashing empty state.

Model identifiers are strings. Each positioned card/day remains a stable lesson in the data model. For class schedules only, `LessonBlocks.kt` groups parallel split-group lessons that occupy the same slot into one presentation block while retaining each underlying lesson for details/exports. Teacher and room browsing remain unmerged. Home class is separate from temporary selection. Date-specific week loads use each day's effective revision. The source has no calendar-to-week-cycle mapping, so multiweek schools require manual cycle selection.

UI uses `MaterialExpressiveTheme` with Material 3 surfaces, native bottom navigation, overflow actions, detail sheets, a date picker and full-sized icon controls. The 1.5.0-alpha29 Material 3 dependency is the explicit exception to the stable-dependency policy needed for the Expressive theme; it is pinned and must pass Android CI and device review before release. Other Compose libraries remain BOM-managed. Timetable content scrolls inside Scaffold system insets. Layouts avoid fixed text heights and support large type. Week columns remain readable rather than shrinking five columns into 360dp. Material You is available on Android 12+; a green neutral fallback supports older devices.

`ScheduleWidget.kt` provides individually configured home-screen widgets (today, next, coming days, optional rooms/teachers). Android's RemoteViews renders the widget, while its configuration activity uses Compose. Each widget reads the saved home class and validated disk snapshots through `Repository.loadCached`; launcher updates never contact EduPage. App refresh, saved-class/group/cycle changes and the optional background refresh request a redraw; the launcher also requests periodic updates at most every 30 minutes. Missing cache is marked as unsaved. Widgets show the published regular timetable, not live substitutions.

Localization is resource-driven. English is the default resource set and Estonian lives in `values-et`; `AppLanguage` stores only stable locale identifiers and `AppLocale` applies an explicit language to Activity/background contexts. Domain models do not own translated display labels. User-visible dates use the active configuration locale explicitly, while background notifications and exported labels resolve through the persisted app language. Adding a locale requires a translated string resource set, an entry in `locales_config.xml`, and an `AppLanguage` entry. Unit tests enforce translation-key and format-placeholder parity.

## Background and exports

An opt-in hourly WorkManager task refreshes the default class; Android battery restrictions can delay the refresh itself. First sync establishes a baseline and never emits a false change alert. SHA-256 of default-class lesson content detects changes. Class-time delivery is separate: `ClassReminders.kt` schedules PendingIntent-backed `AlarmManager` alarms so reminders survive process death, uses an idle-capable exact alarm for the actual class start, suppresses deliveries that arrive outside a short validity window, and exposes `Mute 1h` / `Mute today` actions. The app keeps today and tomorrow armed when data is available and requests a reschedule after reboot, clock/time-zone changes, package replacement, or exact-alarm access changes. A daily GitHub release check shares the opt-in refresh worker.

Calendar insertion uses `ACTION_INSERT`, giving the user control of the destination calendar and event. It needs no calendar read/write permissions. Week exports use Storage Access Framework, no broad storage permission. Events have stable UIDs, UTC timestamps converted from the school zone, escaped text and UTF-8-safe 75-octet folding. CSV quotes fields and neutralizes spreadsheet formula prefixes.

## In-app updates

Release discovery still uses GitHub's latest stable release API. For an available version, `Updater.kt` constructs only the expected repository release URLs, follows HTTPS redirects only to an allowlist of GitHub release-asset hosts, downloads `SHA256SUMS` and the matching APK into app-private cache, enforces size limits, verifies the published SHA-256, package name, version name, increasing version code, and signing certificate against the installed app, then streams the APK into a `PackageInstaller` session.

Android 8+ treats EduSchedule as an external install source, so the user must explicitly allow installs from EduSchedule once and Android still owns the final update-confirmation UI. There is no silent installation. The temporary APK is removed after it has been copied into the installer session, including on failure. Debug builds have a different package name/signing key and therefore cannot self-update from production releases.

## Known scope boundaries

- Regular published timetable only; no substitution/holiday overlay or login.
- Calendar integration is add/export, not continuous Google synchronization.
- Group settings use source group IDs; school changes reset them. A revision may replace IDs and require reselecting groups/home class.
- Teacher/room bell times use the lesson's class bell, then teacher bell, then base period. Multiple classes with differing bells in one card need further school-specific validation.
- Weekly snapshot export skips lessons whose structured time is invalid; details disable calendar insertion for those lessons.
- English and Estonian interfaces are supported; source-provided school, subject, teacher, room and revision names remain exactly as published by EduPage.
- Device/emulator visual and accessibility review is a release gate; passing compilation alone does not establish production readiness.
