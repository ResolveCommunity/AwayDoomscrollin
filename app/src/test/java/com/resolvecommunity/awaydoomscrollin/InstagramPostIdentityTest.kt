package com.resolvecommunity.awaydoomscrollin

import org.junit.Assert.*
import org.junit.Test

class InstagramPostIdentityTest {
    @Test fun notificationFallbackCannotTreatHomeRowsAsDetail() {
        for (hasBack in listOf(false, true)) {
            for (detailsTabs in listOf(false, true)) {
                assertFalse(InstagramPostIdentity.isGenericViewer(
                    false, true, true, hasBack, detailsTabs))
            }
        }
    }

    @Test fun notificationPostWithoutHomeHeaderRemainsDetected() {
        assertTrue(InstagramPostIdentity.isGenericViewer(false, false, true, true, false))
        assertTrue(InstagramPostIdentity.isGenericViewer(false, false, true, false, false))
        assertFalse(InstagramPostIdentity.isGenericViewer(false, false, false, true, false))
        assertFalse(InstagramPostIdentity.isGenericViewer(false, false, true, false, true))
    }

    @Test fun fullPhotoViewerIsNotMistakenForUnderlyingHome() {
        assertTrue(InstagramPostIdentity.isGenericViewer(true, true, true, false, false))
    }

    private val observed = setOf("action_bar_button_back", "layout_listview_parent_container",
        "sticky_header_list", "row_feed_profile_header", "row_feed_photo_imageview",
        "media_group", "row_feed_view_group_buttons")

    @Test fun observedPhotoIsNotHomeAfterCommentsClose() {
        val signals = InstagramUiSignals(ids = observed, titles = setOf("Photo"), hasBackNavigation = true)
        assertEquals(InstagramScreen.COMMENTS_OR_DETAIL, InstagramProtectionEngine.classify(signals))
        assertEquals(InstagramGuardAction.EXIT_REELS, InstagramProtectionEngine.guardAction(
            signals, InstagramInteraction(InstagramEventKind.CONTENT_CHANGED)))
    }
    @Test fun mountedHomeTabCannotOverridePhotoToolbarAndBack() {
        assertEquals(InstagramScreen.COMMENTS_OR_DETAIL, InstagramProtectionEngine.classify(
            InstagramUiSignals(ids = observed, titles = setOf("Photo"), hasBackNavigation = true,
                selectedTabs = setOf(InstagramTab.HOME))))
    }
    @Test fun inlineHomePhotoIsNotAViewer() {
        assertFalse(InstagramPostIdentity.isViewer(observed + "main_feed_action_bar", setOf("Photo"), true))
        assertFalse(InstagramPostIdentity.isViewer(observed, setOf("Photo"), false))
    }
    @Test fun photoWordWithoutPostStructureCannotBlockEditorOrProfile() {
        assertFalse(InstagramPostIdentity.isViewer(setOf("action_bar_button_back", "photo_view"), setOf("Photo"), true))
        assertFalse(InstagramPostIdentity.isViewer(observed, setOf("Settings"), true))
    }
    @Test fun turkishPhotoToolbarIsRecognized() {
        assertTrue(InstagramPostIdentity.isViewer(observed, setOf("Fotoğraf"), true))
    }
}
