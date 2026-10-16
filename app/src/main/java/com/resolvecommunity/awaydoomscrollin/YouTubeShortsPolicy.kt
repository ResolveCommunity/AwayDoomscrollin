package com.resolvecommunity.awaydoomscrollin

/**
 * Balanced-mode single-video policy: an intentionally opened Short is allowed and it
 * can be watched to the end; the first vertical swipe toward the next video requests a
 * deterministic Back action that closes the viewer on its source surface. Comment and
 * bottom-sheet scrolling stay usable. There is no time limit, quota or pattern rule - the
 * chain is what gets interrupted, never a single video.
 */
internal object YouTubeShortsPolicy {
    const val EXIT_RETRY_DELAY_MS = 250L

    /** The feed tab is never intentional single-video entry; strict also rejects links/results. */
    fun shouldBlockEntry(mode: ProtectionMode, fromShortsTab: Boolean): Boolean =
        mode == ProtectionMode.STRICT || fromShortsTab

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
}
