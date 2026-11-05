package com.resolvecommunity.awaydoomscrollin

/**
 * Keeps a Balanced single-Reel session across a SystemUI visit (notification
 * shade, quick settings, volume panel).
 *
 * Leaving Instagram clears every protection session, so pulling the shade down
 * over an allowed Reel used to return to a viewer without ingress and exit it.
 * Only a return to the same viewer window restores the session; the pager gate
 * still exits on the next video. Any other first Reels evaluation consumes it.
 */
internal class InstagramReelShadeHold {
    private var windowId = -1
    private var origin = InstagramScreen.UNKNOWN
    private var until = 0L

    val isHeld: Boolean
        get() = windowId >= 0

    fun hold(viewerWindowId: Int, origin: InstagramScreen, nowMs: Long) {
        if (viewerWindowId < 0) return
        windowId = viewerWindowId
        this.origin = origin
        until = nowMs + TTL_MS
    }

    /**
     * Returns the original ingress screen when the same viewer came back in
     * time. Another viewer seen in between (a blocked Reel on the way back)
     * does not consume the hold; only a match or expiry ends it.
     */
    fun take(viewerWindowId: Int, nowMs: Long): InstagramScreen? {
        if (windowId < 0) return null
        if (nowMs >= until) {
            clear()
            return null
        }
        if (viewerWindowId != windowId) return null
        val result = origin
        clear()
        return result
    }

    fun clear() {
        windowId = -1
        origin = InstagramScreen.UNKNOWN
        until = 0L
    }

    companion object {
        const val TTL_MS = 120_000L

        /**
         * Every departure counts, not only SystemUI: Samsung's Recents belongs
         * to the launcher, and returning from it closed an allowed Reel.
         */
        fun holdsOnDeparture(
            screen: InstagramScreen,
            watching: Boolean,
            exiting: Boolean,
            mode: ProtectionMode
        ): Boolean = screen == InstagramScreen.REELS && watching && !exiting &&
            mode == ProtectionMode.BALANCED

        /**
         * Leaving an allowed Reel for another Instagram window (the viewer's
         * "+" camera) and pressing Back returns to the same viewer window.
         * A destination in the viewer's own window is not a side trip.
         */
        fun holdsOnInAppSideTrip(
            previous: InstagramScreen,
            viewerWindowId: Int,
            destinationWindowId: Int,
            watching: Boolean,
            exiting: Boolean,
            mode: ProtectionMode
        ): Boolean = holdsOnDeparture(previous, watching, exiting, mode) &&
            viewerWindowId >= 0 && destinationWindowId >= 0 && destinationWindowId != viewerWindowId
    }
}
