package com.aura.player.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aura.player.data.TrackEntity
import com.aura.player.ui.LocalAura
import com.aura.player.ui.components.Artwork
import com.aura.player.ui.components.MenuItemSpec
import com.aura.player.ui.components.PlainIconButton
import com.aura.player.ui.components.highlightClick
import com.aura.player.ui.components.rememberAnchor
import com.aura.player.ui.glass.GlassLevel
import com.aura.player.ui.glass.glass
import com.aura.player.ui.theme.AuraColors
import com.aura.player.ui.theme.AuraType
import kotlinx.coroutines.launch

@Composable
fun AddToPlaylistSheet(tracks: List<TrackEntity>, dismiss: () -> Unit) {
    val env = LocalAura.current
    val playlists by remember { env.repo.dao.playlists() }.collectAsStateWithLifecycle(emptyList())
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
        Text("Add to playlist", style = AuraType.Title, color = AuraColors.TextPrimary)
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item("new") {
                Row(
                    Modifier.fillMaxWidth().height(60.dp).glass(RoundedCornerShape(20.dp), GlassLevel.Subtle)
                        .highlightClick(RoundedCornerShape(20.dp), onClick = {
                            dismiss()
                            promptNewPlaylist(env) { id ->
                                env.scope.launch {
                                    env.repo.addToPlaylist(id, tracks.map { it.id })
                                    env.overlay.showToast("Added to playlist")
                                }
                            }
                        }).padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(Modifier.size(40.dp).glass(CircleShape, GlassLevel.Strong), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Add, null, tint = AuraColors.Cyan)
                    }
                    Text("New playlist", style = AuraType.Body, color = AuraColors.TextPrimary)
                }
            }
            itemsIndexed(playlists, key = { _, p -> p.id }) { _, p ->
                Row(
                    Modifier.fillMaxWidth().height(60.dp)
                        .highlightClick(RoundedCornerShape(20.dp), onClick = {
                            dismiss()
                            env.scope.launch {
                                val n = env.repo.addToPlaylist(p.id, tracks.map { it.id })
                                env.overlay.showToast(if (n > 0) "Added to ${p.name}" else "Already in ${p.name}")
                            }
                        }).padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(p.name, style = AuraType.Body, color = AuraColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${p.trackCount} songs", style = AuraType.Caption, color = AuraColors.TextSecondary)
                    }
                }
            }
        }
    }
}

/** Up Next: tap to jump, per-item menu to reorder or remove. Rows animate with default lazy-list springs. */
@Composable
fun QueueSheet(dismiss: () -> Unit) {
    val env = LocalAura.current
    val queue by env.player.queue.collectAsStateWithLifecycle()
    val ps by env.player.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) { if (ps.index > 1) listState.scrollToItem(ps.index - 1) }
    Column(Modifier.padding(bottom = 12.dp)) {
        Text("Up next", style = AuraType.Title, color = AuraColors.TextPrimary, modifier = Modifier.padding(horizontal = 24.dp))
        Text("${queue.size} songs", style = AuraType.Caption, color = AuraColors.TextSecondary, modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp))
        Spacer(Modifier.height(10.dp))
        LazyColumn(state = listState, contentPadding = PaddingValues(horizontal = 12.dp)) {
            itemsIndexed(queue, key = { i, q -> "${q.mediaId}#$i" }) { i, q ->
                val current = i == ps.index
                val anchor = rememberAnchor()
                Row(
                    Modifier.fillMaxWidth().animateItem()
                        .highlightClick(RoundedCornerShape(18.dp), onClick = { env.player.jumpTo(i) })
                        .padding(horizontal = 8.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Artwork(q.artUri, q.title, Modifier.size(46.dp), RoundedCornerShape(12.dp), sizePx = 120)
                    Column(Modifier.weight(1f)) {
                        Text(q.title, style = AuraType.Body, color = if (current) AuraColors.Cyan else AuraColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(q.artist, style = AuraType.Caption, color = AuraColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (current) Icon(Icons.Rounded.GraphicEq, null, tint = AuraColors.Cyan, modifier = Modifier.size(20.dp))
                    PlainIconButton(Icons.Rounded.MoreVert, {
                        env.overlay.menu(anchor.rect, listOf(
                            MenuItemSpec("Play now", Icons.Rounded.PlayArrow) { env.player.jumpTo(i) },
                            MenuItemSpec("Move up", Icons.Rounded.ArrowUpward) { env.player.moveQueueItem(i, i - 1) },
                            MenuItemSpec("Move down", Icons.Rounded.ArrowDownward) { env.player.moveQueueItem(i, i + 1) },
                            MenuItemSpec("Remove", Icons.Rounded.Delete, danger = true) { env.player.removeAt(i) },
                        ))
                    }, anchor.modifier, size = 36.dp, iconSize = 20.dp)
                }
            }
        }
    }
}
