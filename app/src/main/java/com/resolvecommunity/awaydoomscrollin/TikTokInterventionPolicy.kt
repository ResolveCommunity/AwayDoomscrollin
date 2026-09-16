package com.resolvecommunity.awaydoomscrollin

/**
 * Pure policy for TikTok's intentionally narrow contract: opening TikTok is allowed, while
 * the first vertical scroll on a classified feed surface requests a deterministic Back
 * action. Surfaces classified safe or unknown by [TikTokSurfacePolicy] never trigger it.
 */
internal object TikTokInterventionPolicy {
    const val EXIT_COOLDOWN_MS = 600L
    const val EXIT_RETRY_DELAY_MS = 250L
    const val TOP_TAB_CHECK_MIN_INTERVAL_MS = 400L

    fun shouldExit(
        protectionEnabled: Boolean,
        isScrollEvent: Boolean,
        packageInForeground: Boolean,
        isVerticalScroll: Boolean,
        isSafeSurface: Boolean
    ): Boolean = protectionEnabled &&
        isScrollEvent &&
        packageInForeground &&
        isVerticalScroll &&
        !isSafeSurface

    fun cooldownElapsed(nowMs: Long, lastAcceptedExitMs: Long): Boolean =
        lastAcceptedExitMs <= 0L || nowMs - lastAcceptedExitMs >= EXIT_COOLDOWN_MS

    /** Throttles the top-tab safety-net scan against TikTok's noisy content events. */
    fun topTabCheckElapsed(nowMs: Long, lastCheckMs: Long): Boolean =
        lastCheckMs <= 0L || nowMs - lastCheckMs >= TOP_TAB_CHECK_MIN_INTERVAL_MS
}
