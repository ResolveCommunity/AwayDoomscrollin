package com.resolvecommunity.awaydoomscrollin

import org.junit.Assert.assertEquals
import org.junit.Test

class InstagramBalancedReelIngressTest {
    private fun resolve(
        mode: ProtectionMode = ProtectionMode.BALANCED,
        previousScreen: InstagramScreen,
        originWindowId: Int = 10,
        viewerWindowId: Int = 11,
        profileReelIntentWindowId: Int = -1,
        profileReelIntentPending: Boolean = false,
        reelsTabTapLatched: Boolean = false,
        mainTabBarVisible: Boolean = false,
        actionBarBackVisible: Boolean = false,
        directIntentPending: Boolean = false,
        notificationItemWatching: Boolean = false
    ) = InstagramBalancedReelIngressPolicy.resolve(
        mode, previousScreen, originWindowId, viewerWindowId,
        profileReelIntentWindowId, profileReelIntentPending, reelsTabTapLatched,
        mainTabBarVisible, actionBarBackVisible, directIntentPending,
        notificationItemWatching = notificationItemWatching)

    // Pinned-comment notification: comment sheet over a Reel, closed by hand.
    @Test fun closingNotificationCommentSheetAdmitsItsOwnReel() {
        assertEquals(InstagramBalancedReelIngress.NOTIFICATION_COMMENT_SHEET,
            resolve(previousScreen = InstagramScreen.COMMENTS_OR_DETAIL, originWindowId = 2088,
                viewerWindowId = 2088, notificationItemWatching = true))
    }

    @Test fun commentSheetWithoutNotificationOriginGrantsNoReel() {
        assertEquals(InstagramBalancedReelIngress.NONE,
            resolve(previousScreen = InstagramScreen.COMMENTS_OR_DETAIL, originWindowId = 2088,
                viewerWindowId = 2088))
        assertEquals(InstagramBalancedReelIngress.NONE,
            resolve(previousScreen = InstagramScreen.COMMENTS_OR_DETAIL, originWindowId = 2088,
                viewerWindowId = 2090, notificationItemWatching = true))
        assertEquals(InstagramBalancedReelIngress.NONE,
            resolve(mode = ProtectionMode.STRICT, previousScreen = InstagramScreen.COMMENTS_OR_DETAIL,
                originWindowId = 2088, viewerWindowId = 2088, notificationItemWatching = true))
        assertEquals(InstagramBalancedReelIngress.NONE,
            resolve(previousScreen = InstagramScreen.COMMENTS_OR_DETAIL, originWindowId = 2088,
                viewerWindowId = 2088, notificationItemWatching = true, mainTabBarVisible = true))
    }

    @Test fun changedDmViewerWindowAllowsFirstReelBeforeItsBackButtonAppears() {
        assertEquals(InstagramBalancedReelIngress.DIRECT_VIEWER_WINDOW,
            resolve(previousScreen = InstagramScreen.DIRECT_MESSAGES))
    }

    @Test fun explicitProfileReelThumbnailIntentAllowsSameWindowViewer() {
        assertEquals(InstagramBalancedReelIngress.PROFILE_REEL_INTENT,
            resolve(previousScreen = InstagramScreen.PROFILE, originWindowId = 20,
                viewerWindowId = 20, profileReelIntentWindowId = 20,
                profileReelIntentPending = true))
    }

    @Test fun visibleProfileReelsGridAloneCannotGrantAReel() {
        assertEquals(InstagramBalancedReelIngress.NONE,
            resolve(previousScreen = InstagramScreen.PROFILE, originWindowId = 20,
                viewerWindowId = 20, profileReelIntentWindowId = 20,
                profileReelIntentPending = false))
    }

    @Test fun mainReelsTabAlwaysWinsOverAllowedSources() {
        for (latched in listOf(false, true)) {
            val tabBar = !latched
            assertEquals(InstagramBalancedReelIngress.NONE,
                resolve(previousScreen = InstagramScreen.DIRECT_MESSAGES,
                    reelsTabTapLatched = latched, mainTabBarVisible = tabBar,
                    actionBarBackVisible = true, directIntentPending = true))
            assertEquals(InstagramBalancedReelIngress.NONE,
                resolve(previousScreen = InstagramScreen.PROFILE,
                    viewerWindowId = 10, profileReelIntentWindowId = 10,
                    profileReelIntentPending = true,
                    reelsTabTapLatched = latched, mainTabBarVisible = tabBar,
                    actionBarBackVisible = true))
        }
    }

    @Test fun strictNeverAllowsAnySingleReelSource() {
        assertEquals(InstagramBalancedReelIngress.NONE,
            resolve(mode = ProtectionMode.STRICT,
                previousScreen = InstagramScreen.DIRECT_MESSAGES,
                actionBarBackVisible = true, directIntentPending = true))
        assertEquals(InstagramBalancedReelIngress.NONE,
            resolve(mode = ProtectionMode.STRICT,
                previousScreen = InstagramScreen.PROFILE,
                viewerWindowId = 10, profileReelIntentWindowId = 10,
                profileReelIntentPending = true))
    }

    @Test fun unchangedDmWindowNeedsExplicitIntentOrBackButton() {
        assertEquals(InstagramBalancedReelIngress.NONE,
            resolve(previousScreen = InstagramScreen.DIRECT_MESSAGES,
                viewerWindowId = 10))
        assertEquals(InstagramBalancedReelIngress.DIRECT_INTENT,
            resolve(previousScreen = InstagramScreen.DIRECT_MESSAGES,
                viewerWindowId = 10, directIntentPending = true))
        assertEquals(InstagramBalancedReelIngress.ACTION_BAR,
            resolve(previousScreen = InstagramScreen.DIRECT_MESSAGES,
                viewerWindowId = 10, actionBarBackVisible = true))
    }

    @Test fun actionBarAloneCannotAdmitReelFromExternalFeed() {
        assertEquals(InstagramBalancedReelIngress.NONE,
            resolve(previousScreen = InstagramScreen.HOME_FEED,
                viewerWindowId = 10, actionBarBackVisible = true))
        assertEquals(InstagramBalancedReelIngress.NONE,
            resolve(previousScreen = InstagramScreen.EXPLORE,
                viewerWindowId = 10, actionBarBackVisible = true))
        assertEquals(InstagramBalancedReelIngress.NONE,
            resolve(previousScreen = InstagramScreen.NOTIFICATIONS,
                viewerWindowId = 10, actionBarBackVisible = true))
    }

    @Test fun savedCollectionMayUseActionBarFallback() {
        assertEquals(InstagramBalancedReelIngress.ACTION_BAR,
            resolve(previousScreen = InstagramScreen.SAVED_COLLECTION,
                viewerWindowId = 10, actionBarBackVisible = true))
    }

    @Test fun verifiedSessionSurvivesLateMainChromeUntilPagerExit() {
        assertEquals(true, InstagramBalancedReelIngressPolicy.keepsVerifiedSession(
            ProtectionMode.BALANCED, watching = true, exiting = false))
        assertEquals(false, InstagramBalancedReelIngressPolicy.keepsVerifiedSession(
            ProtectionMode.BALANCED, watching = true, exiting = true))
        assertEquals(false, InstagramBalancedReelIngressPolicy.keepsVerifiedSession(
            ProtectionMode.STRICT, watching = true, exiting = false))
    }
}
