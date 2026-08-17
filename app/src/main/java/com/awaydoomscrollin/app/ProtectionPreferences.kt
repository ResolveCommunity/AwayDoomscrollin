package com.awaydoomscrollin.app

import android.content.Context
import android.content.SharedPreferences

enum class ProtectedApp(
    val packageName: String,
    val preferenceKey: String
) {
    INSTAGRAM("com.instagram.android", "is_instagram_enabled"),
    TIKTOK("com.zhiliaoapp.musically", "is_tiktok_enabled"),
    YOUTUBE("com.google.android.youtube", "is_youtube_enabled")
}

object ProtectionPreferences {
    const val PREFS_NAME = "away_doomscroll_prefs"

    fun isEnabled(prefs: SharedPreferences, app: ProtectedApp): Boolean =
        prefs.getBoolean(app.preferenceKey, true)

    fun isEnabled(context: Context, app: ProtectedApp): Boolean =
        isEnabled(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE), app)

    fun isPackageEnabled(context: Context, packageName: String): Boolean {
        val app = ProtectedApp.values().firstOrNull { it.packageName == packageName }
            ?: return true
        return isEnabled(context, app)
    }

    /**
     * Persist user-facing shield changes synchronously so the accessibility service
     * cannot restart with an older value while the user switches to a target app.
     */
    fun setEnabled(
        prefs: SharedPreferences,
        app: ProtectedApp,
        enabled: Boolean,
        resetStreak: Boolean = false
    ): Boolean {
        val editor = prefs.edit().putBoolean(app.preferenceKey, enabled)
        if (resetStreak) editor.putInt("streak_days", 0)
        return editor.commit()
    }
}
