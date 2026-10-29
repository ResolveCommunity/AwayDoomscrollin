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
    private var fromCollection = false
    var watching: Boolean = false
        private set

    fun arm(mode: ProtectionMode, verifiedProfile: Boolean, profileThumbnail: Boolean, now: Long,
        verifiedCollection: Boolean = false): Boolean {
        if (mode != ProtectionMode.BALANCED ||
            (!verifiedProfile && !verifiedCollection) || !profileThumbnail) {
            return false
        }
        pendingUntil = now + launchWindowMs
        fromCollection = !verifiedProfile && verifiedCollection
        watching = false
        return true
    }

    /** A profile thumbnail was chosen and its viewer may still be opening, or is open. */
    fun isChosen(now: Long): Boolean = watching || (pendingUntil != 0L && now <= pendingUntil)

    /** As [isChosen], for a thumbnail chosen in a user collection (Saved, Likes). */
    fun isChosenFromCollection(now: Long): Boolean = fromCollection && isChosen(now)

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
        fromCollection = false
        watching = false
    }
}
