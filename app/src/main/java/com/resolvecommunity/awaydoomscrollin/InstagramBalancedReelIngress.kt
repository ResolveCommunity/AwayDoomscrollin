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
 * The single-Reel viewer's Back arrow (opened from a chat, a profile or a
 * collection) has no view id; v1.3.0 found it by its "Back"/"Geri" label,
 * which no longer exists in v1.4.0's language-independent checks. Its group,
 * `clips_action_bar_start_action_buttons`, does have an id. A text-free,
 * clickable control of that group in the leading top corner is that Back.
 */
internal object InstagramReelViewerBack {
    const val startButtonsId = "clips_action_bar_start_action_buttons"

    /** [bounds] are already in left-to-right coordinates (mirrored for RTL). */
    fun isBack(
        clickable: Boolean,
        hasText: Boolean,
        bounds: InstagramGeometrySample,
        window: InstagramGeometrySample
    ): Boolean {
        val width = bounds.right - bounds.left
        val height = bounds.bottom - bounds.top
        val windowWidth = window.right - window.left
        val windowHeight = window.bottom - window.top
        return clickable && !hasText && width > 0 && height > 0 &&
            bounds.left >= window.left && bounds.right <= window.left + windowWidth / 3 &&
            bounds.top >= window.top && bounds.bottom <= window.top + windowHeight / 4 &&
            // An icon button, not a title strip.
            width <= height * 2 && height <= width * 2
    }
}

/**
 * Identifies a deliberately opened single Reel without granting the same
 * exception to Instagram's main Reels feed.
 */
internal object InstagramBalancedReelIngressPolicy {
    private val profileGridAncestorIds = setOf("profile_viewpager", "profile_tabs_container",
        "profile_header_fixed_list", "profile_header_container")
    private val audioPageAncestorIds = setOf("clips_pivot_page_fragment", "videos_list")

    /**
     * A thumbnail tap inside a profile's grid, read from the tapped node's
     * ancestors alone. Used when the service has not classified the profile
     * yet: Instagram can stall our queries while it opens a profile from a
     * Reel, so the profile page and the tap arrive late (seen: 2.5 s) and the
     * last classified screen is still the Reel. The audio page shares the
     * thumbnail id but is never a profile.
     */
    fun isProfileGridTap(ancestorIds: Collection<String>): Boolean =
        ancestorIds.any { it in profileGridAncestorIds } &&
            ancestorIds.none { it in audioPageAncestorIds }

    /** How old a late profile-grid tap may be and still count (stalls seen up to 2.5 s). */
    const val LATE_PROFILE_TAP_MAX_AGE_MS = 4_000L

    /**
     * Instagram sometimes opens a chat's or profile's Reel inside the main
     * window, so the bottom tab bar stays visible underneath it (seen on the
     * S23 and reported from a razr fold: the Reel closed at once). While the
     * originating tab is still the selected one, the visible bar is that
     * host, not the main Reels feed (whose own tab is selected instead).
     */
    fun tabBarHostsSingleReel(selectedTab: InstagramTab?, previousScreen: InstagramScreen): Boolean =
        when (selectedTab) {
            InstagramTab.DIRECT -> previousScreen == InstagramScreen.DIRECT_MESSAGES
            InstagramTab.PROFILE -> previousScreen == InstagramScreen.PROFILE ||
                previousScreen == InstagramScreen.SAVED_COLLECTION
            else -> false
        }

    /**
     * The allowed Reel belongs to the viewer window it was verified in. A new
     * viewer opened on top of it (e.g. a Reel from the audio page reached
     * from a chat Reel) must earn its own ingress; it did not inherit one in
     * v1.4.0 only by accident. A window recreated by a configuration change
     * (fold, rotation) carries the session over once via [rebindAllowed].
     */
    fun keepsVerifiedSession(
        mode: ProtectionMode,
        watching: Boolean,
        exiting: Boolean,
        viewerWindowId: Int = -1,
        verifiedWindowId: Int = -1,
        rebindAllowed: Boolean = false
    ): Boolean = mode == ProtectionMode.BALANCED && watching && !exiting &&
        (verifiedWindowId < 0 || viewerWindowId < 0 || viewerWindowId == verifiedWindowId ||
            rebindAllowed)

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
