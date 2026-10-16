package com.resolvecommunity.awaydoomscrollin

internal object InstagramStoryEntryPolicy {
    fun isScrollTarget(id: String, className: String, horizontal: Boolean): Boolean {
        if (horizontal) return id == "highlights_reel_tray_recycler_view"
        if (id in setOf("swipeable_nav_view_pager_inner_recycler_view", "swipeable_tab_view_pager",
                "profile_viewpager", "highlights_reel_tray_recycler_view")) return false
        return id == "profile_header_fixed_list" ||
            className == "android.widget.ScrollView" ||
            className == "androidx.core.widget.NestedScrollView"
    }

    fun isProfileStoryRing(hasProfileHeader: Boolean, hasStoryRing: Boolean,
        bounds: InstagramGeometrySample, window: InstagramGeometrySample): Boolean =
        hasProfileHeader && hasStoryRing && bounds.right > bounds.left && bounds.bottom > bounds.top &&
            bounds.right - bounds.left <= (window.right - window.left) / 2 &&
            bounds.bottom > window.top && bounds.top < window.bottom &&
            bounds.right > window.left && bounds.left < window.right
}
