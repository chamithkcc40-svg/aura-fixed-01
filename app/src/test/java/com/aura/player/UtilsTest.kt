package com.aura.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Test

class UtilsTest {
    @Test fun formatsMinutesAndSeconds() {
        assertEquals("0:00", formatDuration(0))
        assertEquals("3:05", formatDuration(185_000))
        assertEquals("1:02:03", formatDuration(3_723_000))
        assertEquals("0:00", formatDuration(-5000))
    }

    @Test fun greetingMatchesHour() {
        assertEquals("Good morning", greetingFor(8))
        assertEquals("Good afternoon", greetingFor(14))
        assertEquals("Good evening", greetingFor(20))
        assertEquals("Late night", greetingFor(2))
    }

    @Test fun movedReordersAndIgnoresInvalid() {
        val l = listOf(1, 2, 3, 4)
        assertEquals(listOf(2, 1, 3, 4), l.moved(1, 0))
        assertEquals(listOf(1, 3, 4, 2), l.moved(1, 3))
        assertSame(l, l.moved(-1, 2))
        assertSame(l, l.moved(2, 2))
        assertSame(l, l.moved(0, 9))
    }

    @Test fun albumKeyPrefersMediaStoreId() {
        assertEquals("ms:42", albumKeyOf(42, "A", "B"))
        assertEquals("saf:a|b", albumKeyOf(0, "A", "B"))
    }

    @Test fun cleanTagFallsBack() {
        assertEquals("Unknown", cleanTag(null, "Unknown"))
        assertEquals("Unknown", cleanTag("<unknown>", "Unknown"))
        assertEquals("Song", cleanTag("  Song ", "Unknown"))
    }

    @Test fun trackNumberStripsDiscPrefix() {
        assertEquals(5, normalizeTrackNumber(5))
        assertEquals(7, normalizeTrackNumber(2007))
    }

    @Test fun hueIsStableAndInRange() {
        assertEquals(hueFor("abc"), hueFor("abc"), 0f)
        assertNotEquals(hueFor("abc"), hueFor("abd"), 0f)
        assert(hueFor("anything") in 0f..360f)
    }

    // ---- numeric safety: spring undershoot / unknown duration must never reach range-checked APIs ----
    @Test fun clamp01HandlesOvershootUndershootAndNaN() {
        assertEquals(0f, clamp01(-0.02f), 0f)   // spring released to 0 dips below 0 (crashed Color.copy before)
        assertEquals(1f, clamp01(1.05f), 0f)
        assertEquals(0f, clamp01(Float.NaN), 0f)
        assertEquals(0.4f, clamp01(0.4f), 0f)
    }

    @Test fun progressFractionIsAlwaysInRange() {
        assertEquals(0f, progressFraction(5_000, 0), 0f)        // unknown duration (C.TIME_UNSET -> 0)
        assertEquals(0f, progressFraction(-1, 10_000), 0f)
        assertEquals(1f, progressFraction(20_000, 10_000), 0f)
        assertEquals(0.5f, progressFraction(5_000, 10_000), 0f)
    }

    @Test fun seekTargetIsSafe() {
        assertEquals(0L, seekTarget(0.5f, 0))
        assertEquals(5_000L, seekTarget(0.5f, 10_000))
        assertEquals(10_000L, seekTarget(7f, 10_000))
        assertEquals(0L, seekTarget(-3f, 10_000))
    }

    @Test fun rubberBandResistsAndKeepsSign() {
        assertEquals(0f, rubberBand(0f, 100f), 0f)
        assert(rubberBand(50f, 100f) in 0f..50f)
        assert(rubberBand(10_000f, 100f) < 100f)
        assert(rubberBand(-50f, 100f) < 0f)
        assertEquals(0f, rubberBand(40f, 0f), 0f)
    }

    @Test fun fitSquareNeverNegativeOrZeroForUsableSpace() {
        assertEquals(300f, fitSquare(300f, 500f), 0f)
        assertEquals(120f, fitSquare(300f, 120f), 0f)
        assertEquals(0f, fitSquare(300f, -14f), 0f)
    }

    @Test fun crashReportContainsStackTraceAndContext() {
        val r = buildCrashReport("Pixel, Android 14 (API 34)", "now", "main", IllegalStateException("boom"))
        assert(r.contains("IllegalStateException: boom"))
        assert(r.contains("Thread: main"))
        assert(r.contains("Pixel"))
    }
}
