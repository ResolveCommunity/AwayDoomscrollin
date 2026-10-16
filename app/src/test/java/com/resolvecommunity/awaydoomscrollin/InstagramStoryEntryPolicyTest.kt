package com.resolvecommunity.awaydoomscrollin

import org.junit.Assert.*
import org.junit.Test

class InstagramStoryEntryPolicyTest {
    @Test fun verticalGestureNeverScrollsMainOrProfilePager() {
        for (id in listOf("swipeable_nav_view_pager_inner_recycler_view", "swipeable_tab_view_pager",
                "profile_viewpager", "highlights_reel_tray_recycler_view")) {
            assertFalse(InstagramStoryEntryPolicy.isScrollTarget(id, "android.widget.ScrollView", false))
        }
    }
    @Test fun onlyVerifiedVerticalContainersAcceptVerticalGesture() {
        assertTrue(InstagramStoryEntryPolicy.isScrollTarget("profile_header_fixed_list", "android.widget.ListView", false))
        assertTrue(InstagramStoryEntryPolicy.isScrollTarget("", "androidx.core.widget.NestedScrollView", false))
        assertFalse(InstagramStoryEntryPolicy.isScrollTarget("recycler_view", "androidx.recyclerview.widget.RecyclerView", false))
    }
    @Test fun horizontalGestureRemainsConfinedToHighlightsTray() {
        assertTrue(InstagramStoryEntryPolicy.isScrollTarget("highlights_reel_tray_recycler_view", "", true))
        assertFalse(InstagramStoryEntryPolicy.isScrollTarget("profile_header_fixed_list", "", true))
        assertFalse(InstagramStoryEntryPolicy.isScrollTarget("swipeable_nav_view_pager_inner_recycler_view", "", true))
    }

    private val window = InstagramGeometrySample(0, 98, 1080, 2295)
    private val avatar = InstagramGeometrySample(48, 266, 312, 530)
    @Test fun capturedProfileStoryRingIsBlocked() {
        assertTrue(InstagramStoryEntryPolicy.isProfileStoryRing(true, true, avatar, window))
    }
    @Test fun ordinaryProfilePhotoIsNotAStoryEntry() {
        assertFalse(InstagramStoryEntryPolicy.isProfileStoryRing(true, false, avatar, window))
        assertFalse(InstagramStoryEntryPolicy.isProfileStoryRing(false, true, avatar, window))
    }
    @Test fun staleAndContainerGeometryAreRejected() {
        assertFalse(InstagramStoryEntryPolicy.isProfileStoryRing(true, true, window, window))
        assertFalse(InstagramStoryEntryPolicy.isProfileStoryRing(true, true, InstagramGeometrySample(48, -300, 312, -100), window))
        assertFalse(InstagramStoryEntryPolicy.isProfileStoryRing(true, true, InstagramGeometrySample(48, 266, 48, 530), window))
    }
}
