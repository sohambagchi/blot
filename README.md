# Blot

A pen-based notes app for Android. It's built for a Lenovo TB-J616F tablet with a Lenovo pen, and also runs on a Pixel 7a for viewing and light editing.

## Develop

```sh
nix develop                      # JDK, Gradle wrapper, build SDK, emulator scripts
emu-tablet                       # boot tab-31 (Android 12, 2000x1200 @ 204dpi) as emulator-5556
EMU_API=35 emu-tablet            # same shape on Android 15 (tab-35)
emu-up                           # boot emu-35 (Pixel 7 profile) as emulator-5554; EMU_API=31 for emu-31

./gradlew :core:test             # host-only model/geometry tests
./gradlew :app:testDebugUnitTest # Robolectric
ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest
ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedAndroidTest -Pblot.testBuildType=shrunk   # against R8-shrunk code
./gradlew :app:assembleRelease   # shrunk release APK
depot-publish app/build/outputs/apk/release/app-release.apk # publish to depot (clean tree)
EMU_PORT=5556 emu-shot           # screenshot of the tablet emulator
```

Builds are signed with `~/.android/blot.jks`, outside the repo, so every build installs over the last. Create it once with
`keytool -genkeypair -keystore ~/.android/blot.jks -alias blot -keyalg EC -groupname secp256r1 -validity 10000 -storepass blotkey -keypass blotkey -dname CN=blot`.
An untracked `signing.properties` (`signing.store`, `signing.password`) points elsewhere; without the key, builds use the debug key.

Install `app/build/outputs/apk/release/app-release.apk` to try a build without adb. Builds from the dev shell update themselves from depot (~/dev/depot) once a device has enrolled with Tailscale on; builds outside it have no updater. The developer screens (pen test, canvas test, UI preview) are under Settings → long-press About.

Docs live in [docs/INDEX.md](docs/INDEX.md). Open work is in [docs/TODO.md](docs/TODO.md).

MIT licensed; see [LICENSE](LICENSE).
