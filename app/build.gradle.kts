plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.mono.music"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mono.music"
        // Android 8.0. Media3's foreground media service and the storage-access
        // framework both behave consistently from here up.
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "2.0"
        vectorDrawables.useSupportLibrary = true
    }

    // Both build types are signed with a keystore committed to this repo. The
    // point is not secrecy — it is that every APK this project ever produces has
    // the same signature, so a new build installs straight over the previous one
    // instead of forcing an uninstall (which would wipe playlists and likes).
    signingConfigs {
        create("sideload") {
            storeFile = file("mono-sideload.jks")
            storePassword = "monosideload"
            keyAlias = "mono"
            keyPassword = "monosideload"
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("sideload")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("sideload")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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

    lint {
        lintConfig = file("lint.xml")
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.common)

    testImplementation(libs.junit)
}
