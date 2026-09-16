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
