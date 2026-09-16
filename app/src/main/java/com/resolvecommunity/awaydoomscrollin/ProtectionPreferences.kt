package com.resolvecommunity.awaydoomscrollin

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
            ?: return false
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

    const val KEY_IG_FEED_MODE = "ig_feed_mode"
    const val KEY_IG_ALLOW_DM_REELS = "ig_allow_dm_reels"
    const val KEY_IG_BLOCK_STORIES = "ig_block_stories"
    const val KEY_IG_HIDE_EXPLORE = "ig_hide_explore_feed"
    const val KEY_IG_BLOCK_COMMENTS = "ig_block_comments"

    fun getInstagramConfig(prefs: SharedPreferences): InstagramProtectionConfig {
        val feedMode = prefs.getString(KEY_IG_FEED_MODE, InstagramProtectionConfig.FEED_MODE_FULL)
            ?: InstagramProtectionConfig.FEED_MODE_FULL
        return if (feedMode == InstagramProtectionConfig.FEED_MODE_FULL) {
            InstagramProtectionConfig(
                feedMode = InstagramProtectionConfig.FEED_MODE_FULL,
                allowDmReels = false,
                blockStories = false,
                hideExploreFeed = true,
                blockComments = true
            )
        } else {
            InstagramProtectionConfig(
                feedMode = feedMode,
                allowDmReels = prefs.getBoolean(KEY_IG_ALLOW_DM_REELS, true),
                blockStories = prefs.getBoolean(KEY_IG_BLOCK_STORIES, false),
                hideExploreFeed = prefs.getBoolean(KEY_IG_HIDE_EXPLORE, true),
                blockComments = prefs.getBoolean(KEY_IG_BLOCK_COMMENTS, true)
            )
        }
    }

    fun getInstagramConfig(context: Context): InstagramProtectionConfig =
        getInstagramConfig(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE))

    fun setInstagramFeedMode(prefs: SharedPreferences, mode: String): Boolean =
        prefs.edit().putString(KEY_IG_FEED_MODE, mode).commit()

    fun setInstagramAllowDmReels(prefs: SharedPreferences, allow: Boolean): Boolean =
        prefs.edit().putBoolean(KEY_IG_ALLOW_DM_REELS, allow).commit()

    fun setInstagramBlockStories(prefs: SharedPreferences, block: Boolean): Boolean =
        prefs.edit().putBoolean(KEY_IG_BLOCK_STORIES, block).commit()

    fun setInstagramHideExplore(prefs: SharedPreferences, hide: Boolean): Boolean =
        prefs.edit().putBoolean(KEY_IG_HIDE_EXPLORE, hide).commit()

    fun setInstagramBlockComments(prefs: SharedPreferences, block: Boolean): Boolean =
        prefs.edit().putBoolean(KEY_IG_BLOCK_COMMENTS, block).commit()
}

data class InstagramProtectionConfig(
    val feedMode: String = FEED_MODE_FULL,
    val allowDmReels: Boolean = false,
    val blockStories: Boolean = false,
    val hideExploreFeed: Boolean = true,
    val blockComments: Boolean = true
) {
    companion object {
        const val FEED_MODE_FULL = "full"
        const val FEED_MODE_DIRECTLY_DM = "directly_dm"
    }
}
