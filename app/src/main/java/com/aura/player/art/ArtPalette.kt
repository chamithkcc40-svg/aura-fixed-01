package com.aura.player.art

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Three ambient colours derived from artwork. */
data class AmbientColors(val a: Color, val b: Color, val c: Color)

object ArtPalette {
    private val cache = LruCache<String, IntArray>(96)

    suspend fun colorsFor(ctx: Context, loader: ImageLoader, uri: String): AmbientColors? {
        cache.get(uri)?.let { return it.toAmbient() }
        val req = ImageRequest.Builder(ctx).data(ArtModel(uri)).size(96).allowHardware(false).build()
        val src = ((loader.execute(req) as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap ?: return null
        val ints = withContext(Dispatchers.Default) {
            try {
                // ContentResolver.loadThumbnail may hand back a HARDWARE bitmap, which Palette cannot read.
                val soft = if (src.config == Bitmap.Config.HARDWARE) src.copy(Bitmap.Config.ARGB_8888, false) else src
                soft?.let { extract(it) }
            } catch (e: Exception) {
                null
            }
        } ?: return null
        cache.put(uri, ints)
        return ints.toAmbient()
    }

    private fun IntArray.toAmbient() = AmbientColors(Color(this[0]), Color(this[1]), Color(this[2]))

    private fun extract(bmp: Bitmap): IntArray? {
        val p = Palette.from(bmp).maximumColorCount(14).generate()
        val primary = p.vibrantSwatch ?: p.lightVibrantSwatch ?: p.dominantSwatch ?: return null
        val secondary = p.darkVibrantSwatch ?: p.mutedSwatch ?: primary
        val tertiary = p.lightMutedSwatch ?: p.lightVibrantSwatch ?: secondary
        return intArrayOf(
            tune(primary.rgb, 0.55f, 0.95f, 0.50f),
            tune(secondary.rgb, 0.45f, 0.75f, 0.40f),
            tune(tertiary.rgb, 0.40f, 0.95f, 0.45f),
        )
    }

    /** Keep hue, push saturation/value into a range that glows on a near-black background. */
    private fun tune(rgb: Int, minV: Float, maxV: Float, minS: Float): Int {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(rgb, hsv)
        hsv[1] = hsv[1].coerceAtLeast(minS).coerceAtMost(0.95f)
        hsv[2] = hsv[2].coerceIn(minV, maxV)
        return android.graphics.Color.HSVToColor(hsv)
    }
}
