# depot's client owns the network security config

**Status:** accepted

**Context:** Blot generated its own cleartext policy from `DEPOT_URLS` ([2026-11-24-2346-release-builds-unobfuscated-cleartext-only-to-depot](2026-11-24-2346-release-builds-unobfuscated-cleartext-only-to-depot.md)). depot adopted the rule for every app and moved to HTTPS only, reaching malt through buckwheat's Tailscale route.
**Decision:** Blot sets no network security config and no `usesCleartextTraffic`; depot's client supplies both: cleartext refused, except to `http://` hosts in `DEPOT_URLS` or `DEPOT_CLEARTEXT_HOSTS` (none by default). Blot keeps `-dontobfuscate`.
**Alternatives:** Keep Blot's generator — it would clash with the library's attribute in the manifest merge.
**Consequences:** A default build allows no cleartext at all. An emulator on malt can't enroll through the HTTPS name; build with `DEPOT_URLS=https://depot.wheat.sohambagchi.com,http://100.97.41.27:8473` for that. `CleartextPolicyTest` takes the expected hosts as `-e cleartextHosts`.
