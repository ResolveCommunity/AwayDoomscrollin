package com.resolvecommunity.awaydoomscrollin

import android.content.Context
import android.content.SharedPreferences

/**
 * Persists the versioned, in-app accessibility disclosure acceptance.
 * Android's system permission remains a separate decision made by the user.
 */
internal object AccessibilityConsent {
    const val PREFS_NAME = "away_doomscroll_prefs"
    private const val KEY_DISCLOSURE_VERSION = "accessibility_disclosure_version"
    private const val CURRENT_DISCLOSURE_VERSION = 1

    fun isAccepted(context: Context): Boolean =
        isAccepted(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE))

    fun isAccepted(prefs: SharedPreferences): Boolean =
        prefs.getInt(KEY_DISCLOSURE_VERSION, 0) >= CURRENT_DISCLOSURE_VERSION

    fun accept(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_DISCLOSURE_VERSION, CURRENT_DISCLOSURE_VERSION)
            .commit()
}
