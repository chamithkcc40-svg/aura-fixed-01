package com.aura.player.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.QueuePlayNext
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import com.aura.player.AuraApp
import com.aura.player.art.AmbientColors
import com.aura.player.data.TrackEntity
import com.aura.player.ui.glass.Spr
import com.aura.player.ui.components.MenuItemSpec
import com.aura.player.ui.components.OverlayController
import com.aura.player.ui.screens.AddToPlaylistSheet
import com.aura.player.ui.screens.QueueSheet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class Tab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    Home("Home", Icons.Outlined.Home, Icons.Rounded.Home),
    Library("Library", Icons.Outlined.LibraryMusic, Icons.Rounded.LibraryMusic),
    Search("Search", Icons.Outlined.Search, Icons.Rounded.Search),
    Playlists("Playlists", Icons.Outlined.QueueMusic, Icons.Rounded.QueueMusic),
    Settings("Settings", Icons.Outlined.Settings, Icons.Rounded.Settings),
}

sealed interface Route {
    data class TabRoute(val tab: Tab) : Route
    data class Album(val key: String) : Route
    data class Artist(val name: String) : Route
    data class Playlist(val id: Long) : Route
}

@Stable
class Navigator {
    var tab by mutableStateOf(Tab.Home)
        private set
    val stack = mutableStateListOf<Route>()
    var dir by mutableIntStateOf(1)
        private set
    var libraryPage by mutableIntStateOf(0)

    val current: Route get() = stack.lastOrNull() ?: Route.TabRoute(tab)

    fun select(t: Tab) {
        if (t == tab && stack.isEmpty()) return
        dir = if (t.ordinal >= tab.ordinal) 1 else -1
        stack.clear()
        tab = t
    }

    fun open(r: Route) { dir = 1; stack.add(r) }

    fun back(): Boolean {
        if (stack.isEmpty()) return false
        dir = -1
        stack.removeAt(stack.lastIndex)
        return true
    }
}

/** Mini-player <-> Now Playing expansion. Gesture-driven; settles with springs (velocity preserved). */
@Stable
class PlayerSheet(private val scope: CoroutineScope) {
    val progress = Animatable(0f)
    var heightPx by mutableFloatStateOf(1f)
    var open by mutableStateOf(false)
        private set
    var reduceMotion = false

    private fun spec(damping: Float, stiffness: Float) =
        if (reduceMotion) snap<Float>() else spring(damping, stiffness)

    fun expand() { open = true; scope.launch { progress.animateTo(1f, spec(Spr.Mini.damping, Spr.Mini.stiffness)) } }
    fun collapse() { open = false; scope.launch { progress.animateTo(0f, spec(Spr.Critical.damping, 520f)) } }

    fun dragBy(deltaY: Float) {
        val h = heightPx.coerceAtLeast(1f)
        scope.launch { progress.snapTo((progress.value - deltaY / h).coerceIn(0f, 1f)) }
    }

    fun settle(velocityY: Float) {
        val target = when {
            velocityY < -900f -> 1f
            velocityY > 900f -> 0f
            progress.value > 0.5f -> 1f
            else -> 0f
        }
        open = target == 1f
        scope.launch {
            progress.animateTo(
                target,
                if (reduceMotion) snap() else spring(if (target == 1f) Spr.Mini.damping else 1f, 420f),
                initialVelocity = -velocityY / heightPx.coerceAtLeast(1f),
            )
        }
    }
}

data class PlaylistCtx(val id: Long, val index: Int, val size: Int)

@Stable
class AuraEnv(
    val app: AuraApp,
    val overlay: OverlayController,
    val nav: Navigator,
    val sheet: PlayerSheet,
) {
    val repo get() = app.repo
    val player get() = app.player
    val settings get() = app.settings
    val scope: CoroutineScope get() = app.appScope
    var requestAudio: () -> Unit = {}

    fun play(tracks: List<TrackEntity>, index: Int = 0, shuffle: Boolean = false) =
        player.play(tracks, if (shuffle) tracks.indices.randomOrNull() ?: 0 else index, shuffle)

    fun playFlow(flow: Flow<List<TrackEntity>>, shuffle: Boolean = false) {
        scope.launch { play(flow.first(), 0, shuffle) }
    }

    fun shuffleAll() {
        scope.launch { play(repo.songsList("", 0), 0, true) }
    }

    fun openRoute(r: Route) {
        if (sheet.open) sheet.collapse()
        nav.open(r)
    }

    fun toggleFavorite(id: Long) { scope.launch { repo.toggleFavorite(id) } }

    fun trackMenu(anchor: Rect, t: TrackEntity, isFav: Boolean, playlist: PlaylistCtx? = null) {
        val items = mutableListOf(
            MenuItemSpec("Play next", Icons.Rounded.QueuePlayNext) { player.playNext(t); overlay.showToast("Playing next") },
            MenuItemSpec("Add to queue", Icons.Rounded.QueueMusic) { player.addToQueue(t); overlay.showToast("Added to queue") },
            MenuItemSpec(if (isFav) "Remove favorite" else "Favorite", if (isFav) Icons.Rounded.FavoriteBorder else Icons.Rounded.Favorite) { toggleFavorite(t.id) },
            MenuItemSpec("Add to playlist", Icons.Rounded.PlaylistAdd) { addToPlaylist(listOf(t)) },
            MenuItemSpec("Go to album", Icons.Rounded.Album) { openRoute(Route.Album(t.albumKey)) },
            MenuItemSpec("Go to artist", Icons.Rounded.Person) { openRoute(Route.Artist(t.artist)) },
        )
        if (playlist != null) {
            if (playlist.index > 0) items += MenuItemSpec("Move up", Icons.Rounded.ArrowUpward) {
                scope.launch { repo.dao.moveEntry(playlist.id, playlist.index, playlist.index - 1) }
            }
            if (playlist.index < playlist.size - 1) items += MenuItemSpec("Move down", Icons.Rounded.ArrowDownward) {
                scope.launch { repo.dao.moveEntry(playlist.id, playlist.index, playlist.index + 1) }
            }
            items += MenuItemSpec("Remove from playlist", Icons.Rounded.Delete, danger = true) {
                scope.launch { repo.dao.removeEntry(playlist.id, t.id) }
            }
        }
        overlay.menu(anchor, items)
    }

    fun addToPlaylist(tracks: List<TrackEntity>) {
        overlay.sheet { dismiss -> AddToPlaylistSheet(tracks, dismiss) }
    }

    fun showQueue() {
        overlay.sheet { dismiss -> QueueSheet(dismiss) }
    }
}

val LocalAura = staticCompositionLocalOf<AuraEnv> { error("AuraEnv not provided") }

/** Stable accent palette from settings (controls). Artwork-derived colours are used for ambience only. */
val LocalAccent = staticCompositionLocalOf { AmbientColors(androidx.compose.ui.graphics.Color.Magenta, androidx.compose.ui.graphics.Color.Blue, androidx.compose.ui.graphics.Color.Cyan) }

val LocalFavorites = compositionLocalOf<Set<Long>> { emptySet() }
val LocalCurrentId = compositionLocalOf<String?> { null }
val LocalBottomPad = compositionLocalOf { androidx.compose.ui.unit.Dp(0f) }
