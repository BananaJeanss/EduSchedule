# Releases and signing

## One-time owner setup

Create a durable signing key outside the repository and back it up securely. Android requires the same key for future updates. Do not use the debug key, generate a fresh key per build, or paste passwords into issues.

```sh
keytool -genkeypair -v -keystore eduschedule-release.jks -alias eduschedule -keyalg RSA -keysize 4096 -validity 10000
```

In GitHub's `release` environment (or repository settings), configure:

| Type | Name | Value |
| --- | --- | --- |
| Secret | `SIGNING_KEYSTORE_BASE64` | Base64 of the JKS, a single line |
| Secret | `SIGNING_STORE_PASSWORD` | Keystore password |
| Secret | `SIGNING_KEY_PASSWORD` | Private key password |

These settings must be supplied by the owner; no signing secret is created or committed by this project. The release workflow uses the fixed keystore alias `eduschedule`, matching the documented key-generation command. Restrict the `release` environment to trusted branches/tags. `Signed release` fails before building if any signing input is missing.

## Publish

After Android CI, emulator tests and CodeQL pass, run `Signed release` manually with a stable semantic version, or push a `vX.Y.Z` tag. The workflow derives an increasing Android version code from its run number, runs unit tests and release lint, builds APK/AAB, verifies the APK certificate, creates checksums and GitHub provenance attestations, and publishes a GitHub release. Signing material is removed even if a step fails. Never reuse a published version tag or move it to a different commit.

The app reads GitHub's latest stable release endpoint and compares numeric semantic versions. A valid release must publish `EduSchedule-X.Y.Z.apk` and `SHA256SUMS`. The in-app updater downloads those assets from the trusted repository, verifies the checksum plus APK package/version/signing certificate, and submits it through Android `PackageInstaller`. Android requires the user to allow EduSchedule as an install source and to confirm each update; the app does not silently install APKs.

Debug builds have a separate application ID and are available from Android CI artifacts. Normal CI also assembles a minified unsigned release so release-only issues surface before signing credentials are supplied.

The release workflow also builds and signs separate `EduSchedule-Wear-X.Y.Z.apk` and `.aab` artifacts with the same key and application ID on the watch. It verifies both APK signatures and includes both in the checksums and provenance. The phone's in-app updater deliberately selects the phone APK by exact filename. Install and update the Wear APK through the watch's supported install channel; the phone updater does not install it.

## Release review

For theme and sheet changes, verify every preset and custom colors in light/dark mode, high-contrast text, wallpaper color switching, app restart, the widget setup screen, and a lesson sheet with short and long linked-entity lists on both gesture and three-button navigation. The Add to calendar action must be fully visible without dragging a short sheet.

The Expressive theme currently pins Material 3 `1.5.0-alpha29` because the theme API is not in the 1.4 stable release. Recheck its API and test all screens before promoting this build. Add and resize multiple widgets with different modes/options; confirm the saved home class, offline/unsaved states, dark colors, midnight rollover and reconfiguration. Widget updates are launcher-controlled and may lag by up to 30 minutes.

Check real-device light/dark/dynamic colors, gesture and three-button navigation, rotation and large font sizes. Verify school/class selection, split groups, week/date transitions, offline restart, calendar insertion, .ics import in Google Calendar, denied notifications, class reminders with the app swiped away/process dead, reminder recovery after reboot/package update, stale-reminder suppression, denied/allowed exact-alarm access on Android 12 where applicable, denied/allowed unknown-app install access, update download/verification, Android install confirmation, and update cancellation. Check the regular-timetable limitation text.

For the updater specifically, install an older **signed production** APK on a physical device, allow installs from EduSchedule, tap Download & install, and check that Android displays its confirmation after verification. Repeat with the app backgrounded during download: the notification should return to the confirmation. The emulator callback regression test uses a synthetic intent and cannot prove a real package manager's signing and confirmation behavior.

For localization changes, test English, Estonian and System default from both first-run setup and Settings. Confirm the activity recreates into the selected locale; weekday/month names, notifications, reminder actions, calendar descriptions and CSV headers follow it; and source-provided timetable names remain unchanged. Unit tests enforce translation-key and format-placeholder parity, but device review is still required for truncation and layout regressions.

An APK that compiles is not proof these device behaviors passed.
