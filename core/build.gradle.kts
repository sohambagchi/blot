// Pure-JVM module: document model, geometry, serialization. No Android dependencies,
// so its tests run on the host in seconds.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    // api: the model types expose their generated serializers.
    api(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
