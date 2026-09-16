package com.resolvecommunity.awaydoomscrollin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusTierTest {

    @Test
    fun testGetCurrentFocusTierBoundaries() {
        // Negative days should coerce to 0 (Level 1 - Seed)
        val negativeTier = getCurrentFocusTier(-5)
        assertEquals(1, negativeTier.level)
        assertEquals("Seed", negativeTier.nameEn)
        assertEquals("Tohum", negativeTier.nameTr)

        // Day 0 -> Tier 1 (Seed)
        val day0Tier = getCurrentFocusTier(0)
        assertEquals(1, day0Tier.level)

        // Day 1 & 2 -> Tier 2 (First Spark)
        assertEquals(2, getCurrentFocusTier(1).level)
        assertEquals(2, getCurrentFocusTier(2).level)

        // Day 3..5 -> Tier 3 (Dopamine Reset)
        assertEquals(3, getCurrentFocusTier(3).level)
        assertEquals(3, getCurrentFocusTier(5).level)

        // Day 6..9 -> Tier 4 (Steel Will)
        assertEquals(4, getCurrentFocusTier(6).level)
        assertEquals(4, getCurrentFocusTier(9).level)

        // Day 10..13 -> Tier 5 (Flow Master)
        assertEquals(5, getCurrentFocusTier(10).level)
        assertEquals(5, getCurrentFocusTier(13).level)

        // Day 14..20 -> Tier 6 (Mental Clarity)
        assertEquals(6, getCurrentFocusTier(14).level)
        assertEquals(6, getCurrentFocusTier(20).level)

        // Day 21..29 -> Tier 7 (Neuroplastic Shift)
        assertEquals(7, getCurrentFocusTier(21).level)
        assertEquals(7, getCurrentFocusTier(29).level)

        // Day 30..44 -> Tier 8 (Focus Champion)
        assertEquals(8, getCurrentFocusTier(30).level)
        assertEquals(8, getCurrentFocusTier(44).level)

        // Day 45..59 -> Tier 9 (Diamond Discipline)
        assertEquals(9, getCurrentFocusTier(45).level)
        assertEquals(9, getCurrentFocusTier(59).level)

        // Day 60..89 -> Tier 10 (Time Architect)
        assertEquals(10, getCurrentFocusTier(60).level)
        assertEquals(10, getCurrentFocusTier(89).level)

        // Day 90..99 -> Tier 11 (Loop Master)
        assertEquals(11, getCurrentFocusTier(90).level)
        assertEquals(11, getCurrentFocusTier(99).level)

        // Day 100+ -> Tier 12 (Zen Master)
        val zenTier = getCurrentFocusTier(100)
        assertEquals(12, zenTier.level)
        assertEquals("Zen Master", zenTier.nameEn)
        assertEquals("Zen Ustası", zenTier.nameTr)

        val masterTier = getCurrentFocusTier(365)
        assertEquals(12, masterTier.level)
    }

    @Test
    fun testGetNextFocusTier() {
        val nextFrom0 = getNextFocusTier(0)
        assertNotNull(nextFrom0)
        assertEquals(2, nextFrom0?.level)

        val nextFromDay21 = getNextFocusTier(21)
        assertNotNull(nextFromDay21)
        assertEquals(8, nextFromDay21?.level)

        val nextFromDay95 = getNextFocusTier(95)
        assertNotNull(nextFromDay95)
        assertEquals(12, nextFromDay95?.level)

        // Level 12 is top tier, has no next tier
        val nextFromZen = getNextFocusTier(100)
        assertNull(nextFromZen)

        val nextFromHigh = getNextFocusTier(500)
        assertNull(nextFromHigh)
    }

    @Test
    fun testTierProgress() {
        // At minDays of Tier 1 (0 days), progress to Tier 2 (minDays: 1)
        // current.minDays = 0, next.minDays = 1, elapsed = 0 -> progress = 0f
        assertEquals(0.0f, getTierProgress(0), 0.001f)

        // Tier 3: minDays = 3, maxDays = 5. Next Tier 4: minDays = 6.
        // span = 6 - 3 = 3
        // At day 3: elapsed = 0 -> 0.0f
        assertEquals(0.0f, getTierProgress(3), 0.001f)
        // At day 4: elapsed = 1 -> 1/3 ~ 0.333f
        assertEquals(1f / 3f, getTierProgress(4), 0.001f)
        // At day 5: elapsed = 2 -> 2/3 ~ 0.666f
        assertEquals(2f / 3f, getTierProgress(5), 0.001f)

        // Top tier has no next tier, should return 1.0f
        assertEquals(1.0f, getTierProgress(100), 0.001f)
        assertEquals(1.0f, getTierProgress(250), 0.001f)
    }
}
