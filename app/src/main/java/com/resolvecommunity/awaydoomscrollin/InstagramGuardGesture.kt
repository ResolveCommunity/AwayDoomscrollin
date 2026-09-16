package com.resolvecommunity.awaydoomscrollin

import kotlin.math.abs

internal enum class InstagramGuardScrollDirection {
    FORWARD,
    BACKWARD
}

internal object InstagramGuardGesture {
    fun verticalScrollDirection(
        deltaX: Float,
        deltaY: Float,
        threshold: Float
    ): InstagramGuardScrollDirection? {
        if (abs(deltaY) < threshold || abs(deltaY) < abs(deltaX)) return null
        return if (deltaY < 0f) {
            InstagramGuardScrollDirection.FORWARD
        } else {
            InstagramGuardScrollDirection.BACKWARD
        }
    }
}
