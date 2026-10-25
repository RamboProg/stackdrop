import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// Firebase stays off until app/google-services.json is added; the game then simply runs offline.
if (file("google-services.json").exists()) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
    // Uploads R8 mapping files with release builds so crash reports show readable stack traces.
    apply(plugin = libs.plugins.firebase.crashlytics.get().pluginId)
}

// Crash reports are sent from release builds only, so development crashes don't fill the
// dashboard. Set stackdrop.crashlyticsDebug=true in gradle.properties to test from a debug build.
val crashlyticsDebug = providers.gradleProperty("stackdrop.crashlyticsDebug").orElse("false").get()

// Debug builds talk to the local Firebase Emulator Suite when gradle.properties sets
// stackdrop.useEmulators=true. 10.0.2.2 is the host machine as seen from the Android emulator.
val useEmulators = providers.gradleProperty("stackdrop.useEmulators").orElse("false").get()
val emulatorHost = providers.gradleProperty("stackdrop.emulatorHost").orElse("10.0.2.2").get()

// AdMob: debug builds always use Google's public test IDs, so real ads are never requested while
// developing. Release builds need the real IDs in gradle.properties; until AdMob approves the app,
// stackdrop.allowTestAds=true lets a release build fall back to the test IDs for testing tracks.
val testAppId = "ca-app-pub-3940256099942544~3347511713"
val testRewarded = "ca-app-pub-3940256099942544/5224354917"
val testInterstitial = "ca-app-pub-3940256099942544/1033173712"
val testBanner = "ca-app-pub-3940256099942544/9214589741"
val allowTestAds = providers.gradleProperty("stackdrop.allowTestAds").orElse("false").get().toBoolean()
val adIdNames = listOf("stackdrop.admobAppId", "stackdrop.admobRewardedId", "stackdrop.admobInterstitialId",
    "stackdrop.admobBannerId")
val missingAdIds = adIdNames.filter { !providers.gradleProperty(it).isPresent }
fun adProperty(name: String, test: String) = providers.gradleProperty(name).orElse(test).get()

// The consent form only appears for players in the EEA, UK and Switzerland. Set this to true in
// gradle.properties to see it on a debug build from anywhere else.
val consentDebugEea = providers.gradleProperty("stackdrop.consentDebugEea").orElse("false").get()

// Upload key for release builds, read from keystore.properties in the project root. That file holds
// passwords, so it stays out of git; without it, release builds come out unsigned.
val keystoreProps = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "com.stackdrop.game"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        // Play listing ID (com.stackdrop.game was already taken on Play). Code stays in the
        // com.stackdrop.game namespace; only the installed app's ID differs.
        applicationId = "com.stackdrop.towers"
        minSdk = 24
        targetSdk = 37
        // Raise stackdrop.versionCode in gradle.properties for every upload; Play rejects a reused code.
        versionCode = providers.gradleProperty("stackdrop.versionCode").orElse("1").get().toInt()
        versionName = providers.gradleProperty("stackdrop.versionName").orElse("1.0").get()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("upload") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "USE_EMULATORS", useEmulators)
            buildConfigField("String", "EMULATOR_HOST", "\"$emulatorHost\"")
            manifestPlaceholders["admobAppId"] = testAppId
            buildConfigField("String", "AD_REWARDED_ID", "\"$testRewarded\"")
            buildConfigField("String", "AD_INTERSTITIAL_ID", "\"$testInterstitial\"")
            buildConfigField("String", "AD_BANNER_ID", "\"$testBanner\"")
            buildConfigField("boolean", "CONSENT_DEBUG_EEA", consentDebugEea)
            manifestPlaceholders["crashlyticsEnabled"] = crashlyticsDebug
        }
        release {
            buildConfigField("boolean", "USE_EMULATORS", "false")
            buildConfigField("String", "EMULATOR_HOST", "\"\"")
            manifestPlaceholders["admobAppId"] = adProperty("stackdrop.admobAppId", testAppId)
            buildConfigField("String", "AD_REWARDED_ID", "\"${adProperty("stackdrop.admobRewardedId", testRewarded)}\"")
            buildConfigField("String", "AD_INTERSTITIAL_ID",
                "\"${adProperty("stackdrop.admobInterstitialId", testInterstitial)}\"")
            buildConfigField("String", "AD_BANNER_ID", "\"${adProperty("stackdrop.admobBannerId", testBanner)}\"")
            buildConfigField("boolean", "CONSENT_DEBUG_EEA", "false")
            manifestPlaceholders["crashlyticsEnabled"] = "true"
            signingConfig = signingConfigs.findByName("upload")
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    buildFeatures {
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.material)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.functions)
    implementation(libs.firebase.appcheck.playintegrity)
    debugImplementation(libs.firebase.appcheck.debug)
    implementation(libs.firebase.crashlytics)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.play.services.ads)
    implementation(libs.ump)
    implementation(libs.billing)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}

// Release checks, run only when a release build is requested so debug builds never trip on them.
val releaseAdsMissing = if (allowTestAds) emptyList() else missingAdIds
val releaseSigned = keystoreProps.isNotEmpty()
tasks.configureEach {
    if (name == "preReleaseBuild") {
        val missing = releaseAdsMissing
        val signed = releaseSigned
        doFirst {
            if (missing.isNotEmpty()) {
                throw GradleException(
                    "Release build needs the real AdMob IDs in gradle.properties: ${missing.joinToString()}. " +
                        "Set stackdrop.allowTestAds=true to build with test ads for a testing track."
                )
            }
            if (!signed) logger.warn("WARNING: keystore.properties not found; this release build is UNSIGNED.")
        }
    }
}
