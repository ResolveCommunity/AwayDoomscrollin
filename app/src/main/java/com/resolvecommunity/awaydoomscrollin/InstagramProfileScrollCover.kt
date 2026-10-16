package com.resolvecommunity.awaydoomscrollin

/** Predict real displacement, then follow newly published grid geometry. */
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
    private var predictionOrigin: Int? = null
    private var conservativeSettle = false
    private var sessionCeilingTop: Int? = null
    val isHolding: Boolean get() = heldTop != null

    fun onScroll(
        previousTop: Int,
        headerFloor: Int,
        now: Long,
        movementPixels: Int = 0,
        safetyMarginPixels: Int = 0,
        settleConservatively: Boolean = false
    ): Int {
        val predicted = (previousTop.toLong() - movementPixels.coerceAtLeast(0) -
            safetyMarginPixels.coerceAtLeast(0))
            .coerceAtLeast(headerFloor.toLong()).coerceAtMost(previousTop.toLong()).toInt()
        val top = minOf(predicted, heldTop ?: previousTop)
        predictionOrigin = lastObservedTop ?: previousTop
        heldTop = top
        conservativeSettle = settleConservatively
        if (settleConservatively) {
            sessionCeilingTop = minOf(sessionCeilingTop ?: top, top)
        }
        lastScroll = now
        lastStableTop = null
        stableSamples = 0
        return top
    }

    @Suppress("UNUSED_PARAMETER")
    fun measure(top: Int, headerFloor: Int, now: Long): Int {
        val previousObserved = lastObservedTop
        lastObservedTop = top
        sessionCeilingTop?.let { sessionCeilingTop = minOf(it, top) }
        // A measured boundary is direct evidence, not a reason to cover the
        // entire header. New geometry in either direction resolves prediction.
        val held = heldTop ?: return minOf(top, sessionCeilingTop ?: top)
        if (conservativeSettle) {
            sessionCeilingTop = minOf(sessionCeilingTop ?: top, top)
            stableSamples = if (lastStableTop == top) stableSamples + 1 else 1
            lastStableTop = top
            if (now - lastScroll >= 250L && stableSamples >= 3) {
                resetPrediction()
                lastObservedTop = top
                return minOf(top, sessionCeilingTop ?: top)
            }
            return minOf(top, held, sessionCeilingTop ?: top)
        }
        if (top != predictionOrigin || (previousObserved != null && top != previousObserved)) {
            resetPrediction()
            lastObservedTop = top
            return top
        }
        stableSamples = if (lastStableTop == top) stableSamples + 1 else 1
        lastStableTop = top
        if (now - lastScroll >= 250L && stableSamples >= 3) {
            resetPrediction()
            lastObservedTop = top
            return top
        }
        return minOf(top, held)
    }

    fun reset() {
        resetPrediction()
        sessionCeilingTop = null
    }

    private fun resetPrediction() {
        heldTop = null
        lastObservedTop = null
        lastStableTop = null
        stableSamples = 0
        lastScroll = 0L
        predictionOrigin = null
        conservativeSettle = false
    }
}
