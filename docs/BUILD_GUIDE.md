# Build Guide

## Requirements
- JDK 17
- Android SDK with `platforms;android-35` and `build-tools;34.0.0` (AGP downloads build-tools if licences are accepted)
- ~2 GB free RAM for Gradle (`org.gradle.jvmargs=-Xmx2g` in `gradle.properties`; lower it on small machines)

## Local
```bash
export ANDROID_HOME=$HOME/Android/Sdk          # or create local.properties with sdk.dir=...
./gradlew testDebugUnitTest
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
Android Studio: *File ▸ Open* the project root, let Gradle sync, run the `app` configuration.

## GitHub Actions
`.github/workflows/android-build.yml` runs on every push to `main` and on manual *Run workflow*:

1. checkout → JDK 17 (Temurin) → Android SDK (`android-actions/setup-android`)
2. Gradle Wrapper validation (`gradle/actions/wrapper-validation`)
3. `./gradlew testDebugUnitTest`
4. `./gradlew assembleDebug`
5. verify `app/build/outputs/apk/debug/app-debug.apk` exists
6. `aapt2 dump badging` → verify package `com.aura.player` and `versionName 1.0.0`
7. upload artifact **`AURA-debug-apk`** (30-day retention)

No signing secrets are needed: it is a debug-signed APK.

## Permissions at runtime
- Android 13+: `READ_MEDIA_AUDIO` and `POST_NOTIFICATIONS` (playback notification)
- Android ≤ 12: `READ_EXTERNAL_STORAGE`
- Optionally add folders with the system folder picker (Settings ▸ Library); access is persisted.

## Troubleshooting
- *Gradle OOM on small machines:* `./gradlew --max-workers=1 -Dorg.gradle.jvmargs=-Xmx1100m -Pkotlin.compiler.execution.strategy=in-process assembleDebug`
- *"SDK location not found":* create `local.properties` with `sdk.dir=/path/to/Android/Sdk` (never commit it).
