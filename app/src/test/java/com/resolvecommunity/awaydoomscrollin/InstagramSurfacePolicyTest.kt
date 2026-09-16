package com.resolvecommunity.awaydoomscrollin

import org.junit.Assert.*
import org.junit.Test

class InstagramSurfacePolicyTest {
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
