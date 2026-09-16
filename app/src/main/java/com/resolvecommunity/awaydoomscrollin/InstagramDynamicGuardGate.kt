package com.resolvecommunity.awaydoomscrollin

/**
 * Keeps a broader input shield while Instagram is animating a protected card.
 * Accessibility bounds can trail the rendered card by several frames.
 */
internal class InstagramDynamicGuardGate(
    private val quietPeriodMs: Long = 320L,
    private val requiredStableSamples: Int = 3,
    private val maximumHoldMs: Long = 2_500L,
    private val tolerancePx: Int = 2
) {
    private var windowId = -1
    private var lastSample: InstagramGeometrySample? = null
    private var stableSamples = 0
    private var holdStartedAt = 0L
    private var motionUntil = 0L

    var isActive: Boolean = false
        private set

    fun observe(windowId: Int, sample: InstagramGeometrySample?, now: Long): Boolean {
        if (sample == null) {
            if (isActive && now - holdStartedAt >= maximumHoldMs) isActive = false
            return isActive
        }

        val replacementWindow = this.windowId != windowId
        val moved = lastSample?.approximatelyEquals(sample, tolerancePx) != true
        if (replacementWindow || moved) {
            if (!isActive || replacementWindow) holdStartedAt = now
            this.windowId = windowId
            lastSample = sample
            stableSamples = 1
            motionUntil = now + quietPeriodMs
            isActive = true
        } else if (isActive) {
            stableSamples++
        }

        if (isActive && (now - holdStartedAt >= maximumHoldMs ||
                (now >= motionUntil && stableSamples >= requiredStableSamples))) {
            isActive = false
        }
        return isActive
    }

    fun reset() {
        windowId = -1
        lastSample = null
        stableSamples = 0
        holdStartedAt = 0L
        motionUntil = 0L
        isActive = false
    }
}
