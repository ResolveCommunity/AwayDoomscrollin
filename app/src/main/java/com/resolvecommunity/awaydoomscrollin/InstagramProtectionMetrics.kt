package com.resolvecommunity.awaydoomscrollin

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Measures the time in which the Instagram content curtain is actually mounted.
 * This is deliberately not presented as "time saved": a visible curtain proves
 * protection activity, but cannot prove what the user would otherwise have done.
 */
internal object InstagramProtectionMetrics {
    const val MEASUREMENT_VERSION = 1
    const val KEY_TOTAL_MS = "instagram_protection_total_ms"
    const val KEY_ACTIVE_STARTED_AT_MS = "instagram_protection_active_started_at_ms"
    const val KEY_MEASUREMENT_VERSION = "instagram_protection_measurement_version"
    private const val DAY_PREFIX = "instagram_protection_day_"
    private const val MAX_SESSION_MS = 12L * 60L * 60L * 1000L

    fun dayKey(date: String): String = "$DAY_PREFIX$date"

    fun dateString(timeMs: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(timeMs))

    @Synchronized
    fun onCurtainShown(context: Context, nowMs: Long = System.currentTimeMillis()) {
        val prefs = preferences(context)
        val existing = prefs.getLong(KEY_ACTIVE_STARTED_AT_MS, 0L)
        if (existing in 1..nowMs && nowMs - existing <= MAX_SESSION_MS) return

        prefs.edit()
            .putInt(KEY_MEASUREMENT_VERSION, MEASUREMENT_VERSION)
            .putLong(KEY_ACTIVE_STARTED_AT_MS, nowMs)
            .apply()
    }

    @Synchronized
    fun onCurtainHidden(context: Context, nowMs: Long = System.currentTimeMillis()) {
        val prefs = preferences(context)
        val startedAt = prefs.getLong(KEY_ACTIVE_STARTED_AT_MS, 0L)
        if (startedAt <= 0L) return

        val safeEnd = nowMs.coerceAtLeast(startedAt)
        val safeStart = startedAt.coerceAtLeast(safeEnd - MAX_SESSION_MS)
        val additions = splitByDay(safeStart, safeEnd)
        val editor = prefs.edit().remove(KEY_ACTIVE_STARTED_AT_MS)
        var addedTotal = 0L
        additions.forEach { (date, durationMs) ->
            addedTotal += durationMs
            editor.putLong(dayKey(date), safeAdd(prefs.getLong(dayKey(date), 0L), durationMs))
        }
        editor.putLong(KEY_TOTAL_MS, safeAdd(prefs.getLong(KEY_TOTAL_MS, 0L), addedTotal))
        editor.putInt(KEY_MEASUREMENT_VERSION, MEASUREMENT_VERSION)
        editor.apply()
    }

    /** A reconnect starts with no trusted overlay window, so a stale open span is discarded. */
    @Synchronized
    fun discardInterruptedSession(context: Context) {
        preferences(context).edit().remove(KEY_ACTIVE_STARTED_AT_MS).apply()
    }

    fun totalMs(prefs: SharedPreferences, nowMs: Long = System.currentTimeMillis()): Long =
        safeAdd(prefs.getLong(KEY_TOTAL_MS, 0L), activeElapsedMs(prefs, nowMs))

    fun todayMs(prefs: SharedPreferences, nowMs: Long = System.currentTimeMillis()): Long {
        val today = dateString(nowMs)
        val stored = prefs.getLong(dayKey(today), 0L)
        val activeStart = validActiveStart(prefs, nowMs) ?: return stored
        val activeToday = splitByDay(activeStart, nowMs)[today] ?: 0L
        return safeAdd(stored, activeToday)
    }

    fun dayMs(prefs: SharedPreferences, date: String): Long =
        prefs.getLong(dayKey(date), 0L).coerceAtLeast(0L)

    private fun activeElapsedMs(prefs: SharedPreferences, nowMs: Long): Long {
        val start = validActiveStart(prefs, nowMs) ?: return 0L
        return nowMs - start
    }

    private fun validActiveStart(prefs: SharedPreferences, nowMs: Long): Long? {
        val start = prefs.getLong(KEY_ACTIVE_STARTED_AT_MS, 0L)
        return start.takeIf { it in 1..nowMs && nowMs - it <= MAX_SESSION_MS }
    }

    private fun splitByDay(startMs: Long, endMs: Long): Map<String, Long> {
        if (endMs <= startMs) return emptyMap()
        val result = linkedMapOf<String, Long>()
        var cursor = startMs
        while (cursor < endMs) {
            val calendar = Calendar.getInstance().apply {
                timeInMillis = cursor
                add(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val boundary = minOf(calendar.timeInMillis, endMs)
            val date = dateString(cursor)
            result[date] = safeAdd(result[date] ?: 0L, boundary - cursor)
            cursor = boundary
        }
        return result
    }

    private fun safeAdd(left: Long, right: Long): Long =
        if (right > 0L && left > Long.MAX_VALUE - right) Long.MAX_VALUE
        else (left + right).coerceAtLeast(0L)

    private fun preferences(context: Context): SharedPreferences =
        context.getSharedPreferences(ProtectionPreferences.PREFS_NAME, Context.MODE_PRIVATE)
}

fun formatMeasuredDuration(durationMs: Long, isEn: Boolean): String {
    val totalSeconds = (durationMs.coerceAtLeast(0L) / 1000L)
    if (totalSeconds < 60L) {
        return if (isEn) "${totalSeconds}s" else "$totalSeconds sn"
    }
    val minutes = totalSeconds / 60L
    val hours = minutes / 60L
    val remainingMinutes = minutes % 60L
    return when {
        hours == 0L -> if (isEn) "$minutes min" else "$minutes dk"
        remainingMinutes == 0L -> if (isEn) "$hours hr" else "$hours sa"
        isEn -> "$hours hr $remainingMinutes min"
        else -> "$hours sa $remainingMinutes dk"
    }
}
