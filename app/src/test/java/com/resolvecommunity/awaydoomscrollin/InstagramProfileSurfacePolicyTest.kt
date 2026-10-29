package com.resolvecommunity.awaydoomscrollin

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstagramProfileSurfacePolicyTest {
    @Test
    fun collectionOpenedFromAFeedKeepsThatFeedCovered() {
        assertTrue(InstagramProfileSurfacePolicy.keepsFeedCoverOnCollectionExit(
            InstagramScreen.EXPLORE, InstagramScreen.EXPLORE))
        assertTrue(InstagramProfileSurfacePolicy.keepsFeedCoverOnCollectionExit(
            InstagramScreen.HOME_FEED, InstagramScreen.HOME_FEED))
        // Profile menu origin returns to an allowed menu; a stale curtain goes.
        assertFalse(InstagramProfileSurfacePolicy.keepsFeedCoverOnCollectionExit(
            InstagramScreen.PROFILE, InstagramScreen.EXPLORE))
        assertFalse(InstagramProfileSurfacePolicy.keepsFeedCoverOnCollectionExit(
            InstagramScreen.UNKNOWN, InstagramScreen.HOME_FEED))
        assertFalse(InstagramProfileSurfacePolicy.keepsFeedCoverOnCollectionExit(
            InstagramScreen.EXPLORE, null))
    }

    @Test
    fun delayedSearchEntryIsNotAnAllowedProfileDeparture() {
        for (id in setOf("row_search_user_container", "row_search_avatar_with_ring",
                "row_search_user_username", "row_search_user_follow_button")) {
            assertFalse(InstagramProfileSurfacePolicy.isAllowedDeparture(setOf(id), emptySet()))
        }
    }

    @Test
    fun missingClickSourceCannotProveAnAllowedDeparture() {
        assertFalse(InstagramProfileSurfacePolicy.isAllowedDeparture(emptySet(), emptySet()))
        assertFalse(InstagramProfileSurfacePolicy.isAllowedDeparture(
            setOf("profile_header_container", "profile_viewpager"), emptySet()))
    }

    @Test
    fun observedProfileCameraHostMustNotReleaseTheCurtain() {
        val ids = setOf("profile_viewpager", "profile_header_container", "profile_tabs_container",
            "bottom_sheet_camera_container", "reel_ring", "highlights_tray")
        repeat(100) { assertFalse(InstagramProfileSurfacePolicy.hasAllowedLayer(ids)) }
    }

    @Test
    fun SheetNamedBackgroundAndDecorationsAreNotForegroundMenus() {
        assertFalse(InstagramProfileSurfacePolicy.hasAllowedLayer(setOf(
            "profile_viewpager", "action_sheet_background", "bottom_sheet_backdrop")))
        assertTrue(InstagramProfileSurfacePolicy.hasAllowedLayer(setOf(
            "profile_viewpager", "bottom_sheet_camera_container", "action_sheet_container")))
    }
    @Test
    fun highlightTapReleasesProfileCurtain() {
        assertTrue(InstagramProfileSurfacePolicy.isAllowedDeparture(
            setOf("highlights_reel_tray_recycler_view"), emptySet()))
    }

    @Test
    fun profileOverflowTapReleasesProfileCurtain() {
        assertTrue(InstagramProfileSurfacePolicy.isAllowedDeparture(
            setOf("right_action_bar_buttons", "action_bar_button_more"), emptySet()))
    }

    @Test
    fun profileThumbnailDoesNotReleaseCurtain() {
        assertFalse(InstagramProfileSurfacePolicy.isAllowedDeparture(
            setOf("profile_viewpager", "image_button"), setOf("gönderi")))
    }

    @Test
    fun nativeActionSheetWinsOverMountedProfileGrid() {
        assertTrue(InstagramProfileSurfacePolicy.hasAllowedLayer(setOf(
            "profile_viewpager", "profile_header_container", "action_sheet_container")))
    }

    @Test
    fun aboutThisAccountIsAllowedByItsIds() {
        assertTrue(InstagramProfileSurfacePolicy.hasAllowedLayer(
            setOf("profile_viewpager", "about_this_account_container")))
        assertTrue(InstagramProfileSurfacePolicy.isAboutAccountDeparture(
            setOf("about_this_account_row"), emptySet()))
    }

    @Test
    fun shareAndEditProfileAreAllowedByTheirIds() {
        assertTrue(InstagramProfileSurfacePolicy.isAllowedProfileSurface(setOf(
            "profile_share_card", "profile_share_card_copy_link_button",
            "profile_share_card_download_button")))
        assertTrue(InstagramProfileSurfacePolicy.isAllowedProfileSurface(
            setOf("edit_profile_fields")))
        assertTrue(InstagramProfileSurfacePolicy.isAllowedDeparture(setOf("edit_profile_button"), emptySet()))
        assertTrue(InstagramProfileSurfacePolicy.isAllowedDeparture(setOf("share_profile_button"), emptySet()))
    }

    @Test
    fun localizedTitlesAndLabelsNeverDecideInAnyLanguage() {
        // Turkish/English titles used to release the curtain; no language may
        // behave differently from another, so none of them decides anymore.
        for (title in listOf("Edit profile", "Profili düzenle", "Share profile", "Profili paylaş",
                "bu hesap hakkında", "About this account", "Öne çıkanlara ekle", "Add to highlights",
                "Modifier le profil", "Редактировать профиль")) {
            assertFalse(title, InstagramProfileSurfacePolicy.isAllowedProfileSurface(emptySet(), setOf(title)))
            assertFalse(title, InstagramProfileSurfacePolicy.hasAllowedLayer(emptySet(), setOf(title)))
            assertFalse(title, InstagramProfileSurfacePolicy.isAllowedDeparture(
                emptySet(), setOf(title.lowercase())))
        }
        // "Threads" is a brand name, not a translated label.
        assertTrue(InstagramProfileSurfacePolicy.isAllowedDeparture(emptySet(), setOf("threads")))
    }

    @Test
    fun ordinaryProfileDecorationsNeverYield() {
        assertFalse(InstagramProfileSurfacePolicy.isAllowedProfileSurface(setOf(
            "profile_viewpager", "profile_header_container", "bottom_sheet_camera_container",
            "reel_ring", "highlights_tray")))
    }

    @Test
    fun ordinaryProfileGridStillNeedsCurtain() {
        assertFalse(InstagramProfileSurfacePolicy.hasAllowedLayer(setOf(
            "profile_viewpager", "profile_header_container", "profile_tabs_container")))
        assertFalse(InstagramProfileSurfacePolicy.isAllowedDeparture(
            setOf("button_container"), emptySet()))
    }

    @Test
    fun menuCollectionScreensAreRecognizedByStructureInEveryLanguage() {
        // Saved and the Story archive by their own ids (any language).
        assertTrue(InstagramProfileSurfacePolicy.isUserCollection(setOf("saved_view_pager")))
        assertTrue(InstagramProfileSurfacePolicy.isUserCollection(setOf("archive_home_fragment_container")))
        // Your activity's Likes/Reposts/Tags: an id-less Bloks media grid.
        assertTrue(InstagramProfileSurfacePolicy.isUserCollection(setOf("bloks_container"), 15))
        assertFalse(InstagramProfileSurfacePolicy.isUserCollection(setOf("bloks_container"), 0))
        // An ordinary profile or settings page is not a collection.
        assertFalse(InstagramProfileSurfacePolicy.isUserCollection(setOf("profile_viewpager")))
    }

    @Test
    fun idlessMediaTilesAreGridSizedImages() {
        // Likes (3 columns, ~354x360) and Reposts (2 columns, ~455x612) on a 1080 px window.
        assertTrue(InstagramProfileSurfacePolicy.isIdlessMediaTile("android.widget.ImageView", 354, 360, 1080))
        assertTrue(InstagramProfileSurfacePolicy.isIdlessMediaTile("android.widget.ImageView", 455, 612, 1080))
        // Badges, full-width images and text are not tiles.
        assertFalse(InstagramProfileSurfacePolicy.isIdlessMediaTile("android.widget.ImageView", 96, 96, 1080))
        assertFalse(InstagramProfileSurfacePolicy.isIdlessMediaTile("android.widget.ImageView", 1080, 1080, 1080))
        assertFalse(InstagramProfileSurfacePolicy.isIdlessMediaTile("android.widget.TextView", 354, 360, 1080))
    }
}
