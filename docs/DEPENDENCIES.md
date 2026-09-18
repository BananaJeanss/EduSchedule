# Dependency verification

Verified against official Maven metadata and GitHub release refs on 2026-09-18. Versions are pinned in `gradle/libs.versions.toml`; Compose modules use the stable BOM.

| Component | Version |
| --- | --- |
| Android Gradle Plugin | 9.4.1 |
| Gradle | 9.7.1 |
| Kotlin Compose compiler plugin | 2.4.20 |
| Compose BOM | 2026.09.00 |
| Activity Compose | 1.13.0 |
| Lifecycle | 2.11.0 |
| Core | 1.19.0 |
| WorkManager | 2.11.2 |
| Coroutines Android | 1.11.0 |
| JVM test JSON | 20260814 |
| JUnit 4 | 4.13.2 |
| AndroidX test runner / ext JUnit | 1.7.0 / 1.3.0 |

AGP provides built-in Kotlin support; the Compose compiler plugin is explicitly pinned. Build Tools 36.0.0 are AGP's documented default, not a runtime library. Compile/target API 37; minimum API 26. Do not opt into previews merely to raise version numbers.

Registry sources: [Google Maven](https://dl.google.com/dl/android/maven2/index.html), [Maven Central](https://repo.maven.apache.org/maven2/), [Gradle versions](https://services.gradle.org/versions/current), [AGP compatibility](https://developer.android.com/build/releases/agp-9-4-0-release-notes), [Compose BOM](https://developer.android.com/develop/ui/compose/bom/bom-mapping). GitHub Actions are pinned to resolved commit SHAs, with Dependabot updates enabled. Gradle wrapper JAR and distribution checksums were checked against Gradle's version metadata.
