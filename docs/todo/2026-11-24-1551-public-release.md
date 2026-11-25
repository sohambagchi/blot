# Public open-source release: what to settle first

Only needed if the app goes public. For a sideloaded personal build, none of this blocks anything.

- **Third-party notices.** The project is MIT ([ADR](../adr/2026-11-24-1742-mit-licence.md)). List ported code and libraries, with their copyright lines and any Apache `NOTICE` text, in a `THIRD_PARTY.md` or an about screen.
- **Application ID.** `app.blot` is a placeholder. Use a domain or GitHub-namespace ID we can keep (e.g. `io.github.<user>.screenscratch`). Changing it later breaks updates for anyone who installed it.
- **Signing.** Make one release keystore and keep it out of git and backed up. Losing it means users must uninstall to update. GitHub Actions can sign with the keystore stored as encrypted secrets.
- **Distribution.**
  - GitHub Releases: upload signed APKs and let users update with Obtainium.
  - F-Droid: needs a reproducible build from source, no proprietary dependencies (none planned), and fastlane metadata.
  - Play Store: possible, since storage uses SAF and no broad storage permission.
- **ABIs.** graphics-core ships native code. Both target devices are `arm64-v8a`, so release builds can filter to it; debug builds keep `x86_64` for emulators.
- **Privacy.** No network permission and no analytics. Say so in the README; F-Droid flags anti-features.
- **CI.** A GitHub Actions workflow that runs `:core:test` and `:app:testDebugUnitTest`, plus emulator tests via a KVM-enabled runner. Make release builds from tags.
- **Repo hygiene.** Remove the absolute `git+file:///home/...` android-emu input from `flake.nix`. Publish android-emu or point at it by a GitHub URL, and offer a plain Gradle path for contributors without Nix.
- **Format stability.** Once other people's notes exist, `FORMAT_VERSION` migrations become a compatibility promise. Document the on-disk format.
