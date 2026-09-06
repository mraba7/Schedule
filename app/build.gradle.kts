plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// The signing key lives in GitHub's encrypted secrets, never in the repo:
// CI writes it to this path at build time and the file is git-ignored. The
// password comes from the environment for the same reason. A stable key is
// what lets each build install over the last one instead of beside it.
val sharedKeystore = rootProject.file("keystore/schedule.jks")
val keystorePassword: String = System.getenv("ANDROID_KEYSTORE_PASSWORD") ?: "schedule"

android {
    namespace = "com.mrabah.oneuischedule"
    compileSdk = 35

    signingConfigs {
        create("shared") {
            if (sharedKeystore.exists()) {
                storeFile = sharedKeystore
                storePassword = keystorePassword
                keyAlias = "schedule"
                keyPassword = keystorePassword
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
            // R8 plus resource shrinking takes the APK from ~9.8 MB to ~3 MB
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (sharedKeystore.exists()) {
                signingConfig = signingConfigs.getByName("shared")
            }
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
        buildConfig = true
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

    testImplementation("junit:junit:4.13.2")
}
