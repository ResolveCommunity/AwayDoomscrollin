package com.resolvecommunity.awaydoomscrollin

/** Only a verified Home -> Notifications route may pre-cover a Back return. */
internal class InstagramNotificationReturnState {
    var returnsHome = false
        private set
    private var revision = 0L
    private var pendingUntil = 0L

    fun observe(screen: InstagramScreen, previous: InstagramScreen) {
        if (screen == InstagramScreen.UNKNOWN) return
        if (screen == InstagramScreen.NOTIFICATIONS) {
            if (previous != InstagramScreen.NOTIFICATIONS && previous != InstagramScreen.UNKNOWN) {
                returnsHome = previous == InstagramScreen.HOME_FEED
            }
        } else reset()
    }

    fun begin(now: Long): Long? {
        if (!returnsHome || isPending(now)) return null
        pendingUntil = now + 1_500L
        return ++revision
    }

    fun isPending(now: Long): Boolean = pendingUntil != 0L && now < pendingUntil
    fun accepts(token: Long, now: Long): Boolean = token == revision && isPending(now)

    fun reset() {
        returnsHome = false
        pendingUntil = 0L
        revision++
    }
}
