# The release build passes the device tests, and maths renders in it

- Maths in text boxes typesets in release builds: the math font is looked up by its absolute resource path, since R8 moves `MathFontFile` to another package
- A `shrunk` build type (release plus `proguard-device-tests.pro`) carries the device tests: libraries are kept whole for the test runner, Blot's own members are kept but renamed as in release
- `-Pblot.testBuildType=shrunk` runs them; 42 device tests pass on the API 35 emulator against it
