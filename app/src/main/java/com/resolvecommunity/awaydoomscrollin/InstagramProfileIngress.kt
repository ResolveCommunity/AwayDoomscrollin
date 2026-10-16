package com.resolvecommunity.awaydoomscrollin

/** A draw callback from an earlier navigation may never release a newer one. */
internal class InstagramProfileIngress {
    var generation = 0L
        private set
    var pending = false
        private set
    var sawProfile = false
        private set
    private var sample: String? = null
    private var stableSamples = 0
    private var curtainRequested = false
    private var startedAt = 0L
    var awaitingCurtainDraw = false
        private set

    fun requestCurtainDraw(): Boolean {
        if (!pending || curtainRequested) return false
        curtainRequested = true
        awaitingCurtainDraw = true
        return true
    }

    fun confirmCurtainDraw(token: Long) {
        if (isCurrent(token)) awaitingCurtainDraw = false
    }

    /** Structural bursts may request a scan, not repeat foreground IPC before each draw. */
    fun coalesceStructuralEvent(instagramEvent: Boolean, sameTrackedApp: Boolean,
        structuralEvent: Boolean): Boolean =
        pending && instagramEvent && sameTrackedApp && structuralEvent

    /** Only defer expensive classification briefly; never release protection on a timer. */
    fun waitForInitialGrid(now: Long): Boolean =
        pending && !curtainRequested && now - startedAt in 0L until 500L

    fun begin(now: Long = 0L): Long {
        reset()
        startedAt = now
        pending = true
        return generation
    }

    fun ready(fingerprint: String?): Boolean {
        if (!pending || fingerprint == null) {
            sample = null
            stableSamples = 0
            return false
        }
        sawProfile = true
        stableSamples = if (sample == fingerprint) stableSamples + 1 else 1
        sample = fingerprint
        return stableSamples >= 3
    }

    fun isCurrent(token: Long) = pending && generation == token

    fun reset() {
        generation++
        pending = false
        sawProfile = false
        sample = null
        stableSamples = 0
        curtainRequested = false
        awaitingCurtainDraw = false
    }
}
