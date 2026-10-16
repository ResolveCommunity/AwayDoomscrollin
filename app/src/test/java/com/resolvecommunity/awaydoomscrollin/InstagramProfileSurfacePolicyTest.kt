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
    fun aboutThisAccountIsAllowed() {
        assertTrue(InstagramProfileSurfacePolicy.hasAllowedLayer(
            setOf("profile_viewpager", "about_this_account_container")))
        assertTrue(InstagramProfileSurfacePolicy.hasAllowedLayer(
            setOf("profile_viewpager"), setOf("bu hesap hakkında")))
    }

    @Test
    fun shareProfileCardIsAllowed() {
        assertTrue(InstagramProfileSurfacePolicy.isAllowedProfileSurface(setOf(
            "profile_share_card", "profile_share_card_copy_link_button",
            "profile_share_card_download_button")))
        assertTrue(InstagramProfileSurfacePolicy.isAllowedProfileSurface(
            emptySet(), setOf("Share profile")))
        assertTrue(InstagramProfileSurfacePolicy.isAllowedProfileSurface(
            emptySet(), setOf("Profili paylaş")))
    }

    @Test
    fun editProfileBodyIsAllowed() {
        assertTrue(InstagramProfileSurfacePolicy.isAllowedProfileSurface(
            setOf("edit_profile_fields")))
        assertTrue(InstagramProfileSurfacePolicy.isAllowedProfileSurface(
            emptySet(), setOf("Edit profile")))
        assertTrue(InstagramProfileSurfacePolicy.isAllowedProfileSurface(
            emptySet(), setOf("Profili düzenle")))
    }

    @Test
    fun ordinaryProfileDecorationsNeverYield() {
        assertFalse(InstagramProfileSurfacePolicy.isAllowedProfileSurface(setOf(
            "profile_viewpager", "profile_header_container", "bottom_sheet_camera_container",
            "reel_ring", "highlights_tray")))
    }

    @Test
    fun aboutThisAccountActionSheetRowReleasesCurtainBeforeNavigation() {
        assertTrue(InstagramProfileSurfacePolicy.isAllowedDeparture(
            setOf("action_sheet_row_text_view"), setOf("about this account")))
        assertTrue(InstagramProfileSurfacePolicy.isAboutAccountDeparture(
            emptySet(), setOf("bu hesap hakkında")))
    }

    @Test
    fun ordinaryProfileGridStillNeedsCurtain() {
        assertFalse(InstagramProfileSurfacePolicy.hasAllowedLayer(setOf(
            "profile_viewpager", "profile_header_container", "profile_tabs_container")))
    }

    @Test
    fun editAndShareProfileReleaseAndKeepTheCurtainAway() {
        for (label in setOf("profili düzenle", "edit profile", "profili paylaş", "share profile")) {
            assertTrue(InstagramProfileSurfacePolicy.isAllowedDeparture(emptySet(), setOf(label)))
            assertTrue(InstagramProfileSurfacePolicy.hasAllowedLayer(emptySet(), setOf(label)))
        }
        assertFalse(InstagramProfileSurfacePolicy.isAllowedDeparture(
            setOf("button_container"), emptySet()))
    }

    @Test
    fun highlightStoryPickerReleasesTheProfileCurtain() {
        // Real action-bar title of the full-screen story picker (Turkish locale):
        // "Öne çıkanlara ekle" - note the çıkanlara spelling from the live dump.
        assertTrue(InstagramProfileSurfacePolicy.isHighlightPicker(setOf("Öne çıkanlara ekle")))
        assertTrue(InstagramProfileSurfacePolicy.isHighlightPicker(
            setOf("Öne çıkanlara ekle Öne çıkanlara ekle")))
        assertTrue(InstagramProfileSurfacePolicy.hasAllowedLayer(
            setOf("action_bar_title", "day_text", "video_duration_label"),
            setOf("Öne çıkanlara ekle")))
        assertTrue(InstagramProfileSurfacePolicy.hasAllowedLayer(
            emptySet(), setOf("Add to highlights")))
    }

    @Test
    fun otherProfileTitlesNeverReleaseThroughThePickerRule() {
        assertFalse(InstagramProfileSurfacePolicy.isHighlightPicker(
            setOf("ornek_hesap_", "Profil", "Gönderiler", "1 Seçili")))
        assertFalse(InstagramProfileSurfacePolicy.hasAllowedLayer(
            setOf("profile_viewpager"), setOf("ornek_hesap_")))
    }

    @Test
    fun menuCollectionScreensAreBlockedByTitle() {
        // Real action-bar titles of the Saved / Likes / Reposts screens.
        assertTrue(InstagramProfileSurfacePolicy.isBlockedCollectionTitle(setOf("Likes")))
        assertTrue(InstagramProfileSurfacePolicy.isBlockedCollectionTitle(setOf("Beğeniler")))
        assertTrue(InstagramProfileSurfacePolicy.isBlockedCollectionTitle(setOf("Saved")))
        assertTrue(InstagramProfileSurfacePolicy.isBlockedCollectionTitle(setOf("Kaydedilenler")))
        assertTrue(InstagramProfileSurfacePolicy.isBlockedCollectionTitle(setOf("Reposts")))
        assertTrue(InstagramProfileSurfacePolicy.isBlockedCollectionTitle(setOf("Yeniden paylaşılanlar")))
    }

    @Test
    fun menuCollectionScreensAreRecognizedAsUserCollections() {
        assertTrue(InstagramProfileSurfacePolicy.isUserCollectionTitle(setOf("Saved")))
        assertTrue(InstagramProfileSurfacePolicy.isUserCollectionTitle(setOf("Reposts")))
        assertTrue(InstagramProfileSurfacePolicy.isUserCollectionTitle(setOf("Kaydedilenler")))
        assertTrue(InstagramProfileSurfacePolicy.isUserCollectionTitle(setOf("All Posts")))
        assertTrue(InstagramProfileSurfacePolicy.isUserCollectionTitle(setOf("All Reels")))
        assertFalse(InstagramProfileSurfacePolicy.isUserCollectionTitle(setOf("Explore")))
    }

    @Test
    fun otherMenuScreensAreNotBlockedCollections() {
        assertFalse(InstagramProfileSurfacePolicy.isBlockedCollectionTitle(
            setOf("Ayarlar ve hareketler", "ornek_hesap_")))
        // The highlight picker is released by its own rule, not this one.
        assertFalse(InstagramProfileSurfacePolicy.isBlockedCollectionTitle(
            setOf("Öne çıkanlara ekle")))
    }

    @Test
    fun profileSettingsSurfaceIsRecognizedForStaleCurtainRelease() {
        assertTrue(InstagramProfileSurfacePolicy.isSettingsSurfaceTitle(
            setOf("Settings and activity")))
        assertTrue(InstagramProfileSurfacePolicy.isSettingsSurfaceTitle(
            setOf("Ayarlar ve hareketler")))
        assertFalse(InstagramProfileSurfacePolicy.isSettingsSurfaceTitle(
            setOf("Saved", "Explore")))
    }
}
