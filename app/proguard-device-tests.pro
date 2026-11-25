# The instrumentation runner and Compose test rule load library code from the app's classpath
# that the app itself never calls, so R8 would strip it: libraries are kept whole here.
-keep class !app.blot.**,** { *; }
# Tests call Blot members the app doesn't, so they are kept too, but still renamed as in
# release: lookups by name, such as the note format's serializers, are exercised as shipped.
-keep,allowobfuscation class app.blot.** { *; }
