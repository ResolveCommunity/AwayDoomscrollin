package com.resolvecommunity.awaydoomscrollin

/** Keep the earliest pending scan: event bursts cannot postpone it indefinitely. */
internal class InstagramScanSchedule {
    private var dueAt = Long.MAX_VALUE
    fun request(now: Long, delay: Long): Boolean {
        val proposed = now + delay
        if (proposed >= dueAt) return false
        dueAt = proposed
        return true
    }
    fun clear() { dueAt = Long.MAX_VALUE }
}
