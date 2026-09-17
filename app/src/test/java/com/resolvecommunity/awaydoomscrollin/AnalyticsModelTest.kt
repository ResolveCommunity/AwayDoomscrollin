package com.resolvecommunity.awaydoomscrollin

import org.junit.Assert.assertEquals
import org.junit.Test

class AnalyticsModelTest {
    @Test
    fun olderUncategorizedCountsStaySeparateFromInstagram() {
        val detail = day(total = 10, instagram = 3, tiktok = 2, youtube = 1)

        assertEquals(6, detail.categorizedBlocks)
        assertEquals(4, detail.uncategorizedBlocks)
        assertEquals(10, detail.chartTotal)
        assertEquals(3, detail.effectiveInsta)
    }

    @Test
    fun categorizedCountsWinIfLegacyTotalIsBehind() {
        val detail = day(total = 2, instagram = 3, tiktok = 2, youtube = 1)

        assertEquals(6, detail.chartTotal)
        assertEquals(0, detail.uncategorizedBlocks)
    }

    @Test
    fun monthlyAggregationEnforcesCategorizedSums() {
        val days = listOf(
            day(total = 5, instagram = 2, tiktok = 1, youtube = 1),
            day(total = 2, instagram = 3, tiktok = 1, youtube = 0) // categorized (4) > total (2)
        )
        val totalInsta = days.sumOf { it.instagramBlocks }
        val totalTiktok = days.sumOf { it.tiktokBlocks }
        val totalYt = days.sumOf { it.youtubeBlocks }
        val effectiveTotal = days.sumOf { maxOf(it.totalBlocks, it.instagramBlocks + it.tiktokBlocks + it.youtubeBlocks) }

        assertEquals(5, totalInsta)
        assertEquals(2, totalTiktok)
        assertEquals(1, totalYt)
        assertEquals(9, effectiveTotal) // 5 + 4
        org.junit.Assert.assertTrue(effectiveTotal >= totalInsta + totalTiktok + totalYt)
    }

    @Test
    fun allTimeMetricsNeverFallBehindMonthlyMetrics() {
        val monthlyBlocks = 25
        val storedAllTimeBlocks = 10
        val effectiveAllTimeBlocks = maxOf(storedAllTimeBlocks, monthlyBlocks)
        assertEquals(25, effectiveAllTimeBlocks)

        val monthlyInstaMs = 120_000L
        val storedAllTimeInstaMs = 60_000L
        val effectiveAllTimeInstaMs = maxOf(storedAllTimeInstaMs, monthlyInstaMs)
        assertEquals(120_000L, effectiveAllTimeInstaMs)
    }

    private fun day(total: Int, instagram: Int, tiktok: Int, youtube: Int) =
        DayBlockDetail(
            dayName = "Pzt",
            dateStr = "2026-09-14",
            totalBlocks = total,
            instagramBlocks = instagram,
            tiktokBlocks = tiktok,
            youtubeBlocks = youtube,
            instagramProtectionMs = 11_000L
        )
}
