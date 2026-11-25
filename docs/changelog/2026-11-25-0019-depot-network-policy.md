# depot's client sets the network policy, and Blot reaches depot over HTTPS only

- android-emu is at fec68e0: `DEPOT_URLS` is the HTTPS name only, and depot's client generates the network security config; Blot's own generator and manifest overrides are gone, and a default build refuses all cleartext
- The device tests' document providers are named after the test package, so the debug and shrunk test APKs install side by side
- 43 device tests pass on the API 31 emulator
- Rationale: see ADR [2026-11-25-0019-depot-client-owns-the-network-policy](../adr/2026-11-25-0019-depot-client-owns-the-network-policy.md)
