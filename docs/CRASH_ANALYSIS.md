# Now Playing crash — analysis and fixes

## Honest status
The crash was reported on a real phone. **It could not be reproduced here**: the build sandbox has no Android
device/emulator and cannot reach Google Maven or the Android SDK, so the project could not even be compiled in this
session. That means **no stack trace was captured** and the single root-cause line cannot be quoted with certainty.

What *was* done instead, in order of confidence:

1. Every code path that runs when Now Playing opens was read line by line.
2. Defects that can throw on that path (or on adjacent paths) were identified from the library contracts, and fixed.
3. A crash recorder was added so that, if anything still crashes, the **real** stack trace is captured
   (Settings ▸ Diagnostics ▸ Last crash report ▸ Copy, or `adb logcat -b crash`).

## Defects found and fixed (all verified by reading code, none by running it)

| # | Defect | Where | Why it throws | Fix |
|---|---|---|---|---|
| 1 | **Zero-size glass node** — `Brush.radialGradient(radius = max(w,h) * k)` | `Glass.kt` | `android.graphics.RadialGradient` throws `IllegalArgumentException: radius must be > 0` when a node is 0×0. Now Playing sized its artwork with `fillMaxHeight().aspectRatio(1f)` inside a `weight(1f)` slot; when the slot has no height left (landscape, small/split-screen windows, large font scale) the artwork collapses to 0×0 and the glass modifier on it crashes. | `glass()` draws nothing for nodes < 2 px; Now Playing sizes artwork with `fitSquare()` and skips it if the slot is < 72 dp; landscape gets its own two-pane layout. |
| 2 | **Spring undershoot into `Color.copy(alpha)`** | `Controls.kt` (`GlassChip`), focus ring, press highlight | Springs overshoot. A chip released to `0f` dips slightly below 0, and `Color.copy(alpha < 0)` throws `IllegalArgumentException` (alpha must be in 0..1). Not on the Now Playing path, but it is the same class of bug and would crash the Library chips. | Every spring-driven alpha goes through `clamp01()`. |
| 3 | **Queue emptied while Now Playing is open** | `NowPlaying.kt` | `visible && ps.current != null` hid the content but left `sheet.open == true` and a non-zero progress: the player was "open" with nothing to show and no back handler. | `LaunchedEffect(hasTrack)` collapses the sheet. |
| 4 | **Unprotected `MediaController` access** | `PlayerController.kt` | Player listener callbacks and queued commands ran unguarded on the main looper; any `IllegalStateException`/`IndexOutOfBounds` from a transitional timeline (empty queue, index `C.INDEX_UNSET`) would be fatal. A failed connection was swallowed silently and never retried. | All commands/refreshes wrapped; index normalised (`-1` when no item); `jumpTo` range-checked; connection retried twice and logged. |
| 5 | **Unsafe progress math** | `NowPlaying.kt` | Seek fraction divided by an unknown duration and by `size.width` without guards. | `progressFraction()` / `seekTarget()` (unit-tested). |
| 6 | **Sheet overshoot exposed a gap** | `NowPlaying.kt`, `Overlay.kt` | Spring overshoot (> 1) moved the sheet above its resting position. | Travel clamped; overshoot becomes a stretch. |
| 7 | **Dismiss threshold mixed dp and px** | `Overlay.kt` | `140.dp.value * 2.6f` compared dp numerals against pixels. | Real density conversion. |

## Most likely cause of the reported crash
Items 1 and 4 are the only ones that can fire on a plain portrait "open the player" tap. Neither is *proven* to be the
reporter's crash. If the crash persists after this build, open Settings ▸ Diagnostics ▸ Last crash report ▸ Copy
and send that text: it is the ground truth.

## Test matrix (requested) — status
All ten scenarios are **unverified at runtime** (no device). The code path for each was reviewed:

| Scenario | Reviewed outcome |
|---|---|
| Open while playing / paused / right after selecting | state read from `PlayerState`; no null deref; controls guarded |
| Artwork unavailable | Coil converts fetcher failures into `ErrorResult`; placeholder gradient is shown |
| Empty/unavailable queue | `ps.current == null` ⇒ layer not composed; sheet auto-collapses |
| Switch screen and return / close and reopen | sheet state lives in `PlayerSheet`; no per-open one-shot state |
| Next / previous | `seekToNextMediaItem` / `seekToPreviousMediaItem` wrapped by `safely()` |
| Rotate / recreate | `configChanges` keeps the activity; landscape layout added; artwork never 0×0 |

## How to capture the real trace if it still crashes
```
adb logcat -c
# open the player
adb logcat -b crash -d | tail -60
```
or Settings ▸ Diagnostics ▸ Last crash report (shown after relaunch).
