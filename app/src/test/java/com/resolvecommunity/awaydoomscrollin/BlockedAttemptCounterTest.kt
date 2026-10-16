package com.resolvecommunity.awaydoomscrollin

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class BlockedAttemptCounterTest {
    @Test fun failedActionsDoNotCountAndAnAcceptedRetryCountsOnce() {
        val counter = BlockedAttemptCounter()
        assertFalse(counter.acceptedIntervention(false))
        assertFalse(counter.acceptedIntervention(false))
        assertTrue(counter.acceptedIntervention(true))
        repeat(20) { assertFalse(counter.acceptedIntervention(true)) }
    }

    @Test fun eachVerifiedNewEncounterCountsOnceWithoutTimeCooldown() {
        val counter = BlockedAttemptCounter()
        var total = 0
        repeat(20) {
            counter.reset()
            repeat(5) { if (counter.acceptedIntervention(true)) total++ }
        }
        assertEquals(20, total)
    }

    @Test fun anIgnoredFirstVideoHasNoCountAndExitRetryDoesNotCreateNewEncounter() {
        val counter = BlockedAttemptCounter()
        assertTrue(counter.acceptedIntervention(true))
        // Session timeout/reclassification is not reset: only a verified return is.
        assertFalse(counter.acceptedIntervention(false))
        assertFalse(counter.acceptedIntervention(true))
        counter.reset()
        assertTrue(counter.acceptedIntervention(true))
    }

    @Test fun separateCompletedTapsAreNotMergedButDragsAndCancellationDoNotCount() {
        val gesture = BlockedTapGesture(12f)
        repeat(5) {
            gesture.begin(20f, 20f)
            assertTrue(gesture.finish(21f, 21f))
            assertFalse(gesture.finish(21f, 21f))
        }
        gesture.begin(20f, 20f)
        gesture.move(35f, 20f)
        assertFalse(gesture.finish(20f, 20f))
        gesture.begin(20f, 20f)
        assertFalse(gesture.finish(20f, 40f))
        gesture.begin(20f, 20f)
        gesture.cancel()
        assertFalse(gesture.finish(20f, 20f))
    }
}
