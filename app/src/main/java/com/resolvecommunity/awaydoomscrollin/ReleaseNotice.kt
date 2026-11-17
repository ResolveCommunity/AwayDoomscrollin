package com.resolvecommunity.awaydoomscrollin

import android.content.Context
import android.content.SharedPreferences
import android.os.Build

/**
 * The "what's new" summary shown once after the app is updated.
 *
 * Only an installation that was updated (Android's last update time is later
 * than its first install time) and has finished setup sees it, once per
 * version. A fresh installation marks the current version as seen right away,
 * so a new user is not greeted with a list of changes to a version they never
 * used, and still sees the summary of the next update.
 */
internal object ReleaseNotice {
    private const val KEY_SEEN_VERSION = "release_notice_seen_version"

    fun shouldShow(
        seenVersion: Int,
        currentVersion: Int,
        updatedInstall: Boolean,
        setupDone: Boolean
    ): Boolean = updatedInstall && setupDone && seenVersion < currentVersion

    /** Decides for this launch; a fresh installation is marked as seen. */
    fun isPending(context: Context, prefs: SharedPreferences): Boolean {
        val current = currentVersion(context) ?: return false
        val updated = isUpdatedInstall(context)
        if (!updated) {
            markSeen(prefs, current)
            return false
        }
        return shouldShow(
            seenVersion = prefs.getInt(KEY_SEEN_VERSION, 0),
            currentVersion = current,
            updatedInstall = true,
            setupDone = prefs.getBoolean("onboarding_completed", false)
        )
    }

    fun markSeen(context: Context, prefs: SharedPreferences) {
        currentVersion(context)?.let { markSeen(prefs, it) }
    }

    private fun markSeen(prefs: SharedPreferences, version: Int) {
        if (prefs.getInt(KEY_SEEN_VERSION, 0) < version) {
            prefs.edit().putInt(KEY_SEEN_VERSION, version).apply()
        }
    }

    fun versionName(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
            ?.substringBefore('-')
    }.getOrNull().orEmpty()

    private fun currentVersion(context: Context): Int? = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode.toInt()
        else @Suppress("DEPRECATION") info.versionCode
    }.getOrNull()

    private fun isUpdatedInstall(context: Context): Boolean = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.lastUpdateTime > info.firstInstallTime
    }.getOrDefault(false)
}
