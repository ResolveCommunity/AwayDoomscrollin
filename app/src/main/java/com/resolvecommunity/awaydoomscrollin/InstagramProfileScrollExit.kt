package com.resolvecommunity.awaydoomscrollin

/** At most one navigation per continuous upward-scroll burst. */
internal class InstagramProfileScrollExit {
    companion object {
        fun verticalDelta(dx: Int, dy: Int, previousTop: Int, measuredTop: Int?): Int {
            if (dx != 0 || dy != 0 || measuredTop == null) return dy
            return (previousTop.toLong() - measuredTop.toLong())
                .coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
        }
    }
    private var lastUpwardEvent: Long? = null

    fun request(dx: Int, dy: Int, eventTime: Long): Boolean {
        if (dy <= 0 || dy.toLong() <= kotlin.math.abs(dx.toLong())) return false
        val previous = lastUpwardEvent
        if (previous != null && eventTime <= previous) return false
        lastUpwardEvent = eventTime
        return previous == null || eventTime - previous >= 800L
    }

    fun reset() { lastUpwardEvent = null }
}
