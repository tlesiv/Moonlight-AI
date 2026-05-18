plugins {
    kotlin("jvm") version "1.9.24"
    id("org.jetbrains.compose") version "1.7.0"
}

repositories {
    mavenCentral()
    google()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.7.3")}

kotlin {
    jvmToolchain(17)
}

sourceSets {
    named("main") {
        kotlin.srcDir("src")
        resources.srcDir("src/resources")
    }
}

compose.desktop {
    application {
        mainClass = "MainKt"
    }
}
