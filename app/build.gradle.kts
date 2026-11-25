import java.util.Properties

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
        applicationId = "app.blot"
        minSdk = 31 // Lenovo TB-J616F runs Android 12
        targetSdk = 37
        versionCode = 1
        versionName = "0.18.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    }

    // `-Pblot.testBuildType=release` runs the device tests against the shrunk build.
    testBuildType = providers.gradleProperty("blot.testBuildType").getOrElse("debug")

    buildFeatures { compose = true }

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
