package com.resolvecommunity.awaydoomscrollin

/** Conservative entry recognition. Generic media, story trays and profile tabs are not Reels. */
internal object InstagramEntryPolicy {
    val profileThumbnailIds = setOf(
        "image_button", "media_grid_item", "grid_media", "media_thumbnail",
        "clips_grid_item", "clips_thumbnail"
    )

    fun isProfileThumbnail(screen: InstagramScreen, id: String, insideProfileGrid: Boolean): Boolean =
        screen == InstagramScreen.PROFILE && insideProfileGrid && id in profileThumbnailIds

    fun isVerifiedProfileReturn(screen: InstagramScreen, ids: Set<String>): Boolean =
        screen == InstagramScreen.PROFILE && ids.any {
            it in setOf("profile_header_container", "profile_viewpager", "profile_tabs_container")
        }

    // Observed on the user's DM screen. The card delegates gestures and reports
    // clickable=false; its specific structure, not that flag, identifies it.
    fun isDirectReelCard(id: String, parentId: String, hasReelFooter: Boolean): Boolean =
        id == "reel_share_item_view" &&
            (parentId in setOf(
                "message_content_portrait_xma_container",
                "message_content_generic_xma_container"
            ) || hasReelFooter)

    // Tab ids only; a tab's localized description is never read.
    fun isExploreTabIntent(ids: Set<String>): Boolean = "search_tab" in ids

    fun isHomeTabIntent(ids: Set<String>): Boolean = "feed_tab" in ids

    /**
     * A horizontal main-pager swipe does not emit the click intent used by the
     * regular entry guards. Treat an exclusively selected bottom Reels tab as
     * an entered main Reels destination, while excluding viewers/sheets which
     * merely retain that tab underneath their own Back navigation.
     */
    fun isMainReelsTabDestination(
        reelsSelected: Boolean,
        anotherMainTabSelected: Boolean,
        hasBackNavigation: Boolean
    ): Boolean = reelsSelected && !anotherMainTabSelected && !hasBackNavigation

    fun isDirectReelIntent(
        ancestryIds: Set<String>,
        ancestryLabels: Set<String>,
        insideKnownGuard: Boolean,
        hasReelDescendant: Boolean
    ): Boolean = insideKnownGuard || hasReelDescendant ||
        ancestryIds.any { id ->
            id.contains("reel", ignoreCase = true) || id.contains("clip", ignoreCase = true)
        } || ancestryLabels.any { label ->
            // A Reel link is a URL, identical in every UI language; the
            // localized word "Reel" is not read.
            label.contains("instagram.com/reel", ignoreCase = true)
        }

    private val reelLink = Regex("https?://(?:www\\.)?instagram\\.com/reels?/[A-Za-z0-9_-]+/?(?:[?#]\\S*)?", RegexOption.IGNORE_CASE)

    fun isEntry(id: String, clickable: Boolean, label: String): Boolean =
        id == "clips_tab" ||
            (clickable && hasReelEvidence(id, label))

    fun hasReelEvidence(id: String, label: String): Boolean =
        id == "clips_grid_item" || id == "clips_thumbnail" || reelLink.matches(label.trim())

    // Generic hosts require explicit Reel evidence from a descendant, never just their own ID.
    fun isCardContainer(id: String): Boolean = id in setOf(
        "message_content_generic_xma_container", "media_container", "clips_grid_item", "clips_thumbnail"
    )

    fun isFastViewer(
        playerVisible: Boolean,
        controlsVisible: Boolean,
        storyVisible: Boolean,
        homeHostVisible: Boolean = false,
        backVisible: Boolean = false,
        directLaunchPending: Boolean = false
    ): Boolean = playerVisible && (controlsVisible || directLaunchPending) && !storyVisible &&
        (!homeHostVisible || backVisible)
}
