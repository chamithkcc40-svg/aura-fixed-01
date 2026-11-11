package com.aura.player.art

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Size
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.key.Keyer
import coil.request.Options
import coil.size.Dimension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Coil model: artwork of an audio file (MediaStore or SAF document uri). */
data class ArtModel(val uri: String)

class ArtKeyer : Keyer<ArtModel> {
    override fun key(data: ArtModel, options: Options): String = "art:${data.uri}"
}

/**
 * Loads artwork off the main thread. API 29+: ContentResolver.loadThumbnail (system-cached, sampled).
 * Otherwise / for SAF documents: embedded picture via MediaMetadataRetriever, decoded with inSampleSize.
 */
class ArtFetcher(
    private val ctx: Context,
    private val data: ArtModel,
    private val options: Options,
) : Fetcher {

    override suspend fun fetch(): FetchResult? = withContext(Dispatchers.IO) {
        val target = ((options.size.width as? Dimension.Pixels)?.px ?: 512).coerceIn(64, 720)
        val bmp = load(Uri.parse(data.uri), target) ?: return@withContext null
        DrawableResult(BitmapDrawable(ctx.resources, bmp), isSampled = true, dataSource = DataSource.DISK)
    }

    private fun load(uri: Uri, target: Int): Bitmap? {
        if (Build.VERSION.SDK_INT >= 29 && uri.authority == "media") {
            try {
                return ctx.contentResolver.loadThumbnail(uri, Size(target, target), null)
            } catch (_: Exception) {
            }
        }
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(ctx, uri)
            val bytes = r.embeddedPicture ?: return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= target) sample *= 2
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        } catch (_: Exception) {
            null
        } finally {
            try { r.release() } catch (_: Exception) {}
        }
    }

    class Factory(private val ctx: Context) : Fetcher.Factory<ArtModel> {
        override fun create(data: ArtModel, options: Options, imageLoader: ImageLoader): Fetcher =
            ArtFetcher(ctx, data, options)
    }
}
