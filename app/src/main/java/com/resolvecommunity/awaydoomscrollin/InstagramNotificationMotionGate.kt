package com.resolvecommunity.awaydoomscrollin

/** Holds taps during native list animation, not a visible feed curtain. */
internal class InstagramNotificationMotionGate {
    val isHolding: Boolean get() = startedAt != null
    private var startedAt: Long? = null
    private var quietUntil = 0L
    private var signature: String? = null
    private var stableSamples = 0

    fun arm(now: Long) {
        if (startedAt == null) startedAt = now
        quietUntil = now + 180L
        stableSamples = 0
    }

    fun observe(sample: String, now: Long): Boolean {
        val started = startedAt ?: return false
        if (signature != sample) {
            signature = sample
            quietUntil = now + 180L
            stableSamples = 1
        } else stableSamples++
        if (now - started >= 2_000L || (now >= quietUntil && stableSamples >= 2)) {
            reset()
            return false
        }
        return true
    }

    fun reset() {
        startedAt = null
        signature = null
        stableSamples = 0
        quietUntil = 0L
    }
}
