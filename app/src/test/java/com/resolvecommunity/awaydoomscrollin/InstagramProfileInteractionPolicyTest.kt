package com.resolvecommunity.awaydoomscrollin

import org.junit.Assert.*
import org.junit.Test

class InstagramProfileInteractionPolicyTest {
    @Test fun bothModesKeepProfileGesturesNative() {
        for (mode in ProtectionMode.entries) {
            assertFalse(InstagramProfileInteractionPolicy.interceptsProfileTouches(mode))
        }
    }
    @Test fun strictStoryViewerStillRequiresExitButBalancedAllowsIt() {
        assertTrue(InstagramProfileInteractionPolicy.exitsStoryViewer(ProtectionMode.STRICT))
        assertFalse(InstagramProfileInteractionPolicy.exitsStoryViewer(ProtectionMode.BALANCED))
    }
    @Test fun onlyStrictBlocksProfilePosts() {
        assertTrue(InstagramProfileInteractionPolicy.blocksProfilePosts(ProtectionMode.STRICT))
        assertFalse(InstagramProfileInteractionPolicy.blocksProfilePosts(ProtectionMode.BALANCED))
    }
    @Test fun missingClickFallbackRequiresBalancedAndAVerifiedProfileOrigin() {
        assertTrue(InstagramProfileInteractionPolicy.allowsProfilePostFallback(
            ProtectionMode.BALANCED, InstagramScreen.PROFILE))
        assertFalse(InstagramProfileInteractionPolicy.allowsProfilePostFallback(
            ProtectionMode.STRICT, InstagramScreen.PROFILE))
        for (screen in InstagramScreen.entries.filter { it != InstagramScreen.PROFILE }) {
            assertFalse(InstagramProfileInteractionPolicy.allowsProfilePostFallback(
                ProtectionMode.BALANCED, screen))
        }
    }
}
