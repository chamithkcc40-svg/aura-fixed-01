package com.aura.player.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aura.player.data.PlaylistRow
import com.aura.player.ui.LocalAura
import com.aura.player.ui.LocalBottomPad
import com.aura.player.ui.Route
import com.aura.player.ui.components.EmptyState
import com.aura.player.ui.components.GlassAlert
import com.aura.player.ui.components.GlassIconButton
import com.aura.player.ui.components.GlassPrompt
import com.aura.player.ui.components.MenuItemSpec
import com.aura.player.ui.components.PlaylistCard
import com.aura.player.ui.components.PrimaryButton
import com.aura.player.ui.components.ScreenTitle
import kotlinx.coroutines.launch

@Composable
fun PlaylistsScreen() {
    val env = LocalAura.current
    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Playlists") {
            GlassIconButton(Icons.Rounded.Add, { promptNewPlaylist(env) })
        }
        PlaylistsGrid()
    }
}

fun promptNewPlaylist(env: com.aura.player.ui.AuraEnv, then: ((Long) -> Unit)? = null) {
    env.overlay.dialog { dismiss ->
        GlassPrompt("New playlist", placeholder = "Playlist name", confirmLabel = "Create", dismiss = dismiss) { name ->
            env.scope.launch {
                val id = env.repo.createPlaylist(name)
                env.overlay.showToast("Created “${name.trim()}”")
                then?.invoke(id)
            }
        }
    }
}

@Composable
fun PlaylistsGrid() {
    val env = LocalAura.current
    val playlists by remember { env.repo.dao.playlists() }.collectAsStateWithLifecycle(emptyList())
    if (playlists.isEmpty()) {
        EmptyState(Icons.Rounded.QueueMusic, "Make your first playlist", "Collect songs into a mix with beautiful artwork.") {
            PrimaryButton("New playlist", { promptNewPlaylist(env) }, Modifier.fillMaxWidth(), Icons.Rounded.Add)
        }
        return
    }
    LazyVerticalGrid(
        GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = LocalBottomPad.current),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        items(playlists, key = { it.id }) { p ->
            PlaylistCard(
                p,
                onClick = { env.openRoute(Route.Playlist(p.id)) },
                onMenu = { rect -> env.overlay.menu(rect, playlistMenu(env, p)) },
            )
        }
    }
}

fun playlistMenu(env: com.aura.player.ui.AuraEnv, p: PlaylistRow): List<MenuItemSpec> = listOf(
    MenuItemSpec("Play", Icons.Rounded.PlayArrow) { env.playFlow(env.repo.dao.playlistTracks(p.id)) },
    MenuItemSpec("Shuffle", Icons.Rounded.Shuffle) { env.playFlow(env.repo.dao.playlistTracks(p.id), shuffle = true) },
    MenuItemSpec("Rename", Icons.Rounded.Edit) {
        env.overlay.dialog { dismiss ->
            GlassPrompt("Rename playlist", p.name, "Playlist name", "Save", dismiss) { name ->
                env.scope.launch { env.repo.dao.renamePlaylist(p.id, name.trim()) }
            }
        }
    },
    MenuItemSpec("Delete", Icons.Rounded.Delete, danger = true) {
        env.overlay.dialog { dismiss ->
            GlassAlert("Delete “${p.name}”?", "The songs stay in your library. This can't be undone.", "Delete", true, dismiss) {
                env.scope.launch { env.repo.dao.deletePlaylist(p.id) }
            }
        }
    },
)
