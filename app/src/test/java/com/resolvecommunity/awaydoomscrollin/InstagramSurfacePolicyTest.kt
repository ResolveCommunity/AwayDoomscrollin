package com.resolvecommunity.awaydoomscrollin

import org.junit.Assert.*
import org.junit.Test

class InstagramSurfacePolicyTest {
    @Test fun inboxPruningDoesNotSkipChatsOrViewerLayers() {
        assertTrue(InstagramSurfacePolicy.skipInboxRows("inbox_refreshable_thread_list_recyclerview"))
        for (id in listOf("message_list", "root_clips_layout", "story_viewer_container",
                "thread_details_pager", "tab_bar", "direct_inbox_action_bar")) {
            assertFalse(InstagramSurfacePolicy.skipInboxRows(id))
        }
    }

    @Test fun feedSnapshotCanYieldOnlyTwiceAndOnlyAfterBudget() {
        assertFalse(InstagramSurfacePolicy.yieldFeedSnapshot(99, 0))
        assertTrue(InstagramSurfacePolicy.yieldFeedSnapshot(100, 0))
        assertTrue(InstagramSurfacePolicy.yieldFeedSnapshot(1000, 1))
        assertFalse(InstagramSurfacePolicy.yieldFeedSnapshot(1000, 2))
    }

    @Test fun onlyMeasuredProfilePagerCanSkipMediaDescendants() {
        assertTrue(InstagramSurfacePolicy.skipProtectedProfileGridChildren("profile_viewpager", true, true))
        assertFalse(InstagramSurfacePolicy.skipProtectedProfileGridChildren("profile_viewpager", false, true))
        assertFalse(InstagramSurfacePolicy.skipProtectedProfileGridChildren("profile_viewpager", true, false))
        for (id in listOf("action_bar", "profile_header_container", "message_list", "clips_view_pager")) {
            assertFalse(InstagramSurfacePolicy.skipProtectedProfileGridChildren(id, true, true))
        }
    }

    @Test fun verifiedAccountSearchReleasesOnlyAnAbsentProfile() {
        assertTrue(InstagramSurfacePolicy.canReleaseProfileToAccountSearch(true, true, true, false))
        assertFalse(InstagramSurfacePolicy.canReleaseProfileToAccountSearch(true, true, true, true))
        assertFalse(InstagramSurfacePolicy.canReleaseProfileToAccountSearch(true, false, true, false))
        assertFalse(InstagramSurfacePolicy.canReleaseProfileToAccountSearch(true, true, false, false))
        assertFalse(InstagramSurfacePolicy.canReleaseProfileToAccountSearch(false, true, true, false))
    }

    @Test fun homeExploreAndProfileGridsHaveVisibleCurtains() {
        for (screen in InstagramScreen.entries) {
            assertEquals(screen.name, screen in setOf(
                InstagramScreen.HOME_FEED, InstagramScreen.EXPLORE, InstagramScreen.PROFILE,
                InstagramScreen.DIRECT_SHARED_MEDIA),
                InstagramSurfacePolicy.needsCurtain(screen))
        }
    }
    @Test fun dmSharedMediaUsesACurtainWithoutBecomingAViewerExit() {
        val screen = InstagramProtectionEngine.classify(InstagramUiSignals(ids = setOf(
            "thread_details_header", "shared_section", "thread_details_pager", "shared_media_list")))
        assertEquals(InstagramScreen.DIRECT_SHARED_MEDIA, screen)
        assertTrue(InstagramSurfacePolicy.needsCurtain(screen))
        assertFalse(InstagramSurfacePolicy.needsExit(screen))
    }

    @Test fun dmDetailsWithoutMediaGridRemainsAllowed() {
        val screen = InstagramProtectionEngine.classify(InstagramUiSignals(ids = setOf(
            "thread_details_header", "shared_section", "thread_details_pager")))
        assertEquals(InstagramScreen.DIRECT_MESSAGES, screen)
        assertFalse(InstagramSurfacePolicy.needsCurtain(screen))
    }
    @Test fun postsAndReelsExitWithoutCurtain() {
        for (screen in listOf(InstagramScreen.REELS, InstagramScreen.COMMENTS_OR_DETAIL)) {
            assertTrue(InstagramSurfacePolicy.needsExit(screen))
            assertFalse(InstagramSurfacePolicy.needsCurtain(screen))
        }
    }
    @Test fun openedProfilePostWithSharedFeedShellIsNotAllowed() {
        val screen = InstagramProtectionEngine.classify(InstagramUiSignals(
            ids = setOf("profile_action_bar", "refreshable_container", "sticky_header_list", "feed_tab"),
            titles = setOf("Gönderiler"), selectedTabs = setOf(InstagramTab.PROFILE), hasBackNavigation = true))
        assertEquals(InstagramScreen.COMMENTS_OR_DETAIL, screen)
        assertTrue(InstagramSurfacePolicy.needsExit(screen))
    }
    @Test fun profileGridAndStoriesStayAllowed() {
        assertFalse(InstagramSurfacePolicy.needsExit(InstagramProtectionEngine.classify(
            InstagramUiSignals(ids = setOf("profile_header_container", "profile_tabs_container"),
                selectedTabs = setOf(InstagramTab.PROFILE)))))
        assertFalse(InstagramSurfacePolicy.needsExit(InstagramScreen.STORY))
    }
    @Test fun repeatedPostViewerDoesNotRearmBackGate() {
        val gate = InstagramExitGate()
        assertTrue(gate.requestBack())
        gate.observe(InstagramScreen.COMMENTS_OR_DETAIL, 0)
        gate.observe(InstagramScreen.COMMENTS_OR_DETAIL, 1000)
        assertFalse(gate.requestBack())
    }

    @Test fun stuckReelsViewerAllowsOneFailsafeBackAfterCooldown() {
        val gate = InstagramExitGate()
        assertTrue(gate.requestBack(100L))
        assertFalse(gate.requestBack(599L))
        assertTrue(gate.requestBack(601L))
        assertFalse(gate.requestBack(602L))
    }

    @Test fun autonomousPlayerMutationKeepsAnAlreadyProtectedHome() {
        assertTrue(InstagramSurfacePolicy.shouldKeepVerifiedHome(
            InstagramScreen.HOME_FEED,
            homeCurtainVisible = true,
            sameWindow = true,
            hasFreshUserInput = false))
    }

    @Test fun realUserLaunchCanLeaveAnAlreadyProtectedHome() {
        assertFalse(InstagramSurfacePolicy.shouldKeepVerifiedHome(
            InstagramScreen.HOME_FEED,
            homeCurtainVisible = true,
            sameWindow = true,
            hasFreshUserInput = true))
    }

    @Test fun retainedCurtainCannotPinHomeAfterWindowOrScreenChanged() {
        assertFalse(InstagramSurfacePolicy.shouldKeepVerifiedHome(
            InstagramScreen.UNKNOWN,
            homeCurtainVisible = true,
            sameWindow = true,
            hasFreshUserInput = false))
        assertFalse(InstagramSurfacePolicy.shouldKeepVerifiedHome(
            InstagramScreen.HOME_FEED,
            homeCurtainVisible = true,
            sameWindow = false,
            hasFreshUserInput = false))
    }
}
