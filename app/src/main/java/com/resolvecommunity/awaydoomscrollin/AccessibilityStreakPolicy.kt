package com.resolvecommunity.awaydoomscrollin

import android.content.Context
import android.content.SharedPreferences
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

data class StreakResetDecision(
    val shouldReset: Boolean,
    val newStreak: Int,
    val reason: String
)

data class StreakDayUpdate(
    val streak: Int,
    val lastActiveDay: String
)

/**
 * Pure policy and state synchronization for preserving or resetting daily streaks
 * based on accessibility service availability.
 *
 * Rule: The streak counts consecutive calendar days on which accessibility protection
 * was enabled; a day without any block does not break it. If accessibility protection
 * is turned off by the user and remains off for 24 or more consecutive hours, the
 * active daily streak is reset to 0.
 */
object AccessibilityStreakPolicy {

    const val RESET_THRESHOLD_MS: Long = 24 * 60 * 60 * 1000L // 24 hours

    const val KEY_ACCESSIBILITY_DISABLED_SINCE_MS = "accessibility_disabled_since_ms"
    const val KEY_LAST_ACCESSIBILITY_HEARTBEAT_MS = "last_accessibility_heartbeat_ms"
    const val KEY_STREAK_DAYS = "streak_days"
    // Last calendar day (yyyy-MM-dd) already counted in the streak.
    const val KEY_LAST_ACTIVE_DAY = "last_active_day"

    /**
     * Pure evaluation of whether a streak should be reset.
     */
    fun evaluateStreakReset(
        currentTimeMs: Long,
        disabledSinceMs: Long,
        currentStreak: Int,
        thresholdMs: Long = RESET_THRESHOLD_MS
    ): StreakResetDecision {
        if (currentStreak <= 0) {
            return StreakResetDecision(
                shouldReset = false,
                newStreak = 0,
                reason = "No active streak"
            )
        }
        if (disabledSinceMs <= 0L) {
            return StreakResetDecision(
                shouldReset = false,
                newStreak = currentStreak,
                reason = "Accessibility service has not been recorded as disabled"
            )
        }
        if (currentTimeMs < disabledSinceMs) {
            return StreakResetDecision(
                shouldReset = false,
                newStreak = currentStreak,
                reason = "Clock anomaly: current time earlier than disabled timestamp"
            )
        }

        val elapsedMs = currentTimeMs - disabledSinceMs
        return if (elapsedMs >= thresholdMs) {
            StreakResetDecision(
                shouldReset = true,
                newStreak = 0,
                reason = "Accessibility disabled for ${elapsedMs / 1000}s (threshold: ${thresholdMs / 1000}s)"
            )
        } else {
            StreakResetDecision(
                shouldReset = false,
                newStreak = currentStreak,
                reason = "Within grace period (${elapsedMs / 1000}s / ${thresholdMs / 1000}s)"
            )
        }
    }

    /**
     * Pure evaluation of the streak for [today] while protection is enabled.
     *
     * Every calendar day since [lastActiveDay] is counted: the service only sees events
     * from the protected apps, so a quiet day leaves no trace, and an off period long
     * enough to skip a whole day is already handled by the 24-hour reset above.
     */
    fun advanceForProtectedDay(
        currentStreak: Int,
        lastActiveDay: String,
        today: LocalDate
    ): StreakDayUpdate {
        val lastDay = parseDay(lastActiveDay)
        if (currentStreak <= 0) {
            // A streak reset earlier today starts counting again tomorrow.
            return if (lastDay != null && !lastDay.isBefore(today)) {
                StreakDayUpdate(0, lastActiveDay)
            } else {
                StreakDayUpdate(1, today.toString())
            }
        }
        // Unknown last day: keep the value rather than guessing elapsed days.
        if (lastDay == null) return StreakDayUpdate(currentStreak, today.toString())
        // Same day, or the clock moved backwards: never count a day twice.
        if (!lastDay.isBefore(today)) return StreakDayUpdate(currentStreak, lastActiveDay)

        val elapsedDays = ChronoUnit.DAYS.between(lastDay, today)
        val streak = (currentStreak.toLong() + elapsedDays).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        return StreakDayUpdate(streak, today.toString())
    }

    fun localDay(now: Long): LocalDate =
        Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()

    private fun parseDay(value: String): LocalDate? =
        if (value.isEmpty()) null
        else try {
            LocalDate.parse(value)
        } catch (_: DateTimeParseException) {
            null
        }

    /**
     * Counts the day of [now] as protected into [editor]. Call only while the service is
     * enabled, with [currentStreak] taken after any pending 24-hour reset.
     */
    fun putProtectedDay(
        prefs: SharedPreferences,
        editor: SharedPreferences.Editor,
        now: Long,
        currentStreak: Int = prefs.getInt(KEY_STREAK_DAYS, 0)
    ) {
        val update = advanceForProtectedDay(
            currentStreak = currentStreak,
            lastActiveDay = prefs.getString(KEY_LAST_ACTIVE_DAY, "").orEmpty(),
            today = localDay(now)
        )
        editor.putInt(KEY_STREAK_DAYS, update.streak)
            .putString(KEY_LAST_ACTIVE_DAY, update.lastActiveDay)
    }

    /**
     * Called when AntiScrollService successfully connects to the system.
     */
    fun onServiceConnected(context: Context, now: Long = System.currentTimeMillis()): Boolean {
        val prefs = context.getSharedPreferences(ProtectionPreferences.PREFS_NAME, Context.MODE_PRIVATE)
        val disabledSince = prefs.getLong(KEY_ACCESSIBILITY_DISABLED_SINCE_MS, 0L)
        val currentStreak = prefs.getInt(KEY_STREAK_DAYS, 0)
        val decision = evaluateStreakReset(now, disabledSince, currentStreak)

        val editor = prefs.edit()
            .remove(KEY_ACCESSIBILITY_DISABLED_SINCE_MS)
            .putLong(KEY_LAST_ACCESSIBILITY_HEARTBEAT_MS, now)
        putProtectedDay(prefs, editor, now, decision.newStreak)
        editor.apply()
        return decision.shouldReset
    }

    /**
     * Called when AntiScrollService is unbound or destroyed.
     */
    fun onServiceDisconnected(context: Context, now: Long = System.currentTimeMillis()) {
        val prefs = context.getSharedPreferences(ProtectionPreferences.PREFS_NAME, Context.MODE_PRIVATE)
        val existingDisabledSince = prefs.getLong(KEY_ACCESSIBILITY_DISABLED_SINCE_MS, 0L)
        if (existingDisabledSince <= 0L) {
            prefs.edit().putLong(KEY_ACCESSIBILITY_DISABLED_SINCE_MS, now).apply()
        }
    }

    /**
     * Records a live heartbeat from the running service to guarantee accurate uptime tracking.
     */
    fun recordHeartbeat(context: Context, now: Long = System.currentTimeMillis()) {
        val prefs = context.getSharedPreferences(ProtectionPreferences.PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
            .putLong(KEY_LAST_ACCESSIBILITY_HEARTBEAT_MS, now)
            .remove(KEY_ACCESSIBILITY_DISABLED_SINCE_MS)
        putProtectedDay(prefs, editor, now)
        editor.apply()
    }

    /**
     * Synchronizes accessibility state and resets the streak if the service was disabled for 24+ hours.
     * While the service is enabled, the current day is counted into the streak.
     * Can be called safely from Activity lifecycle (onResume/onCreate).
     */
    fun syncState(
        context: Context,
        isAccessibilityActive: Boolean,
        now: Long = System.currentTimeMillis()
    ): Boolean {
        val prefs = context.getSharedPreferences(ProtectionPreferences.PREFS_NAME, Context.MODE_PRIVATE)
        val disabledSince = prefs.getLong(KEY_ACCESSIBILITY_DISABLED_SINCE_MS, 0L)
        val currentStreak = prefs.getInt(KEY_STREAK_DAYS, 0)

        if (isAccessibilityActive) {
            val decision = evaluateStreakReset(now, disabledSince, currentStreak)
            val editor = prefs.edit()
                .remove(KEY_ACCESSIBILITY_DISABLED_SINCE_MS)
                .putLong(KEY_LAST_ACCESSIBILITY_HEARTBEAT_MS, now)
            putProtectedDay(prefs, editor, now, decision.newStreak)
            editor.apply()
            return decision.shouldReset
        } else {
            if (disabledSince <= 0L) {
                val lastHeartbeat = prefs.getLong(KEY_LAST_ACCESSIBILITY_HEARTBEAT_MS, 0L)
                val effectiveDisabledSince = if (lastHeartbeat in 1..now) lastHeartbeat else now
                val decision = evaluateStreakReset(now, effectiveDisabledSince, currentStreak)
                val editor = prefs.edit().putLong(KEY_ACCESSIBILITY_DISABLED_SINCE_MS, effectiveDisabledSince)
                if (decision.shouldReset) {
                    editor.putInt(KEY_STREAK_DAYS, 0)
                }
                editor.apply()
                return decision.shouldReset
            } else {
                val decision = evaluateStreakReset(now, disabledSince, currentStreak)
                if (decision.shouldReset) {
                    prefs.edit().putInt(KEY_STREAK_DAYS, 0).apply()
                    return true
                }
                return false
            }
        }
    }
}
