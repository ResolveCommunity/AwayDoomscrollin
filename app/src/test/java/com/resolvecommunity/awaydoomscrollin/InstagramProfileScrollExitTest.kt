package com.resolvecommunity.awaydoomscrollin

import org.junit.Assert.*
import org.junit.Test

class InstagramProfileScrollExitTest {
    @Test fun missingDeltaUsesUpwardGridMovement() {
        assertEquals(200, InstagramProfileScrollExit.verticalDelta(0, 0, 900, 700))
        assertEquals(-200, InstagramProfileScrollExit.verticalDelta(0, 0, 700, 900))
    }
    @Test fun missingMeasurementOrHorizontalScrollCannotInventUpwardMotion() {
        assertEquals(0, InstagramProfileScrollExit.verticalDelta(0, 0, 900, null))
        assertEquals(0, InstagramProfileScrollExit.verticalDelta(50, 0, 900, 700))
        assertEquals(0, InstagramProfileScrollExit.verticalDelta(0, 0, 900, 900))
    }
    @Test fun upwardMovementExitsOnFirstEvent() {
        assertTrue(InstagramProfileScrollExit().request(0, 1, 100))
    }
    @Test fun inertiaDoesNotRepeatedlyNavigateBack() {
        val gate = InstagramProfileScrollExit()
        assertTrue(gate.request(0, 40, 100))
        for (time in 200L..5000L step 100) assertFalse(gate.request(0, 40, time))
    }
    @Test fun horizontalDownwardAndZeroEventsDoNotExit() {
        val gate = InstagramProfileScrollExit()
        assertFalse(gate.request(0, 0, 1))
        assertFalse(gate.request(0, -20, 2))
        assertFalse(gate.request(50, 10, 3))
        assertFalse(gate.request(-50, 10, 4))
        assertTrue(gate.request(0, 20, 5))
    }
    @Test fun separateScrollAfterQuietPeriodCanExitAgain() {
        val gate = InstagramProfileScrollExit()
        assertTrue(gate.request(0, 20, 100))
        assertFalse(gate.request(0, 20, 200))
        assertTrue(gate.request(0, 20, 1000))
    }
    @Test fun duplicateOrOutOfOrderEventsCannotRearm() {
        val gate = InstagramProfileScrollExit()
        assertTrue(gate.request(0, 20, 100))
        assertFalse(gate.request(0, 20, 100))
        assertFalse(gate.request(0, 20, 50))
    }
}
