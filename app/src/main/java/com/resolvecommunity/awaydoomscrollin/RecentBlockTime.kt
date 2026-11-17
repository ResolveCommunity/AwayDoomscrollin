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

    // Wording in the string resources (time_*); weekday and month names come
    // from the platform in the app's language.
    fun label(
        blockMs: Long,
        nowMs: Long,
        language: AppLanguage = AppText.language(),
        zone: ZoneId = ZoneId.systemDefault()
    ): String {
        val block = Instant.ofEpochMilli(blockMs).atZone(zone)
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val time = block.format(timeFormat)
        val day = block.toLocalDate()
        val daysAgo = ChronoUnit.DAYS.between(day, today)
        val locale = AppText.locale(language)
        return when {
            daysAgo <= 0L -> AppText.getIn(language, R.string.time_today_at, time)
            daysAgo == 1L -> AppText.getIn(language, R.string.time_yesterday_at, time)
            daysAgo < 7L -> AppText.getIn(language, R.string.time_day_at,
                capitalized(day.dayOfWeek.getDisplayName(TextStyle.FULL, locale), locale), time)
            else -> AppText.getIn(language, R.string.time_day_at, shortDate(day, language, locale), time)
        }
    }

    private fun shortDate(day: LocalDate, language: AppLanguage, locale: Locale): String {
        // "Oct 21", "21 Eki", but "21 oct", "21 ott", "21 окт": French, Italian,
        // Spanish and Russian write month names in lower case.
        val raw = day.month.getDisplayName(TextStyle.SHORT, locale).removeSuffix(".")
        val month = if (language == AppLanguage.EN || language == AppLanguage.TR) capitalized(raw, locale) else raw
        return AppText.getIn(language, R.string.time_short_date, month, day.dayOfMonth.toString())
    }

    private fun capitalized(name: String, locale: Locale) = name.replaceFirstChar { it.titlecase(locale) }

    /**
     * The line under "Busiest hour" on Home and in Analysis. One wording in
     * both places, and the same word as the "14 blocks" headline.
     */
    fun peakHourBlocks(count: Int, language: AppLanguage = AppText.language()): String =
        AppText.pluralIn(language, R.plurals.peak_hour_blocks, count)
}
