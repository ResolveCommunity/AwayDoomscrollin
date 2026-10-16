package com.resolvecommunity.awaydoomscrollin

internal enum class YouTubeShortsSessionState {
    OUTSIDE,
    ENTERING,
    WATCHING,
    SAFE_SHEET,
    EXITING
}

internal enum class YouTubeShortsDecision {
    ALLOW,
    EXIT
}

internal data class YouTubeShortsObservation(
    val nowMs: Long,
    val shortsStructureVisible: Boolean,
    val launchRequested: Boolean,
    val definitelyOutsideShorts: Boolean,
    val safeBottomSheetOpen: Boolean,
    val isScrollEvent: Boolean,
    val hasVerticalMovement: Boolean,
    val sourceIsShortsPager: Boolean,
    val feedTabEntry: Boolean = false
)

/**
 * Explicit lifecycle for one intentional Shorts viewing session.
 *
 * YouTube emits zero-movement pager scroll events while opening a Short and while dismissing a
 * bottom sheet. Those events update context but never request an exit. Only a scroll carrying
 * real vertical movement whose source path is the Shorts pager can move a confirmed session to
 * EXITING. Duplicate events are suppressed only for a bounded exit attempt, not forever.
 * Each attempt has an identity so delayed work cannot act on a newly opened viewer.
 */
internal class YouTubeShortsSession {
    companion object {
        const val LAUNCH_PENDING_MS = 2_000L
        const val EXIT_PENDING_MS = 750L
    }

    var state: YouTubeShortsSessionState = YouTubeShortsSessionState.OUTSIDE
        private set

    private var launchPendingUntilMs = 0L
    private var exitPendingUntilMs = 0L
    var generation: Long = 0L
        private set

    fun isExitPending(token: Long, nowMs: Long): Boolean =
        token == generation && state == YouTubeShortsSessionState.EXITING &&
            nowMs < exitPendingUntilMs

    fun finishExit(token: Long) {
        if (token == generation && state == YouTubeShortsSessionState.EXITING) clear()
    }

    fun observe(observation: YouTubeShortsObservation): YouTubeShortsDecision {
        if (state == YouTubeShortsSessionState.EXITING &&
            observation.nowMs >= exitPendingUntilMs
        ) clear()
        if (observation.feedTabEntry) {
            if (state == YouTubeShortsSessionState.EXITING) return YouTubeShortsDecision.ALLOW
            return requestExit(observation.nowMs)
        }
        if (observation.launchRequested) {
            generation++
            exitPendingUntilMs = 0L
            state = YouTubeShortsSessionState.ENTERING
            launchPendingUntilMs = observation.nowMs + LAUNCH_PENDING_MS
        }

        val launchStillPending = state == YouTubeShortsSessionState.ENTERING &&
            observation.nowMs <= launchPendingUntilMs
        if ((state == YouTubeShortsSessionState.ENTERING &&
                !launchStillPending &&
                !observation.shortsStructureVisible) ||
            (observation.definitelyOutsideShorts &&
                !observation.shortsStructureVisible &&
                !observation.launchRequested &&
                !launchStillPending)
        ) {
            clear()
        }

        val knownShortsContext = observation.shortsStructureVisible ||
            observation.sourceIsShortsPager ||
            state == YouTubeShortsSessionState.WATCHING ||
            state == YouTubeShortsSessionState.SAFE_SHEET
        if (state != YouTubeShortsSessionState.EXITING) {
            if (observation.safeBottomSheetOpen && knownShortsContext) {
                state = YouTubeShortsSessionState.SAFE_SHEET
            } else if (observation.shortsStructureVisible) {
                state = YouTubeShortsSessionState.WATCHING
                launchPendingUntilMs = 0L
            }
        }

        if (state == YouTubeShortsSessionState.EXITING) {
            return YouTubeShortsDecision.ALLOW
        }

        val inShortsContext = knownShortsContext ||
            state == YouTubeShortsSessionState.WATCHING ||
            state == YouTubeShortsSessionState.SAFE_SHEET
        val shouldExit = YouTubeShortsPolicy.shouldExit(
            protectionEnabled = true,
            isScrollEvent = observation.isScrollEvent,
            packageInForeground = true,
            shortsVisible = inShortsContext && observation.sourceIsShortsPager,
            isVerticalScroll = observation.hasVerticalMovement,
            isSafeSourcePath = observation.safeBottomSheetOpen
        )
        if (!shouldExit) return YouTubeShortsDecision.ALLOW

        return requestExit(observation.nowMs)
    }

    private fun requestExit(nowMs: Long): YouTubeShortsDecision {
        generation++
        state = YouTubeShortsSessionState.EXITING
        exitPendingUntilMs = nowMs + EXIT_PENDING_MS
        return YouTubeShortsDecision.EXIT
    }

    fun clear() {
        generation++
        state = YouTubeShortsSessionState.OUTSIDE
        launchPendingUntilMs = 0L
        exitPendingUntilMs = 0L
    }
}
