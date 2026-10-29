package com.resolvecommunity.awaydoomscrollin

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class RecentBlockTimeTest {
    private val zone = ZoneId.of("Europe/Istanbul")
    // Wednesday, 28 October 2026, 15:40
    private val now = ms(2026, 10, 28, 15, 40)

    private fun ms(y: Int, mo: Int, d: Int, h: Int, mi: Int) =
        LocalDateTime.of(y, mo, d, h, mi).atZone(zone).toInstant().toEpochMilli()

    private fun en(blockMs: Long) = RecentBlockTime.label(blockMs, now, isEn = true, zone = zone)
    private fun tr(blockMs: Long) = RecentBlockTime.label(blockMs, now, isEn = false, zone = zone)

    @Test
    fun `today and yesterday are named`() {
        assertEquals("Today at 14:09", en(ms(2026, 10, 28, 14, 9)))
        assertEquals("Bugün 14:09", tr(ms(2026, 10, 28, 14, 9)))
        assertEquals("Yesterday at 22:30", en(ms(2026, 10, 27, 22, 30)))
        assertEquals("Dün 22:30", tr(ms(2026, 10, 27, 22, 30)))
    }

    @Test
    fun `just after midnight is yesterday, not today`() {
        val now = ms(2026, 10, 29, 0, 5)
        assertEquals("Yesterday at 23:58",
            RecentBlockTime.label(ms(2026, 10, 28, 23, 58), now, true, zone))
    }

    @Test
    fun `the past week shows the weekday`() {
        assertEquals("Monday at 18:05", en(ms(2026, 10, 26, 18, 5)))
        assertEquals("Pazartesi 18:05", tr(ms(2026, 10, 26, 18, 5)))
        assertEquals("Thursday at 09:00", en(ms(2026, 10, 22, 9, 0)))
    }

    @Test
    fun `older blocks show a short date`() {
        assertEquals("Oct 21 at 14:09", en(ms(2026, 10, 21, 14, 9)))
        assertEquals("21 Eki 14:09", tr(ms(2026, 10, 21, 14, 9)))
    }

    @Test
    fun `a clock that moved backwards still reads as today`() {
        assertEquals("Today at 16:00", en(ms(2026, 10, 28, 16, 0)))
    }
}
