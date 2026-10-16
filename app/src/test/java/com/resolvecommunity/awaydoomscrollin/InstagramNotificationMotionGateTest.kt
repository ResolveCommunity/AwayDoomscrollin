package com.resolvecommunity.awaydoomscrollin

import org.junit.Assert.*
import org.junit.Test

class InstagramNotificationMotionGateTest {
    @Test fun staticNotificationListIsImmediatelyUsable() {
        assertFalse(InstagramNotificationMotionGate().observe("rows", 100))
    }
    @Test fun scrollingKeepsTapProtectionUntilGeometrySettles() {
        val gate = InstagramNotificationMotionGate()
        gate.arm(100)
        assertTrue(gate.observe("row at 300", 110))
        assertTrue(gate.observe("row at 266", 200))
        assertTrue(gate.observe("row at 266", 300))
        assertFalse(gate.observe("row at 266", 380))
    }
    @Test fun repeatedScrollEventsDoNotReleaseOnCachedBounds() {
        val gate = InstagramNotificationMotionGate()
        gate.arm(100)
        gate.observe("cached", 101)
        gate.arm(250)
        assertTrue(gate.observe("cached", 300))
        assertTrue(gate.observe("cached", 429))
        assertFalse(gate.observe("cached", 430))
    }
    @Test fun missedSettleCannotTrapListForever() {
        val gate = InstagramNotificationMotionGate()
        gate.arm(100)
        assertTrue(gate.observe("moving", 500))
        gate.arm(2_000)
        assertFalse(gate.observe("still moving", 2_100))
    }
    @Test fun leavingNotificationsClearsPendingProtection() {
        val gate = InstagramNotificationMotionGate()
        gate.arm(100)
        gate.reset()
        assertFalse(gate.observe("another screen", 110))
    }
}
