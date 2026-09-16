package com.resolvecommunity.awaydoomscrollin

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstagramDynamicGuardGateTest {
    private val card = InstagramGeometrySample(546, 700, 1056, 1618)

    @Test fun firstCardGeometryUsesBroadGuardUntilQuietAndStable() {
        val gate = InstagramDynamicGuardGate()
        assertTrue(gate.observe(3, card, 0))
        assertTrue(gate.observe(3, card, 200))
        assertTrue(gate.observe(3, card, 319))
        assertFalse(gate.observe(3, card, 320))
    }

    @Test fun movingCardRestartsTheQuietPeriod() {
        val gate = InstagramDynamicGuardGate()
        assertTrue(gate.observe(3, card, 0))
        val moved = card.copy(top = 900, bottom = 1818)
        assertTrue(gate.observe(3, moved, 300))
        assertTrue(gate.observe(3, moved, 500))
        assertFalse(gate.observe(3, moved, 620))
    }

    @Test fun temporarilyMissingCardDoesNotOpenASeam() {
        val gate = InstagramDynamicGuardGate()
        assertTrue(gate.observe(3, card, 0))
        assertTrue(gate.observe(3, null, 200))
    }

    @Test fun maximumHoldPreventsPermanentConversationBlocking() {
        val gate = InstagramDynamicGuardGate(requiredStableSamples = 99)
        assertTrue(gate.observe(3, card, 0))
        assertFalse(gate.observe(3, null, 2_500))
    }
}
