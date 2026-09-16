package com.resolvecommunity.awaydoomscrollin

internal object InstagramProfileGeometry {
    fun clip(top: Int, bottom: Int, headerBottom: Int?, navigationTop: Int?): Pair<Int, Int>? {
        val start = maxOf(top, headerBottom ?: top)
        val end = minOf(bottom, navigationTop ?: bottom)
        return if (end > start) start to end else null
    }

    fun scrollFloor(
        windowTop: Int,
        actionBarBottom: Int?,
        statusBarInset: Int,
        toolbarHeight: Int
    ): Int {
        val safeFallback = windowTop + statusBarInset.coerceAtLeast(0) +
            toolbarHeight.coerceAtLeast(0)
        return maxOf(safeFallback, actionBarBottom ?: safeFallback)
    }
}
