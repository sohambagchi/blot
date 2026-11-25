# Personal builds update themselves through depot

**Status:** accepted

**Context:** Installing each build by hand over USB doesn't scale to a tablet and a phone; android-emu's depot serves APKs from malt and its client installs them.
**Decision:** The dev shell merges `android-emu.lib.depotEnv`; when `DEPOT_CLIENT` is set, `settings.gradle.kts` includes depot's client (mirrored from the Nix store into `build/depot-client`, since Gradle needs a writable project directory) and the app depends on it. `versionCode` is the commit count and `versionName` the newest `[X.Y.Z]` subject, so every published commit installs over the last. android-emu is pinned to its committed `main`.
**Alternatives:** A hand-maintained `versionCode` — forgets to bump and blocks updates; app code calling depot — the client needs none.
**Consequences:** Builds outside the dev shell (a public build) have no updater, no network permissions and no cleartext. Inside it, the app gains INTERNET, install and notification permissions and `usesCleartextTraffic` (depot's Tailscale address). The first publish pins the Blot signing key ([2026-11-24-2213-fixed-signing-key-outside-the-repo](2026-11-24-2213-fixed-signing-key-outside-the-repo.md)) on the server.
