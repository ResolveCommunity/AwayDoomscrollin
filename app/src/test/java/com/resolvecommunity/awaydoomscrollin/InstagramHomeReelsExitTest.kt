package com.resolvecommunity.awaydoomscrollin

import org.junit.Assert.assertEquals
import org.junit.Test

class InstagramHomeReelsExitTest {
    @Test fun openedViewerWinsOverLingeringHomeBeforeSizeSettles() {
        assertEquals(InstagramScreen.REELS, InstagramProtectionEngine.classify(InstagramUiSignals(
            ids = setOf("main_feed_action_bar", "clips_viewer_action_bar"),
            selectedTabs = setOf(InstagramTab.HOME), hasBackNavigation = true,
            hasClipsInteractionBar = true)))
    }
    @Test fun embeddedHomeVideoDoesNotCauseBackNavigation() {
        assertEquals(InstagramScreen.HOME_FEED, InstagramProtectionEngine.classify(InstagramUiSignals(
            ids = setOf("main_feed_action_bar", "clips_video_container", "clips_ufi"),
            selectedTabs = setOf(InstagramTab.HOME), hasClipsInteractionBar = true)))
    }

    @Test fun embeddedHomeVideoEndingDoesNotBecomeFullscreenReels() {
        assertEquals(InstagramScreen.HOME_FEED, InstagramProtectionEngine.classify(InstagramUiSignals(
            ids = setOf("main_feed_action_bar", "root_clips_layout", "clips_video_container", "clips_ufi"),
            selectedTabs = setOf(InstagramTab.HOME, InstagramTab.REELS),
            hasClipsInteractionBar = true,
            hasFullscreenReelsViewer = true)))
    }

    @Test fun realViewerOverHomeStillExitsWhenBackChromeIsPresent() {
        assertEquals(InstagramScreen.REELS, InstagramProtectionEngine.classify(InstagramUiSignals(
            ids = setOf("main_feed_action_bar", "root_clips_layout", "clips_viewer_action_bar"),
            selectedTabs = setOf(InstagramTab.HOME, InstagramTab.REELS),
            hasBackNavigation = true,
            hasClipsInteractionBar = true,
            hasFullscreenReelsViewer = true)))
    }
}
