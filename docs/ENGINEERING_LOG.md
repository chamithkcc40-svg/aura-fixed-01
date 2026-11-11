# AURA Engineering Log

## 1. Starting point
No prior AURA source tree was available in the build environment, so this is a **from-scratch implementation** of the
architecture the brief asked to preserve (Media3 + MediaSession service, Room, MediaStore, Paging, favourites, ordered
playlists, history, SAF, permissions). Nothing is fake demo data: an empty library shows an onboarding state, and every list
comes from Room, which is filled from MediaStore / SAF.

## 2. Telegram Android research (DrKLO/Telegram)
Files inspected (raw sources fetched and grepped): `ActionBarPopupWindow.java`, `BottomSheet.java`, `ActionBarLayout.java`,
`RecyclerListView.java`, `Theme.java`, `CubicBezierInterpolator.java`, plus the directory listing of `ui/ActionBar`.

| Telegram technique | Observation | How AURA uses the *idea* (original code) |
|---|---|---|
| `ActionBarPopupWindow` | Popup content animates scale + alpha with an `AnimatorSet`; duration scales with item count (`150 + 16 × visibleCount`); items are inserted into a custom layout, not a stock `PopupMenu`; shadow/background are custom drawables | In-tree glass menu: anchored placement with flip-above fallback, scale-from-anchor-corner via `placeWithLayer`, per-item staggered alpha/translation |
| `BottomSheet` (`ContainerView`) | Own container with `VelocityTracker`, `NestedScrollingParent` integration, dim-behind, `EASE_OUT_QUINT` open interpolator, dismiss decided by drag distance **and** velocity | Sheet with drag handle zone; dismiss by distance or fling velocity (>1400 px/s); spring return; scrim fades with drag |
| `ActionBarLayout` | Hand-rolled navigation stack with its own transition animators, swipe-back, preview mode, decelerate interpolators | Own `Navigator` back stack + `AnimatedContent` springs; `SaveableStateHolder` keeps each screen's scroll state |
| `RecyclerListView` | Subclassed RecyclerView with custom selector drawables, fast scroller, touch/long-press handling | Lazy lists with stable keys + `contentType`-free simple rows; custom press-highlight modifier (no ripple); Paging for the large Songs list |
| `Theme.java` | Central colour registry, runtime-tinted drawables, `ColorMatrix` / `PorterDuff` filters, HSV colour manipulation | Central `AuraColors` / `AuraType`; HSV normalisation of artwork swatches; `ColorFilter.colorMatrix` saturation boost on the glass backdrop |
| Custom `View`s generally | Draw-heavy views with cached paints, minimal view hierarchy | `drawWithCache` for glass (outline path, brushes cached per size); canvas-drawn seek bar, play/pause morph, ambient scene |

**Licensing.** Telegram for Android is **GPL v2 or later** (confirmed from file headers and `LICENSE`). Copying it into AURA would
impose GPL obligations, so **no Telegram source was copied or translated line-by-line**. Only the high-level patterns above were
studied; all AURA code is original and uses Compose APIs. No Telegram branding, layouts or assets are used.

## 3. Architecture decisions
- **Compose only.** Views were considered for the seek bar / list rendering but Compose's `Canvas`, `drawWithCache` and lazy lists
  meet the performance goal without a second UI toolkit. Java interop was not needed. One language: Kotlin.
- **Single process, MediaController.** UI talks to `PlaybackService` through `MediaController`; the service owns ExoPlayer and
  `MediaSession` so the notification / lock screen / Bluetooth controls work and playback survives the activity.
- **Position is not state.** `PlayerState` carries no position; a 4 Hz poll feeds a primitive `MutableLongState` read only inside
  draw lambdas and two small `Text`s → no app-wide recomposition while playing.
- **Queue state separated** from core state; it is rebuilt only on timeline changes.
- **Favorites / history / searches / playlists** are separate Room tables so a library rescan (which replaces track rows per source)
  never loses user data. SAF track ids are stable hashes of the document URI.
- **Paging 3 + Room** for Songs with SQL-side sort & filter (`CASE` ordering, `LIKE`), `cachedIn(viewModelScope)`.
- **Artwork:** custom Coil fetcher: `ContentResolver.loadThumbnail` (API 29+, system cached, sampled), else
  `MediaMetadataRetriever.embeddedPicture` decoded with `inSampleSize`; works for SAF documents too. Memory cache capped at 18 % of heap.
  Placeholder gradient is deterministic per title when a file has no art.
- **Glass engine:** see `UI_DESIGN.md`. Backdrop blur uses `GraphicsLayer` + `RenderEffect` (API 31+) – *not* `Modifier.blur()`.
  API < 31 uses an opaque-ish frosted material.
- **Reduced motion:** user setting or system animator scale 0 → all springs become `snap()`.

## 4. Verification actually performed (in the build sandbox)
| Check | Result |
|---|---|
| JDK 17 (Temurin) + Android SDK 35 + Gradle 8.9 provisioned in sandbox | done |
| Gradle Wrapper generated (`gradlew`, `gradlew.bat`, `gradle-wrapper.jar`, properties) | present |
| Kotlin compile (`compileDebugKotlin`), KSP/Room schema validation of all SQL | **passed** |
| Resource compilation, manifest merge, dexing, packaging | **passed** |
| `testDebugUnitTest` – 7 JVM tests (`UtilsTest`) | **passed (7/7)** |
| `assembleDebug` | **passed**; `app/build/outputs/apk/debug/app-debug.apk` (~21 MB) produced |
| `aapt2 dump badging` | package `com.aura.player`, versionName `1.0.0`, minSdk 26, targetSdk 35, launchable `MainActivity` |
| Workflow YAML parsed with PyYAML | valid |
| Fresh-extract rebuild from the delivered ZIP using the bundled wrapper | see final response |

## 5. NOT verified (be honest)
- **No physical device or emulator test was possible.** Runtime behaviour, visual fidelity of the glass, frame rate and
  smoothness on a Samsung Galaxy A06-class device are **unverified**. Performance work is by design (see below), not by measurement.
- Backdrop-blur correctness relies on `GraphicsLayer`/`RenderEffect` behaviour on real GPUs; verify on API 31+ hardware.
- MediaStore thumbnails, SAF scanning of large trees, and the notification/lock-screen UI were not exercised at runtime.
- GitHub Actions run itself was not executed (no push access); the workflow mirrors the commands that passed locally.
- No screenshots exist because nothing was rendered.

## 6. Performance design (not measured)
- Only ~3–4 blurred surfaces on screen; list/card glass is paint-only. Quality setting can turn blur off.
- `drawWithCache` caches paths/brushes; `graphicsLayer {}` lambdas read animated state in the layer phase.
- Ambient colours animate in the draw phase; no infinite animations.
- Artwork requested at the display size; bounded memory cache; Palette from a 96 px decode, cached in an LRU.
- Lazy lists use stable keys; Songs are paged; playback progress isolated.
- No work on the main thread: scans, SQL, artwork decode and palette run on IO/Default dispatchers.

## 7. Known limitations
- Debug build only is produced by CI; the release type (R8 on) has not been built or tested.
- No equalizer, sleep timer, lyrics, or gapless-settings UI.
- SAF scanning reads tags with `MediaMetadataRetriever` (slow for very large folders; runs in the background).
- Queue is not persisted across process death.
- Frosted (non-blur) fallback on API 26–30.
- Shared-element transition between mini player and Now Playing is approximated (sheet expansion + artwork scale/fade), not a pixel-matched shared element.

## 8. Iteration 2 — crash hardening, Liquid Glass upgrade, spring system (this release)

**Environment limits (be honest).** This iteration was done in a sandbox with **no Android SDK, no device/emulator and no
access to Google Maven / Gradle plugin portal**. Consequently:
- The project was **not compiled with Gradle** and **no APK was built** in this session.
- Kotlin sources were checked with a standalone Kotlin 2.0.20 compiler for *syntax only* (library types are unresolved
  there); the only reported parse-class finding is a pre-existing false positive caused by the missing libraries.
- Pure logic added to `Utils.kt` (`clamp01`, `progressFraction`, `seekTarget`, `rubberBand`, `fitSquare`,
  `buildCrashReport`) **was executed** with a small harness and passed; the equivalent JUnit tests were added to
  `UtilsTest.kt` but not run through Gradle.
- Nothing about rendering, frame rate or the crash fix was observed on a device.

Changes:
| Area | Change |
|---|---|
| Crash | see `CRASH_ANALYSIS.md`; crash recorder (`diag/CrashLog.kt`) + Settings ▸ Diagnostics |
| Blur | **kept**: same layer record, radii, saturation, base tint and fill alphas |
| Glass | AGSL edge refraction (API 33+), directional rim, inner glow, sheen/bounce, spectral fringe, layered shadow |
| Motion | semantic spring presets; squash-and-stretch press; jelly menus; stretchy sheets; scrubbable nav lens; hero Mini→Now Playing |
| Now Playing | landscape two-pane layout, never-0×0 artwork, glass transport keys, staggered reveal |
| Build | **added the missing `gradle/wrapper/gradle-wrapper.jar`** (it was absent from the supplied ZIP, so `./gradlew` and the wrapper-validation CI step could not work); removed `local.properties` and `.gradle/` |
| Reduced motion | OS animator-scale is now observed live (was read once) |

Not done: light theme (AURA is dark-only by design); instrumentation tests (none existed, none added, nothing could run them).
