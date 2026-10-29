package com.resolvecommunity.awaydoomscrollin

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * When a recent block happened, relative to now: "Today at 14:09",
 * "Yesterday at 22:30", "Monday at 18:05" within the last week, and a short
 * date after that. A bare time read as if it were today's.
 */
internal object RecentBlockTime {
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

    fun label(blockMs: Long, nowMs: Long, isEn: Boolean, zone: ZoneId = ZoneId.systemDefault()): String {
        val block = Instant.ofEpochMilli(blockMs).atZone(zone)
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val time = block.format(timeFormat)
        val day = block.toLocalDate()
        val daysAgo = ChronoUnit.DAYS.between(day, today)
        val locale = if (isEn) Locale.ENGLISH else Locale.forLanguageTag("tr")
        return when {
            daysAgo <= 0L -> if (isEn) "Today at $time" else "Bugün $time"
            daysAgo == 1L -> if (isEn) "Yesterday at $time" else "Dün $time"
            daysAgo < 7L -> {
                val weekday = day.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
                if (isEn) "$weekday at $time" else "$weekday $time"
            }
            else -> {
                val date = shortDate(day, locale, isEn)
                if (isEn) "$date at $time" else "$date $time"
            }
        }
    }

    private fun shortDate(day: LocalDate, locale: Locale, isEn: Boolean): String {
        val month = day.month.getDisplayName(TextStyle.SHORT, locale)
        return if (isEn) "$month ${day.dayOfMonth}" else "${day.dayOfMonth} $month"
    }
}
