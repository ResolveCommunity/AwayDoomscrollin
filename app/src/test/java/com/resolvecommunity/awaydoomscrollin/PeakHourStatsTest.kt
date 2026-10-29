package com.resolvecommunity.awaydoomscrollin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PeakHourStatsTest {
    private val stored = mapOf(
        "blocks_2026-10-21_hour_22" to 26,
        "blocks_2026-10-21_hour_23" to 30,
        "blocks_2026-10-22_hour_00" to 25,
        "blocks_2026-10-22_hour_23" to 4,
        "blocks_2026-10-28_hour_13" to 9,
        "blocks_2026-10-28_instagram" to 99,
        "blocks_total" to 500,
        "streak_days" to 7
    )

    @Test
    fun `all time sums each hour across days`() {
        assertEquals(PeakHourStats.PeakHour(23, 34), PeakHourStats.busiestHour(stored, null))
    }

    @Test
    fun `a period only counts its own days`() {
        assertEquals(PeakHourStats.PeakHour(13, 9),
            PeakHourStats.busiestHour(stored, setOf("2026-10-28")))
        assertEquals(PeakHourStats.PeakHour(0, 25),
            PeakHourStats.busiestHour(stored, setOf("2026-10-22")))
    }

    @Test
    fun `a tie keeps the earliest hour`() {
        val tie = mapOf("blocks_2026-10-28_hour_20" to 3, "blocks_2026-10-28_hour_09" to 3)
        assertEquals(9, PeakHourStats.busiestHour(tie, null)?.hour)
    }

    @Test
    fun `a period without blocks has no busiest hour`() {
        assertNull(PeakHourStats.busiestHour(stored, setOf("2026-10-27")))
        assertNull(PeakHourStats.busiestHour(emptyMap<String, Any>(), null))
    }

    @Test
    fun `the range wraps at midnight`() {
        assertEquals("23:00–00:00", PeakHourStats.formatRange(23))
        assertEquals("09:00–10:00", PeakHourStats.formatRange(9))
    }
}
