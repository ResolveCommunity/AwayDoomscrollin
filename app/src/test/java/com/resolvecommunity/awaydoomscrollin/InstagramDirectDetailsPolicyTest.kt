package com.resolvecommunity.awaydoomscrollin

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstagramDirectDetailsPolicyTest {
    @Test fun mediaAndRepostsRemainProtected() {
        assertTrue(InstagramDirectDetailsPolicy.shouldProtect(0, false))
        assertTrue(InstagramDirectDetailsPolicy.shouldProtect(1, false))
    }

    @Test fun linksRemainAvailable() {
        assertFalse(InstagramDirectDetailsPolicy.shouldProtect(2, true))
    }

    @Test fun transientMissingSelectionKeepsPreviousState() {
        assertTrue(InstagramDirectDetailsPolicy.shouldProtect(null, true))
        assertFalse(InstagramDirectDetailsPolicy.shouldProtect(null, false))
    }

    @Test fun curtainOverlapsTheMovingPagerSeam() {
        assertTrue(InstagramDirectDetailsPolicy.curtainTop(
            pagerTop = 1000, tabBottom = 1000, edgeBleed = 12) == 988)
        assertTrue(InstagramDirectDetailsPolicy.curtainTop(
            pagerTop = 980, tabBottom = 1000, edgeBleed = 12) == 980)
    }

    @Test fun firstTwoTabsCanBeRecognizedBeforeLinksTabLoads() {
        assertTrue(InstagramDirectDetailsPolicy.selectedTabIndex(2, 0) == 0)
        assertTrue(InstagramDirectDetailsPolicy.selectedTabIndex(2, 1) == 1)
        assertTrue(InstagramDirectDetailsPolicy.selectedTabIndex(1, 0) == 0)
        assertTrue(InstagramDirectDetailsPolicy.selectedTabIndex(0, -1) == null)
    }
}
