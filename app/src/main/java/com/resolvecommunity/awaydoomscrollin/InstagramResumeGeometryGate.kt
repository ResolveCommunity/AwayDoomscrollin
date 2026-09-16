package com.resolvecommunity.awaydoomscrollin

/**
 * Rejects transient geometry emitted while Instagram is resuming.
 *
 * Instagram replaces its accessibility window several times while the Recents
 * animation is still running. A new window id is therefore not proof that its
 * first (often translated/scaled) rectangle is ready to render.
 */
internal class InstagramResumeGeometryGate(
    private val minimumSettleMs: Long = 600L,
    private val requiredStableSamples: Int = 3,
    private val maximumHoldMs: Long = 2_500L,
    private val tolerancePx: Int = 2
) {
    private var screen: InstagramScreen? = null
    private var windowId = -1
    private var startedAt = 0L
    private var candidate: InstagramGeometrySample? = null
    private var stableSamples = 0

    val isActive: Boolean
        get() = screen != null

    fun begin(screen: InstagramScreen, windowId: Int, now: Long) {
        this.screen = screen
        this.windowId = windowId
        startedAt = now
        candidate = null
        stableSamples = 0
    }

    /** True means this candidate is safe to render. */
    fun accept(
        screen: InstagramScreen,
        windowId: Int,
        bounds: InstagramGeometrySample,
        now: Long
    ): Boolean {
        val expectedScreen = this.screen ?: return true
        if (screen != expectedScreen) {
            clear()
            return true
        }

        if (windowId != this.windowId) {
            // Keep the retained, application-space curtain on screen and make
            // the replacement window earn the same settling period. Accepting
            // this first sample caused the curtain to start narrow/on the right
            // after returning from Recents.
            this.windowId = windowId
            startedAt = now
            candidate = bounds
            stableSamples = 1
            return false
        }

        stableSamples = if (candidate?.approximatelyEquals(bounds, tolerancePx) == true) {
            stableSamples + 1
        } else {
            candidate = bounds
            1
        }

        val elapsed = now - startedAt
        val settled = elapsed >= minimumSettleMs && stableSamples >= requiredStableSamples
        if (settled || elapsed >= maximumHoldMs) {
            clear()
            return true
        }
        return false
    }

    fun clear() {
        screen = null
        windowId = -1
        startedAt = 0L
        candidate = null
        stableSamples = 0
    }
}
