# AURA — Liquid Glass Music Player for Android

AURA is a native Android music player (Kotlin + Jetpack Compose + Media3) with a custom **Liquid Glass**
visual identity: deep navy/black scenes lit by purple, blue and cyan ambience derived from the current artwork,
floating translucent glass surfaces with real backdrop blur (Android 12+), illuminated edges, and a
centralized spring animation system.

It plays **your real music library** — no demo data.

| Area | Implementation |
|---|---|
| Playback | Media3 `ExoPlayer` inside a `MediaSessionService` (notification, lock screen, headset controls), `MediaController` in the UI |
| Library | `MediaStore` scan **and** SAF folder access (persisted tree permissions) |
| Storage | Room: tracks, favorites, playlists + ordered entries, play history, recent searches |
| Lists | Paging 3 (`Pager` + Room `PagingSource`) for the Songs list, lazy lists with stable keys elsewhere |
| Artwork | Custom Coil `Fetcher` (`loadThumbnail` / embedded picture) + `Palette` → ambient colours |
| UI | Compose only; custom glass engine, overlay system (menus / dialogs / sheets), spring motion presets |

## Screens
Home · Library (Albums / Artists / Songs / Playlists / Favorites) · Search · Playlists · Album / Artist / Playlist detail ·
Mini Player · Now Playing (+ Queue sheet) · Settings.

## Build
See [`docs/BUILD_GUIDE.md`](docs/BUILD_GUIDE.md). Short version:

```bash
./gradlew testDebugUnitTest assembleDebug
# -> app/build/outputs/apk/debug/app-debug.apk
```

Or push to GitHub: the workflow in `.github/workflows/android-build.yml` builds the debug APK and uploads it as the
`AURA-debug-apk` artifact.

Requirements: JDK 17, Android SDK platform 35. `minSdk` 26, `targetSdk` 35, package `com.aura.player`.

## Docs
- [`docs/UI_DESIGN.md`](docs/UI_DESIGN.md) — visual system, glass engine, animation system
- [`docs/ENGINEERING_LOG.md`](docs/ENGINEERING_LOG.md) — Telegram source research, decisions, verification status, limitations
- [`docs/BUILD_GUIDE.md`](docs/BUILD_GUIDE.md) — local and CI builds
- [`docs/CRASH_ANALYSIS.md`](docs/CRASH_ANALYSIS.md) — Now Playing crash analysis, fixes, and how to capture a trace
- [`AURA-APK-Build-Guide.md`](AURA-APK-Build-Guide.md) — step-by-step: GitHub upload → APK

## Source layout
```
app/src/main/java/com/aura/player/
  AuraApp.kt  MainActivity.kt  Utils.kt
  data/        Room entities/DAO, MediaStore + SAF scanner, repository, settings
  playback/    PlaybackService (MediaSession), PlayerController (MediaController wrapper)
  art/         Coil fetcher/keyer, palette extraction
  ui/theme/    colours + typography
  diag/        CrashLog (records the last uncaught exception; shown in Settings > Diagnostics)
  ui/glass/    Glass.kt (material engine), Motion.kt (spring presets, press feedback)
  ui/components/  Controls, Overlay (menus/dialogs/sheets), Media (artwork, rows, cards)
  ui/screens/  Home, Library, Search, Playlists, Collection (album/artist/playlist), NowPlaying + MiniPlayer, Settings, Sheets
  ui/          AuraRoot (scene + floating layer), AuraEnv (navigation, player sheet, shared actions)
```
