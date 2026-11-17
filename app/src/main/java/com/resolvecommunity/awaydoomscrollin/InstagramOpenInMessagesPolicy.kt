package com.resolvecommunity.awaydoomscrollin

/**
 * "Open in Messages": when the user turns it on, Instagram opened from
 * outside (launcher, recent apps, another app) goes straight to the Messages
 * tab instead of staying on the covered Home feed.
 *
 * It acts once per entry and only when the first screen Instagram shows is
 * Home. A notification, a shared post or a link opens its own screen first,
 * so it is left alone, and moving around inside Instagram never triggers it.
 */
internal object InstagramOpenInMessagesPolicy {
    /** Instagram's cold start can take a few seconds before Home is recognized. */
    const val ENTRY_WINDOW_MS = 6_000L

    /** How long, after our Messages tap, the inbox is watched for directly. */
    const val RELEASE_PROBE_MS = 700L

    /** The settled inbox must still be there this much later (about three frames). */
    const val RELEASE_CONFIRM_MS = 48L

    /** How long the quick Home check waits for Instagram to become the active root. */
    const val QUICK_HOME_CHECK_MS = 200L

    /**
     * Visible ids that mean the entry is not plainly on Home: a pushed
     * screen (Back), a viewer, Messages sliding in, Explore. Any of them
     * leaves the decision to the full scan.
     */
    private val NOT_PLAIN_HOME_IDS = listOf(
        "action_bar_button_back",
        "reel_viewer_root",
        "story_viewer_container",
        "direct_inbox_action_bar",
        "explore_action_bar"
    )

    /**
     * A few targeted lookups instead of the full tree walk (274 ms on a
     * Galaxy Note 10 Lite). True only for the plain Home feed, where the
     * full classification also answers Home; otherwise the scan decides.
     */
    fun plainHomeAtEntry(isVisible: (String) -> Boolean, feedTabSelected: () -> Boolean): Boolean =
        isVisible("main_feed_action_bar") && feedTabSelected() && NOT_PLAIN_HOME_IDS.none(isVisible)

    /**
     * Arms the redirect only when Instagram opens fresh: an event comes from an
     * Instagram window this service has not seen before (a cold start, or after
     * Instagram was closed from Recents or by Android). Coming back to a window
     * already seen is Instagram resuming where the user left it (Recents, the
     * notification shade and panels opened from it, another app and back), and
     * must not move the user. The service gets no launcher or SystemUI events,
     * so the window is the only reliable sign of a launch.
     *
     * The redirect stays bound to that new window (see [decide]): a dialog or
     * pop-up that opens over Home is a new window too, but Home is then
     * recognized in the main window, so nothing happens. Window size cannot be
     * used instead: during the opening animation the new window is scaled down.
     */
    fun armsOnEntry(settingOn: Boolean, protectionOn: Boolean, openedFresh: Boolean): Boolean =
        settingOn && protectionOn && openedFresh

    /**
     * The inbox list spans the window's width. While Home and Messages slide
     * past each other the list is offset, and the feed is still beside it.
     */
    fun inboxSettled(listLeft: Int, listRight: Int, windowLeft: Int, windowRight: Int, tolerance: Int): Boolean =
        listRight > listLeft &&
            kotlin.math.abs(listLeft - windowLeft) <= tolerance &&
            kotlin.math.abs(listRight - windowRight) <= tolerance

    /**
     * Instagram window ids already seen. Kept across service restarts (an app
     * update, or the phone restarting the service in the background): window
     * ids stay valid until the phone reboots, and a restarted service must not
     * mistake the Instagram window the user is in for a fresh launch.
     */
    class WindowMemory(private val max: Int = 16) {
        private val ids = LinkedHashSet<Int>()

        /** Saved as "bootCount|id,id,..."; another boot's ids mean nothing now. */
        fun restore(saved: String?, bootCount: Int) {
            ids.clear()
            if (bootCount < 0) return
            val parts = saved?.split('|') ?: return
            if (parts.size != 2 || parts[0].toIntOrNull() != bootCount) return
            parts[1].split(',').mapNotNull { it.toIntOrNull() }.forEach { add(it) }
        }

        /** True when [id] was not seen before (and is remembered from now on). */
        fun add(id: Int): Boolean {
            if (id < 0 || !ids.add(id)) return false
            while (ids.size > max) ids.remove(ids.first())
            return true
        }

        fun save(bootCount: Int): String = "$bootCount|" + ids.joinToString(",")
    }

    sealed class Decision {
        /** Still loading: keep waiting for the first recognized screen. */
        object Wait : Decision()

        /** Home was the first screen: open Messages. */
        object OpenMessages : Decision()

        /** Another first screen, or too late: this entry is done. */
        object Done : Decision()
    }

    /**
     * [inArmedWindow]: the screen was recognized in the window that armed the
     * redirect. Home recognized in any other window (the main window under a
     * dialog that opened over Home) ends this entry without moving the user.
     */
    fun decide(
        armedAtMs: Long,
        nowMs: Long,
        firstScreen: InstagramScreen,
        inArmedWindow: Boolean = true
    ): Decision = when {
        armedAtMs <= 0L || nowMs - armedAtMs > ENTRY_WINDOW_MS -> Decision.Done
        firstScreen == InstagramScreen.UNKNOWN -> Decision.Wait
        !inArmedWindow -> Decision.Done
        firstScreen == InstagramScreen.HOME_FEED -> Decision.OpenMessages
        else -> Decision.Done
    }
}
