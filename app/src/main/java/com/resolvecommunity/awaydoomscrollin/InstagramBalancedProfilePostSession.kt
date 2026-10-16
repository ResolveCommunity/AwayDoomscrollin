package com.resolvecommunity.awaydoomscrollin

/**
 * Keeps a static post available only when it was deliberately opened from a
 * verified profile or user collection in Balanced mode. The short pending
 * window cannot grant a notification, Direct gallery or Explore launch a
 * lasting exception.
 */
internal class InstagramBalancedProfilePostSession(
    private val launchWindowMs: Long = 2_000L
) {
    private var pendingUntil = 0L
    var watching: Boolean = false
        private set

    fun arm(mode: ProtectionMode, verifiedProfile: Boolean, profileThumbnail: Boolean, now: Long,
        verifiedCollection: Boolean = false): Boolean {
        if (mode != ProtectionMode.BALANCED ||
            (!verifiedProfile && !verifiedCollection) || !profileThumbnail) {
            return false
        }
        pendingUntil = now + launchWindowMs
        watching = false
        return true
    }

    fun confirm(mode: ProtectionMode, now: Long): Boolean {
        if (mode != ProtectionMode.BALANCED) {
            clear()
            return false
        }
        if (!watching && (pendingUntil == 0L || now > pendingUntil)) {
            clear()
            return false
        }
        watching = true
        pendingUntil = 0L
        return true
    }

    fun observeVerifiedSurface(screen: InstagramScreen, now: Long) {
        if (!watching && pendingUntil != 0L && now > pendingUntil) pendingUntil = 0L
        when {
            watching && screen != InstagramScreen.COMMENTS_OR_DETAIL &&
                screen != InstagramScreen.UNKNOWN -> clear()
            !watching && screen !in setOf(
                InstagramScreen.PROFILE,
                InstagramScreen.COMMENTS_OR_DETAIL,
                InstagramScreen.UNKNOWN
            ) -> clear()
        }
    }

    fun clear() {
        pendingUntil = 0L
        watching = false
    }
}
