package com.resolvecommunity.awaydoomscrollin

import org.junit.Assert.assertEquals
import org.junit.Test

class InstagramStabilityTest {
    @Test fun staleEventAncestryCannotReplaceCurrentProfile() {
        assertEquals(InstagramScreen.PROFILE, InstagramProtectionEngine.classify(
            InstagramUiSignals(ids = setOf("profile_action_bar"),
                sourceIds = setOf("main_feed_action_bar", "clips_viewer"))))
    }

    @Test fun exploreSearchFieldWithoutFocusDoesNotUncoverGrid() {
        assertEquals(InstagramScreen.EXPLORE, InstagramProtectionEngine.classify(
            InstagramUiSignals(ids = setOf("explore_action_bar", "action_bar_search_edit_text"),
                selectedTabs = setOf(InstagramTab.SEARCH))))
    }

    @Test fun typingInExploreAllowsSearch() {
        assertEquals(InstagramScreen.SEARCH_RESULTS, InstagramProtectionEngine.classify(
            InstagramUiSignals(ids = setOf("explore_action_bar", "action_bar_search_edit_text"),
                searchFieldFocused = true)))
    }

    @Test fun fullscreenReelsWinsOverNonHomeMountedHosts() {
        for (tab in InstagramTab.entries.filterNot { it == InstagramTab.HOME }) {
            assertEquals(InstagramScreen.REELS, InstagramProtectionEngine.classify(
                InstagramUiSignals(ids = setOf("root_clips_layout", "reply_bar_edittext",
                    "direct_thread_header", "profile_action_bar", "main_feed_action_bar"),
                    selectedTabs = setOf(tab), hasFullscreenReelsViewer = true)))
        }
    }

    @Test fun fullscreenReelsOverVerifiedHomeNeedsViewerBackChrome() {
        val mountedHome = InstagramUiSignals(
            ids = setOf("root_clips_layout", "main_feed_action_bar"),
            selectedTabs = setOf(InstagramTab.HOME),
            hasFullscreenReelsViewer = true)
        assertEquals(InstagramScreen.HOME_FEED,
            InstagramProtectionEngine.classify(mountedHome))
        assertEquals(InstagramScreen.REELS,
            InstagramProtectionEngine.classify(mountedHome.copy(hasBackNavigation = true)))
    }

    @Test fun externalFullscreenReelNeedsNoTabOrClick() {
        assertEquals(InstagramScreen.REELS, InstagramProtectionEngine.classify(
            InstagramUiSignals(hasFullscreenReelsViewer = true)))
    }

    @Test fun allowedStoryWinsOverUnderlyingHome() {
        val signals = InstagramUiSignals(ids = setOf("story_viewer_container", "main_feed_action_bar"),
            selectedTabs = setOf(InstagramTab.HOME))
        assertEquals(InstagramScreen.STORY, InstagramProtectionEngine.classify(signals))
        assertEquals(InstagramGuardAction.ALLOW, InstagramProtectionEngine.guardAction(signals,
            InstagramInteraction(InstagramEventKind.CONTENT_CHANGED),
            InstagramProtectionConfig(blockStories = false)))
    }

    @Test fun staleSourceWithoutCurrentEvidenceStaysUnknown() {
        assertEquals(InstagramScreen.UNKNOWN, InstagramProtectionEngine.classify(
            InstagramUiSignals(sourceIds = setOf("main_feed_action_bar"))))
    }
}
