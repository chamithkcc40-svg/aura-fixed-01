# AURA — APK Build Guide (GitHub upload → debug APK)

> **Read first.** This release was edited in an environment that could not run Gradle or a device. The code has **not been
> compiled or run** by me. The first GitHub Actions run is therefore the first real compile. If it fails, copy the red
> error lines from the log and send them — it will be a small, local fix.

## 1. Upload to GitHub (no git needed)
1. Unzip `AURA-Liquid-Glass-Fixed.zip`. The files are at the **root** of the ZIP (no wrapper folder).
2. On GitHub create a repository (or open your existing AURA repo).
3. *Add file ▸ Upload files* → drag **everything inside the unzipped folder**. Make sure these arrive:
   - `.github/workflows/android-build.yml` (folders starting with a dot are sometimes hidden in file managers — enable
     "show hidden files"; if the browser upload skips it, use *Add file ▸ Create new file*, name it
     `.github/workflows/android-build.yml`, paste the file contents)
   - `gradle/wrapper/gradle-wrapper.jar` and `gradle/wrapper/gradle-wrapper.properties`
   - `gradlew`, `gradlew.bat`, `settings.gradle.kts`, `build.gradle.kts`, `app/…`
4. Commit to the `main` branch.

## 2. Build
- Pushing to `main` starts **Actions ▸ Android Build** automatically; or *Run workflow* manually.
- Steps: JDK 17 → Android SDK → Gradle wrapper validation → unit tests → `assembleDebug` → verify APK → upload.
- ~6–12 minutes the first time.

## 3. Download the APK
Actions ▸ the finished run ▸ **Artifacts ▸ `AURA-debug-apk`** → unzip → `app-debug.apk` → install on the phone
(allow "install unknown apps").

## 4. Local build (optional)
```bash
# JDK 17 + Android SDK platform 35
./gradlew testDebugUnitTest assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 5. If Now Playing still crashes
1. Relaunch AURA ▸ **Settings ▸ Diagnostics ▸ Last crash report ▸ Copy**, and send that text; or
2. `adb logcat -b crash -d | tail -60` right after the crash.
That trace is the ground truth; see `docs/CRASH_ANALYSIS.md`.

## 6. Things to check on the phone (nothing here was observed by me)
- Open Now Playing: playing / paused / right after tapping a song / track without artwork / after skipping / rotated.
- Blur still looks like before on nav bar and Mini Player.
- Android 13+ phones also show the AGSL edge refraction; Android 12 shows blur + rim/inner-glow only.
- Springs: press a button (squash then wobble), drag the nav indicator, open a dropdown in Settings, drag the Mini
  Player up slowly (it stretches), pull a bottom sheet up past its rest position (rubber band).
- Settings ▸ *Reduce motion* turns every spring into a snap.
- AURA is dark-only; there is no light theme.

## Troubleshooting
- *"Could not find or load main class org.gradle.wrapper.GradleWrapperMain"* → `gradle/wrapper/gradle-wrapper.jar`
  was not uploaded.
- *Wrapper validation fails* → the jar was altered; re-extract it from the ZIP.
- *Gradle OOM* → see `docs/BUILD_GUIDE.md`.
