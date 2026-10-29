package com.resolvecommunity.awaydoomscrollin

/** Story viewer IDs observed on-device. A story tray or generic pager is not a viewer. */
internal object InstagramStoryIdentity {
    fun isViewer(ids: Set<String>): Boolean = isViewer(ids::contains)

    // Stop at the first conclusive ID instead of querying every node over accessibility IPC.
    fun isViewer(hasVisibleId: (String) -> Boolean): Boolean =
        hasVisibleId("reel_viewer_root") || hasVisibleId("story_viewer_container") ||
            (hasVisibleId("reel_viewer_progress_bar") &&
                (hasVisibleId("reel_viewer_media_container") || hasVisibleId("reel_viewer_header_container"))) ||
            (hasVisibleId("reel_item_toolbar_container") && hasVisibleId("reel_viewer_media_container"))

    /**
     * Avatars which open a Story when tapped, by id (any UI language): the
     * profile header photo frame (a Story ring sits beside it when one is
     * live; without one the tap opens nothing that needs protection) and a
     * search-result avatar drawn inside a Story ring.
     */
    fun isStoryLaunchSource(id: String): Boolean = id in setOf(
        "row_profile_header_imageview_frame_layout", "row_profile_header_imageview",
        "row_search_avatar_in_ring"
    )

    /** The Story viewer opens in Instagram's modal activity window. */
    fun isStoryViewerWindowClass(className: CharSequence?): Boolean =
        className?.toString()?.endsWith("ModalActivity") == true
}
