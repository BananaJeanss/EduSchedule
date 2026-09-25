# EduSchedule

A native Android reader for public aSc / EduPage timetables. Built around the next lesson, rather than a desktop timetable squeezed onto a phone.

## Features

- Material 3 Expressive theme, Android wallpaper colors, light/dark/system modes, Default/Catppuccin/Ocean palettes and editable custom colors, edge-to-edge layout, adaptive monochrome launcher icon, system back and proper navigation controls.
- Four home-screen widget picker entries with complete light/dark English/Estonian image previews (including MIUI fallback support): Today, Next lesson, Upcoming lessons, and Coming days. Each widget can display Today, Tomorrow, Next, Upcoming, or Week, with per-widget colors (app, wallpaper, Default, Catppuccin, Ocean), borders (none, solid, dashed), row count, and optional room/teacher details. Uses the saved home class and offline cache.
- English and Estonian interfaces with an in-app language selector, localized dates, notifications, calendar copy and export headers. The localization layer is resource-driven so additional languages can be added without branching UI logic.
- Day agenda with current/next lesson, merged split-group blocks, a current-time marker, and a horizontally scrollable week board. Day and week pages follow your finger, reveal the adjacent schedule, and snap into place or back. Week columns scroll normally before handing an edge swipe to the week pager.
- Search classes, teachers and rooms. Save a default class while browsing any other schedule. Hide unwanted split groups.
- Date-aware timetable revisions, real bell times, multiple-period lessons, and explicit week-cycle selection when a school publishes multiple cycles.
- Validated atomic offline cache plus a bounded in-memory day cache for instant back/forward navigation; current week prefetched; stale/offline state and fetch time shown.
- Add a lesson through Android's calendar editor (including Google Calendar); export the displayed week as RFC 5545 `.ics` or spreadsheet `.csv` through the system file picker.
- Opt-in alarm-backed class-start reminders that continue after the app process closes, suppress stale late deliveries, and include Mute 1h / Mute today actions, plus timetable-change and app-release notifications. In-app updates download the signed GitHub APK, verify its checksum/package/version/signing certificate, then hand it to Android for the required install confirmation.
- Debug APK artifacts, release signing workflow, JVM tests, emulator UI tests, Android lint, CodeQL and Dependabot.
- Wear OS companion for the saved home class: day and week schedules, synced appearance and settings, offline cache, class reminders and a next-class watch-face complication.

First launch asks for a public `*.edupage.org` school address and time zone; no school is built in or contacted before setup. Android 8+ (API 26).

**This is a regular timetable reader, not a replacement for official school announcements.** Public EduPage feeds vary by school and may omit substitutions, cancellations, holidays, or current-day changes. Calendar exports are dated snapshots, not subscriptions or two-way sync.

## Build

Use JDK 17 and the Android SDK. Install platform 37.0 and build tools 36.0.0. Set `ANDROID_HOME`, or add `sdk.dir` to an untracked `local.properties` file.

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
./gradlew connectedDebugAndroidTest  # with an emulator/device
```

Debug installs separately as `dev.bananajeans.eduschedule.debug`. Release is `dev.bananajeans.eduschedule`. Unsigned release assembly is allowed for CI; publishing requires the signing workflow and secrets. See [release setup](docs/RELEASING.md).

The Wear OS module requires API 30+. Install the phone and watch variants signed with the same key and application ID on paired devices. The watch displays only the phone's saved home class, and requests a refresh through the paired phone.

## Documentation

- [Implementation plan and architecture](docs/ARCHITECTURE.md)
- [Public data protocol, parsing and limitations](docs/DATA_SOURCE.md)
- [Build, release signing and updates](docs/RELEASING.md)
- [Versioning and release checklist](RELEASES.md)
- [Dependency verification](docs/DEPENDENCIES.md)
- [Privacy](docs/PRIVACY.md)
- [Wear OS setup, offline behavior and battery validation](docs/WEAR_OS.md)
- [Design standards](DESIGN.md)
- [Contributing](CONTRIBUTING.md)
- [Security policy](SECURITY.md)
- [Agent/contributor instructions](AGENTS.md)

This project is independent of aSc and EduPage. No affiliation is implied. No login, advertising or analytics SDK is included.

## License

EduSchedule is licensed under the MIT License. See [LICENSE](LICENSE).

