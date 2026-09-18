# EduSchedule

A native Android reader for public aSc / EduPage timetables. Built around the next lesson, rather than a desktop timetable squeezed onto a phone.

## Features

- Material 3, Android wallpaper colors, light/dark/system themes, edge-to-edge layout, adaptive monochrome launcher icon, system back and proper navigation controls.
- Day agenda with current/next lesson and a horizontally scrollable week board.
- Search classes, teachers and rooms. Save a default class while browsing any other schedule. Hide unwanted split groups.
- Date-aware timetable revisions, real bell times, multiple-period lessons, and explicit week-cycle selection when a school publishes multiple cycles.
- Validated atomic offline cache; current week prefetched; stale/offline state and fetch time shown. Manual refresh and bounded storage.
- Add a lesson through Android's calendar editor (including Google Calendar); export the displayed week as RFC 5545 `.ics` or spreadsheet `.csv` through the system file picker.
- Opt-in notifications for changes to your default class and app releases. Manual GitHub release checking. No silent APK installation.
- Debug APK artifacts, release signing workflow, JVM tests, emulator UI tests, Android lint, CodeQL and Dependabot.

Default source: [Tallinna Kunstigümnaasium](https://kunst.edupage.org/timetable/). Change the public EduPage school address and its time zone in Settings. Android 8+ (API 26).

**This is a regular timetable reader, not a replacement for the school's official announcements.** The example school's public feed disables its daily/current timetable. Substitutions, cancellations and holidays are not applied. Calendar exports are dated snapshots, not subscriptions or two-way sync. Check the school when in doubt.

## Build

Use JDK 17 and the Android SDK. Install platform 37.0 and build tools 36.0.0. Set `ANDROID_HOME`, or add `sdk.dir` to an untracked `local.properties` file.

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
./gradlew connectedDebugAndroidTest  # with an emulator/device
```

Debug installs separately as `dev.bananajeans.eduschedule.debug`. Release is `dev.bananajeans.eduschedule`. Unsigned release assembly is allowed for CI; publishing requires the signing workflow and secrets. See [release setup](docs/RELEASING.md).

## Documentation

- [Implementation plan and architecture](docs/ARCHITECTURE.md)
- [Public data protocol, parsing and limitations](docs/DATA_SOURCE.md)
- [Build, release signing and updates](docs/RELEASING.md)
- [Dependency verification](docs/DEPENDENCIES.md)
- [Privacy](docs/PRIVACY.md)
- [Agent/contributor instructions](AGENTS.md)

This project is independent of aSc, EduPage and the school. No affiliation is implied. No login, advertising or analytics SDK is included.
