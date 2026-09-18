# Working on EduSchedule

## Product contract
- Native Android, Kotlin and Jetpack Compose Material 3. Prioritize readable schedules, touch targets, system back, edge-to-edge insets, large fonts and offline behavior.
- No WebView wrapper, fabricated timetable, analytics, passwords or private EduPage scraping.
- The regular timetable is not a substitutions feed. Keep this distinction visible in the UI, calendar descriptions and docs.
- Keep the saved home class independent of temporary class/teacher/room browsing.

## Architecture
- `Timetable.kt`: pure models and explicit JSON parser; no Android dependencies.
- `Repository.kt`: public RPC transport, validated atomic cache, preferences, release discovery.
- `ScheduleViewModel.kt`: UI state and coroutine orchestration. Keep network/disk IO off main.
- `MainActivity.kt`: Compose UI and user-mediated calendar/export actions.
- `Background.kt`: opt-in WorkManager notifications; never claim exact delivery.
- `Exports.kt`: pure RFC 5545/CSV serialization.

## Required checks
Run `./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease` and `./gradlew connectedDebugAndroidTest` when an emulator is available. Fix failures, never disable lint/tests just to turn CI green. Parser changes require sanitized schema fixtures and regression tests. Never check in raw school data with personal names.

## Data invariants
Select the last non-hidden revision effective on the requested date. Never automatically choose a future revision. Use structured period/bell times, not display labels. Days/weeks are positional bit strings, IDs are strings (including `*` and `-`). Keep split lessons distinct. Unknown times cannot generate calendar events. Each export date needs its own effective revision. Multiweek cycles require an explicit user selection until calendar mapping is supported.

## Dependencies and security
Use pinned latest stable versions verified against official registries. Compose versions come from its BOM. Do not introduce alpha/beta packages without a documented need. Keep Gradle's distribution checksum and pin Actions to full commit SHAs. Never commit signing material. Release builds must fail closed when signing secrets are missing; never silently debug-sign releases. Keep permission scope minimal and cancellation propagation intact.

## Documentation
Update README, docs/ARCHITECTURE.md, docs/DATA_SOURCE.md and docs/RELEASING.md when behavior changes. Record actual test results separately from checks that are only configured. This repository is the source of truth; use small reviewable commits and don't overwrite others' work.
