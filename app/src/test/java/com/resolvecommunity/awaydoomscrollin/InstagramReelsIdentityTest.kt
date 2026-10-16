package com.resolvecommunity.awaydoomscrollin

import org.junit.Assert.*
import org.junit.Test

class InstagramReelsIdentityTest {
    @Test fun fastExitRequiresTheConfirmedCurrentWindow() {
        assertTrue(InstagramReelsIdentity.isCurrentSessionEvent(10, 10, 10, 120, 100, 130))
        assertFalse(InstagramReelsIdentity.isCurrentSessionEvent(10, 11, 10, 120, 100, 130))
        assertFalse(InstagramReelsIdentity.isCurrentSessionEvent(10, 10, 11, 120, 100, 130))
        assertFalse(InstagramReelsIdentity.isCurrentSessionEvent(-1, -1, -1, 120, 100, 130))
    }
    @Test fun oldScrollCannotCloseANewlyOpenedReel() {
        assertFalse(InstagramReelsIdentity.isCurrentSessionEvent(10, 10, 10, 90, 100, 130))
        assertFalse(InstagramReelsIdentity.isCurrentSessionEvent(10, 10, 10, 120, 100, 2000))
        assertFalse(InstagramReelsIdentity.isCurrentSessionEvent(10, 10, 10, 140, 100, 130))
    }
    @Test fun realPagerRecognized() {
        assertTrue(InstagramReelsIdentity.isVideoPagerPath(setOf("clips_viewer_view_pager", "root_clips_layout")))
    }
    @Test fun commentLayerWinsEvenWithBackgroundPager() {
        InstagramReelsIdentity.safeSheetIds.forEach {
            assertFalse(InstagramReelsIdentity.isVideoPagerPath(setOf(it, "clips_viewer_view_pager")))
        }
    }
    @Test fun ordinaryListsAndVideoControlsAreNotVideoChanges() {
        listOf("recycler_view", "message_list", "profile_viewpager", "clips_video_container", "clips_ufi_component")
            .forEach { assertFalse(InstagramReelsIdentity.isVideoPagerPath(setOf(it))) }
    }
    @Test fun savedCollectionListIsAValidSingleReelExitPath() {
        assertTrue(InstagramReelsIdentity.isSavedCollectionListPath(
            setOf("layout_listview_parent_container", "list", "media_group")))
        assertFalse(InstagramReelsIdentity.isSavedCollectionListPath(
            setOf("layout_listview_parent_container", "comment_overswipe_dismiss_container")))
    }
}
