package com.resolvecommunity.awaydoomscrollin

/** The visible page, not merely the selected tab behind a secondary page. */
internal enum class YouTubeCurtainScreen {
    HOME, SUBSCRIPTIONS, YOU, SEARCH, NOTIFICATIONS, OTHER_ALLOWED, SHORTS, UNKNOWN
}

/**
 * One authority for the feed curtain across non-monotonic accessibility snapshots.
 *
 * A navigation request covers protected destinations immediately and retains existing coverage
 * when entering allowed destinations. The first verified target frame completes the transition;
 * there is no mandatory hold after that frame. Once completed, verified observations classify
 * normally, including system Back without a click event. Call [clear] when leaving YouTube.
 */
internal class YouTubeCurtainState(private val requestTimeoutMs: Long = 2_500L) {
    init {
        require(requestTimeoutMs > 0L)
    }

    var generation: Long = 0L
        private set

    var currentSurface: YouTubeProtectedSurface? = null
        private set

    var pendingDestination: YouTubeCurtainScreen? = null
        private set

    val isPending: Boolean
        get() = pendingDestination != null

    private var expiresAtMs = 0L

    // A settled allowed page completes a pending request only after it is
    // confirmed on consecutive observations. YouTube's tab restore can show a
    // single search frame, flash the feed behind it and return to the search;
    // releasing on the first frame made the curtain blink down-up-down. The
    // allowed page visible at request time is the outgoing page of the old tab
    // and must never complete the request (search -> Subscriptions switch).
    private var restoredAllowedStreakScreen: YouTubeCurtainScreen? = null
    private var restoredAllowedStreakCount = 0
    private var restoredAllowedFirstSeenMs = 0L
    private var lastObservedScreen: YouTubeCurtainScreen? = null
    private var outgoingScreenAtRequest: YouTubeCurtainScreen? = null

    // Duplicate tab-click echoes: YouTube can deliver a second click event for
    // the same tab right after a request completed. Re-arming while an allowed
    // page is visible would blink the curtain over that page.
    private var lastCompletedDestination: YouTubeCurtainScreen? = null
    private var lastCompletedAtMs = 0L

    /** The screen visible when the last request was armed (diagnostics). */
    val lastRequestOutgoingScreen: YouTubeCurtainScreen?
        get() = outgoingScreenAtRequest

    private fun resetRestoredAllowedStreak() {
        restoredAllowedStreakScreen = null
        restoredAllowedStreakCount = 0
        restoredAllowedFirstSeenMs = 0L
    }

    private companion object {
        const val RESTORED_ALLOWED_DEADLINE_MS = 900L
        const val DUPLICATE_RESTORED_REQUEST_WINDOW_MS = 800L
    }

    /** Returns a token that every asynchronous action must check before dispatching. */
    fun request(destination: YouTubeCurtainScreen, nowMs: Long): Long {
        require(destination != YouTubeCurtainScreen.UNKNOWN)
        // A duplicate click echo for a tab whose request just completed must
        // not re-arm and blink the curtain over the visible allowed page.
        if (destination == lastCompletedDestination &&
            nowMs - lastCompletedAtMs < DUPLICATE_RESTORED_REQUEST_WINDOW_MS &&
            lastObservedScreen in setOf(
                YouTubeCurtainScreen.SEARCH, YouTubeCurtainScreen.NOTIFICATIONS,
                YouTubeCurtainScreen.OTHER_ALLOWED, YouTubeCurtainScreen.YOU
            )
        ) {
            return generation
        }
        // A request that replaces a still-pending one is a rapid tab bounce.
        // The last verified screen can be the very page the destination tab
        // restores (search -> Subscriptions -> Home), so the outgoing-screen
        // suppression must not block the restored page from completing it.
        val replacedPending = pendingDestination != null
        generation++
        pendingDestination = destination
        expiresAtMs = nowMs + requestTimeoutMs
        outgoingScreenAtRequest = if (replacedPending) null else lastObservedScreen
        resetRestoredAllowedStreak()
        protectedSurface(destination)?.let { currentSurface = it }
        return generation
    }

    fun observe(screen: YouTubeCurtainScreen, nowMs: Long): YouTubeProtectedSurface? =
        observe(screen, nowMs, YouTubeNavigationDestination.UNKNOWN)

    fun observe(
        screen: YouTubeCurtainScreen,
        nowMs: Long,
        selectedDestination: YouTubeNavigationDestination = YouTubeNavigationDestination.UNKNOWN
    ): YouTubeProtectedSurface? {
        if (screen != YouTubeCurtainScreen.UNKNOWN) {
            // Track the last VERIFIED screen: a transitional UNKNOWN frame
            // arriving together with the tab click must not erase the identity
            // of the outgoing page (search -> Subscriptions cover retention).
            lastObservedScreen = screen
        }
        val target = pendingDestination
        if (target != null) {
            // YouTube restores the destination tab's previous page: the Home
            // tab can return to its settled search results, the You tab to a
            // secondary page. When the selected tab matches the pending
            // request and a settled allowed page is visible, the navigation
            // has resolved. The page must hold for two consecutive
            // observations (or the bounded deadline) so a single transitional
            // frame cannot release the curtain and re-cover on a feed flash.
            // Outgoing allowed frames (selected tab still elsewhere) keep the
            // coverage.
            val selectedMatches = selectedDestination != YouTubeNavigationDestination.UNKNOWN &&
                selectedDestination.curtainScreen() == target
            val settledAllowedPage = screen == YouTubeCurtainScreen.SEARCH ||
                screen == YouTubeCurtainScreen.NOTIFICATIONS ||
                screen == YouTubeCurtainScreen.OTHER_ALLOWED ||
                screen == YouTubeCurtainScreen.YOU
            val sameAsOutgoing = outgoingScreenAtRequest == screen
            var restoredAllowedPage = false
            if (selectedMatches && settledAllowedPage && !sameAsOutgoing) {
                if (restoredAllowedStreakScreen == screen) {
                    restoredAllowedStreakCount++
                } else {
                    restoredAllowedStreakScreen = screen
                    restoredAllowedStreakCount = 1
                }
                if (restoredAllowedFirstSeenMs == 0L) restoredAllowedFirstSeenMs = nowMs
                restoredAllowedPage = restoredAllowedStreakCount >= 2 ||
                    nowMs - restoredAllowedFirstSeenMs >= RESTORED_ALLOWED_DEADLINE_MS
            } else {
                restoredAllowedStreakScreen = null
                restoredAllowedStreakCount = 0
            }
            if (screen == target || restoredAllowedPage) {
                currentSurface = protectedSurface(screen)
                pendingDestination = null
                expiresAtMs = 0L
                lastCompletedDestination = target
                lastCompletedAtMs = nowMs
                resetRestoredAllowedStreak()
                return currentSurface
            }
            if (nowMs < expiresAtMs) return currentSurface
            // Failed navigation no longer pins the previous target forever. Retain coverage
            // for UNKNOWN and otherwise recover using the current verified visible screen.
            pendingDestination = null
            expiresAtMs = 0L
            resetRestoredAllowedStreak()
            generation++
        }
        if (screen != YouTubeCurtainScreen.UNKNOWN) {
            currentSurface = protectedSurface(screen)
        }
        return currentSurface
    }

    /** Invalidates queued work and any screen identity from the previous foreground session. */
    fun clear() {
        generation++
        currentSurface = null
        pendingDestination = null
        expiresAtMs = 0L
        lastObservedScreen = null
        outgoingScreenAtRequest = null
        lastCompletedDestination = null
        lastCompletedAtMs = 0L
        resetRestoredAllowedStreak()
    }

    fun isCurrentGeneration(token: Long): Boolean = token == generation

    private fun protectedSurface(screen: YouTubeCurtainScreen): YouTubeProtectedSurface? =
        when (screen) {
            YouTubeCurtainScreen.HOME -> YouTubeProtectedSurface.HOME
            YouTubeCurtainScreen.SUBSCRIPTIONS -> YouTubeProtectedSurface.SUBSCRIPTIONS
            else -> null
        }
}
