package com.aura.player.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.aura.player.data.MusicRepository
import com.aura.player.data.TrackEntity
import com.aura.player.ui.LocalAura
import com.aura.player.ui.LocalBottomPad
import com.aura.player.ui.Route
import com.aura.player.ui.components.AlbumCard
import com.aura.player.ui.components.ArtistRowItem
import com.aura.player.ui.components.ChipRow
import com.aura.player.ui.components.EmptyState
import com.aura.player.ui.components.GlassIconButton
import com.aura.player.ui.components.GlassTextField
import com.aura.player.ui.components.MenuItemSpec
import com.aura.player.ui.components.PrimaryButton
import com.aura.player.ui.components.ScreenTitle
import com.aura.player.ui.components.SoftButton
import com.aura.player.ui.components.TrackRow
import com.aura.player.ui.components.rememberAnchor
import com.aura.player.ui.glass.LocalReduceMotion
import com.aura.player.ui.glass.Spr
import com.aura.player.ui.glass.sprRaw
import com.aura.player.ui.theme.AuraColors
import com.aura.player.ui.theme.AuraType
import androidx.compose.material3.Text
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class LibraryViewModel(private val repo: MusicRepository) : ViewModel() {
    val query = MutableStateFlow("")
    val sort = MutableStateFlow(0)
    val songs: Flow<PagingData<TrackEntity>> = combine(query.debounce(120), sort) { q, s -> q.trim() to s }
        .flatMapLatest { (q, s) -> repo.songsPaged(q, s) }
        .cachedIn(viewModelScope)
}

private val SortNames = listOf("Title", "Artist", "Recently added", "Duration")
private val Pages = listOf("Albums", "Artists", "Songs", "Playlists", "Favorites")

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun LibraryScreen() {
    val env = LocalAura.current
    val nav = env.nav
    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Library")
        ChipRow(Pages, nav.libraryPage, { nav.libraryPage = it })
        Spacer(Modifier.height(12.dp))
        val reduce = LocalReduceMotion.current
        AnimatedContent(
            targetState = nav.libraryPage,
            transitionSpec = {
                (fadeIn(sprRaw(Spr.Soft, reduce)) + scaleIn(sprRaw(Spr.Soft, reduce), initialScale = 0.96f)) togetherWith
                    (fadeOut() + scaleOut(targetScale = 0.98f))
            },
            label = "libraryPage",
        ) { page ->
            when (page) {
                0 -> AlbumsPage()
                1 -> ArtistsPage()
                2 -> SongsPage()
                3 -> PlaylistsGrid()
                else -> FavoritesPage()
            }
        }
    }
}

@Composable
private fun AlbumsPage() {
    val env = LocalAura.current
    val albums by remember { env.repo.dao.albums() }.collectAsStateWithLifecycle(emptyList())
    if (albums.isEmpty()) { NoMusic(); return }
    LazyVerticalGrid(
        GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = LocalBottomPad.current),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        items(albums, key = { it.albumKey }) { a -> AlbumCard(a) }
    }
}

@Composable
private fun ArtistsPage() {
    val env = LocalAura.current
    val artists by remember { env.repo.dao.artists() }.collectAsStateWithLifecycle(emptyList())
    if (artists.isEmpty()) { NoMusic(); return }
    LazyColumn(contentPadding = PaddingValues(bottom = LocalBottomPad.current)) {
        items(artists, key = { it.artist }) { a -> ArtistRowItem(a) { env.openRoute(Route.Artist(a.artist)) } }
    }
}

@Composable
private fun SongsPage() {
    val env = LocalAura.current
    val app = env.app
    val vm: LibraryViewModel = viewModel(factory = viewModelFactory { initializer { LibraryViewModel(app.repo) } })
    val query by vm.query.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()
    val items = vm.songs.collectAsLazyPagingItems()
    val anchor = rememberAnchor()
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassTextField(query, { vm.query.value = it }, "Filter songs", Modifier.weight(1f), leading = Icons.Rounded.Search)
            GlassIconButton(Icons.Rounded.Sort, {
                env.overlay.menu(
                    anchor.rect,
                    SortNames.mapIndexed { i, n ->
                        MenuItemSpec(n, checked = i == sort) { vm.sort.value = i }
                    },
                )
            }, anchor.modifier, size = 52.dp)
        }
        Spacer(Modifier.height(8.dp))
        if (items.itemCount == 0 && query.isBlank()) { NoMusic(); return@Column }
        LazyColumn(contentPadding = PaddingValues(bottom = LocalBottomPad.current)) {
            items(count = items.itemCount, key = items.itemKey { it.id }) { i ->
                items[i]?.let { t ->
                    TrackRow(t, onClick = {
                        scope.launch {
                            val list = env.repo.songsList(query.trim(), sort)
                            env.play(list, list.indexOfFirst { it.id == t.id }.coerceAtLeast(0))
                        }
                    })
                }
            }
        }
    }
}

@Composable
private fun FavoritesPage() {
    val env = LocalAura.current
    val favs by remember { env.repo.dao.favorites() }.collectAsStateWithLifecycle(emptyList())
    if (favs.isEmpty()) {
        EmptyState(Icons.Rounded.Favorite, "No favorites yet", "Tap the heart on any song to keep it close.")
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = LocalBottomPad.current)) {
        item("actions") {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton("Play", { env.play(favs) }, Modifier.weight(1f), Icons.Rounded.PlayArrow)
                SoftButton("Shuffle", { env.play(favs, 0, true) }, Modifier.weight(1f), Icons.Rounded.Shuffle)
            }
            Spacer(Modifier.height(8.dp))
        }
        items(favs, key = { it.id }) { t -> TrackRow(t, onClick = { env.play(favs, favs.indexOf(t)) }) }
    }
}

@Composable
fun NoMusic() {
    EmptyState(Icons.Rounded.MusicNote, "Nothing here yet", "Allow access or add a folder in Settings to fill your library.")
}
