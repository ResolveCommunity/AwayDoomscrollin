package com.resolvecommunity.awaydoomscrollin

internal object InstagramScrollGeometry {
    fun top(previous: Int, measured: Int, headerBottom: Int, deltaX: Int, deltaY: Int,
            flingThreshold: Int = Int.MAX_VALUE): Int {
        // Upward feed motion may reach the display before fresh accessibility bounds.
        // Only expand coverage speculatively; never expose content based on a delta.
        val predicted = if (deltaY > 0 && kotlin.math.abs(deltaY.toLong()) > kotlin.math.abs(deltaX.toLong())) {
            if (deltaY >= flingThreshold) headerBottom else
                (previous.toLong() - deltaY).coerceAtLeast(headerBottom.toLong()).toInt()
        } else measured
        return minOf(measured, predicted).coerceAtLeast(headerBottom)
    }
}
