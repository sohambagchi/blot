pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "blot"
include(":core", ":app")

// depot's update client (android-emu), built in only when the dev shell provides it. Gradle
// needs a writable project directory, so the read-only Nix store copy is mirrored into build/.
System.getenv("DEPOT_CLIENT")?.let { source ->
    val dir = file("build/depot-client")
    val stamp = dir.resolve(".source")
    if (!stamp.isFile || stamp.readText() != source) {
        dir.deleteRecursively()
        file(source).copyRecursively(dir)
        dir.walk().forEach { it.setWritable(true, true) }
        stamp.writeText(source)
    }
    include(":depot")
    project(":depot").projectDir = dir
}
