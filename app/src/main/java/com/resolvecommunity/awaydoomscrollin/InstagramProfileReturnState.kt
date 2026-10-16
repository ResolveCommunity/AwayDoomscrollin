package com.resolvecommunity.awaydoomscrollin

/**
 * Owns the short transition from an explicitly allowed Strict-profile layer
 * (for example About this account or Instagram's native profile menu) back to
 * the protected profile. It must never arm for an ordinary profile departure.
 */
internal class InstagramProfileReturnState(
    private val lifetimeMs: Long = 10_000L
) {
    private var armedUntil = 0L
    private var revision = 0L
    private var pending = false
    private var sawAllowedLayer = false

    fun arm(now: Long) {
        armedUntil = now + lifetimeMs
        pending = false
        sawAllowedLayer = false
        revision++
    }

    fun observeAllowedLayer(now: Long) {
        if (!isArmed(now)) arm(now)
        sawAllowedLayer = true
    }

    fun isArmed(now: Long): Boolean {
        if (armedUntil == 0L || now >= armedUntil) {
            clear()
            return false
        }
        return true
    }

    fun begin(now: Long): Long? {
        if (!canPrecoverReturn(now) || pending) return null
        pending = true
        return ++revision
    }

    fun canPrecoverReturn(now: Long): Boolean = isArmed(now) && sawAllowedLayer

    fun accepts(token: Long, now: Long): Boolean =
        token == revision && pending && isArmed(now)

    fun clear() {
        armedUntil = 0L
        pending = false
        sawAllowedLayer = false
        revision++
    }
}
