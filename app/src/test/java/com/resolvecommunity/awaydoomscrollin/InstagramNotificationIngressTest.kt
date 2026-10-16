package com.resolvecommunity.awaydoomscrollin

import org.junit.Assert.*
import org.junit.Test

class InstagramNotificationIngressTest {
    @Test fun fastSecondTapIsHeldUntilReplacementWindowsDraw() {
        val gate = InstagramNotificationIngress()
        val token = gate.begin(0)!!
        assertTrue(gate.awaitingDispatch)
        assertFalse(gate.prepareHandoff(token, "rows", true))
        assertTrue(gate.markDispatched(token))
        assertFalse(gate.markDispatched(token))
        assertFalse(gate.awaitingDispatch)
        assertTrue(gate.active(1)) // Native click acknowledgement must retain protection.
        assertFalse(gate.prepareHandoff(token, "rows", true))
        assertTrue(gate.prepareHandoff(token, "rows", true))
        assertTrue(gate.active(2)) // Prepared is not drawn.
        assertFalse(gate.prepareHandoff(token, "rows", true))
        assertTrue(gate.completeHandoff(token, "rows"))
        assertFalse(gate.active(3))
    }
    @Test fun changingGeometryInvalidatesOldDrawAndKeepsBridge() {
        val gate = InstagramNotificationIngress()
        val token = gate.begin(0)!!
        gate.markDispatched(token)
        gate.prepareHandoff(token, "old", true)
        assertTrue(gate.prepareHandoff(token, "old", true))
        assertFalse(gate.prepareHandoff(token, "new", true))
        assertFalse(gate.completeHandoff(token, "old"))
        assertTrue(gate.active(4))
        assertTrue(gate.prepareHandoff(token, "new", true))
        assertTrue(gate.completeHandoff(token, "new"))
    }
    @Test fun loadingAndOldVisitsCannotReleaseCurrentProtection() {
        val gate = InstagramNotificationIngress()
        val old = gate.begin(0)!!
        gate.markDispatched(old)
        gate.prepareHandoff(old, "same", true)
        gate.prepareHandoff(old, "same", true)
        gate.reset()
        val current = gate.begin(10)!!
        gate.markDispatched(current)
        assertFalse(gate.completeHandoff(old, "same"))
        gate.prepareHandoff(current, "same", true)
        gate.prepareHandoff(current, "same", true)
        assertFalse(gate.prepareHandoff(current, "loading", false))
        assertFalse(gate.completeHandoff(current, "same"))
        assertTrue(gate.active(10000))
    }
    @Test fun refinementRequiresPopulatedStableCards() {
        val gate = InstagramNotificationIngress()
        gate.begin(100)
        assertFalse(gate.ready("loading", false))
        assertFalse(gate.ready("cards-a", true))
        assertFalse(gate.ready("cards-b", true))
        assertTrue(gate.ready("cards-b", true))
        gate.reset()
        gate.begin(110)
        assertFalse(gate.ready("cards-b", true))
    }
    @Test fun repeatedTapCannotDispatchTwice() {
        val gate = InstagramNotificationIngress()
        val token = gate.begin(100)!!
        assertNull(gate.begin(101))
        assertTrue(gate.accepts(token, 102))
    }
    @Test fun slowLoadingNeverExpiresIntoAnUnprotectedList() {
        val gate = InstagramNotificationIngress()
        val token = gate.begin(100)!!
        assertTrue(gate.active(2099))
        assertTrue(gate.accepts(token, 2100))
        assertTrue(gate.accepts(token, 10000))
        assertNull(gate.begin(2100))
        gate.reset()
        assertFalse(gate.active(10001))
    }
    @Test fun resetInvalidatesPendingDrawCallbacks() {
        val gate = InstagramNotificationIngress()
        val old = gate.begin(100)!!
        gate.reset()
        val current = gate.begin(110)!!
        assertFalse(gate.accepts(old, 120))
        assertTrue(gate.accepts(current, 120))
    }
    @Test fun newVisitReplacesAbandonedVisit() {
        val gate = InstagramNotificationIngress()
        val abandoned = gate.startNew()
        gate.markDispatched(abandoned)
        val current = gate.startNew()
        assertFalse(gate.accepts(abandoned, 1))
        assertTrue(gate.accepts(current, 1))
        assertTrue(gate.awaitingDispatch)
    }
}
