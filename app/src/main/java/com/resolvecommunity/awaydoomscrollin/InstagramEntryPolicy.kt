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

    fun isExploreTabIntent(ids: Set<String>, labels: Set<String>): Boolean =
        "search_tab" in ids || ("tab_bar" in ids && labels.any {
            it in setOf("search", "ara", "search and explore", "ara ve keşfet")
        })

    fun isHomeTabIntent(ids: Set<String>, labels: Set<String>): Boolean =
        "feed_tab" in ids || ("tab_bar" in ids && labels.any {
            it in setOf("home", "ana sayfa", "feed", "akış")
        })

    fun isDirectReelIntent(
        ancestryIds: Set<String>,
        ancestryLabels: Set<String>,
        insideKnownGuard: Boolean,
        hasReelDescendant: Boolean
    ): Boolean = insideKnownGuard || hasReelDescendant ||
        ancestryIds.any { id ->
            id.contains("reel", ignoreCase = true) || id.contains("clip", ignoreCase = true)
        } || ancestryLabels.any { label ->
            label.contains("reel", ignoreCase = true) ||
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
