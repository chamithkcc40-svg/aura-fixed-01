package com.aura.player.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** source: 0 = MediaStore, 1 = SAF folder */
@Entity(tableName = "tracks", indices = [Index("albumKey"), Index("artist"), Index("dateAdded")])
data class TrackEntity(
    @PrimaryKey val id: Long,
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumKey: String,
    val albumId: Long,
    val duration: Long,
    val dateAdded: Long,
    val trackNo: Int,
    val source: Int,
)

@Entity(tableName = "favorites")
data class FavoriteEntity(@PrimaryKey val trackId: Long, val addedAt: Long)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
)

@Entity(tableName = "playlist_entries", indices = [Index("playlistId")])
data class PlaylistEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val trackId: Long,
    val position: Int,
)

@Entity(tableName = "history", indices = [Index("trackId")])
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: Long,
    val playedAt: Long,
)

@Entity(tableName = "searches")
data class SearchEntity(@PrimaryKey val text: String, val ts: Long)

data class AlbumRow(
    val albumKey: String,
    val album: String,
    val artist: String,
    val trackCount: Int,
    val artUri: String?,
    val newest: Long,
)

data class ArtistRow(
    val artist: String,
    val trackCount: Int,
    val albumCount: Int,
    val artUri: String?,
)

data class PlaylistRow(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val trackCount: Int,
)
