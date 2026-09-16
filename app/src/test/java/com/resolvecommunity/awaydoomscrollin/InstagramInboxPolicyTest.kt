package com.resolvecommunity.awaydoomscrollin

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstagramInboxPolicyTest {
    @Test fun directNavigationIsRecognizedWithoutTreatingMediaAsNavigation() {
        assertTrue(InstagramInboxPolicy.isDirectNavigation("direct_tab", ""))
        assertTrue(InstagramInboxPolicy.isDirectNavigation("action_bar_button_action", "Direct"))
        assertFalse(InstagramInboxPolicy.isDirectNavigation("media_container", "Direct"))
        assertFalse(InstagramInboxPolicy.isDirectNavigation("action_bar_button_action", "Notifications"))
        assertFalse(InstagramInboxPolicy.isDirectNavigation("profile_tab", "Messages"))
    }
    @Test fun verifiedInboxCanReleaseHomeShield() {
        assertTrue(InstagramInboxPolicy.isInbox(true, true, false))
    }
    @Test fun inboxEvidenceCannotOverrideReels() {
        assertFalse(InstagramInboxPolicy.isInbox(true, true, true))
    }
    @Test fun partialOrMissingEvidenceDoesNotUncoverHome() {
        assertFalse(InstagramInboxPolicy.isInbox(true, false, false))
        assertFalse(InstagramInboxPolicy.isInbox(false, true, false))
        assertFalse(InstagramInboxPolicy.isInbox(false, false, false))
    }

    @Test fun onlyAFullWidthInboxLayoutMayReleaseProtection() {
        val display = InstagramGeometrySample(0, 0, 1080, 2340)
        assertTrue(InstagramInboxPolicy.isStableInboxLayout(
            InstagramGeometrySample(0, 266, 1080, 2151), display))
        assertFalse(InstagramInboxPolicy.isStableInboxLayout(
            InstagramGeometrySample(486, 266, 1080, 2151), display))
        assertFalse(InstagramInboxPolicy.isStableInboxLayout(
            InstagramGeometrySample(0, 266, 720, 2151), display))
    }

    @Test fun threadClickInsideVerifiedInboxArmsConversationEntry() {
        val list = InstagramGeometrySample(0, 260, 1080, 2100)
        val row = InstagramGeometrySample(0, 600, 1080, 780)
        assertTrue(InstagramInboxPolicy.isThreadOpenIntent(
            setOf("inbox_refreshable_thread_list_recyclerview", "thread_row"),
            row, list, sameWindow = true))
    }

    @Test fun headerAndStaleWindowClicksDoNotArmConversationEntry() {
        val list = InstagramGeometrySample(0, 260, 1080, 2100)
        assertFalse(InstagramInboxPolicy.isThreadOpenIntent(
            setOf("direct_inbox_search_bar"),
            InstagramGeometrySample(0, 300, 1080, 500), list, sameWindow = true))
        assertFalse(InstagramInboxPolicy.isThreadOpenIntent(
            setOf("thread_row"),
            InstagramGeometrySample(0, 600, 1080, 780), list, sameWindow = false))
    }

    @Test fun compactInboxCardsDoNotMasqueradeAsConversationRows() {
        val list = InstagramGeometrySample(0, 260, 1080, 2100)
        assertFalse(InstagramInboxPolicy.isThreadOpenIntent(
            setOf("pog_root_view"),
            InstagramGeometrySample(36, 480, 330, 914), list, sameWindow = true))
    }
}
