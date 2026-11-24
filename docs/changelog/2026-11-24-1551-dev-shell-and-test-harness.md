# Dev shell, Gradle skeleton and test harness

- `nix develop` provides JDK 21, the Gradle wrapper (9.9.2), a build SDK (platform 37.2, build-tools 37.0.0) and android-emu's `emu-up`, `emu-down` and `emu-shot`.
- `emu-tablet` boots `tab-35` as `emulator-5556`. It's an API 35 AVD shaped like the Lenovo TB-J616F (2000×1200, 240 dpi, landscape). The default `emu-35` (Pixel 7 profile, `emulator-5554`) stands in for the Pixel 7a.
- Gradle project uses AGP 9.5.2 and Kotlin 2.4.21:
  - `:core` is pure JVM (model, geometry, serialization) and runs host-only JUnit tests.
  - `:app` is the Android app with Compose: minSdk 31, compile/target 37.2, id `app.screenscratch`.
- Tests that pass:
  - `./gradlew :core:test`
  - `:app:testDebugUnitTest` (Robolectric)
  - `:app:connectedDebugAndroidTest` on `tab-35`
- The placeholder activity installs and launches on the tablet emulator.
- Rationale: see ADR [2026-11-24-1551-separate-build-sdk-from-emulator-sdk](../adr/2026-11-24-1551-separate-build-sdk-from-emulator-sdk.md)
