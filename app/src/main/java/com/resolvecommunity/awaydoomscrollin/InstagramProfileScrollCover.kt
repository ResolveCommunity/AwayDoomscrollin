package com.resolvecommunity.awaydoomscrollin

/** Cover first, reopen only after quiet scrolling and stable grid measurements. */
internal class InstagramProfileScrollCover {
    companion object {
        fun shouldCover(dx: Int, dy: Int, effectiveDy: Int, knownProfileSource: Boolean): Boolean =
            effectiveDy > 0 && effectiveDy.toLong() > kotlin.math.abs(dx.toLong()) &&
                (knownProfileSource || dy != 0)
    }

    private var heldTop: Int? = null
    private var lastScroll = 0L
    private var lastObservedTop: Int? = null
    private var lastStableTop: Int? = null
    private var stableSamples = 0
    val isHolding: Boolean get() = heldTop != null

    fun onScroll(previousTop: Int, headerFloor: Int, now: Long): Int {
        val top = minOf(previousTop, headerFloor, heldTop ?: previousTop)
        heldTop = top
        lastScroll = now
        lastStableTop = null
        stableSamples = 0
        return top
    }

    fun measure(top: Int, headerFloor: Int, now: Long): Int {
        val previousObserved = lastObservedTop
        lastObservedTop = top
        // Instagram frequently reports dy=0 hundreds of milliseconds late.
        // The grid itself moving upward is sufficient evidence to cover ahead
        // of its stale accessibility boundary, but never issues navigation.
        if (heldTop == null && previousObserved != null && top < previousObserved - 2) {
            return onScroll(previousObserved, headerFloor, now)
        }
        val held = heldTop ?: return top
        stableSamples = if (lastStableTop == top) stableSamples + 1 else 1
        lastStableTop = top
        if (now - lastScroll >= 250L && stableSamples >= 3) {
            reset()
            lastObservedTop = top
            return top
        }
        return minOf(top, held)
    }

    fun reset() {
        heldTop = null
        lastObservedTop = null
        lastStableTop = null
        stableSamples = 0
        lastScroll = 0L
    }
}
