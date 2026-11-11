package com.aura.player.data

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import com.aura.player.moved
import kotlinx.coroutines.flow.Flow

private const val SONG_FILTER = """ FROM tracks WHERE (:q = '' OR title LIKE '%' || :q || '%' OR artist LIKE '%' || :q || '%' OR album LIKE '%' || :q || '%')
 ORDER BY CASE WHEN :sort = 0 THEN title END COLLATE NOCASE ASC,
 CASE WHEN :sort = 1 THEN artist END COLLATE NOCASE ASC,
 CASE WHEN :sort = 2 THEN dateAdded END DESC,
 CASE WHEN :sort = 3 THEN duration END DESC,
 title COLLATE NOCASE ASC"""

private const val ALBUM_SELECT = """SELECT albumKey, album, MIN(artist) AS artist, COUNT(*) AS trackCount,
 (SELECT uri FROM tracks t2 WHERE t2.albumKey = t.albumKey ORDER BY t2.trackNo, t2.id LIMIT 1) AS artUri,
 MAX(dateAdded) AS newest FROM tracks t"""

private const val ARTIST_SELECT = """SELECT artist, COUNT(*) AS trackCount, COUNT(DISTINCT albumKey) AS albumCount,
 (SELECT uri FROM tracks t2 WHERE t2.artist = t.artist ORDER BY t2.id LIMIT 1) AS artUri FROM tracks t"""

@Dao
abstract class MusicDao {
    // ---- tracks ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertTracks(list: List<TrackEntity>)

    @Query("DELETE FROM tracks WHERE source = :source")
    abstract suspend fun deleteBySource(source: Int)

    @Transaction
    open suspend fun replaceSource(source: Int, list: List<TrackEntity>) {
        deleteBySource(source)
        list.chunked(500).forEach { insertTracks(it) }
    }

    @Query("SELECT * $SONG_FILTER")
    abstract fun songsPaged(q: String, sort: Int): PagingSource<Int, TrackEntity>

    @Query("SELECT * $SONG_FILTER")
    abstract suspend fun songsList(q: String, sort: Int): List<TrackEntity>

    @Query("SELECT COUNT(*) FROM tracks")
    abstract fun trackCount(): Flow<Int>

    @Query("SELECT * FROM tracks ORDER BY dateAdded DESC LIMIT :n")
    abstract fun recentTracks(n: Int): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE id = :id")
    abstract suspend fun trackById(id: Long): TrackEntity?

    @Query("$ALBUM_SELECT GROUP BY albumKey ORDER BY album COLLATE NOCASE")
    abstract fun albums(): Flow<List<AlbumRow>>

    @Query("$ARTIST_SELECT GROUP BY artist ORDER BY artist COLLATE NOCASE")
    abstract fun artists(): Flow<List<ArtistRow>>

    @Query("SELECT * FROM tracks WHERE albumKey = :key ORDER BY trackNo, title COLLATE NOCASE")
    abstract fun tracksByAlbum(key: String): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE artist = :name ORDER BY album COLLATE NOCASE, trackNo, title COLLATE NOCASE")
    abstract fun tracksByArtist(name: String): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE artist = :name ORDER BY RANDOM() LIMIT 1")
    abstract suspend fun anyTrackBy(name: String): TrackEntity?

    // ---- search ----
    @Query("SELECT * FROM tracks WHERE title LIKE '%' || :q || '%' OR artist LIKE '%' || :q || '%' OR album LIKE '%' || :q || '%' ORDER BY title COLLATE NOCASE LIMIT 40")
    abstract fun searchTracks(q: String): Flow<List<TrackEntity>>

    @Query("$ALBUM_SELECT WHERE album LIKE '%' || :q || '%' GROUP BY albumKey ORDER BY album COLLATE NOCASE LIMIT 20")
    abstract fun searchAlbums(q: String): Flow<List<AlbumRow>>

    @Query("$ARTIST_SELECT WHERE artist LIKE '%' || :q || '%' GROUP BY artist ORDER BY artist COLLATE NOCASE LIMIT 20")
    abstract fun searchArtists(q: String): Flow<List<ArtistRow>>

    @Query("SELECT * FROM searches ORDER BY ts DESC LIMIT 8")
    abstract fun recentSearches(): Flow<List<SearchEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertSearch(s: SearchEntity)

    @Query("DELETE FROM searches WHERE text = :text")
    abstract suspend fun deleteSearch(text: String)

    @Query("DELETE FROM searches")
    abstract suspend fun clearSearches()

    // ---- favorites ----
    @Query("SELECT t.* FROM tracks t JOIN favorites f ON f.trackId = t.id ORDER BY f.addedAt DESC")
    abstract fun favorites(): Flow<List<TrackEntity>>

    @Query("SELECT trackId FROM favorites")
    abstract fun favoriteIds(): Flow<List<Long>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun addFavorite(f: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE trackId = :id")
    abstract suspend fun removeFavorite(id: Long)

    @Query("SELECT COUNT(*) FROM favorites WHERE trackId = :id")
    abstract suspend fun isFavorite(id: Long): Int

    // ---- playlists ----
    @Query(
        """SELECT p.id AS id, p.name AS name, p.createdAt AS createdAt,
        (SELECT COUNT(*) FROM playlist_entries e JOIN tracks t ON t.id = e.trackId WHERE e.playlistId = p.id) AS trackCount
        FROM playlists p ORDER BY p.createdAt DESC"""
    )
    abstract fun playlists(): Flow<List<PlaylistRow>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    abstract fun playlist(id: Long): Flow<PlaylistEntity?>

    @Query("SELECT t.* FROM tracks t JOIN playlist_entries e ON e.trackId = t.id WHERE e.playlistId = :id ORDER BY e.position")
    abstract fun playlistTracks(id: Long): Flow<List<TrackEntity>>

    @Insert
    abstract suspend fun insertPlaylist(p: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name WHERE id = :id")
    abstract suspend fun renamePlaylist(id: Long, name: String)

    @Query("DELETE FROM playlists WHERE id = :id")
    abstract suspend fun deletePlaylistRow(id: Long)

    @Query("DELETE FROM playlist_entries WHERE playlistId = :id")
    abstract suspend fun deleteEntriesOf(id: Long)

    @Transaction
    open suspend fun deletePlaylist(id: Long) {
        deleteEntriesOf(id)
        deletePlaylistRow(id)
    }

    @Query("SELECT * FROM playlist_entries WHERE playlistId = :id ORDER BY position")
    abstract suspend fun entries(id: Long): List<PlaylistEntry>

    @Insert
    abstract suspend fun insertEntry(e: PlaylistEntry)

    @Update
    abstract suspend fun updateEntry(e: PlaylistEntry)

    @Query("DELETE FROM playlist_entries WHERE playlistId = :pid AND trackId = :tid")
    abstract suspend fun removeEntry(pid: Long, tid: Long)

    @Transaction
    open suspend fun addToPlaylist(pid: Long, trackIds: List<Long>): Int {
        val existing = entries(pid)
        val have = existing.map { it.trackId }.toHashSet()
        var pos = (existing.maxOfOrNull { it.position } ?: -1) + 1
        var added = 0
        for (tid in trackIds) {
            if (have.add(tid)) {
                insertEntry(PlaylistEntry(playlistId = pid, trackId = tid, position = pos++))
                added++
            }
        }
        return added
    }

    @Transaction
    open suspend fun moveEntry(pid: Long, from: Int, to: Int) {
        val list = entries(pid).moved(from, to)
        list.forEachIndexed { i, e -> if (e.position != i) updateEntry(e.copy(position = i)) }
    }

    // ---- history ----
    @Insert
    abstract suspend fun insertHistory(h: HistoryEntity)

    @Query(
        """SELECT t.* FROM tracks t JOIN (SELECT trackId, MAX(playedAt) AS p FROM history GROUP BY trackId) h
        ON h.trackId = t.id ORDER BY h.p DESC LIMIT :n"""
    )
    abstract fun recentlyPlayed(n: Int): Flow<List<TrackEntity>>

    @Query("DELETE FROM history")
    abstract suspend fun clearHistory()
}

@Database(
    entities = [
        TrackEntity::class, FavoriteEntity::class, PlaylistEntity::class,
        PlaylistEntry::class, HistoryEntity::class, SearchEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AuraDatabase : RoomDatabase() {
    abstract fun dao(): MusicDao
}
