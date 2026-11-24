import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// ---------------------------------------------------------------------------------------------
// Version — single source of truth is `appVersion` in gradle.properties (see CLAUDE.md
// "Versioning & tags"). Release tags must be exactly "v$appVersion"; CI enforces this.
// versionCode is derived so it always increases: 1.2.3-rc.4 → 1_02_03_04, 1.2.3 → 1_02_03_99.
// ---------------------------------------------------------------------------------------------
val appVersion: String = providers.gradleProperty("appVersion").get()
val appVersionCode: Int = run {
    val m = Regex("""(\d+)\.(\d+)\.(\d+)(?:-rc\.(\d+))?""").matchEntire(appVersion)
        ?: error("appVersion '$appVersion' must look like 1.2.3 or 1.2.3-rc.1")
    val (major, minor, patch) = m.groupValues.drop(1).take(3).map { it.toInt() }
    val rc = m.groupValues[4].toIntOrNull()
    require(minor < 100 && patch < 100 && (rc == null || rc in 1..98)) {
        "appVersion '$appVersion': minor/patch must be < 100 and rc in 1..98"
    }
    major * 1_000_000 + minor * 10_000 + patch * 100 + (rc ?: 99)
}

// ---------------------------------------------------------------------------------------------
// Signing — one shared key for every build type (see CLAUDE.md). Never regenerate it.
// The key is optional so forks, PRs and Dependabot builds still work: without it, debug builds
// use the default debug key and release builds are unsigned.
// ---------------------------------------------------------------------------------------------
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val sharedKeyFile = rootProject.file(keystoreProps.getProperty("storeFile") ?: "keystore/threads-downloader.jks")
val hasSharedKey = sharedKeyFile.exists() && keystoreProps.getProperty("storePassword") != null

android {
    namespace = "io.github.kevin77688.uvd"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        applicationId = "io.github.kevin77688.uvd"
        minSdk = 29
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersion
    }

    signingConfigs {
        if (hasSharedKey) {
            create("shared") {
                storeFile = sharedKeyFile
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            if (hasSharedKey) signingConfig = signingConfigs.getByName("shared")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (hasSharedKey) signingConfigs.getByName("shared") else null
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // NewPipeExtractor uses java.nio.file APIs that need desugaring below API 33.
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
    }

    androidResources {
        localeFilters += listOf("en", "zh-rTW")
    }

    packaging {
        resources {
            excludes += listOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/*.version",
                "DebugProbesKt.bin",
                "kotlin-tooling-metadata.json",
            )
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.11.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.13.0")

    // YouTube extraction (GPL-3.0). See PLAN.md "v2" for why this is the one non-AndroidX dependency.
    implementation("com.github.teamnewpipe:NewPipeExtractor:v0.27.1")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.5")

    testImplementation("junit:junit:4.13.2")
}
