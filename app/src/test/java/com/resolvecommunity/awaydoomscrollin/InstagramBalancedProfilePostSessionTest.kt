package com.resolvecommunity.awaydoomscrollin

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstagramBalancedProfilePostSessionTest {
    @Test fun balancedVerifiedProfileThumbnailCanOpenOneStaticPost() {
        val session = InstagramBalancedProfilePostSession()
        assertTrue(session.arm(ProtectionMode.BALANCED, true, true, 100L))
        assertTrue(session.confirm(ProtectionMode.BALANCED, 500L))
        assertTrue(session.watching)
        session.observeVerifiedSurface(InstagramScreen.COMMENTS_OR_DETAIL, 3_000L)
        assertTrue(session.watching)
    }

    @Test fun strictAndUnverifiedSourcesNeverReceiveTheException() {
        val session = InstagramBalancedProfilePostSession()
        assertFalse(session.arm(ProtectionMode.STRICT, true, true, 100L))
        assertFalse(session.arm(ProtectionMode.BALANCED, false, true, 100L))
        assertFalse(session.arm(ProtectionMode.BALANCED, true, false, 100L))
        assertFalse(session.confirm(ProtectionMode.BALANCED, 200L))
    }

    @Test fun pendingLaunchExpiresAndConfirmedLaunchEndsOnVerifiedReturn() {
        val session = InstagramBalancedProfilePostSession(launchWindowMs = 100L)
        session.arm(ProtectionMode.BALANCED, true, true, 100L)
        assertFalse(session.confirm(ProtectionMode.BALANCED, 201L))
        session.arm(ProtectionMode.BALANCED, true, true, 300L)
        assertTrue(session.confirm(ProtectionMode.BALANCED, 350L))
        session.observeVerifiedSurface(InstagramScreen.PROFILE, 400L)
        assertFalse(session.watching)
    }

    @Test fun unrelatedDestinationCancelsPendingLaunch() {
        val session = InstagramBalancedProfilePostSession()
        session.arm(ProtectionMode.BALANCED, true, true, 100L)
        session.observeVerifiedSurface(InstagramScreen.EXPLORE, 150L)
        assertFalse(session.confirm(ProtectionMode.BALANCED, 160L))
    }
}
