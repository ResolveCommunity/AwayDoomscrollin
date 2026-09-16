package com.resolvecommunity.awaydoomscrollin

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstagramExitGateTest {
    @Test fun missedSafeReturnCannotLeaveViewerUsableForever() {
        val gate = InstagramExitGate()
        assertTrue(gate.requestBack(100))
        gate.observe(InstagramScreen.REELS, 200)
        assertFalse(gate.requestBack(899))
        assertTrue(gate.requestBack(900))
    }
    @Test fun currentDmClassificationImmediatelyRearmsNextEncounter() {
        val gate = InstagramExitGate()
        assertTrue(gate.requestBack(100))
        gate.observe(InstagramScreen.DIRECT_MESSAGES, 150,
            verifiedConversationReturn = true)
        gate.observe(InstagramScreen.REELS, 151)
        assertTrue(gate.requestBack(151))
    }
    @Test fun repeatedVerifiedConversationReturnsAllowEveryReelEntryWithoutClicks() {
        val gate = InstagramExitGate()
        repeat(100) { index ->
            assertTrue(gate.requestBack())
            assertFalse(gate.requestBack())
            gate.observe(InstagramScreen.DIRECT_MESSAGES, index * 100L,
                verifiedConversationReturn = true)
            gate.observe(InstagramScreen.REELS, index * 100L + 1)
        }
    }
    @Test fun conversationFlagCannotRearmAnActualViewer() {
        val gate = InstagramExitGate()
        gate.requestBack()
        gate.observe(InstagramScreen.REELS, 100, verifiedConversationReturn = true)
        assertFalse(gate.requestBack())
    }
    @Test fun repeatedVerifiedProfileReturnsNeverDependOnClickDelivery() {
        val gate = InstagramExitGate()
        repeat(100) { index ->
            assertTrue(gate.requestBack())
            assertFalse(gate.requestBack())
            val time = index * 100L
            gate.observe(InstagramScreen.PROFILE, time, verifiedProfileReturn = true)
            gate.observe(InstagramScreen.UNKNOWN, time + 1)
            gate.observe(InstagramScreen.COMMENTS_OR_DETAIL, time + 2)
        }
    }
    @Test fun selectedProfileTabInViewerDoesNotProveReturn() {
        assertFalse(InstagramEntryPolicy.isVerifiedProfileReturn(
            InstagramScreen.PROFILE, setOf("profile_tab", "profile_action_bar")))
        assertFalse(InstagramEntryPolicy.isVerifiedProfileReturn(
            InstagramScreen.COMMENTS_OR_DETAIL, setOf("profile_header_container")))
        assertTrue(InstagramEntryPolicy.isVerifiedProfileReturn(
            InstagramScreen.PROFILE, setOf("profile_header_container")))
    }
    @Test fun rapidProfileReentryDoesNotWaitForDwell() {
        val gate = InstagramExitGate()
        assertTrue(gate.requestBack())
        gate.observe(InstagramScreen.PROFILE, 100)
        gate.userClick(120)
        gate.observe(InstagramScreen.COMMENTS_OR_DETAIL, 140)
        assertTrue(gate.requestBack())
        assertFalse(gate.requestBack())
    }
    @Test fun rapidConversationReentrySurvivesViewerEventBeforeClickDelivery() {
        val gate = InstagramExitGate()
        gate.requestBack()
        gate.observe(InstagramScreen.DIRECT_MESSAGES, 100)
        gate.observe(InstagramScreen.REELS, 150)
        gate.userClick(120)
        assertTrue(gate.requestBack())
        gate.userClick(120)
        assertFalse(gate.requestBack())
    }
    @Test fun clickWithoutVerifiedReturnCannotIssueAnotherBack() {
        val gate = InstagramExitGate()
        gate.requestBack()
        gate.userClick(120)
        assertFalse(gate.requestBack())
    }
    @Test fun oldClickBeforeReturnCannotRearm() {
        val gate = InstagramExitGate()
        gate.requestBack()
        gate.observe(InstagramScreen.PROFILE, 100)
        gate.userClick(90)
        assertFalse(gate.requestBack())
    }
    @Test fun repeatedViewerReportsCannotSendSecondBack() {
        val gate = InstagramExitGate()
        assertTrue(gate.requestBack())
        for (time in 0L..5000L step 100) {
            gate.observe(InstagramScreen.REELS, time)
            assertFalse(gate.requestBack())
        }
    }
    @Test fun briefConversationThenStaleViewerDoesNotRearm() {
        val gate = InstagramExitGate()
        assertTrue(gate.requestBack())
        gate.observe(InstagramScreen.DIRECT_MESSAGES, 100)
        gate.observe(InstagramScreen.REELS, 200)
        assertFalse(gate.requestBack())
    }
    @Test fun unknownTransitionDoesNotRearm() {
        val gate = InstagramExitGate()
        gate.requestBack()
        gate.observe(InstagramScreen.DIRECT_MESSAGES, 100)
        gate.observe(InstagramScreen.UNKNOWN, 300)
        gate.observe(InstagramScreen.DIRECT_MESSAGES, 600)
        assertFalse(gate.requestBack())
    }
    @Test fun confirmedSafeDestinationAllowsNextEncounter() {
        val gate = InstagramExitGate()
        gate.requestBack()
        assertTrue(gate.observe(InstagramScreen.DIRECT_MESSAGES, 100))
        assertFalse(gate.observe(InstagramScreen.DIRECT_MESSAGES, 500))
        assertTrue(gate.requestBack())
    }
    @Test fun changingSafeScreensRequiresNewStableInterval() {
        val gate = InstagramExitGate()
        gate.requestBack()
        gate.observe(InstagramScreen.DIRECT_MESSAGES, 100)
        gate.observe(InstagramScreen.PROFILE, 400)
        assertTrue(gate.observe(InstagramScreen.PROFILE, 500))
        assertFalse(gate.requestBack())
    }
    @Test fun serviceResetClearsEncounter() {
        val gate = InstagramExitGate()
        gate.requestBack()
        gate.reset()
        assertTrue(gate.requestBack())
    }
}
