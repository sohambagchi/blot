# Separate build SDK from the android-emu emulator SDK

**Status:** accepted

**Context:** android-emu's SDK is pinned to platform 35 and build-tools 35. Current AndroidX (core-ktx 1.19, Compose BOM 2026.11.01) requires compileSdk 37.1 or later, and AGP can't install missing parts into the read-only Nix store.
**Decision:** `flake.nix` builds its own small SDK (platform 37.2 and build-tools 37.0.0, no emulator or images) for Gradle. Emulators, `adb` scripts and system images still come from `~/dev/android-emu`. The app targets `minSdk 31` (Android 12, the tablet) and compiles against 37.2.
**Alternatives:** Add platform 37.2 to android-emu. Rejected because it changes another repo for one project's needs. Pin older AndroidX that compiles against 35. Rejected because it locks us onto stale libraries.
**Consequences:** two SDK store paths, with only the build one on `ANDROID_HOME`. AGP must not be pointed at android-emu's SDK. Bumping AndroidX may require bumping `platformVersions` in `flake.nix` and `compileSdk` together.
