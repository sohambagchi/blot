# Screen Scratch

A pen-based notes app for Android. It's built for a Lenovo TB-J616F tablet with a Lenovo pen, and also runs on a Pixel 7a for viewing and light editing.

## Develop

```sh
nix develop                      # JDK, Gradle wrapper, build SDK, emulator scripts
emu-tablet                       # boot tab-35 (2000x1200 @ 240dpi) as emulator-5556
emu-up                           # boot emu-35 (Pixel 7 profile) as emulator-5554

./gradlew :core:test             # host-only model/geometry tests
./gradlew :app:testDebugUnitTest # Robolectric
ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest
EMU_PORT=5556 emu-shot           # screenshot of the tablet emulator
```

Docs live in [docs/INDEX.md](docs/INDEX.md). Open work is in [docs/TODO.md](docs/TODO.md).
