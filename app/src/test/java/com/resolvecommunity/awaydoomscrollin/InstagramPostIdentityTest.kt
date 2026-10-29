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

    @Test fun observedPhotoIsClassifiedTheSameInEveryLanguage() {
        val decisions = listOf(setOf("Photo"), setOf("Fotoğraf"), setOf("Фото"), setOf("写真"), emptySet())
            .map { titles ->
                InstagramProtectionEngine.classify(
                    InstagramUiSignals(ids = observed, titles = titles, hasBackNavigation = true))
            }.toSet()
        assertEquals(1, decisions.size)
        // Never an allowed surface: a post reached without a deliberate origin stays protected.
        assertTrue(decisions.single() in setOf(InstagramScreen.HOME_FEED, InstagramScreen.COMMENTS_OR_DETAIL))
    }

    @Test fun fastViewerNeedsADeliberateOriginAndPostStructure() {
        assertTrue(InstagramPostIdentity.isFastViewer(chosen = true, hasBack = true,
            hasHomeHeader = false, hasPostHeader = true, hasPostMedia = true))
        // Without a chosen origin (profile, collection, notification) nothing is admitted.
        assertFalse(InstagramPostIdentity.isFastViewer(chosen = false, hasBack = true,
            hasHomeHeader = false, hasPostHeader = true, hasPostMedia = true))
        // Inline Home photo / missing Back / missing post chrome are never viewers.
        assertFalse(InstagramPostIdentity.isFastViewer(true, true, true, true, true))
        assertFalse(InstagramPostIdentity.isFastViewer(true, false, false, true, true))
        assertFalse(InstagramPostIdentity.isFastViewer(true, true, false, false, true))
        assertFalse(InstagramPostIdentity.isFastViewer(true, true, false, true, false))
    }
}
