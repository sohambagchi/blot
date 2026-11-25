# Release builds are shrunk with R8

- `assembleRelease` minifies code and drops unused resources: the release APK is 3.9 MB against the debug build's 29 MB, signed with the same Blot key, so it installs over a debug install and keeps its settings
- `app/proguard-rules.pro` keeps line numbers for crash traces (the mapping is in `app/build/outputs/mapping/release/`); kotlinx.serialization's own consumer rules cover the note format
- `-Pblot.testBuildType=release` runs the device tests against the shrunk build; `app/proguard-test-rules.pro` covers the test APK
