package com.resolvecommunity.awaydoomscrollin

internal enum class InstagramBalancedReelIngress {
    NONE,
    ACTION_BAR,
    DIRECT_INTENT,
    DIRECT_VIEWER_WINDOW,
    PROFILE_REEL_INTENT,
    COLLECTION_REEL_INTENT,
    NOTIFICATION_COMMENT_SHEET
}

/**
 * Identifies a deliberately opened single Reel without granting the same
 * exception to Instagram's main Reels feed.
 */
internal object InstagramBalancedReelIngressPolicy {
    fun keepsVerifiedSession(
        mode: ProtectionMode,
        watching: Boolean,
        exiting: Boolean
    ): Boolean = mode == ProtectionMode.BALANCED && watching && !exiting

    fun resolve(
        mode: ProtectionMode,
        previousScreen: InstagramScreen,
        originWindowId: Int,
        viewerWindowId: Int,
        profileReelIntentWindowId: Int,
        profileReelIntentPending: Boolean,
        reelsTabTapLatched: Boolean,
        mainTabBarVisible: Boolean,
        actionBarBackVisible: Boolean,
        directIntentPending: Boolean,
        collectionReelIntentWindowId: Int = -1,
        collectionReelIntentPending: Boolean = false,
        notificationItemWatching: Boolean = false
    ): InstagramBalancedReelIngress {
        if (mode != ProtectionMode.BALANCED || reelsTabTapLatched || mainTabBarVisible) {
            return InstagramBalancedReelIngress.NONE
        }
        // The viewer's Back button is only a navigation affordance, not proof
        // of where the Reel came from.  Restrict this fallback to the three
        // deliberate origins; otherwise a Reel opened from Home/Explore or
        // another algorithmic surface could be admitted merely because its
        // action bar happened to render first.
        if (actionBarBackVisible && previousScreen in setOf(
                InstagramScreen.DIRECT_MESSAGES,
                InstagramScreen.PROFILE,
                InstagramScreen.SAVED_COLLECTION
            )) {
            return InstagramBalancedReelIngress.ACTION_BAR
        }
        if (previousScreen == InstagramScreen.DIRECT_MESSAGES && directIntentPending) {
            return InstagramBalancedReelIngress.DIRECT_INTENT
        }
        if (previousScreen == InstagramScreen.DIRECT_MESSAGES && originWindowId >= 0 &&
            viewerWindowId >= 0 && viewerWindowId != originWindowId) {
            return InstagramBalancedReelIngress.DIRECT_VIEWER_WINDOW
        }
        if (previousScreen == InstagramScreen.PROFILE &&
            profileReelIntentPending && profileReelIntentWindowId >= 0 &&
            viewerWindowId == profileReelIntentWindowId) {
            return InstagramBalancedReelIngress.PROFILE_REEL_INTENT
        }
        if (previousScreen == InstagramScreen.SAVED_COLLECTION &&
            collectionReelIntentPending && collectionReelIntentWindowId >= 0 &&
            viewerWindowId == collectionReelIntentWindowId) {
            return InstagramBalancedReelIngress.COLLECTION_REEL_INTENT
        }
        // A comment notification opens its comment sheet over the Reel it
        // belongs to. Closing the sheet reveals that same Reel in the same
        // window: the notification's one destination, not a feed.
        if (previousScreen == InstagramScreen.COMMENTS_OR_DETAIL && notificationItemWatching &&
            originWindowId >= 0 && viewerWindowId == originWindowId) {
            return InstagramBalancedReelIngress.NOTIFICATION_COMMENT_SHEET
        }
        return InstagramBalancedReelIngress.NONE
    }
}
