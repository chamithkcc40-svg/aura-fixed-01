package com.aura.player.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.aura.player.data.MusicRepository
import com.aura.player.data.TrackEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class QueueItem(
    val mediaId: String,
    val title: String,
    val artist: String,
    val artUri: String?,
)

data class PlayerState(
    val current: QueueItem? = null,
    val index: Int = -1,
    val isPlaying: Boolean = false,
    val shuffle: Boolean = false,
    val repeat: Int = Player.REPEAT_MODE_OFF,
    val duration: Long = 0L,
)

/**
 * UI-facing wrapper around a MediaController connected to [PlaybackService].
 * Playback position is deliberately NOT part of [PlayerState]; it is polled by the
 * progress widgets only, so the rest of the UI never recomposes at 4 Hz.
 */
class PlayerController(
    private val ctx: Context,
    private val scope: CoroutineScope,
    private val repo: MusicRepository,
) {
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state

    private val _queue = MutableStateFlow<List<QueueItem>>(emptyList())
    val queue: StateFlow<List<QueueItem>> = _queue

    private var controller: MediaController? = null
    private val pending = ArrayList<(MediaController) -> Unit>()

    private val listener = object : Player.Listener {
        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) = refreshQueue()
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            refreshCore()
            mediaItem?.mediaId?.toLongOrNull()?.let { id ->
                scope.launch(Dispatchers.IO) {
                    try { repo.recordPlay(id) } catch (e: Exception) { Log.w(TAG, "recordPlay failed", e) }
                }
            }
        }
        override fun onEvents(player: Player, events: Player.Events) = refreshCore()
    }

    init {
        connect(attempt = 0)
    }

    private fun connect(attempt: Int) {
        val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        val future = MediaController.Builder(ctx, token).buildAsync()
        future.addListener({
            try {
                val c = future.get()
                controller = c
                c.addListener(listener)
                refreshQueue()
                refreshCore()
                val queued = pending.toList()
                pending.clear()
                queued.forEach { safely(c, it) }
            } catch (e: Exception) {
                Log.e(TAG, "MediaController connection failed (attempt $attempt)", e)
                if (attempt < 2) scope.launch { kotlinx.coroutines.delay(800L); connect(attempt + 1) }
            }
        }, ContextCompat.getMainExecutor(ctx))
    }

    /** Every controller command goes through here so a bad player state can never crash the UI thread. */
    private fun safely(c: MediaController, block: (MediaController) -> Unit) {
        try {
            block(c)
        } catch (e: Exception) {
            Log.w(TAG, "Player command failed", e)
        }
    }

    private fun withController(block: (MediaController) -> Unit) {
        val c = controller
        if (c != null) safely(c, block) else pending += block
    }

    private fun MediaItem.toQueueItem() = QueueItem(
        mediaId = mediaId,
        title = mediaMetadata.title?.toString().orEmpty(),
        artist = mediaMetadata.artist?.toString().orEmpty(),
        artUri = mediaMetadata.extras?.getString("uri"),
    )

    private fun refreshQueue() {
        val c = controller ?: return
        try {
            _queue.value = List(c.mediaItemCount) { c.getMediaItemAt(it).toQueueItem() }
        } catch (e: Exception) {
            Log.w(TAG, "refreshQueue failed", e)
        }
    }

    private fun refreshCore() {
        val c = controller ?: return
        try {
            val count = c.mediaItemCount
            val item = if (count > 0) c.currentMediaItem?.toQueueItem() else null
            // Empty / transitional timelines report index 0 or C.INDEX_UNSET; normalise to "no track".
            val index = if (item == null) -1 else c.currentMediaItemIndex.coerceIn(0, count - 1)
            _state.update {
                it.copy(
                    current = item,
                    index = index,
                    isPlaying = c.isPlaying,
                    shuffle = c.shuffleModeEnabled,
                    repeat = c.repeatMode,
                    duration = c.duration.takeIf { d -> d > 0 } ?: 0L,
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "refreshCore failed", e)
        }
    }

    private fun TrackEntity.toMediaItem(): MediaItem {
        val art = if (albumId > 0) Uri.parse("content://media/external/audio/albumart/$albumId") else null
        val extras = Bundle().apply {
            putString("uri", uri)
            putString("albumKey", albumKey)
            putString("artist", artist)
        }
        return MediaItem.Builder()
            .setMediaId(id.toString())
            .setUri(uri)
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(Uri.parse(uri)).build())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title).setArtist(artist).setAlbumTitle(album)
                    .setArtworkUri(art).setExtras(extras).build(),
            )
            .build()
    }

    // ---- commands ----
    fun play(tracks: List<TrackEntity>, startIndex: Int = 0, shuffle: Boolean = false) {
        if (tracks.isEmpty()) return
        withController { c ->
            c.shuffleModeEnabled = shuffle
            c.setMediaItems(tracks.map { it.toMediaItem() }, startIndex.coerceIn(0, tracks.lastIndex), 0L)
            c.prepare()
            c.play()
        }
    }

    fun playNext(t: TrackEntity) = withController { c ->
        if (c.mediaItemCount == 0) play(listOf(t))
        else c.addMediaItem(c.currentMediaItemIndex + 1, t.toMediaItem())
    }

    fun addToQueue(t: TrackEntity) = withController { c ->
        if (c.mediaItemCount == 0) play(listOf(t)) else c.addMediaItem(t.toMediaItem())
    }

    fun togglePlay() = withController { c -> if (c.isPlaying) c.pause() else c.play() }
    fun next() = withController { it.seekToNextMediaItem() }
    fun previous() = withController {
        if (it.currentPosition > 3000) it.seekTo(0) else it.seekToPreviousMediaItem()
    }
    fun seekTo(ms: Long) = withController { it.seekTo(ms) }
    fun toggleShuffle() = withController { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    fun cycleRepeat() = withController {
        it.repeatMode = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }
    fun jumpTo(i: Int) = withController { if (i in 0 until it.mediaItemCount) { it.seekToDefaultPosition(i); it.play() } }
    fun removeAt(i: Int) = withController { if (i in 0 until it.mediaItemCount) it.removeMediaItem(i) }
    fun moveQueueItem(from: Int, to: Int) = withController {
        if (from in 0 until it.mediaItemCount && to in 0 until it.mediaItemCount) it.moveMediaItem(from, to)
    }

    /** Polled by the progress widgets. Always >= 0, never throws (the controller may be mid-connect or released). */
    fun position(): Long = try {
        (controller?.currentPosition ?: 0L).coerceAtLeast(0L)
    } catch (e: Exception) {
        0L
    }

    fun duration(): Long = try {
        controller?.duration?.takeIf { it > 0 } ?: 0L
    } catch (e: Exception) {
        0L
    }

    private companion object {
        const val TAG = "AuraPlayer"
    }
}
