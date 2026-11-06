plugins {
    kotlin("jvm") version "1.9.22"
}

group = "com.chatapp"
version = "1.0.0"

repositories {
    mavenCentral()
    google()
}

dependencies {
    // Kotlin Coroutines for reactive Flow streaming
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")

    // OkHttp for resilient HTTP/2 & SSE streaming
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // JSON parsing (Standard org.json on JVM; built-in on Android runtime)
    implementation("org.json:json:20231013")

    // Unit Testing
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
}

kotlin {
    jvmToolchain(17)
}

