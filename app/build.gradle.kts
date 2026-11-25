import java.util.Properties

fun git(vararg args: String) = providers.exec { commandLine("git", *args) }.standardOutput.asText.get().trim()

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Untracked overrides for the signing key: signing.store and signing.password.
val signingProps = Properties().apply {
    rootProject.file("signing.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
val signingStore = file(signingProps.getProperty("signing.store", "${System.getProperty("user.home")}/.android/blot.jks"))

android {
    namespace = "app.blot"
    // Both must match the build SDK in flake.nix: the Nix store SDK is read-only, so AGP
    // cannot download a missing platform or build-tools. AndroidX needs compileSdk >= 37.1.
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "com.sohambagchi.blot" // depot requires com.sohambagchi.<app>; the code stays in app.blot
        minSdk = 31 // Lenovo TB-J616F runs Android 12
        targetSdk = 37
        // The commit count, so every commit installs over the last; versionName is X.Y.Z from the
        // newest "[X.Y.Z] ..." commit subject (version-commit).
        versionCode = git("rev-list", "--count", "HEAD").toInt()
        versionName = git("log", "-1", "--grep=^\\[[0-9]", "--format=%s").substringAfter("[").substringBefore("]").ifEmpty { "0.0.0" }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["appLabel"] = "Blot"
    }

    signingConfigs {
        // One key for every build, kept outside the repo, so updates install over each other.
        // Without it (a fresh clone, CI) builds fall back to the debug key.
        create("blot") {
            storeFile = signingStore
            storePassword = signingProps.getProperty("signing.password", "blotkey")
            keyAlias = "blot"
            keyPassword = signingProps.getProperty("signing.password", "blotkey")
        }
    }

    buildTypes {
        // A separate app, so debug and release install side by side and each updates from its own
        // line on depot, which keys releases by application id.
        debug {
            applicationIdSuffix = ".debug"
            manifestPlaceholders["appLabel"] = "Blot debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            testProguardFiles("proguard-test-rules.pro")
        }
        if (signingStore.exists()) {
            debug { signingConfig = signingConfigs.getByName("blot") }
            release { signingConfig = signingConfigs.getByName("blot") }
        }
        // The release build plus keep rules for what the test runner loads from the app's classpath.
        create("shrunk") {
            initWith(getByName("release"))
            matchingFallbacks += "release"
            proguardFile("proguard-device-tests.pro")
        }
    }

    // `-Pblot.testBuildType=shrunk` runs the device tests against the shrunk release code.
    testBuildType = providers.gradleProperty("blot.testBuildType").getOrElse("debug")

    buildFeatures { compose = true }

    // Updates.kt calls depot's "App updates" screen only when depot's client is in the build.
    sourceSets.getByName("main").kotlin.directories.add(if (findProject(":depot") != null) "src/depot/kotlin" else "src/nodepot/kotlin")

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(project(":core"))
    if (findProject(":depot") != null) implementation(project(":depot"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.graphics.core)
    implementation(libs.androidx.input.motionprediction)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.commonmark)
    implementation(libs.commonmark.ext.gfm.strikethrough)
    implementation(libs.commonmark.ext.task.list.items)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
