package com.aura.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.aura.player.art.AmbientColors
import com.aura.player.art.ArtModel
import com.aura.player.art.ArtPalette
import com.aura.player.data.AlbumRow
import com.aura.player.data.ArtistRow
import com.aura.player.data.PlaylistRow
import com.aura.player.data.TrackEntity
import com.aura.player.formatDuration
import com.aura.player.hueFor
import com.aura.player.ui.LocalAura
import com.aura.player.ui.LocalCurrentId
import com.aura.player.ui.LocalFavorites
import com.aura.player.ui.glass.GlassLevel
import com.aura.player.ui.glass.glass
import com.aura.player.ui.glass.pressable
import com.aura.player.ui.theme.AuraColors
import com.aura.player.ui.theme.AuraType

/** Artwork with a deterministic gradient placeholder underneath (shown when a file has no art). */
@Composable
fun Artwork(
    uri: String?,
    seed: String,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    sizePx: Int = 360,
) {
    val hue = remember(seed) { hueFor(seed) }
    val placeholder = remember(hue) {
        Brush.linearGradient(listOf(Color.hsv(hue, 0.55f, 0.62f), Color.hsv((hue + 55f) % 360f, 0.65f, 0.28f)))
    }
    val ctx = LocalContext.current
    val request = remember(uri, sizePx) {
        uri?.let { ImageRequest.Builder(ctx).data(ArtModel(it)).size(sizePx).crossfade(220).build() }
    }
    Box(modifier.clip(shape).background(placeholder), contentAlignment = Alignment.Center) {
        if (request == null) {
            Icon(Icons.Rounded.MusicNote, null, tint = Color.White.copy(alpha = 0.35f), modifier = Modifier.fillMaxSize(0.38f))
        } else {
            AsyncImage(model = request, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

/** Ambient colours derived from artwork; keeps previous colours until the new ones are ready. */
@Composable
fun rememberArtColors(uri: String?, fallback: AmbientColors): AmbientColors {
    val ctx = LocalContext.current
    val loader = ctx.imageLoader
    val derived by produceState<AmbientColors?>(null, uri) {
        value = if (uri == null) null else ArtPalette.colorsFor(ctx, loader, uri)
    }
    return derived ?: fallback
}

@Composable
fun AlbumCard(
    artUri: String?,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Column(modifier.pressable(onClick, onLongClick, scaleTo = 0.95f)) {
        Artwork(
            artUri, title,
            Modifier.fillMaxWidth().aspectRatio(1f).glass(RoundedCornerShape(22.dp), GlassLevel.Subtle),
            RoundedCornerShape(22.dp),
        )
        Spacer(Modifier.height(10.dp))
        Text(title, style = AuraType.Body, color = AuraColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, style = AuraType.Caption, color = AuraColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun AlbumCard(a: AlbumRow, modifier: Modifier = Modifier) {
    val env = LocalAura.current
    AlbumCard(
        a.artUri, a.album, "${a.artist} · ${a.trackCount}", modifier,
        onClick = { env.openRoute(com.aura.player.ui.Route.Album(a.albumKey)) },
        onLongClick = { env.playFlow(env.repo.dao.tracksByAlbum(a.albumKey)) },
    )
}

@Composable
fun TrackCard(t: TrackEntity, modifier: Modifier = Modifier, onClick: () -> Unit) =
    AlbumCard(t.uri, t.title, t.artist, modifier, onClick)

@Composable
fun TrackRow(
    track: TrackEntity,
    modifier: Modifier = Modifier,
    number: Int? = null,
    onClick: () -> Unit,
    playlist: com.aura.player.ui.PlaylistCtx? = null,
) {
    val env = LocalAura.current
    val isCurrent = LocalCurrentId.current == track.id.toString()
    val isFav = track.id in LocalFavorites.current
    val anchor = rememberAnchor()
    val openMenu = { env.trackMenu(anchor.rect, track, isFav, playlist) }
    Row(
        modifier
            .fillMaxWidth()
            .highlightClick(RoundedCornerShape(20.dp), onClick = onClick, onLongClick = openMenu)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (number != null) {
            Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
                if (isCurrent) Icon(Icons.Rounded.GraphicEq, null, tint = AuraColors.Cyan, modifier = Modifier.size(20.dp))
                else Text("$number", style = AuraType.Body, color = AuraColors.TextTertiary)
            }
        } else {
            Artwork(track.uri, track.album, Modifier.size(52.dp), RoundedCornerShape(14.dp), sizePx = 140)
        }
        Column(Modifier.weight(1f)) {
            Text(
                track.title, style = AuraType.Body,
                color = if (isCurrent) AuraColors.Cyan else AuraColors.TextPrimary,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (number != null) formatDuration(track.duration) else "${track.artist} · ${formatDuration(track.duration)}",
                style = AuraType.Caption, color = AuraColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (isFav) Icon(Icons.Rounded.Favorite, null, tint = AuraColors.Pink, modifier = Modifier.size(16.dp))
        PlainIconButton(Icons.Rounded.MoreVert, openMenu, anchor.modifier, size = 36.dp, iconSize = 20.dp)
    }
}

@Composable
fun ArtistRowItem(a: ArtistRow, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().highlightClick(RoundedCornerShape(20.dp), onClick).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Artwork(a.artUri, a.artist, Modifier.size(58.dp), CircleShape, sizePx = 160)
        Column(Modifier.weight(1f)) {
            Text(a.artist, style = AuraType.Body, color = AuraColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${a.albumCount} ${if (a.albumCount == 1) "album" else "albums"} · ${a.trackCount} songs",
                style = AuraType.Caption, color = AuraColors.TextSecondary,
            )
        }
    }
}

/** 2x2 mosaic (or single image) of playlist artwork. */
@Composable
fun MosaicArt(uris: List<String>, seed: String, modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(26.dp)) {
    if (uris.size < 4) {
        Artwork(uris.firstOrNull(), seed, modifier, shape, sizePx = 420)
    } else {
        Column(modifier.clip(shape)) {
            for (r in 0..1) Row(Modifier.weight(1f)) {
                for (c in 0..1) Artwork(uris[r * 2 + c], seed + "$r$c", Modifier.weight(1f).fillMaxHeight(), RoundedCornerShape(0.dp), sizePx = 220)
            }
        }
    }
}

@Composable
fun PlaylistCard(p: PlaylistRow, modifier: Modifier = Modifier, onClick: () -> Unit, onMenu: (androidx.compose.ui.geometry.Rect) -> Unit) {
    val env = LocalAura.current
    val tracks by remember(p.id) { env.repo.dao.playlistTracks(p.id) }.collectAsState(emptyList())
    val anchor = rememberAnchor()
    Column(modifier.then(anchor.modifier).pressable(onClick, { onMenu(anchor.rect) }, scaleTo = 0.95f)) {
        MosaicArt(
            tracks.take(4).map { it.uri }, p.name,
            Modifier.fillMaxWidth().aspectRatio(1f).glass(RoundedCornerShape(26.dp), GlassLevel.Subtle),
        )
        Spacer(Modifier.height(10.dp))
        Text(p.name, style = AuraType.Body, color = AuraColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${p.trackCount} songs", style = AuraType.Caption, color = AuraColors.TextSecondary)
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actions: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 24.dp)
            .glass(RoundedCornerShape(32.dp), GlassLevel.Regular).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(64.dp).glass(CircleShape, GlassLevel.Strong), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = AuraColors.Cyan, modifier = Modifier.size(30.dp))
        }
        Text(title, style = AuraType.Title, color = AuraColors.TextPrimary)
        Text(message, style = AuraType.Body, color = AuraColors.TextSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        actions()
    }
}

@Composable
fun ScreenTitle(title: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = AuraType.Display, color = AuraColors.TextPrimary, modifier = Modifier.weight(1f))
        trailing()
    }
}
