package com.resolvecommunity.awaydoomscrollin

/** Counting only: never owns navigation, protection decisions or retry timing. */
internal class BlockedAttemptCounter {
    private var counted = false
    fun acceptedIntervention(accepted: Boolean): Boolean {
        if (!accepted || counted) return false
        counted = true
        return true
    }
    fun reset() { counted = false }
}

/** A cancelled gesture, drag or multi-touch is not an intentional blocked button tap. */
internal class BlockedTapGesture(private val threshold: Float) {
    private var active = false
    private var moved = false
    private var startX = 0f
    private var startY = 0f
    fun begin(x: Float, y: Float) {
        active = true; moved = false; startX = x; startY = y
    }
    fun move(x: Float, y: Float) {
        if (kotlin.math.max(kotlin.math.abs(x - startX), kotlin.math.abs(y - startY)) >= threshold) moved = true
    }
    fun finish(x: Float, y: Float): Boolean {
        move(x, y)
        val tap = active && !moved
        cancel()
        return tap
    }
    fun cancel() { active = false }
}
