package com.aura.player.diag

import android.content.Context
import android.os.Build
import android.util.Log
import com.aura.player.buildCrashReport
import java.io.File
import java.util.Date

/**
 * Last-crash recorder.
 *
 * The Now Playing crash could not be reproduced in the build sandbox (no device / emulator), so AURA now
 * records the REAL stack trace of any uncaught exception to a private file. Settings > Diagnostics shows it
 * and lets you copy it. If a crash ever survives the hardening in this release, that report is the ground
 * truth to debug from (the same trace is also available with `adb logcat -b crash`).
 */
object CrashLog {
    private const val FILE = "last_crash.txt"
    private const val TAG = "AuraCrash"

    fun install(ctx: Context) {
        val app = ctx.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val device = "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
                val text = buildCrashReport(device, Date().toString(), thread.name, error)
                File(app.filesDir, FILE).writeText(text)
                Log.e(TAG, "Uncaught exception recorded", error)
            } catch (_: Throwable) {
                // Never let the recorder itself mask the original crash.
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun read(ctx: Context): String? = try {
        File(ctx.applicationContext.filesDir, FILE).takeIf { it.isFile }?.readText()?.takeIf { it.isNotBlank() }
    } catch (_: Throwable) {
        null
    }

    fun clear(ctx: Context) {
        try {
            File(ctx.applicationContext.filesDir, FILE).delete()
        } catch (_: Throwable) {
        }
    }
}
