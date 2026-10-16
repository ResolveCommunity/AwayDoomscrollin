package com.resolvecommunity.awaydoomscrollin

internal enum class InstagramReelsDecision { ALLOW, EXIT }

/**
 * Feed entry and viewing one chosen Reel are different operations. Missing
 * trees never end a session; only a verified safe destination does. An exit
 * remains latched until that destination so rapid swipes cannot reopen it.
 */
internal class InstagramReelsSession {
    var exiting = false
        private set
    var watching = false
        private set
    var generation = 0L
        private set

    fun observe(mode: ProtectionMode, viewerVisible: Boolean,
        feedEntry: Boolean = false, verifiedOutside: Boolean = false,
        safeSheetOpen: Boolean = false, pagerScroll: Boolean = false,
        verticalMovement: Boolean = false): InstagramReelsDecision {
        if (verifiedOutside && !viewerVisible && !feedEntry) clear()
        if (feedEntry || (mode == ProtectionMode.STRICT && viewerVisible)) {
            return exit()
        }
        if (exiting) return InstagramReelsDecision.EXIT
        if (viewerVisible && !watching) {
            generation++
            watching = true
        }
        if (watching && pagerScroll && verticalMovement && !safeSheetOpen) return exit()
        return InstagramReelsDecision.ALLOW
    }

    private fun exit(): InstagramReelsDecision {
        if (!exiting) generation++
        exiting = true
        return InstagramReelsDecision.EXIT
    }

    fun clear() {
        if (watching || exiting) generation++
        watching = false
        exiting = false
    }
}
