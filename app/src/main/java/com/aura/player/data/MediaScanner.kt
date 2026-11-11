package com.aura.player.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.aura.player.albumKeyOf
import com.aura.player.cleanTag
import com.aura.player.normalizeTrackNumber
import kotlin.math.absoluteValue

/** Reads music from MediaStore and from user-granted SAF folders. Blocking: call from IO. */
class MediaScanner(private val ctx: Context) {

    fun scanMediaStore(): List<TrackEntity> {
        val out = ArrayList<TrackEntity>()
        val proj = arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED, MediaStore.Audio.Media.TRACK,
        )
        val sel = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 20000"
        val base = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        ctx.contentResolver.query(base, proj, sel, null, "${MediaStore.Audio.Media.DATE_ADDED} DESC")?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val iTitle = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val iArtist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val iAlbum = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val iAlbumId = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val iDur = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val iAdded = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val iTrack = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            while (c.moveToNext()) {
                val id = c.getLong(iId)
                val album = cleanTag(c.getString(iAlbum), "Unknown Album")
                val artist = cleanTag(c.getString(iArtist), "Unknown Artist")
                val albumId = c.getLong(iAlbumId)
                out += TrackEntity(
                    id = id,
                    uri = Uri.withAppendedPath(base, id.toString()).toString(),
                    title = cleanTag(c.getString(iTitle), "Untitled"),
                    artist = artist,
                    album = album,
                    albumKey = albumKeyOf(albumId, album, artist),
                    albumId = albumId,
                    duration = c.getLong(iDur),
                    dateAdded = c.getLong(iAdded),
                    trackNo = normalizeTrackNumber(c.getInt(iTrack)),
                    source = 0,
                )
            }
        }
        return out
    }

    /** Walks a SAF tree and reads tags with MediaMetadataRetriever. */
    fun scanTree(tree: Uri): List<TrackEntity> {
        val out = ArrayList<TrackEntity>()
        val root = DocumentsContract.getTreeDocumentId(tree)
        walk(tree, root, out, 0)
        return out
    }

    private fun walk(tree: Uri, docId: String, out: MutableList<TrackEntity>, depth: Int) {
        if (depth > 8) return
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
        val proj = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        val entries = ArrayList<Array<String>>()
        try {
            ctx.contentResolver.query(children, proj, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    entries += arrayOf(c.getString(0), c.getString(1) ?: "", c.getString(2) ?: "", c.getLong(3).toString())
                }
            }
        } catch (_: Exception) {
            return
        }
        for ((id, mime, name, modified) in entries.map { Quad(it[0], it[1], it[2], it[3]) }) {
            when {
                mime == DocumentsContract.Document.MIME_TYPE_DIR -> walk(tree, id, out, depth + 1)
                mime.startsWith("audio/") || name.substringAfterLast('.', "").lowercase() in AUDIO_EXT ->
                    readFile(DocumentsContract.buildDocumentUriUsingTree(tree, id), name, modified.toLongOrNull() ?: 0L)?.let { out += it }
            }
        }
    }

    private data class Quad(val a: String, val b: String, val c: String, val d: String)

    private fun readFile(uri: Uri, fileName: String, modified: Long): TrackEntity? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(ctx, uri)
            val dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            if (dur in 1..19_999) return null
            val album = cleanTag(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM), "Unknown Album")
            val artist = cleanTag(
                r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    ?: r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST), "Unknown Artist",
            )
            val title = cleanTag(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE), fileName.substringBeforeLast('.'))
            val trackNo = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                ?.substringBefore('/')?.toIntOrNull() ?: 0
            TrackEntity(
                id = -(uri.toString().hashCode().toLong().absoluteValue) - 1,
                uri = uri.toString(),
                title = title,
                artist = artist,
                album = album,
                albumKey = albumKeyOf(0, album, artist),
                albumId = 0,
                duration = dur,
                dateAdded = modified / 1000,
                trackNo = trackNo,
                source = 1,
            )
        } catch (_: Exception) {
            null
        } finally {
            try { r.release() } catch (_: Exception) {}
        }
    }

    companion object {
        private val AUDIO_EXT = setOf("mp3", "flac", "m4a", "aac", "ogg", "opus", "wav", "wma")
    }
}
