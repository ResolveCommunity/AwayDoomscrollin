package com.resolvecommunity.awaydoomscrollin

import org.junit.Assert.*
import org.junit.Test

class InstagramProfileIngressTest {
    @Test fun onlyArmedInstagramStructuralBurstsAreCoalesced() {
        val gate = InstagramProfileIngress()
        assertFalse(gate.coalesceStructuralEvent(true, true, true))
        gate.begin()
        repeat(50) { assertTrue(gate.coalesceStructuralEvent(true, true, true)) }
        assertFalse(gate.coalesceStructuralEvent(false, true, true))
        assertFalse(gate.coalesceStructuralEvent(true, false, true))
        assertFalse(gate.coalesceStructuralEvent(true, true, false))
        gate.reset()
        assertFalse(gate.coalesceStructuralEvent(true, true, true))
    }
    @Test fun initialGridWaitIsBoundedAndDoesNotReleaseProtection() {
        val gate = InstagramProfileIngress()
        gate.begin(100L)
        assertTrue(gate.waitForInitialGrid(100L))
        assertTrue(gate.waitForInitialGrid(599L))
        assertFalse(gate.waitForInitialGrid(600L))
        assertTrue(gate.pending)
    }
    @Test fun drawingOrResetEndsInitialGridWait() {
        val gate = InstagramProfileIngress()
        gate.begin(100L)
        gate.requestCurtainDraw()
        assertFalse(gate.waitForInitialGrid(150L))
        gate.reset()
        assertFalse(gate.waitForInitialGrid(150L))
        gate.begin(1_000L)
        assertTrue(gate.waitForInitialGrid(1_001L))
        assertFalse(gate.waitForInitialGrid(999L))
    }
    @Test fun firstCurtainDrawDoesNotWaitForGuardGeometryStability() {
        val gate = InstagramProfileIngress()
        val token = gate.begin()
        assertTrue(gate.requestCurtainDraw())
        assertTrue(gate.awaitingCurtainDraw)
        assertFalse(gate.requestCurtainDraw())
        gate.confirmCurtainDraw(token)
        assertFalse(gate.awaitingCurtainDraw)
        assertTrue(gate.pending)
        assertFalse(gate.ready("profile"))
        assertFalse(gate.ready("profile"))
        assertTrue(gate.ready("profile"))
    }
    @Test fun staleDrawCannotUnblockNewProfileEntry() {
        val gate = InstagramProfileIngress()
        val old = gate.begin()
        gate.requestCurtainDraw()
        val current = gate.begin()
        assertTrue(gate.requestCurtainDraw())
        gate.confirmCurtainDraw(old)
        assertTrue(gate.awaitingCurtainDraw)
        gate.confirmCurtainDraw(current)
        assertFalse(gate.awaitingCurtainDraw)
    }
    @Test fun noEntryMeansNoProvisionalCurtain() {
        val gate = InstagramProfileIngress()
        assertFalse(gate.requestCurtainDraw())
        gate.begin()
        gate.requestCurtainDraw()
        gate.reset()
        assertFalse(gate.awaitingCurtainDraw)
        assertFalse(gate.requestCurtainDraw())
    }
    @Test fun partialProfileCannotReleaseIngress() {
        val gate = InstagramProfileIngress()
        gate.begin()
        repeat(20) { assertFalse(gate.ready(null)) }
    }
    @Test fun geometryMustSettleBeforeDrawHandoff() {
        val gate = InstagramProfileIngress()
        gate.begin()
        assertFalse(gate.ready("avatar"))
        assertFalse(gate.ready("avatar+ring"))
        assertFalse(gate.ready("avatar+ring"))
        assertTrue(gate.ready("avatar+ring"))
    }
    @Test fun missingSampleResetsStability() {
        val gate = InstagramProfileIngress()
        gate.begin()
        gate.ready("a")
        gate.ready("a")
        gate.ready(null)
        assertFalse(gate.ready("a"))
    }
    @Test fun oldDrawCannotReleaseNewNavigation() {
        val gate = InstagramProfileIngress()
        val old = gate.begin()
        val current = gate.begin()
        assertFalse(gate.isCurrent(old))
        assertTrue(gate.isCurrent(current))
        gate.reset()
        assertFalse(gate.isCurrent(current))
    }
    @Test fun verifiedProfileVisitIsRememberedForBackNavigation() {
        val gate = InstagramProfileIngress()
        gate.begin()
        assertFalse(gate.sawProfile)
        gate.ready("profile")
        gate.ready(null)
        assertTrue(gate.sawProfile)
        gate.reset()
        assertFalse(gate.sawProfile)
    }
}
