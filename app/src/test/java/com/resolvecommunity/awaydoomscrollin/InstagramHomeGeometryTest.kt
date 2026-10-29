package com.resolvecommunity.awaydoomscrollin

import org.junit.Assert.assertEquals
import org.junit.Test

class InstagramHomeGeometryTest {
    @Test fun navigationCannotReleaseStoriesOverOutgoingExplore() {
        org.junit.Assert.assertFalse(InstagramHomeGeometry.canMeasureDuringNavigation(true, false))
        org.junit.Assert.assertTrue(InstagramHomeGeometry.canMeasureDuringNavigation(true, true))
        org.junit.Assert.assertTrue(InstagramHomeGeometry.canMeasureDuringNavigation(false, false))
    }

    @Test fun overlayStoriesTrayIsAHomeTrayNotAViewer() {
        org.junit.Assert.assertTrue("overlay_stories_tray_container" in InstagramHomeGeometry.storiesTrayIds)
        org.junit.Assert.assertFalse("story_viewer_container" in InstagramHomeGeometry.storiesTrayIds)
        org.junit.Assert.assertFalse("reel_viewer_root" in InstagramHomeGeometry.storiesTrayIds)
    }
    @Test fun strictCoversObservedStoryTrayButKeepsToolbar() {
        assertEquals(266, InstagramHomeGeometry.top(98, 655, 266, blockStories = true))
    }
    @Test fun balancedKeepsObservedStoryTrayOpen() {
        assertEquals(655, InstagramHomeGeometry.top(98, 655, 266, blockStories = false))
    }
    @Test fun strictWithoutVerifiedToolbarDoesNotExposeStoryTray() {
        assertEquals(98, InstagramHomeGeometry.top(98, 655, null, blockStories = true))
    }
    @Test fun collapsedChromeDoesNotLeaveArtificialTopStrip() {
        assertEquals(0, InstagramHomeGeometry.top(0, null, null))
    }
    @Test fun applicationWindowOffsetIsRespected() {
        assertEquals(80, InstagramHomeGeometry.top(80, null, null))
    }
    @Test fun visibleStoriesRemainAccessible() {
        assertEquals(420, InstagramHomeGeometry.top(80, 420, 180))
    }
    @Test fun verifiedToolbarRemainsAccessible() {
        assertEquals(180, InstagramHomeGeometry.top(80, null, 180))
    }
    @Test fun offscreenTrayDoesNotReserveSpace() {
        assertEquals(80, InstagramHomeGeometry.top(80, 40, null))
    }

    @Test fun followingAndEmptyFavoritesAreAlternateHomeFeeds() {
        val shell = setOf("action_bar_container", "refreshable_container",
            "layout_listview_parent_container", "list")
        org.junit.Assert.assertTrue(InstagramHomeGeometry.isAlternateFeed(
            shell + "row_feed_photo", emptySet(), hasBackNavigation = true))
        org.junit.Assert.assertTrue(InstagramHomeGeometry.isAlternateFeed(
            shell + setOf("empty_state_view_root", "igds_empty_state_image"),
            emptySet(), hasBackNavigation = true))
        // A title alone never decides: it differs in every UI language.
        org.junit.Assert.assertFalse(InstagramHomeGeometry.isAlternateFeed(
            shell, setOf("Favoriler"), hasBackNavigation = true))
        // An empty search list is not a feed.
        org.junit.Assert.assertFalse(InstagramHomeGeometry.isAlternateFeed(
            shell + setOf("empty_state_view_root", "igds_empty_state_image", "search_edit_text"),
            emptySet(), hasBackNavigation = true))
    }

    @Test fun similarlyNamedProfileIsNotAnAlternateHomeFeed() {
        val profile = setOf("action_bar_container", "refreshable_container", "list",
            "profile_header_container", "profile_viewpager")
        org.junit.Assert.assertFalse(InstagramHomeGeometry.isAlternateFeed(
            profile, setOf("favorites"), hasBackNavigation = true))
    }

    @Test fun alternateFeedUsesActionBarAndFullApplicationBody() {
        assertEquals(266, InstagramHomeGeometry.alternateFeedTop(0, 2340, 266))
        assertEquals(2295, InstagramHomeGeometry.alternateFeedBottom(266, 2340, 2295))
        assertEquals(2340, InstagramHomeGeometry.alternateFeedBottom(266, 2340, null))
    }

    @Test fun alternateFeedTransitionRetainsLastSafeViewportWhenChromeDisappears() {
        assertEquals(266, InstagramHomeGeometry.alternateFeedTop(
            0, 2340, actionBarBottom = null, retainedTop = 266))
        assertEquals(2295, InstagramHomeGeometry.alternateFeedBottom(
            266, 2340, contentBottom = null, retainedBottom = 2295))
    }

    @Test fun alternateFeedReturnRequiresNativeStandardHomeChrome() {
        org.junit.Assert.assertFalse(InstagramHomeGeometry.returnedToStandardFeed(
            alternateFeedActive = true, backVisible = true,
            mainHeaderAttached = true, storiesAttached = true))
        org.junit.Assert.assertFalse(InstagramHomeGeometry.returnedToStandardFeed(
            alternateFeedActive = true, backVisible = false,
            mainHeaderAttached = false, storiesAttached = false))
        org.junit.Assert.assertTrue(InstagramHomeGeometry.returnedToStandardFeed(
            alternateFeedActive = true, backVisible = false,
            mainHeaderAttached = false, storiesAttached = true))
        org.junit.Assert.assertTrue(InstagramHomeGeometry.returnedToStandardFeed(
            alternateFeedActive = true, backVisible = false,
            mainHeaderAttached = true, storiesAttached = false))
    }
}
