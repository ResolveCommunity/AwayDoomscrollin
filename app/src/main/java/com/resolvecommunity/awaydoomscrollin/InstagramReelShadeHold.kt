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

    /** Returns the original ingress screen when the same viewer came back in time. */
    fun take(viewerWindowId: Int, nowMs: Long): InstagramScreen? {
        val matched = windowId >= 0 && viewerWindowId == windowId && nowMs < until
        val result = origin.takeIf { matched }
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
    }
}
