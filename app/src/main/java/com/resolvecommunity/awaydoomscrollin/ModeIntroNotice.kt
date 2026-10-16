package com.resolvecommunity.awaydoomscrollin

import android.content.SharedPreferences

/**
 * One-time notice for installs updated from a version without protection
 * modes (v1.2.0 and older). Those installs start in Balanced, which is looser
 * on Instagram than v1.2.0 was: one chosen Reel, profile posts and Saved lists
 * are open. The user is told once and sent to the Apps tab to choose. Fresh
 * installs never see the notice.
 */
internal object ModeIntroNotice {
    private const val KEY_STATE = "mode_intro_state"
    private const val PENDING = "pending"
    private const val DONE = "done"

    /**
     * Runs on every launch but decides only once, on the first launch of a
     * version that has modes. At that moment an updated install has finished
     * setup and has no stored mode; a fresh install has not finished setup.
     */
    fun classifyOnce(prefs: SharedPreferences) {
        if (prefs.contains(KEY_STATE)) return
        val updatedInstall = prefs.getBoolean("onboarding_completed", false) &&
            ProtectedApp.entries.none { ProtectionPreferences.hasStoredMode(prefs, it) }
        prefs.edit().putString(KEY_STATE, if (updatedInstall) PENDING else DONE).commit()
    }

    fun isPending(prefs: SharedPreferences): Boolean =
        prefs.getString(KEY_STATE, null) == PENDING

    fun markSeen(prefs: SharedPreferences): Boolean =
        prefs.edit().putString(KEY_STATE, DONE).commit()
}
