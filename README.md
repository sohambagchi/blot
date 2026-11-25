# Screen Scratch

A pen-based notes app for Android. It's built for a Lenovo TB-J616F tablet with a Lenovo pen, and also runs on a Pixel 7a for viewing and light editing.

## Develop

```sh
nix develop                      # JDK, Gradle wrapper, build SDK, emulator scripts
emu-tablet                       # boot tab-31 (Android 12, 2000x1200 @ 240dpi) as emulator-5556
EMU_API=35 emu-tablet            # same shape on Android 15 (tab-35)
emu-up                           # boot emu-35 (Pixel 7 profile) as emulator-5554; EMU_API=31 for emu-31

./gradlew :core:test             # host-only model/geometry tests
./gradlew :app:testDebugUnitTest # Robolectric
ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest
EMU_PORT=5556 emu-shot           # screenshot of the tablet emulator
```

Docs live in [docs/INDEX.md](docs/INDEX.md). Open work is in [docs/TODO.md](docs/TODO.md).

MIT licensed; see [LICENSE](LICENSE).
