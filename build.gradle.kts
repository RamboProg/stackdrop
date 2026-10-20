// Top-level build file where you can add configuration options common to all sub-projects/modules.

// AGP 9 compiles Kotlin with its bundled Kotlin Gradle plugin (2.2.x). Firebase BoM 35 ships
// libraries built with Kotlin 2.4, which that compiler cannot read, so pin a newer KGP here.
// See https://developer.android.com/build/releases/agp-9-0-0-release-notes (Upgrade to a higher KGP version).
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.google.services) apply false
}
