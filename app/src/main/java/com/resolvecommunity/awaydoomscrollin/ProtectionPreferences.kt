package com.resolvecommunity.awaydoomscrollin

import android.content.Context
import android.content.SharedPreferences

enum class ProtectedApp(
    val packageName: String,
    val preferenceKey: String,
    /** The same app published under another package name in some regions. */
    val alternatePackages: List<String> = emptyList()
) {
    INSTAGRAM("com.instagram.android", "is_instagram_enabled"),
    // TikTok in Korea, Japan and Southeast Asia ("trill") is the same app under
    // another package name. TikTok Lite is a different app and is not covered.
    TIKTOK("com.zhiliaoapp.musically", "is_tiktok_enabled", listOf("com.ss.android.ugc.trill")),
    YOUTUBE("com.google.android.youtube", "is_youtube_enabled");

    val packageNames: List<String> = listOf(packageName) + alternatePackages

    fun owns(pkg: String?): Boolean = pkg == packageName || (pkg != null && pkg in alternatePackages)

    companion object {
        // Read for every accessibility event: one map lookup, nothing allocated.
        private val mainPackageOf: Map<String, String> =
            entries.flatMap { app -> app.alternatePackages.map { it to app.packageName } }.toMap()

        /** A regional package name becomes the app's main one, so protection treats both alike. */
        fun canonicalPackage(pkg: String?): String? = pkg?.let { mainPackageOf[it] ?: it }
    }
}

enum class ProtectionMode {
    BALANCED,
    STRICT;

    companion object {
        fun fromStored(value: String?): ProtectionMode =
            entries.firstOrNull { it.name == value } ?: BALANCED
    }
}

object ProtectionPreferences {
    const val PREFS_NAME = "away_doomscroll_prefs"

    /** Opt-in, off by default: Instagram opened from outside goes to Messages. */
    const val INSTAGRAM_OPEN_IN_MESSAGES_KEY = "instagram_open_in_messages"

    fun opensInstagramInMessages(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(INSTAGRAM_OPEN_IN_MESSAGES_KEY, false)

    fun opensInstagramInMessages(context: Context): Boolean =
        opensInstagramInMessages(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE))

    fun setOpensInstagramInMessages(prefs: SharedPreferences, enabled: Boolean): Boolean =
        prefs.edit().putBoolean(INSTAGRAM_OPEN_IN_MESSAGES_KEY, enabled).commit()

    private fun modeKey(app: ProtectedApp): String =
        "protection_mode_${app.name.lowercase()}"

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
     * Missing and unknown values intentionally resolve to BALANCED. Existing installs therefore
     * keep the published behavior without a migration write or any change to counters/streaks.
     */
    fun getMode(prefs: SharedPreferences, app: ProtectedApp): ProtectionMode =
        ProtectionMode.fromStored(prefs.getString(modeKey(app), null))

    fun hasStoredMode(prefs: SharedPreferences, app: ProtectedApp): Boolean =
        prefs.contains(modeKey(app))

    fun getMode(context: Context, app: ProtectedApp): ProtectionMode =
        getMode(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE), app)

    fun setMode(
        prefs: SharedPreferences,
        app: ProtectedApp,
        mode: ProtectionMode
    ): Boolean = prefs.edit().putString(modeKey(app), mode.name).commit()

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
