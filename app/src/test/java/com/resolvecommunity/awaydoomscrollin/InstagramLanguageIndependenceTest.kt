package com.resolvecommunity.awaydoomscrollin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Id sets captured on-device (Instagram 452, French UI). None of these
 * decisions may depend on the UI language.
 */
class InstagramLanguageIndependenceTest {
    private val frenchProfilePostIds = setOf(
        "action_bar", "action_bar_button_back", "action_bar_container", "action_bar_new_title_container",
        "action_bar_root", "action_bar_title", "action_bar_title_and_icons", "action_bar_wrapper",
        "feed_preview_bottom_cta_container", "feed_preview_keep_watching_button", "layout_container_main",
        "layout_listview_parent_container", "media_group", "media_option_button", "refreshable_container",
        "reposts_ufi_icon", "row_feed_button_comment", "row_feed_button_like", "row_feed_button_save",
        "row_feed_button_share", "row_feed_photo_profile_imageview", "row_feed_photo_profile_name",
        "row_feed_profile_header", "row_feed_view_group_buttons", "swipeable_nav_view_pager_inner_recycler_view",
        "video_container")

    private val frenchFollowingFeedIds = setOf(
        "action_bar", "action_bar_button_back", "action_bar_container", "action_bar_new_title_container",
        "action_bar_root", "action_bar_title", "action_bar_title_and_icons", "action_bar_wrapper",
        "carousel_image", "carousel_image_media_group", "layout_container_main",
        "layout_listview_parent_container", "media_option_button", "refreshable_container", "reposts_ufi_icon",
        "row_feed_button_comment", "row_feed_button_like", "row_feed_button_save", "row_feed_button_share",
        "row_feed_photo_profile_imageview", "row_feed_photo_profile_name", "row_feed_profile_header",
        "row_feed_view_group_buttons", "swipeable_nav_view_pager_inner_recycler_view", "zoomable_view_container")

    private val storyEditorIds = setOf(
        "action_bar_root", "add_caption_textview", "camera_container", "edit_buttons_toolbar",
        "layout_container_left", "layout_container_right", "post_capture_button_root_container",
        "post_capture_button_share_container", "post_capture_compose_view", "post_capture_controls_container",
        "quick_capture_fragment_container", "quick_capture_outer_container", "quick_capture_root_container",
        "story_caption_legibility_background", "story_share_controls_action_bar",
        "your_story_share_shortcut_button")

    @Suppress("UNUSED_PARAMETER")
    private fun fastViewer(titles: Set<String>, chosen: Boolean, ids: Set<String> = frenchProfilePostIds) =
        InstagramPostIdentity.isFastViewer(
            chosen = chosen,
            hasBack = "action_bar_button_back" in ids,
            hasHomeHeader = "main_feed_action_bar" in ids,
            hasPostHeader = "row_feed_profile_header" in ids,
            hasPostMedia = ids.any { it in setOf("row_feed_photo_imageview", "media_group",
                "row_feed_view_group_buttons") })

    @Test fun postPageAndFollowingFeedShareTheirStructure() {
        // Why the title or the navigation is required: both are "alternate feeds" by ids.
        for (ids in listOf(frenchProfilePostIds, frenchFollowingFeedIds)) {
            assertTrue(InstagramHomeGeometry.isAlternateFeed(ids, emptySet(), hasBackNavigation = true))
        }
    }

    @Test fun localizedPostPageChosenOnProfileIsAViewerInAnyLanguage() {
        for (title in listOf("Publications", "Beiträge", "Публикации", "")) {
            assertTrue(fastViewer(setOf(title), chosen = true))
        }
    }

    @Test fun postPageWithoutADeliberateOriginIsTheSameInEveryLanguage() {
        // Turkish/English titles no longer admit a post page that French or
        // Russian would not: every language gets the same decision.
        for (title in listOf("Publications", "Gönderiler", "Posts", "Публикации")) {
            assertFalse(title, fastViewer(setOf(title), chosen = false))
        }
    }

    @Test fun profileChoiceNeverTurnsHomeIntoAViewer() {
        assertFalse(fastViewer(emptySet(), chosen = true, ids = frenchProfilePostIds + "main_feed_action_bar"))
        assertFalse(fastViewer(emptySet(), chosen = true, ids = frenchProfilePostIds - "action_bar_button_back"))
        assertFalse(fastViewer(emptySet(), chosen = true, ids = frenchProfilePostIds - "row_feed_profile_header"))
    }

    @Test fun onlyInstagramSearchEditorsCountAsSearch() {
        assertTrue(InstagramSurfacePolicy.isFocusedSearchField(
            "action_bar_search_edit_text", "android.widget.EditText", focused = true))
        // Bloks settings search: EditText without any resource id.
        assertFalse(InstagramSurfacePolicy.isFocusedSearchField("", "android.widget.EditText", focused = true))
        // Story text / caption editors.
        assertFalse(InstagramSurfacePolicy.isFocusedSearchField(
            "text_overlay_edit_text", "android.widget.EditText", focused = true))
        assertFalse(InstagramSurfacePolicy.isFocusedSearchField(
            "action_bar_search_edit_text", "android.widget.EditText", focused = false))
    }

    @Test fun storyEditorIsCreationWhileTyping() {
        assertEquals(InstagramScreen.CREATION, InstagramProtectionEngine.classify(
            InstagramUiSignals(ids = storyEditorIds)))
        assertEquals(InstagramGuardAction.ALLOW, InstagramProtectionEngine.guardAction(
            InstagramUiSignals(ids = storyEditorIds), InstagramInteraction(InstagramEventKind.CONTENT_CHANGED)))
    }

    @Test fun storyCameraIdsDoNotTurnHomeIntoCreation() {
        val home = setOf("main_feed_action_bar", "reels_tray_container", "row_feed_profile_header",
            "quick_capture_root_container")
        assertEquals(InstagramScreen.HOME_FEED, InstagramProtectionEngine.classify(
            InstagramUiSignals(ids = home, selectedTabs = setOf(InstagramTab.HOME))))
    }

    @Test fun unknownSurfaceRescanBacksOffAfterTransitionWindow() {
        assertEquals(80L, InstagramSurfacePolicy.unknownRescanDelayMs(0L))
        assertEquals(80L, InstagramSurfacePolicy.unknownRescanDelayMs(1_499L))
        assertEquals(400L, InstagramSurfacePolicy.unknownRescanDelayMs(1_500L))
    }

    @Test fun resumeGateExpiresWithoutAClassifiedScreen() {
        val gate = InstagramResumeGeometryGate()
        assertFalse(gate.expired(10_000L))
        gate.begin(InstagramScreen.HOME_FEED, 184, 1_000L)
        assertFalse(gate.expired(3_499L))
        assertTrue(gate.expired(3_500L))
    }

    private val frenchSavedIds = setOf(
        "action_bar", "action_bar_button_back", "action_bar_title", "button", "gradient_background",
        "image_button", "layout_recyclerview_parent_container", "recycler_view", "refreshable_container",
        "saved_content_container", "saved_tab_layout", "saved_view_pager", "title")

    @Test fun savedIsACollectionByIdsEvenWithAReelsSectionTitle() {
        val signals = InstagramUiSignals(ids = frenchSavedIds, hasBackNavigation = true,
            titles = setOf("éléments enregistrés", "reels et publications"))
        assertEquals(InstagramScreen.SAVED_COLLECTION, InstagramProtectionEngine.classify(signals))
        assertTrue(InstagramProfileSurfacePolicy.isUserCollection(frenchSavedIds))
        assertFalse(InstagramProfileSurfacePolicy.isUserCollection(frenchProfilePostIds))
    }

    @Test fun notificationThumbnailIsRecognizedByShapeNotCaption() {
        val row = InstagramGeometrySample(0, 396, 1080, 764)
        // Captured FR row: non-clickable, text-less view at the right edge.
        assertTrue(InstagramNotificationEntryPolicy.isMediaThumbnail("", "android.view.View", false,
            InstagramGeometrySample(912, 426, 1032, 594), row, minSidePx = 90))
        // Avatar on the left, follow/message buttons with any caption, the summary text.
        assertFalse(InstagramNotificationEntryPolicy.isMediaThumbnail("", "android.view.View", false,
            InstagramGeometrySample(48, 414, 192, 558), row, minSidePx = 90))
        assertFalse(InstagramNotificationEntryPolicy.isMediaThumbnail("", "android.widget.Button", true,
            InstagramGeometrySample(800, 500, 1040, 600), row, minSidePx = 90))
        assertFalse(InstagramNotificationEntryPolicy.isMediaThumbnail("", "android.widget.TextView", true,
            InstagramGeometrySample(276, 420, 876, 740), row, minSidePx = 90))
        // A small trailing icon is not media.
        assertFalse(InstagramNotificationEntryPolicy.isMediaThumbnail("", "android.widget.ImageView", false,
            InstagramGeometrySample(990, 560, 1040, 610), row, minSidePx = 90))
    }

    @Test fun storyLaunchIsRecognizedByAvatarId() {
        assertTrue(InstagramStoryIdentity.isStoryLaunchSource("row_profile_header_imageview_frame_layout"))
        assertTrue(InstagramStoryIdentity.isStoryLaunchSource("row_search_avatar_in_ring"))
        assertFalse(InstagramStoryIdentity.isStoryLaunchSource("row_search_user_container"))
        assertFalse(InstagramStoryIdentity.isStoryLaunchSource("avatar_container"))
    }

    @Test fun onlyExploresOwnSearchEditorArmsTheExplorePrecover() {
        // Explore search: its editor proves the session; scrolled results keep it.
        assertTrue(InstagramSurfacePolicy.isExploreSearchSession(true, false, false))
        assertTrue(InstagramSurfacePolicy.isExploreSearchSession(false, true, true))
        // Close friends / settings searches (search_edit_text, Bloks) never arm it.
        assertFalse(InstagramSurfacePolicy.isExploreSearchSession(false, false, true))
        assertFalse(InstagramSurfacePolicy.isExploreSearchSession(false, true, false))
    }

    @Test fun emptyFavoritesIsAnAlternateFeedWithoutItsTitle() {
        // Captured FR empty Favorites; titles deliberately omitted.
        val ids = setOf("action_bar_button_back", "action_bar_container", "action_bar_title",
            "empty_state_view_root", "gap_binder_group", "igds_empty_state_image", "igds_empty_state_title",
            "layout_listview_parent_container", "list", "refreshable_container")
        assertTrue(InstagramHomeGeometry.isAlternateFeed(ids, emptySet(), hasBackNavigation = true))
        assertEquals(InstagramScreen.HOME_FEED, InstagramProtectionEngine.classify(
            InstagramUiSignals(ids = ids, hasBackNavigation = true)))
        // An empty-state image outside a feed list (no gap binder, no empty-state root) is not a feed.
        assertFalse(InstagramHomeGeometry.isAlternateFeed(
            ids - "gap_binder_group" - "empty_state_view_root", emptySet(), true))
    }

    @Test fun ownProfileWithANoteBubbleIsStillTheProfile() {
        // Captured on device: the own profile shows the user's note on its avatar.
        val ownProfile = setOf("pog_note_bubble_card_view", "pog_note", "profile_action_bar",
            "profile_header_container", "profile_tabs_container", "profile_tab_layout",
            "feed_tab", "clips_tab", "direct_tab", "profile_tab")
        assertEquals(InstagramScreen.PROFILE, InstagramProtectionEngine.classify(
            InstagramUiSignals(ids = ownProfile, selectedTabs = setOf(InstagramTab.PROFILE))))
        // The Direct notes tray (no profile header) stays a messaging surface.
        assertEquals(InstagramScreen.DIRECT_MESSAGES, InstagramProtectionEngine.classify(
            InstagramUiSignals(ids = setOf("pog_note_bubble_card_view", "inbox_refreshable_thread_list_recyclerview"))))
    }

    @Test fun instagramPromoDialogIsRecognizedByIds() {
        // Captured on device over the DM inbox (Italian UI): the re-cover waits.
        assertTrue(InstagramSurfacePolicy.isInstagramDialog(dialogContainerVisible = true, promoHeadlineVisible = true))
        assertTrue(InstagramSurfacePolicy.isInstagramDialog(dialogContainerVisible = false, promoHeadlineVisible = true))
        assertFalse(InstagramSurfacePolicy.isInstagramDialog(dialogContainerVisible = false, promoHeadlineVisible = false))
    }

    @Test fun profilePostChoiceIsBoundedAndBalancedOnly() {
        val session = InstagramBalancedProfilePostSession()
        assertFalse(session.isChosen(0L))
        assertTrue(session.arm(ProtectionMode.BALANCED, verifiedProfile = true, profileThumbnail = true, now = 0L))
        assertTrue(session.isChosen(2_000L))
        assertFalse(session.isChosen(2_001L))
        assertFalse(InstagramBalancedProfilePostSession().arm(
            ProtectionMode.STRICT, verifiedProfile = true, profileThumbnail = true, now = 0L))
    }
}
