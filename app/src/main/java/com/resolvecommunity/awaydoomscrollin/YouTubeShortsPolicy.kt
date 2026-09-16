package com.resolvecommunity.awaydoomscrollin

/**
 * Pure policy for YouTube Shorts' narrow contract: entering a Short is always allowed and it
 * can be watched to the end; the first vertical swipe toward the next video requests a
 * deterministic Back action that closes the viewer on its source surface. Comment and
 * bottom-sheet scrolling stay usable. There is no time limit, quota or pattern rule - the
 * chain is what gets interrupted, never a single video.
 */
internal object YouTubeShortsPolicy {
    const val EXIT_COOLDOWN_MS = 600L
    const val EXIT_RETRY_DELAY_MS = 250L

    fun shouldExit(
        protectionEnabled: Boolean,
        isScrollEvent: Boolean,
        packageInForeground: Boolean,
        shortsVisible: Boolean,
        isVerticalScroll: Boolean,
        isSafeSourcePath: Boolean
    ): Boolean = protectionEnabled &&
        isScrollEvent &&
        packageInForeground &&
        shortsVisible &&
        isVerticalScroll &&
        !isSafeSourcePath

    fun cooldownElapsed(nowMs: Long, lastAcceptedExitMs: Long): Boolean =
        lastAcceptedExitMs <= 0L || nowMs - lastAcceptedExitMs >= EXIT_COOLDOWN_MS
}
