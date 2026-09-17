package com.resolvecommunity.awaydoomscrollin

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstagramEntryPolicyTest {
    @Test fun profileGridThumbnailsReceiveInputGuards() {
        for (id in InstagramEntryPolicy.profileThumbnailIds) {
            assertTrue(InstagramEntryPolicy.isProfileThumbnail(InstagramScreen.PROFILE, id, true))
        }
    }
    @Test fun identicalImageButtonsOutsideProfileGridRemainUsable() {
        assertFalse(InstagramEntryPolicy.isProfileThumbnail(InstagramScreen.PROFILE, "image_button", false))
        for (screen in listOf(InstagramScreen.STORY, InstagramScreen.DIRECT_MESSAGES, InstagramScreen.HOME_FEED)) {
            assertFalse(InstagramEntryPolicy.isProfileThumbnail(screen, "image_button", true))
        }
    }
    @Test fun profileChromeAndStoriesNeverQualifyAsThumbnails() {
        for (id in listOf("profile_header_container", "profile_tabs_container", "profile_viewpager",
                "profile_pic", "story_ring", "reels_tray_container", "follow_button")) {
            assertFalse(InstagramEntryPolicy.isProfileThumbnail(InstagramScreen.PROFILE, id, true))
        }
    }
    @Test fun observedDmReelCardDoesNotRequireClickableFlagTextOrLateFooter() {
        assertTrue(InstagramEntryPolicy.isDirectReelCard("reel_share_item_view",
            "message_content_portrait_xma_container", false))
        assertTrue(InstagramEntryPolicy.isDirectReelCard("reel_share_item_view",
            "message_content_generic_xma_container", false))
    }
    @Test fun genericPortraitMediaIsNotEnoughToBlockDm() {
        assertFalse(InstagramEntryPolicy.isDirectReelCard("media_container",
            "message_content_portrait_xma_container", true))
        assertFalse(InstagramEntryPolicy.isDirectReelCard("reel_share_item_view",
            "story_viewer_container", false))
    }
    @Test fun nonClickableReelMarkerCanIdentifyItsCard() {
        assertTrue(InstagramEntryPolicy.hasReelEvidence("clips_thumbnail", ""))
        assertTrue(InstagramEntryPolicy.isCardContainer("message_content_generic_xma_container"))
        assertFalse(InstagramEntryPolicy.hasReelEvidence("message_content_generic_xma_container", ""))
    }
    @Test fun directReelIntentSurvivesGenericClickSourcesAndGuardEdges() {
        assertTrue(InstagramEntryPolicy.isDirectReelIntent(
            setOf("generic_xma_media"), emptySet(), insideKnownGuard = true,
            hasReelDescendant = false))
        assertTrue(InstagramEntryPolicy.isDirectReelIntent(
            setOf("message_content_portrait_xma_container"), emptySet(),
            insideKnownGuard = false, hasReelDescendant = true))
        assertFalse(InstagramEntryPolicy.isDirectReelIntent(
            setOf("message_content_portrait_xma_container"), setOf("normal video"),
            insideKnownGuard = false, hasReelDescendant = false))
    }
    @Test fun conversationAndProfileContainersAreNeverCardTargets() {
        for (id in listOf("direct_thread_layout", "row_thread_message", "profile_tabs_container", "recycler_view")) {
            assertFalse(InstagramEntryPolicy.isCardContainer(id))
        }
    }
    @Test fun fastViewerRequiresBothPlayerAndControlsAndExcludesStories() {
        assertTrue(InstagramEntryPolicy.isFastViewer(true, true, false))
        assertFalse(InstagramEntryPolicy.isFastViewer(true, false, false))
        assertFalse(InstagramEntryPolicy.isFastViewer(false, true, false))
        assertFalse(InstagramEntryPolicy.isFastViewer(true, true, true))
    }

    @Test fun embeddedHomeVideoCannotBecomeAFastReelsExit() {
        assertFalse(InstagramEntryPolicy.isFastViewer(
            playerVisible = true, controlsVisible = true, storyVisible = false,
            homeHostVisible = true, backVisible = false))
        assertTrue(InstagramEntryPolicy.isFastViewer(
            playerVisible = true, controlsVisible = true, storyVisible = false,
            homeHostVisible = true, backVisible = true))
    }
    @Test fun pendingDirectLaunchRecognizesPlayerBeforeControlsAppear() {
        assertTrue(InstagramEntryPolicy.isFastViewer(
            playerVisible = true, controlsVisible = false, storyVisible = false,
            directLaunchPending = true))
        assertFalse(InstagramEntryPolicy.isFastViewer(
            playerVisible = true, controlsVisible = false, storyVisible = true,
            directLaunchPending = true))
        assertFalse(InstagramEntryPolicy.isFastViewer(
            playerVisible = true, controlsVisible = false, storyVisible = false,
            homeHostVisible = true, backVisible = false, directLaunchPending = true))
    }
    @Test fun mainReelsTabIsBlockedEvenIfClickableFlagIsOnItsParent() {
        assertTrue(InstagramEntryPolicy.isEntry("clips_tab", false, ""))
    }
    @Test fun horizontalMainPagerArrivalAtReelsIsBlockedWithoutAClick() {
        assertTrue(InstagramEntryPolicy.isMainReelsTabDestination(
            reelsSelected = true,
            anotherMainTabSelected = false,
            hasBackNavigation = false))
    }
    @Test fun transientOrUnderlyingReelsSelectionDoesNotOverrideItsForegroundSurface() {
        assertFalse(InstagramEntryPolicy.isMainReelsTabDestination(
            reelsSelected = true,
            anotherMainTabSelected = true,
            hasBackNavigation = false))
        assertFalse(InstagramEntryPolicy.isMainReelsTabDestination(
            reelsSelected = true,
            anotherMainTabSelected = false,
            hasBackNavigation = true))
        assertFalse(InstagramEntryPolicy.isMainReelsTabDestination(
            reelsSelected = false,
            anotherMainTabSelected = false,
            hasBackNavigation = false))
    }
    @Test fun explicitClickableReelsThumbnailIsBlocked() {
        assertTrue(InstagramEntryPolicy.isEntry("clips_grid_item", true, ""))
        assertTrue(InstagramEntryPolicy.isEntry("clips_thumbnail", true, ""))
    }
    @Test fun explicitInstagramReelLinkIsBlocked() {
        assertTrue(InstagramEntryPolicy.isEntry("link", true, "https://www.instagram.com/reel/ABC_123/?igsh=abc"))
    }
    @Test fun unrelatedLinksAndTextAreNotBlocked() {
        for (label in listOf("https://example.com/reel/abc/", "https://instagram.com.evil.test/reel/abc/",
            "https://instagram.com/p/abc/", "I sent you a reel", "Reels", "https://instagram.com/stories/person/123/")) {
            assertFalse(label, InstagramEntryPolicy.isEntry("link", true, label))
        }
    }
    @Test fun storiesProfilesAndGenericDmMediaRemainAvailable() {
        for (id in listOf("reels_tray_container", "reel_item_toolbar_container", "profile_tab",
            "profile_tabs_container", "media_container", "message_content_generic_xma_container")) {
            assertFalse(id, InstagramEntryPolicy.isEntry(id, true, ""))
        }
    }
    @Test fun nonClickablePreviewDoesNotBlockConversationGestures() {
        assertFalse(InstagramEntryPolicy.isEntry("clips_thumbnail", false, ""))
    }

    @Test fun onlyTheBottomSearchTabPrearmsExplore() {
        assertTrue(InstagramEntryPolicy.isExploreTabIntent(
            setOf("tab_icon", "search_tab", "tab_bar"), emptySet()))
        assertTrue(InstagramEntryPolicy.isExploreTabIntent(
            setOf("tab_bar"), setOf("ara ve keşfet")))
        assertFalse(InstagramEntryPolicy.isExploreTabIntent(
            setOf("action_bar_search_edit_text"), setOf("ara")))
        assertFalse(InstagramEntryPolicy.isExploreTabIntent(
            setOf("profile_tab", "tab_bar"), setOf("profil")))
    }

    @Test fun onlyTheBottomHomeTabPrearmsTheFeed() {
        assertTrue(InstagramEntryPolicy.isHomeTabIntent(
            setOf("tab_icon", "feed_tab", "tab_bar"), emptySet()))
        assertTrue(InstagramEntryPolicy.isHomeTabIntent(
            setOf("tab_bar"), setOf("ana sayfa")))
        assertFalse(InstagramEntryPolicy.isHomeTabIntent(
            setOf("main_feed_action_bar"), setOf("senin için")))
        assertFalse(InstagramEntryPolicy.isHomeTabIntent(
            setOf("profile_tab", "tab_bar"), setOf("profil")))
    }
}
