package com.resolvecommunity.awaydoomscrollin

import org.junit.Assert.*
import org.junit.Test

class InstagramProfileReturnStateTest {
    @Test fun departureAloneCannotPrecoverProfile() {
        val state = InstagramProfileReturnState()
        state.arm(100)

        assertTrue(state.isArmed(101))
        assertFalse(state.canPrecoverReturn(101))
        assertNull(state.begin(101))
    }

    @Test fun verifiedAllowedLayerEnablesOneReturnTransition() {
        val state = InstagramProfileReturnState()
        state.arm(100)
        state.observeAllowedLayer(120)

        assertTrue(state.canPrecoverReturn(121))
        val token = state.begin(121)!!
        assertTrue(state.accepts(token, 122))
        assertNull(state.begin(123))
    }

    @Test fun expiryAndClearInvalidateDeferredReturn() {
        val state = InstagramProfileReturnState(lifetimeMs = 50)
        state.arm(100)
        state.observeAllowedLayer(101)
        val token = state.begin(102)!!

        assertFalse(state.accepts(token, 151))
        state.arm(200)
        state.observeAllowedLayer(201)
        val next = state.begin(202)!!
        state.clear()
        assertFalse(state.accepts(next, 203))
    }

    @Test fun aNewDepartureMustObserveItsOwnAllowedLayer() {
        val state = InstagramProfileReturnState()
        state.arm(100)
        state.observeAllowedLayer(101)
        assertTrue(state.canPrecoverReturn(102))

        state.arm(200)
        assertFalse(state.canPrecoverReturn(201))
    }
}
