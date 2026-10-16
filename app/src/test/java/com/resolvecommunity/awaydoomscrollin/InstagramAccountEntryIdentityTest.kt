package com.resolvecommunity.awaydoomscrollin

import org.junit.Assert.*
import org.junit.Test

class InstagramAccountEntryIdentityTest {
    @Test fun normalControlsDoNotNeedAnAncestryWalk() {
        listOf("profile_header_follow_button", "comment_button", "reel_share_item_view",
            "action_bar_search_edit_text", "action_bar_button_back", "").forEach {
            assertFalse(InstagramAccountEntryIdentity.isPossibleSearchAccountSource(it))
        }
        assertTrue(InstagramAccountEntryIdentity.isPossibleSearchAccountSource("row_search_user_container"))
        assertTrue(InstagramAccountEntryIdentity.isPossibleSearchAccountSource("row_search_avatar_with_ring"))
    }
    @Test fun accountRowAndAvatarIdentifyProfileEntry() {
        assertTrue(InstagramAccountEntryIdentity.isProfileEntry(setOf("row_search_user_container")))
        assertTrue(InstagramAccountEntryIdentity.isProfileEntry(setOf("row_search_avatar_with_ring")))
    }
    @Test fun followButtonMustNotArmProfileGuard() {
        assertFalse(InstagramAccountEntryIdentity.isProfileEntry(
            setOf("row_search_user_follow_button", "row_search_user_container")))
    }
    @Test fun typingScrollingAndUnrelatedProfileControlsAreNotAccountEntry() {
        listOf("action_bar_search_edit_text", "recycler_view", "profile_header_follow_button",
            "highlights_tray", "row_profile_header_imageview_frame_layout").forEach {
            assertFalse(InstagramAccountEntryIdentity.isProfileEntry(setOf(it)))
        }
    }
}
