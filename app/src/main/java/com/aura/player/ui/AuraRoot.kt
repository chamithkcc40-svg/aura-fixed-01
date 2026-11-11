package com.aura.player.ui

import android.Manifest
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aura.player.AuraApp
import com.aura.player.art.AmbientColors
import com.aura.player.data.AccentPalettes
import com.aura.player.ui.components.OverlayController
import com.aura.player.ui.components.OverlayHost
import com.aura.player.ui.glass.pressable
import com.aura.player.ui.components.rememberArtColors
import com.aura.player.ui.glass.AmbientBackground
import com.aura.player.ui.glass.Backdrop
import com.aura.player.ui.glass.GlassLevel
import com.aura.player.ui.glass.LocalBackdrop
import com.aura.player.ui.glass.LocalGlassQuality
import com.aura.player.ui.glass.LocalHaptics
import com.aura.player.ui.glass.LocalReduceMotion
import com.aura.player.ui.glass.Spr
import com.aura.player.ui.glass.glass
import com.aura.player.ui.glass.spr
import com.aura.player.ui.glass.sprRaw
import com.aura.player.ui.screens.AlbumScreen
import com.aura.player.ui.screens.ArtistScreen
import com.aura.player.ui.screens.HomeScreen
import com.aura.player.ui.screens.LibraryScreen
import com.aura.player.ui.screens.MiniPlayer
import com.aura.player.ui.screens.NowPlayingLayer
import com.aura.player.ui.screens.PlaylistScreen
import com.aura.player.ui.screens.PlaylistsScreen
import com.aura.player.ui.screens.SearchScreen
import com.aura.player.ui.screens.SettingsScreen
import com.aura.player.ui.theme.AuraColors
import com.aura.player.ui.theme.AuraType
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun AuraRoot(app: AuraApp) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val st = app.settings

    val quality by st.quality.collectAsStateWithLifecycle()
    val accentIdx by st.accent.collectAsStateWithLifecycle()
    val ambienceOn by st.ambience.collectAsStateWithLifecycle()
    val userReduce by st.reduceMotion.collectAsStateWithLifecycle()
    val haptics by st.haptics.collectAsStateWithLifecycle()
    val systemReduce = rememberSystemReduceMotion()
    val reduce = userReduce || systemReduce

    val overlay = remember { OverlayController() }
    val nav = remember { Navigator() }
    val sheet = remember { PlayerSheet(app.appScope) }
    SideEffect { sheet.reduceMotion = reduce }
    val env = remember { AuraEnv(app, overlay, nav, sheet) }

    val perms = remember {
        buildList {
            add(if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        scope.launch { app.repo.rescan() }
    }
    env.requestAudio = { permLauncher.launch(perms) }
    LaunchedEffect(Unit) {
        if (app.repo.hasAudioPermission()) app.repo.rescan() else permLauncher.launch(perms)
    }

    val ps by app.player.state.collectAsStateWithLifecycle()
    val favorites by remember { app.repo.dao.favoriteIds().map { it.toHashSet() as Set<Long> } }.collectAsStateWithLifecycle(emptySet())
    val pal = AccentPalettes[accentIdx]
    val accent = remember(pal) { AmbientColors(pal.a, pal.b, pal.c) }
    val artColors = rememberArtColors(ps.current?.artUri, accent)
    val sceneColors = if (ambienceOn) artColors else accent

    val backdropLayer = rememberGraphicsLayer()
    val backdrop = remember(backdropLayer) { Backdrop(backdropLayer) }

    // Bottom padding that scrolling screens reserve for the floating nav + mini player.
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val hasTrack = ps.current != null
    val miniExtra by animateDpAsState(if (hasTrack) 76.dp else 0.dp, sprRaw(Spr.Soft, reduce), label = "miniPad")
    val bottomPad = navInset + 10.dp + 68.dp + miniExtra + 20.dp

    CompositionLocalProvider(
        LocalAura provides env,
        LocalReduceMotion provides reduce,
        LocalHaptics provides haptics,
        LocalGlassQuality provides quality,
        LocalAccent provides accent,
        LocalFavorites provides favorites,
        LocalCurrentId provides ps.current?.mediaId,
        LocalBottomPad provides bottomPad,
    ) {
        Box(Modifier.fillMaxSize().background(AuraColors.Bg)) {
            // ---- Scene: recorded once per frame into the backdrop layer, then shown ----
            Box(
                Modifier.fillMaxSize().drawWithContent {
                    backdropLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(backdropLayer)
                },
            ) {
                // Content-level glass samples nothing (flat material) – see Glass.kt.
                CompositionLocalProvider(LocalBackdrop provides null) {
                    AmbientBackground(sceneColors, Modifier.fillMaxSize())
                    ScreenHost(env)
                    NowPlayingLayer()
                }
            }

            // ---- Floating layer: true backdrop-blurred glass ----
            CompositionLocalProvider(LocalBackdrop provides backdrop) {
                val p = sheet.progress
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .navigationBarsPadding()
                        .padding(bottom = 10.dp)
                        .graphicsLayer {
                            // Slide away as Now Playing expands.
                            translationY = p.value * 160.dp.toPx()
                            alpha = (1f - p.value * 1.6f).coerceIn(0f, 1f)
                        },
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AnimatedVisibility(
                        visible = hasTrack,
                        enter = fadeIn(sprRaw(Spr.Soft, reduce)) + expandVertically(sprRaw(Spr.Bouncy, reduce)),
                        exit = fadeOut() + shrinkVertically(),
                    ) { MiniPlayer() }
                    FloatingNav(nav)
                }
                OverlayHost(overlay)
            }
        }
    }
}

/** True while the OS "remove animations" / animator-duration-scale 0 setting is on; updates live. */
@Composable
private fun rememberSystemReduceMotion(): Boolean {
    val ctx = LocalContext.current
    var reduce by remember { mutableStateOf(readAnimatorScaleIsZero(ctx)) }
    DisposableEffect(ctx) {
        val resolver = ctx.contentResolver
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduce = readAnimatorScaleIsZero(ctx)
            }
        }
        try {
            resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        } catch (_: Exception) {
        }
        onDispose {
            try { resolver.unregisterContentObserver(observer) } catch (_: Exception) {}
        }
    }
    return reduce
}

private fun readAnimatorScaleIsZero(ctx: android.content.Context): Boolean = try {
    Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
} catch (_: Exception) {
    false
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun ScreenHost(env: AuraEnv) {
    val nav = env.nav
    val holder = rememberSaveableStateHolder()
    val reduce = LocalReduceMotion.current
    BackHandler(enabled = nav.stack.isNotEmpty()) { nav.back() }

    AnimatedContent(
        targetState = nav.current,
        transitionSpec = {
            val d = nav.dir
            (fadeIn(sprRaw(Spr.Soft, reduce)) +
                slideInHorizontally(sprRaw(Spr.Screen, reduce)) { d * it / 6 } +
                scaleIn(sprRaw(Spr.Screen, reduce), initialScale = 0.95f)) togetherWith
                (fadeOut(sprRaw(Spr.Critical, reduce)) + slideOutHorizontally(sprRaw(Spr.Critical, reduce)) { -d * it / 10 }) using
                SizeTransform(clip = false)
        },
        label = "screens",
    ) { route ->
        holder.SaveableStateProvider(route.toString()) {
            when (route) {
                is Route.TabRoute -> when (route.tab) {
                    Tab.Home -> HomeScreen()
                    Tab.Library -> LibraryScreen()
                    Tab.Search -> SearchScreen()
                    Tab.Playlists -> PlaylistsScreen()
                    Tab.Settings -> SettingsScreen()
                }
                is Route.Album -> AlbumScreen(route.key)
                is Route.Artist -> ArtistScreen(route.name)
                is Route.Playlist -> PlaylistScreen(route.id)
            }
        }
    }
}

/**
 * Floating glass tab bar. The lit glass "lens" indicator follows a spring, stretches in proportion to how far
 * it is from its resting tab, and can be GRABBED and scrubbed across tabs with a finger (it pops up like a lens
 * while held, then snaps to the nearest tab on release).
 */
@Composable
private fun FloatingNav(nav: Navigator) {
    val tabs = Tab.entries
    val selected = tabs.indexOf(nav.tab)
    val reduce = LocalReduceMotion.current
    val scope = rememberCoroutineScope()
    val posAnim = remember { Animatable(selected.toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    val lens by animateFloatAsState(if (dragging) 1f else 0f, spr(Spr.Jelly), label = "navLens")
    LaunchedEffect(selected) {
        if (!dragging) posAnim.animateTo(selected.toFloat(), sprRaw(Spr.Nav, reduce))
    }
    val accent = LocalAccent.current
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(68.dp)
            .glass(RoundedCornerShape(34.dp), GlassLevel.Strong)
            .pointerInput(tabs.size) {
                detectHorizontalDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = {
                        dragging = false
                        val t = posAnim.value.roundToInt().coerceIn(0, tabs.lastIndex)
                        nav.select(tabs[t])
                        scope.launch { posAnim.animateTo(t.toFloat(), sprRaw(Spr.Nav, reduce)) }
                    },
                    onDragCancel = {
                        dragging = false
                        scope.launch { posAnim.animateTo(tabs.indexOf(nav.tab).toFloat(), sprRaw(Spr.Nav, reduce)) }
                    },
                    onHorizontalDrag = { change, _ ->
                        val f = (change.position.x / size.width.coerceAtLeast(1) * tabs.size - 0.5f)
                            .coerceIn(0f, tabs.lastIndex.toFloat())
                        scope.launch { posAnim.snapTo(f) }
                    },
                )
            }
            .padding(6.dp),
    ) {
        val tabW = maxWidth / tabs.size
        Box(
            Modifier
                .offset { IntOffset((tabW.toPx() * posAnim.value).roundToInt(), 0) }
                .width(tabW)
                .fillMaxHeight()
                .graphicsLayer {
                    val d = abs(posAnim.value - selected).coerceAtMost(1.5f)
                    scaleX = 1f + 0.22f * d + 0.10f * lens
                    scaleY = 1f - 0.06f * d + 0.14f * lens
                }
                .glass(RoundedCornerShape(28.dp), GlassLevel.Strong, tint = accent.b),
        )
        Row(Modifier.fillMaxSize()) {
            tabs.forEachIndexed { i, tab ->
                val on = i == selected
                Column(
                    Modifier.weight(1f).fillMaxHeight().pressable({ nav.select(tab) }, scaleTo = 0.9f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    AnimatedContent(
                        targetState = on,
                        transitionSpec = { (fadeIn(sprRaw(Spr.Snappy, reduce)) + scaleIn(sprRaw(Spr.Nav, reduce), initialScale = 0.6f)) togetherWith fadeOut(sprRaw(Spr.Critical, reduce)) },
                        label = "navIcon",
                    ) { sel ->
                        Icon(
                            if (sel) tab.selectedIcon else tab.icon, tab.label,
                            tint = if (sel) Color.White else AuraColors.TextSecondary,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Text(
                        tab.label, style = AuraType.Label,
                        color = if (on) Color.White else AuraColors.TextTertiary, maxLines = 1,
                    )
                }
            }
        }
    }
}
