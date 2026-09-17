package com.resolvecommunity.awaydoomscrollin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityStreakPolicyTest {

    private val oneHourMs = 60 * 60 * 1000L
    private val twentyFourHoursMs = 24 * oneHourMs

    @Test
    fun `streak is preserved when accessibility was never recorded as disabled`() {
        val now = 1_700_000_000_000L
        val decision = AccessibilityStreakPolicy.evaluateStreakReset(
            currentTimeMs = now,
            disabledSinceMs = 0L,
            currentStreak = 14
        )
        assertFalse(decision.shouldReset)
        assertEquals(14, decision.newStreak)
    }

    @Test
    fun `zero streak requires no reset action`() {
        val now = 1_700_000_000_000L
        val disabledSince = now - twentyFourHoursMs - 1000L
        val decision = AccessibilityStreakPolicy.evaluateStreakReset(
            currentTimeMs = now,
            disabledSinceMs = disabledSince,
            currentStreak = 0
        )
        assertFalse(decision.shouldReset)
        assertEquals(0, decision.newStreak)
    }

    @Test
    fun `streak is preserved when disabled for less than 24 hours`() {
        val now = 1_700_000_000_000L
        // Disabled for 23 hours and 59 minutes (within grace period)
        val disabledSince = now - (23 * oneHourMs + 59 * 60 * 1000L)
        val decision = AccessibilityStreakPolicy.evaluateStreakReset(
            currentTimeMs = now,
            disabledSinceMs = disabledSince,
            currentStreak = 7
        )
        assertFalse("Streak should not reset under 24 hours", decision.shouldReset)
        assertEquals(7, decision.newStreak)
    }

    @Test
    fun `streak is reset to 0 when disabled for exactly 24 hours`() {
        val now = 1_700_000_000_000L
        val disabledSince = now - twentyFourHoursMs
        val decision = AccessibilityStreakPolicy.evaluateStreakReset(
            currentTimeMs = now,
            disabledSinceMs = disabledSince,
            currentStreak = 10
        )
        assertTrue("Streak must reset when disabled for 24 hours", decision.shouldReset)
        assertEquals(0, decision.newStreak)
    }

    @Test
    fun `streak is reset to 0 when disabled for more than 24 hours`() {
        val now = 1_700_000_000_000L
        // Disabled for 3 days
        val disabledSince = now - (72 * oneHourMs)
        val decision = AccessibilityStreakPolicy.evaluateStreakReset(
            currentTimeMs = now,
            disabledSinceMs = disabledSince,
            currentStreak = 30
        )
        assertTrue("Streak must reset when disabled for multiple days", decision.shouldReset)
        assertEquals(0, decision.newStreak)
    }

    @Test
    fun `clock manipulation or negative elapsed time does not reset streak`() {
        val now = 1_700_000_000_000L
        // Disabled timestamp in the future due to user changing phone time
        val disabledSince = now + (5 * oneHourMs)
        val decision = AccessibilityStreakPolicy.evaluateStreakReset(
            currentTimeMs = now,
            disabledSinceMs = disabledSince,
            currentStreak = 5
        )
        assertFalse(decision.shouldReset)
        assertEquals(5, decision.newStreak)
    }

    @Test
    fun `custom threshold is respected`() {
        val now = 1_700_000_000_000L
        val threshold = 12 * oneHourMs
        val disabledSince = now - (13 * oneHourMs)
        val decision = AccessibilityStreakPolicy.evaluateStreakReset(
            currentTimeMs = now,
            disabledSinceMs = disabledSince,
            currentStreak = 3,
            thresholdMs = threshold
        )
        assertTrue(decision.shouldReset)
        assertEquals(0, decision.newStreak)
    }
}
