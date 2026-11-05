package com.resolvecommunity.awaydoomscrollin

/**
 * Instagram's large-screen layout (foldables opened, tablets) has no bottom
 * tab bar. Its tabs sit in a vertical rail along the leading edge instead
 * (right edge when mirrored). A curtain that spans the window would cover
 * every tab, so the curtain window leaves the rail uncovered and, with no
 * bottom bar, reaches down to the window bottom.
 */
internal object InstagramSideNavigation {
    /** The rail and the stub it inflates into; captured on device (Instagram 453). */
    val railIds = listOf("ls_nav_bar", "ls_vertical_nav_bar_stub")

    /**
     * A rail is tall (at least half the window), narrow (at most a quarter of
     * it) and flush with the window's left or right edge. Anything else with
     * the same id is a transition frame and is ignored.
     */
    fun rail(
        candidate: InstagramGeometrySample,
        window: InstagramGeometrySample,
        edgeTolerance: Int
    ): InstagramGeometrySample? {
        val width = candidate.right - candidate.left
        val height = candidate.bottom - candidate.top
        val windowWidth = window.right - window.left
        val windowHeight = window.bottom - window.top
        if (width <= 0 || windowWidth <= 0 || height * 2 < windowHeight || width * 4 > windowWidth) {
            return null
        }
        val atLeft = kotlin.math.abs(candidate.left - window.left) <= edgeTolerance
        val atRight = kotlin.math.abs(candidate.right - window.right) <= edgeTolerance
        return candidate.takeIf { atLeft || atRight }
    }

    /**
     * The part of [curtain] beside [rail]. A rail left of the curtain's centre
     * moves the curtain's left edge to the rail's right edge; a mirrored rail
     * moves its right edge. A curtain that would vanish is returned unchanged:
     * protection must never be lost to a bad sample.
     */
    fun excludeRail(
        curtain: InstagramGeometrySample,
        rail: InstagramGeometrySample?
    ): InstagramGeometrySample {
        rail ?: return curtain
        if (rail.bottom <= curtain.top || rail.top >= curtain.bottom ||
            rail.right <= curtain.left || rail.left >= curtain.right) return curtain
        val railOnLeft = rail.left + rail.right < curtain.left + curtain.right
        val result = if (railOnLeft) {
            curtain.copy(left = maxOf(curtain.left, rail.right))
        } else {
            curtain.copy(right = minOf(curtain.right, rail.left))
        }
        return result.takeIf { it.right > it.left } ?: curtain
    }

    /**
     * With the rail as navigation nothing sits at the bottom except the
     * system's own tappable buttons (3-button navigation or a pinned taskbar);
     * with gesture navigation the curtain reaches the window bottom.
     */
    fun bottomWithoutBottomBar(windowBottom: Int, tappableBottomInset: Int): Int =
        windowBottom - tappableBottomInset.coerceAtLeast(0)
}

/**
 * Remembers the last rail lookup so curtain measurements, which run on every
 * scan, cost at most one node lookup per [MAX_AGE_MS]. A different window or
 * window size (fold, unfold, rotation) always probes again at once.
 */
internal class InstagramSideRailProbe {
    data class Result(val rail: InstagramGeometrySample?)

    private var windowId = -1
    private var width = 0
    private var height = 0
    private var probedAt = 0L
    private var result: Result? = null

    fun cached(windowId: Int, width: Int, height: Int, nowMs: Long): Result? =
        result?.takeIf {
            windowId == this.windowId && width == this.width && height == this.height &&
                nowMs - probedAt in 0 until MAX_AGE_MS
        }

    fun store(windowId: Int, width: Int, height: Int, rail: InstagramGeometrySample?, nowMs: Long) {
        this.windowId = windowId
        this.width = width
        this.height = height
        probedAt = nowMs
        result = Result(rail)
    }

    companion object {
        const val MAX_AGE_MS = 1_500L
    }
}
