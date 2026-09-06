plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// A fixed signing key committed to the repo. Without it every CI run signs
// with a fresh throwaway key, Android sees a different signature, and each
// build has to be installed as a new app. With it, builds install over
// each other like a normal update.
val sharedKeystore = rootProject.file("keystore/schedule.jks")

android {
    namespace = "com.mrabah.oneuischedule"
    compileSdk = 35

    signingConfigs {
        create("shared") {
            if (sharedKeystore.exists()) {
                storeFile = sharedKeystore
                storePassword = "schedule"
                keyAlias = "schedule"
                keyPassword = "schedule"
            }
        }
    }

    defaultConfig {
        applicationId = "com.mrabah.oneuischedule"
        minSdk = 31          // Monet, widget cornerRadius, java.time — no desugaring needed
        targetSdk = 35
        // every CI build gets a higher versionCode so it installs as an update
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "1." + (System.getenv("GITHUB_RUN_NUMBER") ?: "0")
    }

    buildTypes {
        debug {
            if (sharedKeystore.exists()) {
                signingConfig = signingConfigs.getByName("shared")
            }
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    // Home screen widget
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.glance:glance-material3:1.1.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
