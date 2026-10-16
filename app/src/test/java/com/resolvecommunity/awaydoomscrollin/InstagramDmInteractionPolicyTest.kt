package com.resolvecommunity.awaydoomscrollin

import org.junit.Assert.*
import org.junit.Test

class InstagramDmInteractionPolicyTest {
    @Test fun onlyStrictChangedConversationWindowGetsViewerPriority() {
        assertTrue(InstagramDmInteractionPolicy.prioritizesChangedViewer(ProtectionMode.STRICT, true, true))
        assertFalse(InstagramDmInteractionPolicy.prioritizesChangedViewer(ProtectionMode.BALANCED, true, true))
        assertFalse(InstagramDmInteractionPolicy.prioritizesChangedViewer(ProtectionMode.STRICT, false, true))
        assertFalse(InstagramDmInteractionPolicy.prioritizesChangedViewer(ProtectionMode.STRICT, true, false))
    }

    @Test fun neitherModeInterceptsMessagingGestures() {
        ProtectionMode.entries.forEach { mode ->
            assertFalse(InstagramDmInteractionPolicy.interceptsChatTouches(mode))
        }
    }

    @Test fun strictConsumesSharedMediaButBalancedLeavesItOpen() {
        assertTrue(InstagramDmInteractionPolicy.blocksSharedMedia(ProtectionMode.STRICT))
        assertFalse(InstagramDmInteractionPolicy.blocksSharedMedia(ProtectionMode.BALANCED))
    }

    @Test fun noChatCardOrBroadMessagePlaneGuard() {
        assertFalse(InstagramDmInteractionPolicy.mayGuardEntry(InstagramScreen.DIRECT_MESSAGES, false))
    }
    @Test fun mainReelsTabIsStillProtected() {
        assertTrue(InstagramDmInteractionPolicy.mayGuardEntry(InstagramScreen.DIRECT_MESSAGES, true))
        assertTrue(InstagramDmInteractionPolicy.mayGuardEntry(InstagramScreen.HOME_FEED, true))
    }
}
