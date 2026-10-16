package com.resolvecommunity.awaydoomscrollin

/** A newly mounted Story tree can precede Instagram's navigation transition. */
internal class InstagramStoryExitReadiness {
    private var windowId = -1
    private var firstSeenAt = 0L

    // This is a small transition grace, not permission to send Back on a timer.
    // The caller must still verify the current foreground Story on every scan.
    fun delayBeforeBack(currentWindowId: Int, now: Long): Long {
        if (currentWindowId < 0) return 200L
        if (windowId != currentWindowId || now < firstSeenAt) {
            windowId = currentWindowId
            firstSeenAt = now
        }
        return (200L - (now - firstSeenAt)).coerceAtLeast(0L)
    }

    fun reset() { windowId = -1; firstSeenAt = 0L }
}
