package com.aura.player.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.DocumentsContract
import androidx.core.content.ContextCompat
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

class MusicRepository(private val ctx: Context, db: AuraDatabase) {
    val dao: MusicDao = db.dao()
    private val scanner = MediaScanner(ctx)

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning

    fun hasAudioPermission(): Boolean {
        val p = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        return ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
    }

    /** Persisted SAF folders the user granted. */
    fun grantedFolders() = ctx.contentResolver.persistedUriPermissions
        .filter { it.isReadPermission && DocumentsContract.isTreeUri(it.uri) }
        .map { it.uri }

    suspend fun rescan() = withContext(Dispatchers.IO) {
        if (_scanning.value) return@withContext
        _scanning.value = true
        try {
            val ms = if (hasAudioPermission()) scanner.scanMediaStore() else emptyList()
            dao.replaceSource(0, ms)
            val saf = grantedFolders().flatMap { scanner.scanTree(it) }.distinctBy { it.id }
            dao.replaceSource(1, saf)
        } finally {
            _scanning.value = false
        }
    }

    fun songsPaged(q: String, sort: Int): Flow<PagingData<TrackEntity>> =
        Pager(PagingConfig(pageSize = 40, prefetchDistance = 30, enablePlaceholders = false)) { dao.songsPaged(q, sort) }.flow

    suspend fun songsList(q: String, sort: Int) = dao.songsList(q, sort)

    suspend fun toggleFavorite(id: Long) {
        if (dao.isFavorite(id) > 0) dao.removeFavorite(id) else dao.addFavorite(FavoriteEntity(id, System.currentTimeMillis()))
    }

    suspend fun createPlaylist(name: String): Long =
        dao.insertPlaylist(PlaylistEntity(name = name.trim().ifEmpty { "New Playlist" }, createdAt = System.currentTimeMillis()))

    suspend fun addToPlaylist(pid: Long, ids: List<Long>) = dao.addToPlaylist(pid, ids)

    suspend fun recordPlay(id: Long) = dao.insertHistory(HistoryEntity(trackId = id, playedAt = System.currentTimeMillis()))

    suspend fun saveSearch(text: String) {
        val t = text.trim()
        if (t.length >= 2) dao.upsertSearch(SearchEntity(t, System.currentTimeMillis()))
    }
}
