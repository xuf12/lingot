# Lingot for Android

An Android port of Lingot. The tuner core (`../src/lingot-core.c`, FFT, filters,
scales, ...) is shared with the desktop application and compiled with the NDK;
audio is captured through a new AAudio backend (`../src/lingot-audio-aaudio.c`),
and the GTK interface is replaced by a native view (gauge, note, frequency,
error and spectrum) driven through JNI (`app/src/main/cpp/lingot-jni.c`).

Requires Android 9 (API 28) or later.

## Building

Requirements: JDK 17 or later, the Android SDK with platform 36, NDK
29.0.14206865 and CMake 4.1.2 (Android Studio or `sdkmanager` will install
them; the Gradle plugin also downloads missing SDK components on its own).

```sh
cd android
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties   # or set ANDROID_HOME
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Install it
on a connected device or emulator with:

```sh
./gradlew installDebug
# or: adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The project can also be opened directly in Android Studio (open the
`android/` folder).

## Releasing

The Android app is versioned independently of the desktop application, with
`android-vX.Y.Z` tags. For each release:

1. Bump `versionName` and increment `versionCode` in `app/build.gradle.kts`
   (devices refuse an update whose `versionCode` is not higher).
2. Build the signed APK with `./gradlew assembleRelease`; it is written to
   `app/build/outputs/apk/release/app-release.apk`.
3. Publish it as an asset of a GitHub release (APKs are not committed).

Release signing reads these Gradle properties, normally kept in
`~/.gradle/gradle.properties` outside the repository:

```properties
LINGOT_RELEASE_STORE_FILE=/path/to/lingot-release.jks
LINGOT_RELEASE_STORE_PASSWORD=...
LINGOT_RELEASE_KEY_ALIAS=lingot
LINGOT_RELEASE_KEY_PASSWORD=...
```

Without them the release APK is left unsigned. Every update must be signed
with the same key, otherwise users have to uninstall the app first, so keep
a backup of the keystore.

## Usage

On first launch the app asks for microphone permission. Pick the instrument
range from the selector at the top (guitar, bass, violin/ukulele, or a wide
C2–C6 range); the gauge spans one semitone (±50 cents) and turns green within
±5 cents of the closest note. Tuning stops while the app is in the background.

## Differences with the desktop version

- Only the default input device is used, at 48 kHz.
- The advanced settings of the desktop configuration dialog (FFT size,
  temporal window, SNR thresholds, custom scales) are not exposed; the desktop
  defaults are used.
- The strobe-disc view is not available.
