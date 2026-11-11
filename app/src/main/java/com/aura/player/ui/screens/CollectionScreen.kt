package com.aura.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aura.player.data.TrackEntity
import com.aura.player.formatDuration
import com.aura.player.ui.LocalAccent
import com.aura.player.ui.LocalAura
import com.aura.player.ui.LocalBottomPad
import com.aura.player.ui.PlaylistCtx
import com.aura.player.ui.components.EmptyState
import com.aura.player.ui.components.GlassAlert
import com.aura.player.ui.components.GlassIconButton
import com.aura.player.ui.components.GlassPrompt
import com.aura.player.ui.components.MenuItemSpec
import com.aura.player.ui.components.PrimaryButton
import com.aura.player.ui.components.SoftButton
import com.aura.player.ui.components.Artwork
import com.aura.player.ui.components.MosaicArt
import com.aura.player.ui.components.TrackRow
import com.aura.player.ui.components.rememberAnchor
import com.aura.player.ui.components.rememberArtColors
import com.aura.player.ui.glass.AmbientBackground
import com.aura.player.ui.glass.GlassLevel
import com.aura.player.ui.glass.glass
import com.aura.player.ui.glass.glow
import com.aura.player.ui.theme.AuraColors
import com.aura.player.ui.theme.AuraType
import kotlinx.coroutines.launch

@Composable
fun AlbumScreen(key: String) {
    val env = LocalAura.current
    val tracks by remember(key) { env.repo.dao.tracksByAlbum(key) }.collectAsStateWithLifecycle(emptyList())
    val first = tracks.firstOrNull()
    CollectionScreen(
        title = first?.album ?: "Album",
        subtitle = first?.let { "${it.artist} · ${tracks.size} songs · ${formatDuration(tracks.sumOf { t -> t.duration })}" } ?: "",
        tracks = tracks, playlistId = null, kindLabel = "ALBUM",
    )
}

@Composable
fun ArtistScreen(name: String) {
    val env = LocalAura.current
    val tracks by remember(name) { env.repo.dao.tracksByArtist(name) }.collectAsStateWithLifecycle(emptyList())
    CollectionScreen(
        title = name,
        subtitle = "${tracks.map { it.albumKey }.distinct().size} albums · ${tracks.size} songs",
        tracks = tracks, playlistId = null, kindLabel = "ARTIST",
    )
}

@Composable
fun PlaylistScreen(id: Long) {
    val env = LocalAura.current
    val pl by remember(id) { env.repo.dao.playlist(id) }.collectAsStateWithLifecycle(null)
    val tracks by remember(id) { env.repo.dao.playlistTracks(id) }.collectAsStateWithLifecycle(emptyList())
    // Playlist deleted while open -> leave.
    LaunchedEffect(pl, tracks) { if (pl == null && tracks.isEmpty()) { /* flow starts null; stay until first emission */ } }
    CollectionScreen(
        title = pl?.name ?: "Playlist",
        subtitle = "${tracks.size} songs · ${formatDuration(tracks.sumOf { it.duration })}",
        tracks = tracks, playlistId = id, kindLabel = "PLAYLIST",
        emptyHint = "Add songs from any track menu.",
        onMenu = { rect ->
            pl?.let { p ->
                env.overlay.menu(rect, listOf(
                    MenuItemSpec("Rename", Icons.Rounded.Edit) {
                        env.overlay.dialog { dismiss ->
                            GlassPrompt("Rename playlist", p.name, "Playlist name", "Save", dismiss) { n ->
                                env.scope.launch { env.repo.dao.renamePlaylist(p.id, n.trim()) }
                            }
                        }
                    },
                    MenuItemSpec("Delete", Icons.Rounded.Delete, danger = true) {
                        env.overlay.dialog { dismiss ->
                            GlassAlert("Delete “${p.name}”?", "The songs stay in your library.", "Delete", true, dismiss) {
                                env.scope.launch { env.repo.dao.deletePlaylist(p.id); env.nav.back() }
                            }
                        }
                    },
                ))
            }
        },
    )
}

/** Shared immersive detail layout for albums, artists and playlists. */
@Composable
fun CollectionScreen(
    title: String,
    subtitle: String,
    tracks: List<TrackEntity>,
    playlistId: Long?,
    kindLabel: String,
    emptyHint: String = "",
    onMenu: ((androidx.compose.ui.geometry.Rect) -> Unit)? = null,
) {
    val env = LocalAura.current
    val accent = LocalAccent.current
    val colors = rememberArtColors(tracks.firstOrNull()?.uri, accent)
    val listState = rememberLazyListState()
    val anchor = rememberAnchor()

    Box(Modifier.fillMaxSize()) {
        AmbientBackground(colors, Modifier.fillMaxSize(), intensity = 1.15f)
        LazyColumn(state = listState, contentPadding = PaddingValues(bottom = LocalBottomPad.current)) {
            item("header") {
                Column(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(top = 72.dp, start = 20.dp, end = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .size(250.dp)
                            .graphicsLayer {
                                // Parallax: artwork shrinks and fades as the list scrolls away.
                                val off = if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else 2000f
                                val f = (1f - off / 700f).coerceIn(0.7f, 1f)
                                scaleX = f; scaleY = f
                                alpha = (1f - off / 600f).coerceIn(0f, 1f)
                                translationY = off * 0.35f
                            }
                            .glow(colors.a, 190.dp, 0.5f),
                    ) {
                        if (playlistId != null) MosaicArt(tracks.take(4).map { it.uri }, title, Modifier.fillMaxSize().glass(RoundedCornerShape(34.dp), GlassLevel.Subtle), RoundedCornerShape(34.dp))
                        else Artwork(tracks.firstOrNull()?.uri, title, Modifier.fillMaxSize().glass(RoundedCornerShape(34.dp), GlassLevel.Subtle), RoundedCornerShape(34.dp), sizePx = 640)
                    }
                    Spacer(Modifier.height(22.dp))
                    Text(kindLabel, style = AuraType.Label, color = colors.c)
                    Spacer(Modifier.height(6.dp))
                    Text(title, style = AuraType.Headline, color = AuraColors.TextPrimary, textAlign = TextAlign.Center, maxLines = 2)
                    Spacer(Modifier.height(4.dp))
                    Text(subtitle, style = AuraType.Caption, color = AuraColors.TextSecondary, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(20.dp))
                    if (tracks.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            PrimaryButton("Play", { env.play(tracks) }, Modifier.weight(1f), Icons.Rounded.PlayArrow)
                            SoftButton("Shuffle", { env.play(tracks, 0, true) }, Modifier.weight(1f), Icons.Rounded.Shuffle)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                }
            }
            if (tracks.isEmpty() && playlistId != null) {
                item("empty") { EmptyState(Icons.Rounded.MusicNote, "Empty playlist", emptyHint) }
            }
            itemsIndexed(tracks, key = { _, t -> t.id }) { i, t ->
                TrackRow(
                    t, number = i + 1,
                    playlist = playlistId?.let { PlaylistCtx(it, i, tracks.size) },
                    onClick = { env.play(tracks, i) },
                )
            }
        }

        // Floating top controls
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            GlassIconButton(Icons.Rounded.ArrowBack, { env.nav.back() })
            val menu: ((androidx.compose.ui.geometry.Rect) -> Unit)? = onMenu ?: { rect ->
                env.overlay.menu(rect, listOf(
                    MenuItemSpec("Add all to playlist", Icons.Rounded.PlaylistAdd) { env.addToPlaylist(tracks) },
                    MenuItemSpec("Play next", Icons.Rounded.PlayArrow) { tracks.reversed().forEach { env.player.playNext(it) }; env.overlay.showToast("Playing next") },
                ))
            }
            GlassIconButton(Icons.Rounded.MoreHoriz, { menu?.invoke(anchor.rect) }, anchor.modifier)
        }
    }
}
