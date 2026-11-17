package com.resolvecommunity.awaydoomscrollin

/**
 * A tap on a bottom tab whose destination is open lifts the feed curtain at
 * once, like the Messages tab does. Waiting for the scan that verifies the new
 * page left the Home or Explore curtain over the profile for 0.5-0.6 s
 * (Galaxy S23: about 25 tree lookups of 16 ms each).
 *
 * Only the profile tab in Balanced qualifies: in Strict the profile's posts
 * are covered too, so the outgoing curtain stays until the profile curtain is
 * mounted. Messages keep their own release (InstagramInboxPolicy).
 */
internal object InstagramTabReleasePolicy {
    /** How long a scan that still sees the old feed waits instead of re-covering. */
    const val GRACE_MS = 1_500L

    /** No verified destination by then: the last feed rectangle is covered again. */
    const val RECOVER_MS = 500L

    /**
     * The curtain lifts once the profile header is in the tree; it is looked
     * for every frame up to this long after the tap, then the regular scan
     * decides alone.
     */
    const val PROBE_MS = 600L

    fun releasesOnTap(curtain: InstagramScreen?, tab: InstagramTab?, mode: ProtectionMode): Boolean =
        (curtain == InstagramScreen.HOME_FEED || curtain == InstagramScreen.EXPLORE) &&
            tab == InstagramTab.PROFILE && mode == ProtectionMode.BALANCED

    /** A scan inside the grace window that still classifies the outgoing feed waits for the swap. */
    fun waitsForSwap(screen: InstagramScreen, inGrace: Boolean): Boolean =
        inGrace && (screen == InstagramScreen.HOME_FEED || screen == InstagramScreen.EXPLORE)
}
