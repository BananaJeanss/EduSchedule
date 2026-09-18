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

UI uses Material 3 surfaces, native bottom navigation, overflow actions, detail sheets, a date picker and 48dp icon buttons. Timetable content scrolls inside Scaffold system insets. Layouts avoid fixed text heights and support large type. Week columns remain readable rather than shrinking five columns into 360dp. Material You is available on Android 12+; a green neutral fallback supports older devices.

## Background and exports

An opt-in hourly WorkManager task refreshes the default class; Android battery restrictions can delay it. First sync establishes a baseline and never emits a false change alert. SHA-256 of default-class lesson content detects changes. The same opt-in system schedules one-time WorkManager jobs for remaining class starts and exposes `Mute 1h` / `Mute today` notification actions. These are reminders, not exact alarms: Android may defer work. A daily GitHub release check shares the opt-in worker.

Calendar insertion uses `ACTION_INSERT`, giving the user control of the destination calendar and event. It needs no calendar read/write permissions. Week exports use Storage Access Framework, no broad storage permission. Events have stable UIDs, UTC timestamps converted from the school zone, escaped text and UTF-8-safe 75-octet folding. CSV quotes fields and neutralizes spreadsheet formula prefixes.

## Known scope boundaries

- Regular published timetable only; no substitution/holiday overlay or login.
- Calendar integration is add/export, not continuous Google synchronization.
- Group settings use source group IDs; school changes reset them. A revision may replace IDs and require reselecting groups/home class.
- Teacher/room bell times use the lesson's class bell, then teacher bell, then base period. Multiple classes with differing bells in one card need further school-specific validation.
- Weekly snapshot export skips lessons whose structured time is invalid; details disable calendar insertion for those lessons.
- English interface; source names and device date formatting retain their language. Estonian localization is a follow-up.
- Device/emulator visual and accessibility review is a release gate; passing compilation alone does not establish production readiness.
