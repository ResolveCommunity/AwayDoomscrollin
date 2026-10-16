package com.resolvecommunity.awaydoomscrollin

/** Generation-checked bridge. Time alone must never expose an unclassified list. */
internal class InstagramNotificationIngress {
    private var generation = 0L
    private var pending = false
    private var signature: String? = null
    private var samples = 0
    private var dispatched = false
    private var drawingIdentity: String? = null
    val awaitingDispatch: Boolean get() = pending && !dispatched
    fun begin(now: Long): Long? {
        if (active(now)) return null
        generation++
        pending = true
        signature = null
        samples = 0
        dispatched = false
        drawingIdentity = null
        return generation
    }
    /** Starts a new Notifications visit even if an abandoned visit is pending. */
    fun startNew(): Long {
        generation++
        pending = true
        signature = null
        samples = 0
        dispatched = false
        drawingIdentity = null
        return generation
    }
    @Suppress("UNUSED_PARAMETER")
    fun active(now: Long) = pending
    fun accepts(token: Long, now: Long) = token == generation && active(now)
    fun markDispatched(token: Long): Boolean {
        if (!pending || token != generation || dispatched) return false
        dispatched = true
        return true
    }
    fun ready(identity: String, populated: Boolean): Boolean {
        if (!populated) { signature = null; samples = 0; drawingIdentity = null; return false }
        if (signature != identity) drawingIdentity = null
        samples = if (signature == identity) samples + 1 else 1
        signature = identity
        return samples >= 2
    }
    fun prepareHandoff(token: Long, identity: String, populated: Boolean): Boolean {
        if (!pending || !dispatched || token != generation) return false
        if (!ready(identity, populated) || drawingIdentity == identity) return false
        drawingIdentity = identity
        return true
    }
    fun completeHandoff(token: Long, identity: String): Boolean {
        if (!pending || token != generation || drawingIdentity != identity || signature != identity) return false
        reset()
        return true
    }
    fun reset() {
        generation++; pending = false; signature = null; samples = 0
        dispatched = false; drawingIdentity = null
    }
}
