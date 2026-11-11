package com.aura.player.ui.glass

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.aura.player.clamp01

/** True when the user (or the OS animator scale) asks for reduced motion. */
val LocalReduceMotion = staticCompositionLocalOf { false }
val LocalHaptics = staticCompositionLocalOf { true }

/**
 * AURA motion system: the ONE place stiffness / damping live.
 *
 * Every animation in the app resolves to one of these presets, so tuning the "feel" is a one-line change
 * and reduced-motion collapses all of them to snaps in one place ([spr] / [sprRaw]).
 *
 * damping < 1 overshoots. Overshoot of a spring is roughly exp(-pi*z/sqrt(1-z^2)) of the travelled
 * distance: 0.52 => ~15 %, 0.36 => ~30 %, 0.78 => ~2 %. Anything spring-driven that feeds a range-checked
 * API (Color alpha, gradient stops) MUST be clamped with clamp01 - undershoot goes negative.
 */
enum class Spr(val damping: Float, val stiffness: Float) {
    // --- generic vocabulary (kept for existing call sites) ---
    /** Calm settle - ambient, padding. */
    Soft(0.88f, 300f),
    /** Quick but damped - toggles, focus rings. */
    Snappy(0.78f, 520f),
    /** Playful overshoot - generic release. */
    Bouncy(0.52f, 420f),
    /** No overshoot - dismissals, colour fades. */
    Critical(1f, 650f),

    // --- semantic presets, one per interaction family ---
    /** Navigation: tab indicator + screen slides. Controlled, a hint of overshoot. */
    Nav(0.70f, 400f),
    /** Full-screen transitions: nearly critically damped so big surfaces never wobble. */
    Screen(0.82f, 380f),
    /** Buttons, press-in: quick compress. */
    Button(0.60f, 700f),
    /** Buttons, release + squash/stretch partner axis: low damping = jelly wobble. */
    Jelly(0.36f, 520f),
    /** Dropdown / popup menu expansion. */
    Menu(0.62f, 520f),
    /** Dialog / toast pop. */
    Pop(0.64f, 420f),
    /** Bottom sheet settle (also used for velocity hand-off after a fling). */
    Sheet(0.80f, 340f),
    /** Mini Player <-> Now Playing expansion. */
    Mini(0.82f, 340f),
    /** Artwork scale / swipe-back. */
    Artwork(0.68f, 320f),
    /** Favourite heart: expressive but restrained. */
    Favorite(0.38f, 520f),
    /** Rubber-band snap-back after an overscroll stretch. */
    Stretch(0.50f, 260f),
}

@Composable
fun <T> spr(kind: Spr = Spr.Snappy): FiniteAnimationSpec<T> {
    val reduce = LocalReduceMotion.current
    return remember(reduce, kind) {
        if (reduce) snap() else spring(dampingRatio = kind.damping, stiffness = kind.stiffness)
    }
}

fun <T> sprRaw(kind: Spr, reduce: Boolean): FiniteAnimationSpec<T> =
    if (reduce) snap() else spring(dampingRatio = kind.damping, stiffness = kind.stiffness)

/**
 * Spring squash-and-release press feedback + click handling. No ripple: glass uses light, not ink.
 *
 * X and Y relax on springs with DIFFERENT damping/stiffness, so on release the control wobbles
 * (stretches taller while it is still narrow, then settles) instead of scaling uniformly.
 * [lightShape] (optional) paints a soft white bloom over the pressed control, clipped to that shape.
 * Parameters are appended so existing positional calls keep working.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.pressable(
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)? = null,
    scaleTo: Float = 0.94f,
    enabled: Boolean = true,
    lightShape: Shape? = null,
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val sx by animateFloatAsState(if (pressed) scaleTo else 1f, spr(Spr.Jelly), label = "pressX")
    val sy by animateFloatAsState(if (pressed) 1f - (1f - scaleTo) * 1.35f else 1f, spr(Spr.Button), label = "pressY")
    val light by animateFloatAsState(if (pressed) 1f else 0f, spr(Spr.Critical), label = "pressLight")
    val haptic = LocalHapticFeedback.current
    val hapticsOn = LocalHaptics.current
    this
        .graphicsLayer { scaleX = sx; scaleY = sy }
        .then(
            if (lightShape == null) Modifier else Modifier.drawWithContent {
                drawContent()
                val a = clamp01(light)
                if (a > 0.01f) {
                    drawOutline(lightShape.createOutline(size, layoutDirection, this), Color.White, alpha = 0.16f * a)
                }
            },
        )
        .combinedClickable(
            interactionSource = source,
            indication = null,
            enabled = enabled && (onClick != null || onLongClick != null),
            onClick = { onClick?.invoke() },
            onLongClick = onLongClick?.let { lc ->
                {
                    if (hapticsOn) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    lc()
                }
            },
        )
}
