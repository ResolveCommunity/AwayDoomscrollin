package com.resolvecommunity.awaydoomscrollin

import android.content.Context
import android.content.SharedPreferences

data class StreakResetDecision(
    val shouldReset: Boolean,
    val newStreak: Int,
    val reason: String
)

/**
 * Pure policy and state synchronization for preserving or resetting daily streaks
 * based on accessibility service availability.
 *
 * Rule: If accessibility protection is turned off by the user and remains off for
 * 24 or more consecutive hours, the active daily streak is reset to 0.
 */
object AccessibilityStreakPolicy {

    const val RESET_THRESHOLD_MS: Long = 24 * 60 * 60 * 1000L // 24 hours

    const val KEY_ACCESSIBILITY_DISABLED_SINCE_MS = "accessibility_disabled_since_ms"
    const val KEY_LAST_ACCESSIBILITY_HEARTBEAT_MS = "last_accessibility_heartbeat_ms"
    const val KEY_STREAK_DAYS = "streak_days"

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

        if (decision.shouldReset) {
            editor.putInt(KEY_STREAK_DAYS, 0)
        }
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
        prefs.edit()
            .putLong(KEY_LAST_ACCESSIBILITY_HEARTBEAT_MS, now)
            .remove(KEY_ACCESSIBILITY_DISABLED_SINCE_MS)
            .apply()
    }

    /**
     * Synchronizes accessibility state and resets the streak if the service was disabled for 24+ hours.
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
            if (decision.shouldReset) {
                editor.putInt(KEY_STREAK_DAYS, 0)
            }
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
