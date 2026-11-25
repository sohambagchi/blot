# Blot moves to com.sohambagchi.blot, with fewer Play Protect red flags

- The app id is `com.sohambagchi.blot` (debug: `com.sohambagchi.blot.debug`), as depot now requires; it installs as a new app that asks for the notes folder once — see [2026-11-24-2346-application-id-com-sohambagchi-blot](../adr/2026-11-24-2346-application-id-com-sohambagchi-blot.md)
- Release builds are shrunk but no longer obfuscated (4.36 MB)
- Cleartext is refused everywhere except depot's Tailscale address, generated from `DEPOT_URLS`; `CleartextPolicyTest` checks the policy on a device — see [2026-11-24-2346-release-builds-unobfuscated-cleartext-only-to-depot](../adr/2026-11-24-2346-release-builds-unobfuscated-cleartext-only-to-depot.md)
