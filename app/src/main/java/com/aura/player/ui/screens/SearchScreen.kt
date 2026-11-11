package com.aura.player.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aura.player.ui.LocalAura
import com.aura.player.ui.LocalBottomPad
import com.aura.player.ui.Route
import com.aura.player.ui.components.AlbumCard
import com.aura.player.ui.components.Artwork
import com.aura.player.ui.components.GlassTextField
import com.aura.player.ui.components.SectionHeader
import com.aura.player.ui.components.TrackRow
import com.aura.player.ui.glass.GlassLevel
import com.aura.player.ui.glass.LocalReduceMotion
import com.aura.player.ui.glass.Spr
import com.aura.player.ui.glass.glass
import com.aura.player.ui.glass.pressable
import com.aura.player.ui.glass.sprRaw
import com.aura.player.ui.theme.AuraColors
import com.aura.player.ui.theme.AuraType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@Composable
fun SearchScreen() {
    val env = LocalAura.current
    val dao = env.repo.dao
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    val reduce = LocalReduceMotion.current

    val q = remember { MutableStateFlow("") }
    LaunchedEffect(query) { q.value = query.trim() }
    val songs by remember { q.debounce(140).flatMapLatest { if (it.isEmpty()) kotlinx.coroutines.flow.flowOf(emptyList()) else dao.searchTracks(it) } }.collectAsStateWithLifecycle(emptyList())
    val albums by remember { q.debounce(140).flatMapLatest { if (it.isEmpty()) kotlinx.coroutines.flow.flowOf(emptyList()) else dao.searchAlbums(it) } }.collectAsStateWithLifecycle(emptyList())
    val artists by remember { q.debounce(140).flatMapLatest { if (it.isEmpty()) kotlinx.coroutines.flow.flowOf(emptyList()) else dao.searchArtists(it) } }.collectAsStateWithLifecycle(emptyList())
    val recents by remember { dao.recentSearches() }.collectAsStateWithLifecycle(emptyList())
    val suggestArtists by remember { dao.artists().map { it.sortedByDescending { a -> a.trackCount }.take(8) } }.collectAsStateWithLifecycle(emptyList())
    val suggestAlbums by remember { dao.albums().map { it.take(10) } }.collectAsStateWithLifecycle(emptyList())

    Column(Modifier.fillMaxSize()) {
        // Title collapses when the user starts searching (animated).
        AnimatedVisibility(
            visible = !focused && query.isEmpty(),
            enter = expandVertically(sprRaw(Spr.Soft, reduce)) + fadeIn(),
            exit = shrinkVertically(sprRaw(Spr.Snappy, reduce)) + fadeOut(),
        ) {
            com.aura.player.ui.components.ScreenTitle("Search")
        }
        val topPad by androidx.compose.animation.core.animateDpAsState(
            if (!focused && query.isEmpty()) 0.dp else 14.dp, sprRaw(Spr.Snappy, reduce), label = "searchTop",
        )
        GlassTextField(
            query, { query = it }, "Songs, albums, artists",
            Modifier.padding(horizontal = 20.dp).fillMaxWidth()
                .then(if (!focused && query.isEmpty()) Modifier else Modifier.statusBarsPadding())
                .padding(top = topPad),
            leading = Icons.Rounded.Search,
            onSearch = { scope.launch { dao.upsertSearch(com.aura.player.data.SearchEntity(query.trim(), System.currentTimeMillis())) } },
            onFocus = { focused = it },
        )
        Spacer(Modifier.height(16.dp))

        AnimatedContent(
            targetState = query.isBlank(),
            transitionSpec = { (fadeIn(tween(220)) + slideInVertically(sprRaw(Spr.Soft, reduce)) { it / 24 }) togetherWith fadeOut(tween(120)) },
            label = "searchState",
        ) { empty ->
            if (empty) {
                LazyColumn(contentPadding = PaddingValues(bottom = LocalBottomPad.current)) {
                    if (recents.isNotEmpty()) {
                        item("recents") {
                            SectionHeader("Recent", action = "Clear", onAction = { scope.launch { dao.clearSearches() } })
                            Spacer(Modifier.height(8.dp))
                            recents.forEach { r ->
                                Row(
                                    Modifier.fillMaxWidth().pressable({ query = r.text }, scaleTo = 0.98f).padding(horizontal = 20.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                ) {
                                    Icon(Icons.Rounded.History, null, tint = AuraColors.TextTertiary, modifier = Modifier.size(20.dp))
                                    Text(r.text, style = AuraType.Body, color = AuraColors.TextPrimary, modifier = Modifier.weight(1f), maxLines = 1)
                                    Icon(
                                        Icons.Rounded.Close, null, tint = AuraColors.TextTertiary,
                                        modifier = Modifier.size(32.dp).pressable({ scope.launch { dao.deleteSearch(r.text) } }, scaleTo = 0.8f).padding(6.dp),
                                    )
                                }
                            }
                            Spacer(Modifier.height(18.dp))
                        }
                    }
                    if (suggestArtists.isNotEmpty()) {
                        item("suggest") {
                            SectionHeader("Suggested artists")
                            Spacer(Modifier.height(12.dp))
                            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                items(suggestArtists, key = { it.artist }) { a ->
                                    Column(
                                        Modifier.width(88.dp).pressable({ env.openRoute(Route.Artist(a.artist)) }, scaleTo = 0.93f),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        Artwork(a.artUri, a.artist, Modifier.size(88.dp).glass(CircleShape, GlassLevel.Subtle), CircleShape, sizePx = 200)
                                        Spacer(Modifier.height(8.dp))
                                        Text(a.artist, style = AuraType.Caption, color = AuraColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                            Spacer(Modifier.height(22.dp))
                            SectionHeader("Try an album")
                            Spacer(Modifier.height(12.dp))
                            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                items(suggestAlbums, key = { it.albumKey }) { a -> AlbumCard(a, Modifier.width(132.dp)) }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = LocalBottomPad.current)) {
                    if (artists.isNotEmpty()) {
                        item("artists") {
                            SectionHeader("Artists"); Spacer(Modifier.height(10.dp))
                            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                items(artists, key = { it.artist }) { a ->
                                    Column(
                                        Modifier.width(84.dp).pressable({
                                            scope.launch { env.repo.saveSearch(query) }
                                            env.openRoute(Route.Artist(a.artist))
                                        }, scaleTo = 0.93f),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        Artwork(a.artUri, a.artist, Modifier.size(84.dp), CircleShape, sizePx = 200)
                                        Spacer(Modifier.height(6.dp))
                                        Text(a.artist, style = AuraType.Caption, color = AuraColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                            Spacer(Modifier.height(20.dp))
                        }
                    }
                    if (albums.isNotEmpty()) {
                        item("albums") {
                            SectionHeader("Albums"); Spacer(Modifier.height(10.dp))
                            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                items(albums, key = { it.albumKey }) { a -> AlbumCard(a, Modifier.width(132.dp)) }
                            }
                            Spacer(Modifier.height(20.dp))
                        }
                    }
                    if (songs.isNotEmpty()) {
                        item("songsHead") { SectionHeader("Songs"); Spacer(Modifier.height(6.dp)) }
                        items(songs, key = { it.id }) { t ->
                            TrackRow(t, onClick = {
                                scope.launch { env.repo.saveSearch(query) }
                                env.play(songs, songs.indexOf(t))
                            })
                        }
                    }
                    if (songs.isEmpty() && albums.isEmpty() && artists.isEmpty()) {
                        item("none") {
                            Text(
                                "No results for “${query.trim()}”", style = AuraType.Body, color = AuraColors.TextSecondary,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
