# Release builds keep their names, and cleartext goes only to depot

**Status:** accepted; amended by [2026-11-25-0019-depot-client-owns-the-network-policy](2026-11-25-0019-depot-client-owns-the-network-policy.md)

**Context:** Play Protect flags the depot-enabled release as possibly harmful. It installs packages, updates itself, runs at boot, allowed cleartext everywhere and was obfuscated: the pattern of a dropper.
**Decision:** R8 shrinks and optimises with `-dontobfuscate`. The manifest sets `usesCleartextTraffic="false"` (overriding depot's `true`) and a generated network security config allows cleartext only to the `http://` hosts in `DEPOT_URLS` (depot's Tailscale address); a build without depot allows none.
**Alternatives:** Turning off Play Protect's scanning — weakens the whole device; submitting to Google for review — kept in reserve if the warning stays.
**Consequences:** The release APK grows from 3.97 MB to 4.36 MB and its stack traces need no mapping. The Tailscale address is generated, never committed.
