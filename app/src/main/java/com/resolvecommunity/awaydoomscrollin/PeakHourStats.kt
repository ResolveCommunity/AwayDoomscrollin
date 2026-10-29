package com.resolvecommunity.awaydoomscrollin

/**
 * The hour of day with the most blocks in a period, from the per-hour
 * counters the service already keeps ("blocks_yyyy-MM-dd_hour_HH").
 * Nothing new is recorded; the counters never leave the device.
 */
internal object PeakHourStats {
    data class PeakHour(val hour: Int, val blocks: Int)

    private val hourKey = Regex("""blocks_(\d{4}-\d{2}-\d{2})_hour_(\d{2})""")

    /**
     * Sums every stored hour counter whose day is in [days] (all days when
     * null) and returns the busiest hour, the earliest one on a tie, or null
     * when the period has no block at all.
     */
    fun busiestHour(stored: Map<String, *>, days: Set<String>?): PeakHour? {
        val totals = IntArray(24)
        for ((key, value) in stored) {
            val match = hourKey.matchEntire(key) ?: continue
            if (days != null && match.groupValues[1] !in days) continue
            val hour = match.groupValues[2].toInt()
            if (hour !in 0..23) continue
            totals[hour] += (value as? Int)?.coerceAtLeast(0) ?: 0
        }
        val best = totals.indices.maxByOrNull { totals[it] * 24 - it } ?: return null
        return PeakHour(best, totals[best]).takeIf { it.blocks > 0 }
    }

    fun formatRange(hour: Int): String =
        "${hour.toString().padStart(2, '0')}:00–${((hour + 1) % 24).toString().padStart(2, '0')}:00"
}
