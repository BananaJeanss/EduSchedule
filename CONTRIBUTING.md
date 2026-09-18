# Contributing to EduSchedule

Thanks for helping improve EduSchedule.

## Before you start

- Search existing issues and pull requests first.
- Use an issue for bugs or larger behavior changes.
- Report security vulnerabilities privately according to [SECURITY.md](SECURITY.md), not in a public issue.
- Never commit real student names, private timetable data, raw authenticated EduPage responses, signing keys, passwords, tokens, or other secrets.

## Development setup

Use JDK 17 and an Android SDK with platform 37.0 and build tools 36.0.0.

Run before opening a pull request:

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
./gradlew assembleDebugAndroidTest
```

When an emulator or device is available:

```sh
./gradlew connectedDebugAndroidTest
```

## Pull requests

Keep changes focused and explain:

- what changed and why;
- how it was tested;
- any user-visible screenshots for UI changes;
- any data-source or privacy impact.

Parser changes require sanitized synthetic regression fixtures. Do not attach raw school responses containing personal names or schedules.

CI, CodeQL, Android lint, unit tests and emulator tests are release gates. Fix failures instead of disabling checks.

## Product constraints

EduSchedule is a native Material 3 Android app. Preserve:

- offline behavior and bounded caching;
- edge-to-edge and system navigation support;
- saved home-class state independent from temporary browsing;
- the distinction between regular timetables and live substitutions;
- HTTPS-only public EduPage access;
- minimal permissions, no analytics, and no login/private scraping.

See [AGENTS.md](AGENTS.md) and the files under `docs/` for architecture and data invariants.
