package com.aura.player.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import com.aura.player.clamp01
import com.aura.player.ui.LocalAccent
import com.aura.player.ui.glass.GlassLevel
import com.aura.player.ui.glass.LocalReduceMotion
import com.aura.player.ui.glass.Spr
import com.aura.player.ui.glass.glass
import com.aura.player.ui.glass.pressable
import com.aura.player.ui.glass.spr
import com.aura.player.ui.theme.AuraColors
import com.aura.player.ui.theme.AuraType
import kotlin.math.roundToInt

/** Light-press highlight used by list rows and menu items (no ripple, no default Android feedback). */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.highlightClick(shape: Shape, onClick: () -> Unit, onLongClick: (() -> Unit)? = null): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val a by animateFloatAsState(if (pressed) 0.14f else 0f, spr(Spr.Critical), label = "hl")
    val s by animateFloatAsState(if (pressed) 0.98f else 1f, spr(Spr.Jelly), label = "hs")
    this
        .graphicsLayer { scaleX = s; scaleY = s }
        .clip(shape)
        .drawBehind { drawRect(Color.White.copy(alpha = clamp01(a))) }
        .combinedClickable(src, null, onClick = onClick, onLongClick = onLongClick)
}

/** Captures an element's bounds so overlays can anchor to it. */
class AnchorState {
    var rect: Rect = Rect.Zero
    val modifier: Modifier = Modifier.onGloballyPositioned { rect = it.boundsInRoot() }
}

@Composable
fun rememberAnchor() = remember { AnchorState() }

@Composable
fun GlassIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
    tint: Color = AuraColors.TextPrimary,
    level: GlassLevel = GlassLevel.Regular,
) {
    Box(
        modifier.size(size).glass(CircleShape, level).pressable(onClick, scaleTo = 0.88f, lightShape = CircleShape),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(iconSize)) }
}

@Composable
fun PlainIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    iconSize: Dp = 22.dp,
    tint: Color = AuraColors.TextSecondary,
) {
    Box(modifier.size(size).pressable(onClick, scaleTo = 0.85f), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    danger: Boolean = false,
) {
    val accent = LocalAccent.current
    val brush = if (danger) Brush.linearGradient(listOf(Color(0xFFFF5C7A), Color(0xFFD9366A)))
    else Brush.linearGradient(listOf(accent.a, accent.b))
    Row(
        modifier
            .height(48.dp)
            .pressable(onClick, scaleTo = 0.93f, lightShape = CircleShape)
            .clip(CircleShape)
            .background(brush)
            .glass(CircleShape, GlassLevel.Subtle)
            .padding(horizontal = 22.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
        Text(text, style = AuraType.Body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = Color.White, maxLines = 1)
    }
}

@Composable
fun SoftButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Row(
        modifier.height(48.dp).glass(CircleShape, GlassLevel.Regular).pressable(onClick, scaleTo = 0.93f, lightShape = CircleShape).padding(horizontal = 22.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, null, tint = AuraColors.TextPrimary, modifier = Modifier.size(20.dp))
        Text(text, style = AuraType.Body, color = AuraColors.TextPrimary, maxLines = 1)
    }
}

@Composable
fun GlassChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    val fillAnim by animateFloatAsState(if (selected) 1f else 0f, spr(Spr.Snappy), label = "chip")
    // Springs overshoot: released to 0 this dips below 0, and Color.copy(alpha < 0) throws IllegalArgumentException.
    val fillA = clamp01(fillAnim)
    val txt by animateColorAsState(if (selected) Color.White else AuraColors.TextSecondary, spr(Spr.Critical), label = "chipT")
    Box(
        modifier
            .height(38.dp)
            .pressable(onClick, scaleTo = 0.92f, lightShape = CircleShape)
            .clip(CircleShape)
            .drawBehind {
                drawRect(Brush.linearGradient(listOf(accent.a.copy(alpha = 0.9f * fillA), accent.b.copy(alpha = 0.9f * fillA))))
            }
            .glass(CircleShape, GlassLevel.Subtle)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = AuraType.Body, color = txt, maxLines = 1) }
}

@Composable
fun ChipRow(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 20.dp)) {
        itemsIndexed(options) { i, label -> GlassChip(label, i == selected, { onSelect(i) }) }
    }
}

/** Segmented control with a spring-driven sliding glass indicator. */
@Composable
fun GlassSegmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val pos by animateFloatAsState(selected.toFloat(), spr(Spr.Bouncy), label = "seg")
    BoxWithConstraints(modifier.height(44.dp).glass(CircleShape, GlassLevel.Subtle).padding(4.dp)) {
        val segW = maxWidth / options.size
        Box(
            Modifier
                .offset { IntOffset((segW.toPx() * pos).roundToInt(), 0) }
                .width(segW)
                .fillMaxHeight()
                .glass(CircleShape, GlassLevel.Strong),
        )
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, label ->
                Box(Modifier.weight(1f).fillMaxHeight().pressable({ onSelect(i) }, scaleTo = 0.96f), contentAlignment = Alignment.Center) {
                    Text(label, style = AuraType.Body, color = if (i == selected) AuraColors.TextPrimary else AuraColors.TextSecondary, maxLines = 1)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GlassToggle(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    val prog by animateFloatAsState(if (checked) 1f else 0f, spr(Spr.Bouncy), label = "tog")
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val thumbW by animateFloatAsState(if (pressed) 32f else 26f, spr(Spr.Snappy), label = "thumbW")
    Box(
        modifier
            .size(width = 56.dp, height = 32.dp)
            .clip(CircleShape)
            .drawBehind {
                drawRect(Brush.linearGradient(listOf(accent.a.copy(alpha = 0.95f * prog.coerceIn(0f, 1f)), accent.c.copy(alpha = 0.9f * prog.coerceIn(0f, 1f)))))
            }
            .glass(CircleShape, GlassLevel.Subtle)
            .combinedClickable(src, null, onClick = { onChange(!checked) }),
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset {
                    val travel = 56.dp.toPx() - thumbW.dp.toPx() - 6.dp.toPx()
                    IntOffset((3.dp.toPx() + travel * prog).roundToInt(), 0)
                }
                .size(width = thumbW.dp, height = 26.dp)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFD7DDF7))))
                .glass(CircleShape, GlassLevel.Subtle),
        )
    }
}

/** Settings-style row that opens a glass dropdown anchored to its value pill. */
@Composable
fun GlassDropdownPill(value: String, onOpen: (Rect) -> Unit) {
    val anchor = rememberAnchor()
    Row(
        anchor.modifier
            .height(38.dp)
            .glass(CircleShape, GlassLevel.Regular)
            .pressable({ onOpen(anchor.rect) })
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(value, style = AuraType.Body, color = AuraColors.TextPrimary, maxLines = 1)
        Icon(Icons.Rounded.UnfoldMore, null, tint = AuraColors.TextSecondary, modifier = Modifier.size(16.dp))
    }
}

@Composable
fun GlassTextField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leading: ImageVector? = null,
    onSearch: (() -> Unit)? = null,
    autoFocus: Boolean = false,
    onFocus: (Boolean) -> Unit = {},
) {
    val accent = LocalAccent.current
    var focused by remember { mutableStateOf(false) }
    val f by animateFloatAsState(if (focused) 1f else 0f, spr(Spr.Snappy), label = "focus")
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(autoFocus) { if (autoFocus) focusRequester.requestFocus() }
    Row(
        modifier
            .height(52.dp)
            .graphicsLayer { val s = 1f + 0.015f * f; scaleX = s; scaleY = s }
            .glass(CircleShape, GlassLevel.Regular)
            .drawWithContent {
                drawContent()
                if (f > 0.01f) drawRoundRect(
                    SolidColor(accent.c.copy(alpha = 0.75f * clamp01(f))),
                    cornerRadius = CornerRadius(size.height / 2),
                    style = Stroke(1.6.dp.toPx()),
                )
            }
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (leading != null) Icon(leading, null, tint = AuraColors.TextSecondary, modifier = Modifier.size(20.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, style = AuraType.Body.copy(fontSize = 16.sp), color = AuraColors.TextTertiary)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = AuraType.Body.copy(fontSize = 16.sp, color = AuraColors.TextPrimary),
                cursorBrush = SolidColor(accent.c),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); onSearch?.invoke() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { focused = it.isFocused; onFocus(it.isFocused) },
            )
        }
        AnimatedVisibility(
            visible = value.isNotEmpty(),
            enter = fadeIn() + scaleIn(initialScale = 0.5f),
            exit = fadeOut() + scaleOut(targetScale = 0.5f),
        ) {
            Icon(
                Icons.Rounded.Close, null, tint = AuraColors.TextSecondary,
                modifier = Modifier.size(20.dp).pressable({ onChange("") }, scaleTo = 0.8f),
            )
        }
    }
}

/** Favourite toggle: expressive-but-restrained spring pop (grow + tilt), softer wobble when un-favouriting. */
@Composable
fun HeartButton(isFav: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 24.dp, boxSize: Dp = 44.dp) {
    val scale = remember { androidx.compose.animation.core.Animatable(1f) }
    var first by remember { mutableStateOf(true) }
    val reduce = LocalReduceMotion.current
    LaunchedEffect(isFav) {
        if (first) { first = false; return@LaunchedEffect }
        if (reduce) { scale.snapTo(1f); return@LaunchedEffect }
        if (isFav) {
            scale.snapTo(0.5f)
            scale.animateTo(1f, androidx.compose.animation.core.spring(Spr.Favorite.damping, Spr.Favorite.stiffness))
        } else {
            scale.snapTo(1.18f)
            scale.animateTo(1f, androidx.compose.animation.core.spring(0.5f, 480f))
        }
    }
    val tint by animateColorAsState(if (isFav) AuraColors.Pink else AuraColors.TextSecondary, spr(Spr.Critical), label = "heart")
    Box(modifier.size(boxSize).pressable(onClick, scaleTo = 0.8f), contentAlignment = Alignment.Center) {
        Icon(
            if (isFav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null, tint = tint,
            modifier = Modifier.size(size).graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                rotationZ = (1f - scale.value) * -28f
            },
        )
    }
}

/** Play/pause glyph that morphs between two quads (triangle halves <-> bars). */
@Composable
fun PlayPauseGlyph(playing: Boolean, color: Color, modifier: Modifier = Modifier) {
    val t by animateFloatAsState(if (playing) 1f else 0f, spr(Spr.Snappy), label = "pp")
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        fun lerp(a: Float, b: Float) = a + (b - a) * t
        // Left quad: play = (0,0)(.5,.25)(.5,.75)(0,1) -> pause = (0,0)(.34,0)(.34,1)(0,1)
        val l = Path().apply {
            moveTo(0f, 0f)
            lineTo(lerp(.5f, .34f) * w, lerp(.25f, 0f) * h)
            lineTo(lerp(.5f, .34f) * w, lerp(.75f, 1f) * h)
            lineTo(0f, h)
            close()
        }
        // Right quad: play = (.5,.25)(1,.5)(1,.5)(.5,.75) -> pause = (.66,0)(1,0)(1,1)(.66,1)
        val r = Path().apply {
            moveTo(lerp(.5f, .66f) * w, lerp(.25f, 0f) * h)
            lineTo(w, lerp(.5f, 0f) * h)
            lineTo(w, lerp(.5f, 1f) * h)
            lineTo(lerp(.5f, .66f) * w, lerp(.75f, 1f) * h)
            close()
        }
        drawPath(l, color)
        drawPath(r, color)
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = AuraType.Section, color = AuraColors.TextPrimary, modifier = Modifier.weight(1f))
        if (action != null) Text(
            action, style = AuraType.Caption, color = AuraColors.Cyan,
            modifier = Modifier.pressable(onAction, scaleTo = 0.92f).padding(8.dp),
        )
    }
}
