package com.resolvecommunity.awaydoomscrollin

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstagramResumeGeometryGateTest {
    private val bounds = InstagramGeometrySample(0, 300, 1080, 2200)

    @Test fun resumeRequiresTimeAndRepeatedStableGeometry() {
        val gate = InstagramResumeGeometryGate()
        gate.begin(InstagramScreen.HOME_FEED, 4, 0)
        assertFalse(gate.accept(InstagramScreen.HOME_FEED, 4, bounds, 100))
        assertFalse(gate.accept(InstagramScreen.HOME_FEED, 4, bounds, 550))
        assertTrue(gate.accept(InstagramScreen.HOME_FEED, 4, bounds, 600))
        assertFalse(gate.isActive)
    }

    @Test fun movingGeometryRestartsTheStableSampleCount() {
        val gate = InstagramResumeGeometryGate()
        gate.begin(InstagramScreen.EXPLORE, 9, 0)
        assertFalse(gate.accept(InstagramScreen.EXPLORE, 9, bounds, 500))
        val moved = bounds.copy(top = 340)
        assertFalse(gate.accept(InstagramScreen.EXPLORE, 9, moved, 600))
        assertFalse(gate.accept(InstagramScreen.EXPLORE, 9, moved, 650))
        assertTrue(gate.accept(InstagramScreen.EXPLORE, 9, moved, 700))
    }

    @Test fun replacementWindowMustSettleBeforeItCanResizeTheCurtain() {
        val gate = InstagramResumeGeometryGate()
        gate.begin(InstagramScreen.PROFILE, 10, 0)
        assertFalse(gate.accept(InstagramScreen.PROFILE, 11, bounds, 1))
        assertFalse(gate.accept(InstagramScreen.PROFILE, 11, bounds, 500))
        assertFalse(gate.accept(InstagramScreen.PROFILE, 11, bounds, 600))
        assertTrue(gate.accept(InstagramScreen.PROFILE, 11, bounds, 601))
        assertFalse(gate.isActive)
    }

    @Test fun everyReplacementWindowRestartsSettlingWithoutDroppingTheHold() {
        val gate = InstagramResumeGeometryGate()
        gate.begin(InstagramScreen.EXPLORE, 10, 0)
        assertFalse(gate.accept(InstagramScreen.EXPLORE, 11, bounds.copy(left = 700), 100))
        assertFalse(gate.accept(InstagramScreen.EXPLORE, 12, bounds.copy(left = 300), 450))
        assertFalse(gate.accept(InstagramScreen.EXPLORE, 12, bounds, 1_000))
        assertFalse(gate.accept(InstagramScreen.EXPLORE, 12, bounds, 1_049))
        assertTrue(gate.accept(InstagramScreen.EXPLORE, 12, bounds, 1_050))
    }

    @Test fun realSurfaceChangeEndsTheOldGeometryHold() {
        val gate = InstagramResumeGeometryGate()
        gate.begin(InstagramScreen.HOME_FEED, 10, 0)
        assertTrue(gate.accept(InstagramScreen.EXPLORE, 11, bounds, 1))
        assertFalse(gate.isActive)
    }

    @Test fun maximumHoldPreventsPermanentTransitionCover() {
        val gate = InstagramResumeGeometryGate(requiredStableSamples = 99)
        gate.begin(InstagramScreen.DIRECT_SHARED_MEDIA, 2, 0)
        assertFalse(gate.accept(InstagramScreen.DIRECT_SHARED_MEDIA, 2, bounds, 2_499))
        assertTrue(gate.accept(InstagramScreen.DIRECT_SHARED_MEDIA, 2,
            bounds.copy(top = 500), 2_500))
    }
}
