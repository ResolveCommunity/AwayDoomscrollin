package com.resolvecommunity.awaydoomscrollin

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstagramProfileSurfacePolicyTest {
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
            setOf("accmali_", "Profil", "Gönderiler", "1 Seçili")))
        assertFalse(InstagramProfileSurfacePolicy.hasAllowedLayer(
            setOf("profile_viewpager"), setOf("accmali_")))
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
    fun otherMenuScreensAreNotBlockedCollections() {
        assertFalse(InstagramProfileSurfacePolicy.isBlockedCollectionTitle(
            setOf("Ayarlar ve hareketler", "accmali_")))
        // The highlight picker is released by its own rule, not this one.
        assertFalse(InstagramProfileSurfacePolicy.isBlockedCollectionTitle(
            setOf("Öne çıkanlara ekle")))
    }
}
