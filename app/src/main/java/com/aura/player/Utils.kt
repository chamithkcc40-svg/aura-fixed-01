package com.aura.player

/** Pure helpers (JVM-testable, no Android dependencies). */

fun formatDuration(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun greetingFor(hour: Int): String = when (hour) {
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    in 18..22 -> "Good evening"
    else -> "Late night"
}

/** Returns a copy of the list with the element at [from] moved to [to]. */
fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (from !in indices || to !in indices || from == to) return this
    val m = toMutableList()
    val item = m.removeAt(from)
    m.add(to, item)
    return m
}

/** Stable key identifying an album independent of MediaStore ids (SAF tracks have none). */
fun albumKeyOf(albumId: Long, album: String, artist: String): String =
    if (albumId > 0) "ms:$albumId" else "saf:${album.lowercase()}|${artist.lowercase()}"

fun cleanTag(value: String?, fallback: String): String {
    val v = value?.trim().orEmpty()
    return if (v.isEmpty() || v == "<unknown>") fallback else v
}

/** MediaStore sometimes encodes disc*1000 + track. */
fun normalizeTrackNumber(raw: Int): Int = if (raw > 1000) raw % 1000 else raw

/** Maps a hue seed from a string so placeholders are stable per title. */
fun hueFor(seed: String): Float = ((seed.hashCode().toLong() and 0x7fffffff) % 360).toFloat()

// ---------------------------------------------------------------------------------------------
// Numeric safety + motion helpers (pure, JVM-tested). Springs OVERSHOOT their target, so any
// spring-driven value that feeds a range-checked API (Color alpha, Brush stops, radii) must be clamped.
// ---------------------------------------------------------------------------------------------

/** Clamp to 0..1; NaN becomes 0. Use for every spring-driven alpha / fraction that reaches Color(...). */
fun clamp01(v: Float): Float = if (v.isNaN()) 0f else if (v < 0f) 0f else if (v > 1f) 1f else v

/** Playback fraction that can never be NaN, negative, > 1, or divide by a zero / unknown duration. */
fun progressFraction(positionMs: Long, durationMs: Long): Float =
    if (durationMs <= 0L) 0f else clamp01(positionMs.toFloat() / durationMs.toFloat())

/** Maps a drag fraction (0..1 across the bar) to a seek position; 0 when the duration is unknown. */
fun seekTarget(fraction: Float, durationMs: Long): Long =
    if (durationMs <= 0L) 0L else (clamp01(fraction) * durationMs).toLong()

/**
 * Rubber-band resistance: tracks the finger 1:1 near zero and asymptotically approaches [limit],
 * the way iOS overscroll feels. Sign-preserving.
 */
fun rubberBand(value: Float, limit: Float): Float {
    if (limit <= 0f) return 0f
    val a = kotlin.math.abs(value)
    val r = limit * (1f - 1f / (a / limit + 1f))
    return if (value < 0f) -r else r
}

/** Largest square that fits, never negative; lets layouts size artwork without ever producing 0x0. */
fun fitSquare(maxW: Float, maxH: Float): Float = maxOf(0f, minOf(maxW, maxH))

/** Plain-text crash report (no Android dependencies so it is unit-testable). */
fun buildCrashReport(device: String, time: String, thread: String, error: Throwable): String {
    val sw = java.io.StringWriter()
    error.printStackTrace(java.io.PrintWriter(sw))
    return "AURA crash report\nTime: $time\nThread: $thread\nDevice: $device\n\n$sw"
}
