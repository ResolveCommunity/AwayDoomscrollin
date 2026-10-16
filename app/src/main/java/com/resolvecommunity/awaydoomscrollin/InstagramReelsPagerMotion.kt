package com.resolvecommunity.awaydoomscrollin

/** fromIndex..toIndex is a visible range, not evidence of a swipe. */
internal class InstagramReelsPagerMotion {
    private var previousIndex = -1
    private var previousY = -1
    fun observe(dx: Int, dy: Int, fromIndex: Int, scrollY: Int): Boolean {
        val indexChanged = previousIndex >= 0 && fromIndex >= 0 && previousIndex != fromIndex
        val yChanged = previousY >= 0 && scrollY >= 0 && previousY != scrollY
        if (fromIndex >= 0) previousIndex = fromIndex
        if (scrollY >= 0) previousY = scrollY
        return if (dx != 0 || dy != 0) kotlin.math.abs(dy) > kotlin.math.abs(dx)
            else indexChanged || yChanged
    }
    fun reset() { previousIndex = -1; previousY = -1 }
}
