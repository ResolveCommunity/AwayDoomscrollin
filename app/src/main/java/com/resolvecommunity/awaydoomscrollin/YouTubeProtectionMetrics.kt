package com.resolvecommunity.awaydoomscrollin

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import java.util.Calendar

/** Local visible-curtain time, not an estimate of time saved. Interrupted spans are discarded. */
internal object YouTubeProtectionMetrics {
    const val MEASUREMENT_VERSION = 1
    private const val TOTAL = "youtube_protection_total_ms"
    private const val START = "youtube_protection_active_started_at_ms"
    private const val ELAPSED_START = "youtube_protection_elapsed_started_at_ms"
    private const val DAY_PREFIX = "youtube_protection_day_"
    private const val MAX_SPAN = 12L * 60L * 60L * 1000L

    @Synchronized
    fun onVisibilityChanged(context: Context, visible: Boolean,
        now: Long = System.currentTimeMillis(), elapsed: Long = SystemClock.elapsedRealtime()) {
        val prefs = context.getSharedPreferences(ProtectionPreferences.PREFS_NAME, Context.MODE_PRIVATE)
        if (visible) {
            if (prefs.contains(ELAPSED_START)) return
            prefs.edit().putLong(START, now).putLong(ELAPSED_START, elapsed).apply()
            return
        }
        if (!prefs.contains(ELAPSED_START)) return
        val duration = activeMs(prefs, elapsed)
        val editor = prefs.edit().remove(START).remove(ELAPSED_START)
        var cursor = now - duration
        while (cursor < now) {
            val boundary = Calendar.getInstance().apply {
                timeInMillis = cursor
                add(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis.coerceAtMost(now)
            val key = DAY_PREFIX + InstagramProtectionMetrics.dateString(cursor)
            editor.putLong(key, safeAdd(prefs.getLong(key, 0L), boundary - cursor))
            cursor = boundary
        }
        editor.putLong(TOTAL, safeAdd(prefs.getLong(TOTAL, 0L), duration)).apply()
    }

    @Synchronized
    fun discardInterruptedSession(context: Context) {
        context.getSharedPreferences(ProtectionPreferences.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().remove(START).remove(ELAPSED_START).apply()
    }

    fun totalMs(prefs: SharedPreferences, elapsed: Long = SystemClock.elapsedRealtime()): Long =
        safeAdd(prefs.getLong(TOTAL, 0L), activeMs(prefs, elapsed))

    fun todayMs(prefs: SharedPreferences, now: Long = System.currentTimeMillis(),
        elapsed: Long = SystemClock.elapsedRealtime()): Long {
        val today = InstagramProtectionMetrics.dateString(now)
        val midnight = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return safeAdd(dayMs(prefs, today), minOf(activeMs(prefs, elapsed), now - midnight))
    }

    fun dayMs(prefs: SharedPreferences, date: String): Long =
        prefs.getLong(DAY_PREFIX + date, 0L).coerceAtLeast(0L)

    private fun activeMs(prefs: SharedPreferences, elapsed: Long): Long {
        if (!prefs.contains(ELAPSED_START)) return 0L
        val duration = elapsed - prefs.getLong(ELAPSED_START, elapsed)
        return duration.takeIf { it in 0L..MAX_SPAN } ?: 0L
    }

    private fun safeAdd(stored: Long, added: Long): Long {
        val value = stored.coerceAtLeast(0L)
        return if (added > Long.MAX_VALUE - value) Long.MAX_VALUE else value + added
    }
}
