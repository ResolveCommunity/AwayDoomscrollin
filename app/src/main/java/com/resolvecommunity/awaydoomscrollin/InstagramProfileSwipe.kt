package com.resolvecommunity.awaydoomscrollin

internal object InstagramProfileSwipe {
    fun isUp(dx: Float, dy: Float, threshold: Float): Boolean =
        -dy >= threshold && -dy > kotlin.math.abs(dx)
}
