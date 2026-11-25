# Shrink and optimise, but keep names: Blot is open source, so renaming hides nothing, and
# obfuscated code that installs packages looks like a dropper to Play Protect's heuristics.
-dontobfuscate
# Note files are read and written by kotlinx.serialization; its own consumer rules keep the
# generated serializers.
-keepattributes SourceFile,LineNumberTable
