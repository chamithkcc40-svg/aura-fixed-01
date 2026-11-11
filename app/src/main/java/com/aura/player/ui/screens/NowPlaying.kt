package com.aura.player.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.aura.player.art.AmbientColors
import com.aura.player.clamp01
import com.aura.player.fitSquare
import com.aura.player.formatDuration
import com.aura.player.playback.PlayerController
import com.aura.player.playback.PlayerState
import com.aura.player.playback.QueueItem
import com.aura.player.progressFraction
import com.aura.player.seekTarget
import com.aura.player.ui.AuraEnv
import com.aura.player.ui.LocalAccent
import com.aura.player.ui.LocalAura
import com.aura.player.ui.LocalFavorites
import com.aura.player.ui.PlayerSheet
import com.aura.player.ui.Route
import com.aura.player.ui.components.AnchorState
import com.aura.player.ui.components.Artwork
import com.aura.player.ui.components.GlassIconButton
import com.aura.player.ui.components.HeartButton
import com.aura.player.ui.components.PlayPauseGlyph
import com.aura.player.ui.components.rememberAnchor
import com.aura.player.ui.components.rememberArtColors
import com.aura.player.ui.glass.AmbientBackground
import com.aura.player.ui.glass.GlassLevel
import com.aura.player.ui.glass.LocalReduceMotion
import com.aura.player.ui.glass.Spr
import com.aura.player.ui.glass.glass
import com.aura.player.ui.glass.glow
import com.aura.player.ui.glass.pressable
import com.aura.player.ui.glass.spr
import com.aura.player.ui.glass.sprRaw
import com.aura.player.ui.theme.AuraColors
import com.aura.player.ui.theme.AuraType
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Playback position is polled here (4 Hz, only while playing and visible) into a primitive state.
 * Only draw lambdas and two tiny Text composables read it, so nothing else recomposes.
 */
@Composable
fun rememberProgress(player: PlayerController, active: Boolean, key: Any?): State<Long> {
    val s = remember { mutableLongStateOf(0L) }
    LaunchedEffect(active, key) {
        s.longValue = player.position()
        while (active && isActive) {
            delay(250)
            s.longValue = player.position()
        }
    }
    return s
}

/** Staggered entrance driven by the sheet's own progress (so it follows the finger and springs with the sheet). */
private fun Modifier.reveal(sheet: PlayerSheet, order: Int): Modifier = graphicsLayer {
    val a = clamp01((sheet.progress.value - 0.30f - order * 0.07f) / 0.45f)
    alpha = a
    translationY = (1f - a) * (28.dp.toPx() + order * 6.dp.toPx())
}

@Composable
fun NowPlayingLayer() {
    val env = LocalAura.current
    val sheet = env.sheet
    val ps by env.player.state.collectAsStateWithLifecycle()
    val hasTrack = ps.current != null
    val visible by remember { derivedStateOf { sheet.progress.value > 0.001f } }
    val topShape = remember { RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp) }

    // If the queue empties (last track removed) while the sheet is open, close it instead of leaving it stuck.
    LaunchedEffect(hasTrack) {
        if (!hasTrack && (sheet.open || sheet.progress.value > 0f)) sheet.collapse()
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        SideEffect { sheet.heightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f) }
        if (visible && hasTrack) {
            BackHandler(enabled = sheet.open) { sheet.collapse() }
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val v = sheet.progress.value
                        // Travel is clamped to the screen edge; spring overshoot (v > 1) becomes a gentle
                        // downward stretch instead of lifting the sheet and exposing a gap.
                        translationY = (1f - v.coerceAtMost(1f)) * size.height
                        transformOrigin = TransformOrigin(0.5f, 0f)
                        scaleY = 1f + (v - 1f).coerceAtLeast(0f) * 0.5f
                        shape = topShape
                        clip = true
                    }
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { sheet.dragBy(it) },
                        onDragStopped = { sheet.settle(it) },
                    ),
            ) { NowPlayingContent() }
        }
    }
}

@Composable
private fun NowPlayingContent() {
    val env = LocalAura.current
    val ps by env.player.state.collectAsStateWithLifecycle()
    val track = ps.current ?: return
    val accent = LocalAccent.current
    val ambienceOn by env.settings.ambience.collectAsStateWithLifecycle()
    val artColors = rememberArtColors(track.artUri, accent)
    val colors = if (ambienceOn) artColors else accent
    val favId = track.mediaId.toLongOrNull()
    val isFav = favId != null && favId in LocalFavorites.current
    val progress = rememberProgress(env.player, ps.isPlaying, track.mediaId)
    val drag = remember { mutableStateOf<Float?>(null) }
    val menuAnchor = rememberAnchor()
    val sheet = env.sheet

    Box(Modifier.fillMaxSize()) {
        AmbientBackground(colors, Modifier.fillMaxSize(), intensity = 1.25f)
        BoxWithConstraints(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            if (maxWidth > maxHeight) {
                // Landscape: artwork left, scrollable controls right. Never a zero-size artwork.
                Row(
                    Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ArtworkArea(Modifier.weight(1f).fillMaxHeight(), track, ps.isPlaying, colors.a)
                    Column(
                        Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        NpHeader(env, favId, isFav, menuAnchor)
                        Spacer(Modifier.height(12.dp))
                        NpInfo(env, track, favId, isFav, Modifier.reveal(sheet, 0))
                        Spacer(Modifier.height(10.dp))
                        NpSeek(env, progress, ps.duration, colors.c, drag, Modifier.reveal(sheet, 1))
                        Spacer(Modifier.height(8.dp))
                        NpControls(env, ps, colors, Modifier.reveal(sheet, 2))
                        Spacer(Modifier.height(12.dp))
                        NpQueueButton(env, Modifier.reveal(sheet, 3))
                    }
                }
            } else {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    NpHeader(env, favId, isFav, menuAnchor)
                    ArtworkArea(Modifier.weight(1f).fillMaxWidth().padding(vertical = 14.dp), track, ps.isPlaying, colors.a)
                    NpInfo(env, track, favId, isFav, Modifier.reveal(sheet, 0))
                    Spacer(Modifier.height(14.dp))
                    NpSeek(env, progress, ps.duration, colors.c, drag, Modifier.reveal(sheet, 1))
                    Spacer(Modifier.height(10.dp))
                    NpControls(env, ps, colors, Modifier.reveal(sheet, 2))
                    Spacer(Modifier.height(18.dp))
                    NpQueueButton(env, Modifier.reveal(sheet, 3))
                }
            }
        }
    }
}

@Composable
private fun NpHeader(env: AuraEnv, favId: Long?, isFav: Boolean, anchor: AnchorState) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        GlassIconButton(Icons.Rounded.KeyboardArrowDown, { env.sheet.collapse() }, size = 42.dp)
        Text(
            "NOW PLAYING", style = AuraType.Label, color = AuraColors.TextSecondary,
            modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
        )
        GlassIconButton(Icons.Rounded.MoreHoriz, {
            favId?.let { id ->
                env.scope.launch { env.repo.dao.trackById(id)?.let { env.trackMenu(anchor.rect, it, isFav) } }
            }
        }, anchor.modifier, size = 42.dp)
    }
}

/** Square artwork that fits its slot. If the slot is too small to be useful it is skipped (never 0 x 0). */
@Composable
private fun ArtworkArea(modifier: Modifier, track: QueueItem, playing: Boolean, glowColor: Color) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val side = fitSquare(maxWidth.value, maxHeight.value).dp
        if (side >= 72.dp) ArtworkPager(side, track.mediaId, track.artUri, track.title, playing, glowColor)
    }
}

@Composable
private fun NpInfo(env: AuraEnv, track: QueueItem, favId: Long?, isFav: Boolean, modifier: Modifier = Modifier) {
    val reduce = LocalReduceMotion.current
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(
            Modifier.weight(1f).pressable({
                favId?.let { id ->
                    env.scope.launch { env.repo.dao.trackById(id)?.let { env.openRoute(Route.Artist(it.artist)) } }
                }
            }, scaleTo = 0.98f),
        ) {
            AnimatedContent(
                targetState = track.title to track.artist,
                transitionSpec = {
                    (slideInVertically(sprRaw(Spr.Nav, reduce)) { it / 3 } + fadeIn(sprRaw(Spr.Soft, reduce))) togetherWith
                        (slideOutVertically(sprRaw(Spr.Critical, reduce)) { -it / 3 } + fadeOut(sprRaw(Spr.Critical, reduce)))
                },
                label = "titleSwap",
            ) { (title, artist) ->
                Column {
                    Text(title, style = AuraType.Headline, color = AuraColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        artist,
                        style = AuraType.Body.copy(fontSize = androidx.compose.ui.unit.TextUnit(17f, androidx.compose.ui.unit.TextUnitType.Sp)),
                        color = AuraColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        HeartButton(isFav, { favId?.let { env.toggleFavorite(it) } }, size = 28.dp, boxSize = 48.dp)
    }
}

@Composable
private fun NpSeek(
    env: AuraEnv,
    progress: State<Long>,
    duration: Long,
    color: Color,
    drag: MutableState<Float?>,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        SeekBar(env.player, progress, duration, color, drag)
        TimeRow(progress, duration, drag)
    }
}

@Composable
private fun NpControls(env: AuraEnv, ps: PlayerState, colors: AmbientColors, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToggleIcon(Icons.Rounded.Shuffle, ps.shuffle, colors.c) { env.player.toggleShuffle() }
        GlassBubble(Icons.Rounded.SkipPrevious, 56.dp, 28.dp) { env.player.previous() }
        Box(
            Modifier.size(78.dp).glow(colors.a, 90.dp, 0.55f).clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFDCE2FA))))
                .glass(CircleShape, GlassLevel.Strong, borderAlpha = 0.7f, elevated = true)
                .pressable({ env.player.togglePlay() }, scaleTo = 0.88f, lightShape = CircleShape),
            contentAlignment = Alignment.Center,
        ) { PlayPauseGlyph(ps.isPlaying, Color(0xFF0B0F2A), Modifier.size(28.dp)) }
        GlassBubble(Icons.Rounded.SkipNext, 56.dp, 28.dp) { env.player.next() }
        ToggleIcon(
            if (ps.repeat == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
            ps.repeat != Player.REPEAT_MODE_OFF, colors.c,
        ) { env.player.cycleRepeat() }
    }
}

/** Round glass key (transport buttons): thick rim, squash-and-release spring, press bloom. */
@Composable
private fun GlassBubble(icon: androidx.compose.ui.graphics.vector.ImageVector, size: Dp, iconSize: Dp, onClick: () -> Unit) {
    Box(
        Modifier.size(size).glass(CircleShape, GlassLevel.Regular).pressable(onClick, scaleTo = 0.84f, lightShape = CircleShape),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = AuraColors.TextPrimary, modifier = Modifier.size(iconSize)) }
}

@Composable
private fun NpQueueButton(env: AuraEnv, modifier: Modifier = Modifier) {
    Row(
        modifier
            .height(46.dp)
            .glass(CircleShape, GlassLevel.Regular)
            .pressable({ env.showQueue() }, lightShape = CircleShape)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Rounded.QueueMusic, null, tint = AuraColors.TextPrimary, modifier = Modifier.size(20.dp))
        Text("Up next", style = AuraType.Body, color = AuraColors.TextPrimary)
    }
}

@Composable
private fun ToggleIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, on: Boolean, onColor: Color, onClick: () -> Unit) {
    val tint by animateColorAsState(if (on) onColor else AuraColors.TextTertiary, spr(Spr.Critical), label = "tg")
    val dot by animateFloatAsState(if (on) 1f else 0f, spr(Spr.Favorite), label = "dot")
    Box(Modifier.size(48.dp).pressable(onClick, scaleTo = 0.82f), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
        Box(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp).size(4.dp)
                .graphicsLayer { scaleX = dot; scaleY = dot }.background(tint, CircleShape),
        )
    }
}

/**
 * Artwork with swipe-to-skip (finger-tracking, stretches while pulled, springs back), track-change animation,
 * play/pause breathing scale, and a hero entrance driven by the sheet progress (grows out of the Mini Player).
 */
@Composable
private fun ArtworkPager(
    side: Dp, mediaId: String, artUri: String?, title: String, playing: Boolean, glowColor: Color,
) {
    val env = LocalAura.current
    val sheet = env.sheet
    val reduce = LocalReduceMotion.current
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val scale by animateFloatAsState(if (playing) 1f else 0.88f, spr(Spr.Artwork), label = "artScale")
    val shape = RoundedCornerShape(36.dp)

    AnimatedContent(
        targetState = Triple(mediaId, artUri, title),
        transitionSpec = {
            (fadeIn(sprRaw(Spr.Soft, reduce)) + scaleIn(sprRaw(Spr.Artwork, reduce), initialScale = 0.82f)) togetherWith
                (fadeOut(sprRaw(Spr.Critical, reduce)) + scaleOut(sprRaw(Spr.Critical, reduce), targetScale = 0.9f))
        },
        contentKey = { it.first },
        label = "artSwap",
    ) { (_, uri, t) ->
        Artwork(
            uri, t,
            Modifier
                .size(side)
                .graphicsLayer {
                    val p = clamp01(sheet.progress.value)
                    val hero = 0.62f + 0.38f * p
                    val pull = abs(offset.value) / size.width.coerceAtLeast(1f)
                    scaleX = scale * hero * (1f + pull * 0.10f)
                    scaleY = scale * hero * (1f - pull * 0.05f)
                    translationX = offset.value
                    translationY = (1f - p) * 72.dp.toPx()
                    rotationZ = offset.value / 40f
                }
                .glow(glowColor, 230.dp, 0.45f)
                .glass(shape, GlassLevel.Subtle, borderAlpha = 0.8f)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val o = offset.value
                            val w = size.width
                            scope.launch {
                                when {
                                    o < -w * 0.28f -> env.player.next()
                                    o > w * 0.28f -> env.player.previous()
                                }
                                offset.animateTo(0f, sprRaw(Spr.Stretch, reduce))
                            }
                        },
                        onDragCancel = { scope.launch { offset.animateTo(0f, sprRaw(Spr.Stretch, reduce)) } },
                        onHorizontalDrag = { _, dx ->
                            val limit = size.width * 0.6f
                            scope.launch { offset.snapTo((offset.value + dx * 0.8f).coerceIn(-limit, limit)) }
                        },
                    )
                },
            shape, sizePx = 720,
        )
    }
}

@Composable
private fun SeekBar(player: PlayerController, pos: State<Long>, duration: Long, color: Color, drag: MutableState<Float?>) {
    val thick by animateFloatAsState(if (drag.value != null) 10f else 6f, spr(Spr.Snappy), label = "thick")
    val thumb by animateFloatAsState(if (drag.value != null) 1f else 0f, spr(Spr.Favorite), label = "thumb")
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(32.dp)
            .pointerInput(duration) {
                detectTapGestures { o ->
                    if (duration > 0) player.seekTo(seekTarget(o.x / size.width.coerceAtLeast(1), duration))
                }
            }
            .pointerInput(duration) {
                detectHorizontalDragGestures(
                    onDragStart = { drag.value = clamp01(it.x / size.width.coerceAtLeast(1)) },
                    onHorizontalDrag = { c, _ -> drag.value = clamp01(c.position.x / size.width.coerceAtLeast(1)) },
                    onDragEnd = { drag.value?.let { if (duration > 0) player.seekTo(seekTarget(it, duration)) }; drag.value = null },
                    onDragCancel = { drag.value = null },
                )
            },
    ) {
        val frac = drag.value ?: progressFraction(pos.value, duration)
        val h = thick.coerceAtLeast(1f).dp.toPx()
        val y = (size.height - h) / 2
        val r = CornerRadius(h / 2)
        drawRoundRect(Color.White.copy(alpha = 0.16f), Offset(0f, y), Size(size.width, h), r)
        if (frac > 0f) drawRoundRect(
            Brush.horizontalGradient(listOf(color.copy(alpha = 0.85f), Color.White)),
            Offset(0f, y), Size(size.width * frac, h), r,
        )
        if (thumb > 0.01f) drawCircle(Color.White, 9.dp.toPx() * thumb, Offset(size.width * frac, size.height / 2))
    }
}

@Composable
private fun TimeRow(pos: State<Long>, duration: Long, drag: State<Float?>) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            formatDuration(drag.value?.let { seekTarget(it, duration) } ?: pos.value),
            style = AuraType.Caption, color = AuraColors.TextSecondary, modifier = Modifier.weight(1f),
        )
        Text(formatDuration(duration), style = AuraType.Caption, color = AuraColors.TextSecondary)
    }
}

/**
 * Floating glass Mini Player. Tap or drag up to expand with a spring. While the sheet opens the pill
 * STRETCHES upward from its bottom edge (scaleY grows faster than scaleX) before it fades into Now Playing.
 */
@Composable
fun MiniPlayer(modifier: Modifier = Modifier) {
    val env = LocalAura.current
    val ps by env.player.state.collectAsStateWithLifecycle()
    val track = ps.current ?: return
    val progress = rememberProgress(env.player, ps.isPlaying, track.mediaId)
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(34.dp)
    val reduce = LocalReduceMotion.current
    Row(
        modifier
            .fillMaxWidth()
            .height(66.dp)
            .graphicsLayer {
                val p = clamp01(env.sheet.progress.value)
                transformOrigin = TransformOrigin(0.5f, 1f)
                scaleX = 1f + p * 0.04f
                scaleY = 1f + p * 0.16f
            }
            .glass(shape, GlassLevel.Strong)
            .drawWithProgress(progress, ps.duration, accent.c)
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { env.sheet.dragBy(it) },
                onDragStopped = { env.sheet.settle(it) },
            )
            .pressable({ env.sheet.expand() }, scaleTo = 0.965f, lightShape = shape)
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Artwork(track.artUri, track.title, Modifier.size(48.dp), CircleShape, sizePx = 140)
        Column(Modifier.weight(1f)) {
            AnimatedContent(
                track.title to track.artist,
                transitionSpec = {
                    (slideInVertically(sprRaw(Spr.Nav, reduce)) { it / 2 } + fadeIn(sprRaw(Spr.Soft, reduce))) togetherWith
                        (slideOutVertically(sprRaw(Spr.Critical, reduce)) { -it / 2 } + fadeOut(sprRaw(Spr.Critical, reduce)))
                },
                label = "miniTitle",
            ) { (t, a) ->
                Column {
                    Text(t, style = AuraType.Body, color = AuraColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(a, style = AuraType.Caption, color = AuraColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Box(
            Modifier.size(44.dp).pressable({ env.player.togglePlay() }, scaleTo = 0.82f),
            contentAlignment = Alignment.Center,
        ) { PlayPauseGlyph(ps.isPlaying, AuraColors.TextPrimary, Modifier.size(18.dp)) }
        Icon(
            Icons.Rounded.SkipNext, null, tint = AuraColors.TextPrimary,
            modifier = Modifier.size(44.dp).pressable({ env.player.next() }, scaleTo = 0.78f).padding(9.dp),
        )
    }
}

private fun Modifier.drawWithProgress(pos: State<Long>, duration: Long, color: Color): Modifier =
    this.drawWithContent {
        drawContent()
        if (duration > 0) {
            val f = progressFraction(pos.value, duration)
            val inset = 30.dp.toPx()
            val w = size.width - inset * 2
            drawRoundRect(color.copy(alpha = 0.18f), Offset(inset, size.height - 5.dp.toPx()), Size(w, 2.dp.toPx()), CornerRadius(1.dp.toPx()))
            drawRoundRect(color, Offset(inset, size.height - 5.dp.toPx()), Size(w * f, 2.dp.toPx()), CornerRadius(1.dp.toPx()))
        }
    }
