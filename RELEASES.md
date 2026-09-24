# Versioning and releases

EduSchedule uses stable `MAJOR.MINOR.PATCH` versions. Published GitHub tags have a `v` prefix (for example, `v0.6.0`); the Android app's `versionName` and the release workflow's manual input do not (`0.6.0`). Only signed APKs published by the `Signed release` workflow are installable updates to production builds.

## Choosing a version

| Change | Bump | Example |
| --- | --- | --- |
| Bug fix, translation, or small behavior correction without a new feature | Patch | `0.5.2` → `0.5.3` |
| New user-visible feature or substantial UI change | Minor | `0.5.2` → `0.6.0` |
| Incompatible change to supported data, updates, or user expectations after 1.0 | Major | `1.4.2` → `2.0.0` |

Before 1.0, use a minor bump for an incompatible change, and explain migration or data loss in the release notes. Increment only one component at a time and reset components to its right: after `0.5.2`, the next minor release is `0.6.0`. Never reuse, delete, or retarget a published version or tag. If a release fails, fix the cause and retry only if the tag/release was never published; otherwise issue the next patch version.

## Release checklist

1. Merge reviewed changes into `main`. Confirm Android CI (unit tests, lint, debug/release assembly, emulator tests) and CodeQL pass for the release commit. Review the device checks in [docs/RELEASING.md](docs/RELEASING.md), especially features touched by the release.
2. Check the [latest release](https://github.com/BananaJeanss/EduSchedule/releases/latest), select an unused higher version, and write release notes covering important changes and any known limitations. Verify the signing secrets in GitHub's `release` environment are available without exposing them.
3. In GitHub Actions, run **Signed release** from `main` with the version number **without** `v`, or push a `vMAJOR.MINOR.PATCH` tag at the reviewed commit. Do not trigger both paths for the same version. The workflow derives Android `versionCode` from its run number; it must increase relative to the previous release for Android to accept the update.
4. Wait for the workflow to finish. Confirm the release points to the intended commit and contains `EduSchedule-X.Y.Z.apk`, `EduSchedule-X.Y.Z.aab`, `SHA256SUMS`, and signature information. Check the APK checksum, version, package ID, and signing certificate; install/update it on a device before announcing it.
5. If the workflow fails or publication is incomplete, diagnose the failed step. Never substitute the unsigned CI APK or manually re-sign with a different key. Update any release notes when the final artifact or scope changes.

The in-app updater compares stable numeric versions from GitHub's latest-release endpoint, downloads the signed APK and checksum, verifies package/version/signature, and requests Android's normal install confirmation. It cannot automatically update debug installs, and it cannot upgrade a production install signed with a different certificate. The normal CI APK is a debug artifact with a separate application ID.

For signing setup, exact workflow behavior, and manual device review, see [docs/RELEASING.md](docs/RELEASING.md).
