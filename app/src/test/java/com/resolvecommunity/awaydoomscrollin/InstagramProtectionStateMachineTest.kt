package com.resolvecommunity.awaydoomscrollin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstagramProtectionStateMachineTest {
    @Test fun everySurfaceHasOneAuthoritativeAction() {
        val machine = InstagramProtectionStateMachine()
        for (screen in InstagramScreen.entries) {
            val expected = when (screen) {
                InstagramScreen.HOME_FEED, InstagramScreen.EXPLORE,
                InstagramScreen.PROFILE, InstagramScreen.DIRECT_SHARED_MEDIA ->
                    InstagramProtectionAction.SHOW_CURTAIN
                InstagramScreen.REELS, InstagramScreen.COMMENTS_OR_DETAIL ->
                    InstagramProtectionAction.EXIT
                InstagramScreen.UNKNOWN -> InstagramProtectionAction.KEEP_CURRENT
                else -> InstagramProtectionAction.ALLOW
            }
            assertEquals(expected, machine.observe(screen, 1).action)
        }
    }

    @Test fun unknownTreeCannotReplaceAConfirmedProtectedSurface() {
        val machine = InstagramProtectionStateMachine()
        machine.observe(InstagramScreen.EXPLORE, 5)
        val decision = machine.observe(InstagramScreen.UNKNOWN, 5)
        assertEquals(InstagramScreen.EXPLORE, decision.screen)
        assertEquals(InstagramProtectionAction.KEEP_CURRENT, decision.action)
        assertFalse(decision.changed)
    }

    @Test fun windowChangeCreatesANewRevisionEvenOnSameScreen() {
        val machine = InstagramProtectionStateMachine()
        val first = machine.observe(InstagramScreen.HOME_FEED, 10)
        val same = machine.observe(InstagramScreen.HOME_FEED, 10)
        val moved = machine.observe(InstagramScreen.HOME_FEED, 11)
        assertTrue(first.changed)
        assertFalse(same.changed)
        assertTrue(moved.changed)
        assertTrue(moved.revision > same.revision)
    }

    @Test fun leavingInvalidatesTheWindowAndState() {
        val machine = InstagramProtectionStateMachine()
        machine.observe(InstagramScreen.PROFILE, 7)
        machine.leaveInstagram()
        assertEquals(InstagramScreen.UNKNOWN, machine.currentScreen)
        assertEquals(-1, machine.currentWindowId)
    }
}
