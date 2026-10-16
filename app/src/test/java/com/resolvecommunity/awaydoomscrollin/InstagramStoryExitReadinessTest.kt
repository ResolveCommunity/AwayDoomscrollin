package com.resolvecommunity.awaydoomscrollin

import org.junit.Assert.*
import org.junit.Test

class InstagramStoryExitReadinessTest {
    @Test fun firstMountedTreeCannotImmediatelyNavigateBack() {
        val gate = InstagramStoryExitReadiness()
        assertEquals(200L, gate.delayBeforeBack(1, 1000L))
        assertEquals(50L, gate.delayBeforeBack(1, 1150L))
        assertEquals(0L, gate.delayBeforeBack(1, 1200L))
    }
    @Test fun repeatedEventsDoNotPostponeTheEncounter() {
        val gate = InstagramStoryExitReadiness()
        gate.delayBeforeBack(1, 1000L)
        for (now in 1001L..1199L) gate.delayBeforeBack(1, now)
        assertEquals(0L, gate.delayBeforeBack(1, 1200L))
    }
    @Test fun verifiedReturnOrNewWindowNeedsANewTransitionCheck() {
        val gate = InstagramStoryExitReadiness()
        gate.delayBeforeBack(1, 1000L)
        assertEquals(200L, gate.delayBeforeBack(2, 1300L))
        gate.reset()
        assertEquals(200L, gate.delayBeforeBack(2, 2000L))
    }
    @Test fun InvalidWindowOrClockRegressionCannotArmAnImmediateBack() {
        val gate = InstagramStoryExitReadiness()
        assertEquals(200L, gate.delayBeforeBack(-1, 1000L))
        gate.delayBeforeBack(1, 1000L)
        assertEquals(200L, gate.delayBeforeBack(1, 900L))
    }
}
