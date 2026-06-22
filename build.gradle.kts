import org.jetbrains.compose.desktop.application.dsl.TargetFormat

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
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")
    implementation("io.github.vinceglb:filekit-compose:0.8.1")
    implementation("org.scilab.forge:jlatexmath:1.0.7")
    implementation("org.scilab.forge:jlatexmath-font-cyrillic:1.0.7")
    implementation("org.scilab.forge:jlatexmath-font-greek:1.0.7")
}

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
        buildTypes.release.proguard {
            isEnabled.set(false)
        }
        nativeDistributions {
            targetFormats(TargetFormat.Exe)
            packageName = "Moonlight AI"
        }
    }
}