package com.resolvecommunity.awaydoomscrollin

import org.junit.Assert.*
import org.junit.Test

class InstagramNotificationShadePolicyTest {
    @Test fun cachedShadeIdentityIsOnlyValidForTheSameMountedSession() {
        assertTrue(InstagramNotificationShadePolicy.canReuseShadeWindow(true, 42, 42))
        assertFalse(InstagramNotificationShadePolicy.canReuseShadeWindow(false, 42, 42))
        assertFalse(InstagramNotificationShadePolicy.canReuseShadeWindow(true, 43, 42))
        assertFalse(InstagramNotificationShadePolicy.canReuseShadeWindow(true, -1, -1))
    }
    @Test fun freshInstagramRootWinsOverPreProbeSystemUiSnapshot() {
        assertTrue(InstagramNotificationShadePolicy.returnedDuringProbe(true, "com.instagram.android"))
        assertFalse(InstagramNotificationShadePolicy.returnedDuringProbe(true, "com.android.systemui"))
        assertFalse(InstagramNotificationShadePolicy.returnedDuringProbe(true, "com.android.settings"))
        assertFalse(InstagramNotificationShadePolicy.returnedDuringProbe(true, null))
        assertFalse(InstagramNotificationShadePolicy.returnedDuringProbe(false, "com.instagram.android"))
    }
    @Test fun delayedSystemUiEventsNeedCurrentRootRouting() {
        assertTrue(InstagramNotificationShadePolicy.useCurrentInstagramRoot(true, true))
        assertFalse(InstagramNotificationShadePolicy.useCurrentInstagramRoot(false, true))
        assertFalse(InstagramNotificationShadePolicy.useCurrentInstagramRoot(true, false))
    }
    @Test fun feedOriginDoesNotIncludeIntentionalReelSources() {
        assertTrue(InstagramNotificationShadePolicy.isProtectedFeedOrigin(InstagramScreen.EXPLORE))
        assertTrue(InstagramNotificationShadePolicy.isProtectedFeedOrigin(InstagramScreen.HOME_FEED))
        assertFalse(InstagramNotificationShadePolicy.isProtectedFeedOrigin(InstagramScreen.SEARCH_RESULTS))
        assertFalse(InstagramNotificationShadePolicy.isProtectedFeedOrigin(InstagramScreen.DIRECT_MESSAGES))
        assertFalse(InstagramNotificationShadePolicy.isProtectedFeedOrigin(InstagramScreen.PROFILE))
    }
    @Test fun opaqueShadeMayOmitThePreviouslyVerifiedApp() {
        assertTrue(InstagramNotificationShadePolicy.hasInstagramContext(null, true))
        assertFalse(InstagramNotificationShadePolicy.hasInstagramContext(null, false))
        assertTrue(InstagramNotificationShadePolicy.hasInstagramContext("com.instagram.android", false))
    }
    @Test fun anotherVisibleAppNeverInheritsTheInstagramCurtain() {
        assertFalse(InstagramNotificationShadePolicy.hasInstagramContext("com.android.settings", true))
        assertFalse(InstagramNotificationShadePolicy.hasInstagramContext("com.google.android.youtube", true))
    }
    @Test fun retainOnlyConfirmedMainFeeds() {
        for (screen in InstagramScreen.entries) {
            assertEquals(screen in setOf(InstagramScreen.HOME_FEED, InstagramScreen.EXPLORE),
                InstagramNotificationShadePolicy.canRetain(true, screen, true, true, true))
        }
    }
    @Test fun neverRetainWithoutEverySafetyCondition() {
        assertFalse(InstagramNotificationShadePolicy.canRetain(false, InstagramScreen.EXPLORE, true, true, true))
        assertFalse(InstagramNotificationShadePolicy.canRetain(true, InstagramScreen.EXPLORE, false, true, true))
        assertFalse(InstagramNotificationShadePolicy.canRetain(true, InstagramScreen.EXPLORE, true, false, true))
        assertFalse(InstagramNotificationShadePolicy.canRetain(true, InstagramScreen.EXPLORE, true, true, false))
    }
}
