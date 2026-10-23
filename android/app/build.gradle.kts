plugins {
    id("com.android.application")
}

// Release signing is read from Gradle properties (e.g. ~/.gradle/gradle.properties)
// so that no key material lives in the repository. Without them, the release
// build is left unsigned.
val releaseStoreFile = providers.gradleProperty("LINGOT_RELEASE_STORE_FILE").orNull

android {
    namespace = "org.nongnu.lingot"
    compileSdk = 36
    ndkVersion = "29.0.14206865"

    defaultConfig {
        applicationId = "org.nongnu.lingot"
        // AAudio input presets require API level 28 (Android 9).
        minSdk = 28
        targetSdk = 36
        // The Android app is versioned independently of the desktop release
        // (tags android-vX.Y.Z). versionCode must increase on every release,
        // otherwise devices refuse to install the update.
        versionCode = 1
        versionName = "0.1.0"

        externalNativeBuild {
            cmake {
                arguments += listOf("-DANDROID_STL=none")
            }
        }
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = providers.gradleProperty("LINGOT_RELEASE_STORE_PASSWORD").get()
                keyAlias = providers.gradleProperty("LINGOT_RELEASE_KEY_ALIAS").get()
                keyPassword = providers.gradleProperty("LINGOT_RELEASE_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (releaseStoreFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "4.1.2"
        }
    }
}
