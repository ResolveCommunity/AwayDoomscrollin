package com.resolvecommunity.awaydoomscrollin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstagramStoryIdentityTest {
    @Test fun storyAvatarLabelsAreLaunchesButCreationIsNot() {
        assertTrue(InstagramStoryIdentity.isStoryLaunchLabel("nasa_es'in görülen hikayesi"))
        assertTrue(InstagramStoryIdentity.isStoryLaunchLabel("Deniz'in hikâyesi"))
        assertTrue(InstagramStoryIdentity.isStoryLaunchLabel("nasa's story, unseen"))
        assertTrue(InstagramStoryIdentity.isStoryLaunchLabel("Story by nasa"))
        assertFalse(InstagramStoryIdentity.isStoryLaunchLabel("Hikayene ekle"))
        assertFalse(InstagramStoryIdentity.isStoryLaunchLabel("Hikayen"))
        assertFalse(InstagramStoryIdentity.isStoryLaunchLabel("Your story"))
        assertFalse(InstagramStoryIdentity.isStoryLaunchLabel("Add to story"))
        assertFalse(InstagramStoryIdentity.isStoryLaunchLabel("Profil fotoğrafı"))
        assertFalse(InstagramStoryIdentity.isStoryLaunchLabel(null))
    }

    @Test fun storyViewerOpensInModalActivity() {
        assertTrue(InstagramStoryIdentity.isStoryViewerWindowClass(
            "com.instagram.modal.TransparentModalActivity"))
        assertFalse(InstagramStoryIdentity.isStoryViewerWindowClass(
            "com.instagram.mainactivity.InstagramMainActivity"))
    }

    @Test fun observedRootStopsFurtherAccessibilityQueries() {
        val queries = mutableListOf<String>()
        assertTrue(InstagramStoryIdentity.isViewer { id ->
            queries.add(id)
            id == "reel_viewer_root"
        })
        assertEquals(listOf("reel_viewer_root"), queries)
    }

    @Test fun observedStoryOverridesMountedHomeOrReels() {
        val ids = setOf("reel_viewer_root", "reel_viewer_progress_bar",
            "reel_viewer_media_container", "reel_viewer_header_container",
            "reel_item_toolbar_container", "main_feed_action_bar", "clips_viewer")
        assertTrue(InstagramStoryIdentity.isViewer(ids))
        assertEquals(InstagramScreen.STORY,
            InstagramProtectionEngine.classify(InstagramUiSignals(ids = ids)))
    }

    @Test fun partialViewerRequiresStorySpecificPair() {
        assertTrue(InstagramStoryIdentity.isViewer(setOf(
            "reel_viewer_progress_bar", "reel_viewer_media_container")))
        assertTrue(InstagramStoryIdentity.isViewer(setOf(
            "reel_item_toolbar_container", "reel_viewer_media_container")))
        assertFalse(InstagramStoryIdentity.isViewer(setOf("reel_item_toolbar_container")))
        assertFalse(InstagramStoryIdentity.isViewer(setOf("reel_viewer_progress_bar")))
    }

    @Test fun traysReelsAndSharedContainersAreNotStories() {
        assertFalse(InstagramStoryIdentity.isViewer(setOf("reels_tray_container",
            "tray_recycler_view", "stories_tray", "view_pager", "clips_viewer",
            "clips_ufi", "swipe_navigation_container", "message_composer_container")))
    }

    @Test fun storyPolicyBlocksOnlyWhenConfigured() {
        val signals = InstagramUiSignals(ids = setOf("reel_viewer_root"))
        val event = InstagramInteraction(InstagramEventKind.CONTENT_CHANGED)
        assertEquals(InstagramGuardAction.ALLOW, InstagramProtectionEngine.guardAction(
            signals, event, InstagramProtectionConfig(blockStories = false)))
        assertEquals(InstagramGuardAction.EXIT_REELS, InstagramProtectionEngine.guardAction(
            signals, event, InstagramProtectionConfig(blockStories = true)))
    }
}
