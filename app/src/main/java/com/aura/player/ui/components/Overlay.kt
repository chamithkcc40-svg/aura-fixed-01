package com.aura.player.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.aura.player.ui.glass.GlassLevel
import com.aura.player.ui.glass.LocalReduceMotion
import com.aura.player.ui.glass.Spr
import com.aura.player.rubberBand
import com.aura.player.ui.glass.glass
import com.aura.player.ui.glass.spr
import com.aura.player.ui.glass.sprRaw
import com.aura.player.ui.theme.AuraColors
import com.aura.player.ui.theme.AuraType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/*
 * Overlay system. Menus, dialogs and sheets are drawn IN the composition tree (not in separate
 * Popup/Dialog windows) so they can sample the same backdrop layer as the nav bar and get true
 * background-aware glass. Pattern informed by Telegram's ActionBarPopupWindow / BottomSheet
 * (anchored scale+alpha popups with per-item staggering, velocity-based sheet dismissal) –
 * re-implemented from scratch with Compose springs.
 */

data class MenuItemSpec(
    val label: String,
    val icon: ImageVector? = null,
    val danger: Boolean = false,
    val checked: Boolean = false,
    val onClick: () -> Unit,
)

enum class OverlayKind { Menu, Dialog, Sheet }

class OverlayEntry(
    val id: Long,
    val kind: OverlayKind,
    val anchor: Rect,
    val content: @Composable (dismiss: () -> Unit, progress: () -> Float) -> Unit,
)

@Stable
class OverlayController {
    val entries = mutableStateListOf<OverlayEntry>()
    var toast by mutableStateOf<String?>(null)
        private set
    private var seq = 0L

    fun menu(anchor: Rect, items: List<MenuItemSpec>) {
        entries += OverlayEntry(seq++, OverlayKind.Menu, anchor) { dismiss, p -> MenuContent(items, dismiss, p) }
    }

    fun dialog(content: @Composable (dismiss: () -> Unit) -> Unit) {
        entries += OverlayEntry(seq++, OverlayKind.Dialog, Rect.Zero) { d, _ -> content(d) }
    }

    fun sheet(content: @Composable (dismiss: () -> Unit) -> Unit) {
        entries += OverlayEntry(seq++, OverlayKind.Sheet, Rect.Zero) { d, _ -> content(d) }
    }

    fun showToast(message: String) { toast = message }
    internal fun clearToast() { toast = null }
    internal fun remove(id: Long) { entries.removeAll { it.id == id } }
}

@Composable
fun OverlayHost(ctrl: OverlayController) {
    ctrl.entries.forEach { e ->
        key(e.id) { OverlayShell(e) { ctrl.remove(e.id) } }
    }
    ToastHost(ctrl)
}

@Composable
private fun ToastHost(ctrl: OverlayController) {
    val msg = ctrl.toast
    LaunchedEffect(msg) {
        if (msg != null) { delay(2200); ctrl.clearToast() }
    }
    var last by remember { mutableStateOf("") }
    if (msg != null) last = msg
    Box(Modifier.fillMaxSize().statusBarsPadding().padding(top = 12.dp), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = msg != null,
            enter = fadeIn(sprRaw(Spr.Snappy, LocalReduceMotion.current)) + scaleIn(sprRaw(Spr.Pop, LocalReduceMotion.current), initialScale = 0.8f),
            exit = fadeOut() + scaleOut(targetScale = 0.9f),
        ) {
            Box(Modifier.glass(CircleShape, GlassLevel.Strong).padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text(last, style = AuraType.Body, color = AuraColors.TextPrimary)
            }
        }
    }
}

@Composable
private fun OverlayShell(entry: OverlayEntry, onRemove: () -> Unit) {
    val ts = remember { MutableTransitionState(false).apply { targetState = true } }
    val dismiss = remember { { ts.targetState = false } }
    val t = updateTransition(ts, label = "overlay")
    val reduce = LocalReduceMotion.current
    val pState = t.animateFloat(
        transitionSpec = {
            if (targetState) {
                sprRaw(
                    when (entry.kind) {
                        OverlayKind.Menu -> Spr.Menu
                        OverlayKind.Dialog -> Spr.Pop
                        OverlayKind.Sheet -> Spr.Sheet
                    },
                    reduce,
                )
            } else sprRaw(Spr.Critical, reduce)
        },
        label = "p",
    ) { if (it) 1f else 0f }
    val progress = { pState.value }

    LaunchedEffect(ts.currentState, ts.targetState) {
        if (!ts.currentState && !ts.targetState) onRemove()
    }
    BackHandler(enabled = ts.targetState) { dismiss() }

    when (entry.kind) {
        OverlayKind.Menu -> MenuShell(entry, dismiss, progress)
        OverlayKind.Dialog -> DialogShell(entry, dismiss, progress)
        OverlayKind.Sheet -> SheetShell(entry, dismiss, progress)
    }
}

@Composable
private fun MenuShell(entry: OverlayEntry, dismiss: () -> Unit, p: () -> Float) {
    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { dismiss() } }) {
        Layout(
            modifier = Modifier.fillMaxSize(),
            content = {
                Box(
                    Modifier
                        .glass(RoundedCornerShape(24.dp), GlassLevel.Strong)
                        .pointerInput(Unit) { detectTapGestures { } },
                ) { entry.content(dismiss, p) }
            },
        ) { measurables, c ->
            val margin = 12.dp.roundToPx()
            val gap = 8.dp.roundToPx()
            val w = 236.dp.roundToPx().coerceAtMost(c.maxWidth - 2 * margin)
            val pl = measurables[0].measure(Constraints(minWidth = w, maxWidth = w, maxHeight = c.maxHeight - 2 * margin))
            val a = entry.anchor
            layout(c.maxWidth, c.maxHeight) {
                val x = (a.right.roundToInt() - pl.width).coerceIn(margin, (c.maxWidth - pl.width - margin).coerceAtLeast(margin))
                val below = a.bottom.roundToInt() + gap
                val fitsBelow = below + pl.height + margin <= c.maxHeight
                val y = if (fitsBelow) below else (a.top.roundToInt() - gap - pl.height).coerceAtLeast(margin)
                val ox = ((a.center.x - x) / pl.width).coerceIn(0f, 1f)
                val oy = if (fitsBelow) 0f else 1f
                pl.placeWithLayer(x, y) {
                    val v = p()
                    // Jelly expansion: height stretches out from the anchor faster than width, and both
                    // overshoot slightly past 1 before the spring settles (Spr.Menu < critical damping).
                    scaleX = 0.72f + 0.28f * v
                    scaleY = 0.38f + 0.62f * v
                    alpha = v.coerceIn(0f, 1f)
                    transformOrigin = TransformOrigin(ox, oy)
                }
            }
        }
    }
}

@Composable
private fun MenuContent(items: List<MenuItemSpec>, dismiss: () -> Unit, p: () -> Float) {
    Column(Modifier.padding(8.dp)) {
        items.forEachIndexed { i, item ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    // Telegram-style stagger: each row eases in slightly after the previous.
                    .graphicsLayer {
                        val a = ((p() - i * 0.05f) / 0.6f).coerceIn(0f, 1f)
                        alpha = a
                        translationY = (1f - a) * 8.dp.toPx()
                    }
                    .highlightClick(RoundedCornerShape(16.dp), onClick = { dismiss(); item.onClick() })
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val tint = if (item.danger) AuraColors.Danger else AuraColors.TextPrimary
                if (item.icon != null) Icon(item.icon, null, tint = tint, modifier = Modifier.size(20.dp))
                Text(item.label, style = AuraType.Body, color = tint, modifier = Modifier.weight(1f), maxLines = 1)
                if (item.checked) {
                    var shown by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) { shown = true }
                    val cs by animateFloatAsState(if (shown) 1f else 0f, spr(Spr.Favorite), label = "menuCheck")
                    Icon(
                        Icons.Rounded.Check, null, tint = AuraColors.Cyan,
                        modifier = Modifier.size(18.dp).graphicsLayer { scaleX = cs; scaleY = cs },
                    )
                }
            }
        }
    }
}

@Composable
private fun DialogShell(entry: OverlayEntry, dismiss: () -> Unit, p: () -> Float) {
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind { drawRect(Color.Black.copy(alpha = 0.55f * p().coerceIn(0f, 1f))) }
            .pointerInput(Unit) { detectTapGestures { dismiss() } }
            .imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .padding(28.dp)
                .widthIn(max = 340.dp)
                .graphicsLayer {
                    val v = p()
                    scaleX = 0.88f + 0.12f * v
                    scaleY = 0.80f + 0.20f * v
                    alpha = v.coerceIn(0f, 1f)
                }
                .glass(RoundedCornerShape(32.dp), GlassLevel.Strong)
                .pointerInput(Unit) { detectTapGestures { } },
        ) { entry.content(dismiss, p) }
    }
}

@Composable
private fun SheetShell(entry: OverlayEntry, dismiss: () -> Unit, p: () -> Float) {
    val drag = remember { Animatable(0f) }
    var raw by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val reduce = LocalReduceMotion.current
    val density = LocalDensity.current
    val dismissDistance = with(density) { 120.dp.toPx() }
    val stretchLimit = with(density) { 56.dp.toPx() }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .drawBehind {
                val fade = (1f - drag.value / size.height).coerceIn(0f, 1f)
                drawRect(Color.Black.copy(alpha = 0.5f * p().coerceIn(0f, 1f) * fade))
            }
            .pointerInput(Unit) { detectTapGestures { dismiss() } },
        contentAlignment = Alignment.BottomCenter,
    ) {
        val maxH = maxHeight * 0.78f
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = maxH)
                .graphicsLayer {
                    val d = drag.value
                    val v = p()
                    // Open/close travel is clamped to the screen edge: spring overshoot (v > 1) becomes an upward
                    // STRETCH from the bottom edge instead of lifting the sheet and exposing a gap beneath it.
                    translationY = (1f - v.coerceAtMost(1f)) * size.height + d.coerceAtLeast(0f)
                    transformOrigin = TransformOrigin(0.5f, 1f)
                    val pull = (-d).coerceAtLeast(0f)
                    scaleY = 1f + (pull / size.height.coerceAtLeast(1f)) + (v - 1f).coerceAtLeast(0f) * 0.6f
                }
                .glass(RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp), GlassLevel.Strong)
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            // Drag handle zone: finger-tracking, rubber-band when pulled up, velocity hand-off into the settle spring.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { d ->
                            raw += d
                            val target = if (raw < 0f) -rubberBand(-raw, stretchLimit) else raw
                            scope.launch { drag.snapTo(target) }
                        },
                        onDragStopped = { v ->
                            raw = 0f
                            if (drag.value > dismissDistance || v > 1400f) dismiss()
                            else scope.launch { drag.animateTo(0f, sprRaw(Spr.Sheet, reduce), initialVelocity = v) }
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(width = 40.dp, height = 5.dp).glass(CircleShape, GlassLevel.Strong))
            }
            Box(Modifier.weight(1f, fill = false).navigationBarsPadding()) { entry.content(dismiss, p) }
        }
    }
}

// ---------- dialog building blocks ----------

@Composable
fun GlassAlert(
    title: String,
    message: String,
    confirmLabel: String,
    danger: Boolean = false,
    dismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = AuraType.Title, color = AuraColors.TextPrimary)
        Text(message, style = AuraType.Body, color = AuraColors.TextSecondary)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SoftButton("Cancel", onClick = dismiss, modifier = Modifier.weight(1f))
            PrimaryButton(
                confirmLabel, onClick = { dismiss(); onConfirm() }, modifier = Modifier.weight(1f),
                danger = danger,
            )
        }
    }
}

@Composable
fun GlassPrompt(
    title: String,
    initial: String = "",
    placeholder: String,
    confirmLabel: String,
    dismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(title, style = AuraType.Title, color = AuraColors.TextPrimary)
        GlassTextField(text, { text = it }, placeholder, onSearch = { if (text.isNotBlank()) { dismiss(); onSubmit(text) } }, autoFocus = true)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SoftButton("Cancel", onClick = dismiss, modifier = Modifier.weight(1f))
            PrimaryButton(confirmLabel, onClick = { if (text.isNotBlank()) { dismiss(); onSubmit(text) } }, modifier = Modifier.weight(1f))
        }
    }
}
