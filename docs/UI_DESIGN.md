# AURA UI Design

## Identity
- **Base:** `#05060F` near-black with navy `#0A1030` depth.
- **Light:** purple `#7B5CFF`, blue `#3B82FF`, cyan `#22D3EE` (default "Aurora" accent). Three more accents in Settings
  (Ember, Emerald, Mono).
- **Ambience:** three radial colour fields. When a track plays, `Palette` extracts vibrant / dark / light swatches from its
  artwork, normalised in HSV to glow on black, and the fields cross-fade (900 ms tween, draw-phase only).
- **Type:** system sans with tight tracking; a wide-tracked gradient `AURA` wordmark; large display greetings; minimal captions.
- **Text is minimal:** artwork, glass and iconography carry hierarchy.

## Liquid Glass engine (`ui/glass/Glass.kt`)
Compose has no backdrop blur; `Modifier.blur()` blurs a node's *own* content. AURA builds it explicitly:

1. **Scene layer.** `AuraRoot` records the ambient background + screens + Now Playing into one `GraphicsLayer`
   (`drawWithContent { layer.record { drawContent() }; drawLayer(layer) }`).
2. **Backdrop sampling.** Floating glass (`Modifier.glass`) outside the scene re-draws that layer, translated by the
   surface's own `positionInRoot`, into a small per-surface `GraphicsLayer` that carries a `RenderEffect` blur
   (18 dp Balanced / 30 dp High, `TileMode.Clamp`) and a 1.45× saturation `ColorFilter`.
3. **Material paint (cached in `drawWithCache`):** navy base tint, white vertical gradient fill (unchanged from the blur
   look the project owner approved), then the Liquid Glass layers added around it:
   - **Edge refraction (API 33+)** — an AGSL `RuntimeShader` chained *after* the blur. It evaluates a rounded-rect signed
     distance field and pulls the blurred backdrop inward within ~14 dp of the rim (quadratic falloff), with a faint
     R/B dispersion at High quality. Built once per surface; falls back to blur-only if it cannot be created (API 31–32,
     shader failure).
   - **Directional rim light** — 1.1 dp stroke lit at the top-left, reflected at the bottom-right, dark between.
   - **Inner edge glow** — a 6 dp stroke clipped to the shape (only the inner 3 dp shows) = glass thickness cue.
   - **Lens-cap sheen + bounce light** — soft white gradient hugging the top edge, cool bounce along the bottom.
   - **Spectral fringe** — thin orange/magenta/blue gradient on the lower edge of the thickest material (cf. the
     rainbow edge in the reference renders).
   - **Layered drop shadow** on floating surfaces, drawn with a *difference clip* so it never darkens the glass interior.
   Specular bloom and cyan caustic from the original engine are kept.

Three materials:
| Mode | When | Cost |
|---|---|---|
| **Backdrop blur** | nav bar, mini player, menus, dialogs, sheets, toast on API 31+ with quality ≥ Balanced | one RenderEffect layer each |
| **Frosted** (near-opaque tinted) | same surfaces on API < 31, or "Frosted (no blur)" | paint only |
| **Flat glass** | cards, rows, buttons inside scrolling content | paint only (no backdrop sampling) |

Content-level glass deliberately never samples the backdrop: the scene layer is being recorded while content draws, and
blurring dozens of list items would be expensive. Only ~3–4 blurred surfaces are ever on screen.

## Components
- **Floating nav** — glass pill; the lit indicator slides on a bouncy spring and *stretches while in motion*;
  selected icon cross-animates outline → filled with a scale spring.
- **Mini Player** — glass capsule, circular artwork, morphing play/pause glyph, draw-only progress hairline,
  tap or drag up to expand.
- **Now Playing** — artwork-lit background, swipe-to-skip artwork (gesture-driven spring), artwork "breathes" smaller when paused,
  custom seek bar (thickens + thumb while dragging), shuffle/repeat with spring dot indicators, heart pop, Up Next sheet.
- **Overlays** (`ui/components/Overlay.kt`) — menus, dialogs and sheets are drawn *in-tree* (not separate windows)
  so they share the backdrop. Menus anchor to the tapped element, scale from the anchor corner, and stagger items;
  sheets have a velocity-aware drag-to-dismiss handle; dialogs spring-scale in. Exit uses a critically damped spring.
- **Controls** — glass toggle (thumb stretches on press), segmented control with sliding indicator, chips, dropdown pill → menu,
  glass text field with animated focus rim, press-scale buttons (no ripples).

## Animation system (`ui/glass/Motion.kt`)
All motion goes through the `Spr` presets via `spr()` / `sprRaw()`. Generic: `Soft` (0.88/300), `Snappy` (0.78/520),
`Bouncy` (0.52/420), `Critical` (1.0/650). Semantic, one per interaction family: `Nav` (0.70/400), `Screen` (0.82/380),
`Button` (0.60/700), `Jelly` (0.36/520), `Menu` (0.62/520), `Pop` (0.64/420), `Sheet` (0.80/340), `Mini` (0.82/340),
`Artwork` (0.68/320), `Favorite` (0.38/520), `Stretch` (0.50/260). Damping < 1 overshoots; the overshoot is what makes
the motion read as elastic.

| Interaction | What moves | Preset |
|---|---|---|
| Press (any `pressable`) | X and Y relax on *different* springs, so release wobbles (squash-and-stretch); optional press bloom clipped to the control shape | `Jelly` + `Button` |
| Nav indicator | spring position; stretches with distance from its tab; can be grabbed and scrubbed; pops up like a lens while held | `Nav`, `Jelly` |
| Screen change | slide + scale + fade | `Screen` |
| Dropdown / menu | scales from the anchor corner, height stretches faster than width, overshoots, rows stagger, selected check springs in | `Menu`, `Favorite` |
| Dialog / toast | scale-pop with differing X/Y | `Pop` |
| Bottom sheet | finger-tracking; rubber-band + upward stretch when pulled past rest; fling velocity handed to the settle spring; overshoot stretches instead of lifting | `Sheet`, `Stretch` |
| Mini → Now Playing | gesture-driven progress; mini pill stretches upward as it opens; artwork grows out from the bottom; controls reveal in a stagger driven by the same progress | `Mini` |
| Artwork | swipe to skip (stretches while pulled, springs back), breathing scale on play/pause, track-change scale | `Artwork`, `Stretch` |
| Favourite | pop-in with tilt (0.38 damping), small wobble on un-favourite | `Favorite` |

Rule: **any spring-driven value that feeds a range-checked API (Color alpha, gradient stops, radii) is clamped with
`clamp01()`** — springs undershoot below 0.

Reduced motion follows the in-app toggle *and* the OS animator-duration scale, observed live with a `ContentObserver`.
With it on, every preset becomes `snap()`. With **reduced motion** (user toggle *or* system animator scale = 0)
every preset collapses to `snap()`, and the player sheet snaps too.

Used with: `AnimatedContent` (screens, library pages, artwork/title swaps), `AnimatedVisibility` (mini player, toast, search title),
`updateTransition` (overlay enter/exit progress), `Animatable` (player sheet, sheet drag, artwork swipe, heart pop),
`animate*AsState`, and `graphicsLayer` lambdas so animated values are read in the layer/draw phase only.

No infinite animations are used.

## Theme
AURA is a **dark-only** design (the glass engine, ambience and every colour token assume a near-black scene). There is no
light theme; the status/navigation bars are fixed to dark styling.
