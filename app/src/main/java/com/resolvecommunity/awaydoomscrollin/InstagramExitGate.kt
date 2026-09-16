package com.resolvecommunity.awaydoomscrollin

/** One navigation per viewer encounter, including stale/oscillating tree reports. */
internal class InstagramExitGate {
    companion object {
        // A missed safe-screen sample must never leave a Reel usable forever.
        // The service asks again only while a blocked viewer is still detected.
        const val VIEWER_FAILSAFE_REARM_MS = 800L
    }

    private var issued = false
    private var issuedAt: Long? = null
    private var safeScreen: InstagramScreen? = null
    private var safeSince = 0L
    private var returnedAt: Long? = null
    private var lastClickTime = Long.MIN_VALUE

    /** Fresh input after a verified return starts a new encounter, without waiting
     * for the safe-screen dwell. Old/synthetic Back events must not reach here. */
    fun userClick(eventTime: Long) {
        if (eventTime <= lastClickTime) return
        lastClickTime = eventTime
        val returned = returnedAt ?: return
        if (issued && eventTime > returned) {
            issued = false
            issuedAt = null
            safeScreen = null
            returnedAt = null
        }
    }

    fun requestBack(now: Long = 0L): Boolean {
        val previousIssue = issuedAt
        if (issued && (previousIssue == null || now - previousIssue < VIEWER_FAILSAFE_REARM_MS)) {
            return false
        }
        issued = true
        issuedAt = now
        safeScreen = null
        returnedAt = null
        return true
    }

    /** Returns true when another safe-screen verification is needed. */
    fun observe(screen: InstagramScreen, now: Long, verifiedProfileReturn: Boolean = false,
                verifiedConversationReturn: Boolean = false): Boolean {
        // A real profile shell (not just its selected bottom tab) proves the
        // viewer has closed. No click event or 400 ms dwell is needed to rearm.
        if ((screen == InstagramScreen.PROFILE && verifiedProfileReturn) ||
            (screen == InstagramScreen.DIRECT_MESSAGES && verifiedConversationReturn)) {
            reset()
            return false
        }
        if (InstagramSurfacePolicy.needsExit(screen) ||
            screen == InstagramScreen.UNKNOWN) {
            safeScreen = null
            return false
        }
        if (!issued) return false
        if (returnedAt == null) returnedAt = now
        if (safeScreen != screen) {
            safeScreen = screen
            safeSince = now
        } else if (now - safeSince >= 400L) {
            reset()
        }
        return issued
    }

    fun reset() {
        issued = false
        issuedAt = null
        safeScreen = null
        safeSince = 0L
        returnedAt = null
    }
}
