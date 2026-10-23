plugins {
    id("com.android.application")
}

android {
    namespace = "org.nongnu.lingot"
    compileSdk = 36
    ndkVersion = "29.0.14206865"

    defaultConfig {
        applicationId = "org.nongnu.lingot"
        // AAudio input presets require API level 28 (Android 9).
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.1.2"

        externalNativeBuild {
            cmake {
                arguments += listOf("-DANDROID_STL=none")
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
