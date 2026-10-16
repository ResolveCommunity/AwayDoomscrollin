package com.resolvecommunity.awaydoomscrollin

import kotlin.math.abs

internal enum class InstagramStoryGuardSwipe {
    PROFILE_FORWARD, PROFILE_BACKWARD, HIGHLIGHTS_FORWARD, HIGHLIGHTS_BACKWARD
}

/** One gesture is either a blocked tap or one native scroll, never both. */
internal class InstagramStoryGuardGesture(private val threshold: Float) {
    private var startX = 0f
    private var startY = 0f
    private var active = false
    private var moved = false
    val isTap: Boolean get() = active && !moved
    fun begin(x: Float, y: Float) { startX = x; startY = y; active = true; moved = false }
    fun update(x: Float, y: Float): InstagramStoryGuardSwipe? {
        if (!active || moved) return null
        val dx = x - startX
        val dy = y - startY
        if (maxOf(abs(dx), abs(dy)) < threshold) return null
        moved = true
        return if (abs(dy) >= abs(dx)) {
            if (dy < 0) InstagramStoryGuardSwipe.PROFILE_FORWARD else InstagramStoryGuardSwipe.PROFILE_BACKWARD
        } else {
            if (dx < 0) InstagramStoryGuardSwipe.HIGHLIGHTS_FORWARD else InstagramStoryGuardSwipe.HIGHLIGHTS_BACKWARD
        }
    }
    fun cancel() { active = false }
}
