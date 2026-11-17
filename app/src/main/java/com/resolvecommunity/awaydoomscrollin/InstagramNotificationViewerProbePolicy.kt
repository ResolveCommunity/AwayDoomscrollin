package com.resolvecommunity.awaydoomscrollin

/**
 * Whether a scan on the Notifications page probes every Instagram window for
 * a Reel, post or comment viewer.
 *
 * The probe asks for about 30 ids per window. Instagram 454's notification
 * list answers each in ~40 ms, so a probe on every scan held the main thread
 * 1.1-1.6 s per scan while the user only looked at the list (Galaxy S23), and
 * a Back to Home waited behind it with the feed uncovered.
 *
 * A viewer opens only after a tap on the list (which arms the exit hold and,
 * for a content row, the launch window) or arrives as a new window or window
 * state. Without either there is nothing new to find.
 */
object InstagramNotificationViewerProbePolicy {
    fun shouldProbe(
        now: Long,
        exitHoldUntil: Long,
        contentLaunchUntil: Long,
        exitInProgress: Boolean,
        windowChangedSinceProbe: Boolean
    ): Boolean = exitInProgress || windowChangedSinceProbe ||
        now < contentLaunchUntil || now < exitHoldUntil
}
