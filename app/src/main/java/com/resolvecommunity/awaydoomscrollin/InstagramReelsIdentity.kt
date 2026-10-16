package com.resolvecommunity.awaydoomscrollin

/** Exact IDs captured in the selected Reel and its comments, not generic recyclers. */
internal object InstagramReelsIdentity {
    fun isCurrentSessionEvent(verifiedWindow: Int, eventWindow: Int, rootWindow: Int,
        eventTime: Long, verifiedAt: Long, now: Long): Boolean =
        verifiedWindow >= 0 && verifiedWindow == eventWindow && verifiedWindow == rootWindow &&
            eventTime >= verifiedAt && now - eventTime in 0L..1_500L

    val pagerIds = setOf("clips_viewer_view_pager", "clips_view_pager")
    val safeSheetIds = setOf("comment_overswipe_dismiss_container", "recycler_view_container_id",
        "comment_composer_parent_updated", "layout_comment_thread_edittext_multiline")

    fun isVideoPagerPath(ids: Set<String>): Boolean =
        ids.any { it in pagerIds } && ids.none { it in safeSheetIds ||
            it == "bottom_sheet_container" || it == "layout_container_bottom_sheet" }

    /**
     * Saved's "All Posts" surface opens a selected Reel in a feed-style list,
     * not in clips_viewer_view_pager. Keep that list distinct from comments so
     * a vertical swipe cannot advance to the next saved item.
     */
    fun isSavedCollectionListPath(ids: Set<String>): Boolean =
        ids.any { it in setOf(
            "layout_listview_parent_container",
            "refreshable_container",
            "list",
            "swipeable_nav_view_pager_inner_recycler_view"
        ) } && ids.none { it in safeSheetIds ||
            it == "bottom_sheet_container" || it == "layout_container_bottom_sheet" }
}
