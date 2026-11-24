# Public open-source release: what to settle first

Only needed if the app goes public. For a sideloaded personal build, none of this blocks anything.

- **Licence.** Choose it before porting any code: it decides what we're allowed to port.
  - MIT or Apache-2.0: we can port from xnotes, Excalidraw, perfect-freehand, Markor and Jetpack Ink, but not from GPL apps.
  - GPL-3.0: we can also port from Rnote, Saber and Notable. Xournal++ is GPL-2.0, so check whether it is "or later" first.
  - Either way, keep upstream copyright lines and Apache `NOTICE` text next to ported code, and list them in a `THIRD_PARTY.md` or an about screen.
- **Application ID.** `app.screenscratch` is a placeholder. Use a domain or GitHub-namespace ID we can keep (e.g. `io.github.<user>.screenscratch`). Changing it later breaks updates for anyone who installed it.
- **Signing.** Make one release keystore and keep it out of git and backed up. Losing it means users must uninstall to update. GitHub Actions can sign with the keystore stored as encrypted secrets.
- **Distribution.**
  - GitHub Releases: upload signed APKs and let users update with Obtainium.
  - F-Droid: needs a reproducible build from source, no proprietary dependencies (none planned), and fastlane metadata.
  - Play Store: would reject `MANAGE_EXTERNAL_STORAGE` for a notes app, so a Play build needs the SAF `NoteStore` implementation.
- **ABIs.** Ship arm64-v8a and armeabi-v7a, plus x86_64 for emulators. Or ship split APKs per ABI. Only relevant if native libraries are added.
- **Privacy.** No network permission and no analytics. Say so in the README; F-Droid flags anti-features.
- **CI.** A GitHub Actions workflow that runs `:core:test` and `:app:testDebugUnitTest`, plus emulator tests via a KVM-enabled runner. Make release builds from tags.
- **Repo hygiene.** Remove the absolute `git+file:///home/...` android-emu input from `flake.nix`. Publish android-emu or point at it by a GitHub URL, and offer a plain Gradle path for contributors without Nix.
- **Format stability.** Once other people's notes exist, `FORMAT_VERSION` migrations become a compatibility promise. Document the on-disk format.
