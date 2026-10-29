package com.resolvecommunity.awaydoomscrollin

/**
 * Right-to-left languages (Arabic, Hebrew, Persian, Urdu) mirror the protected
 * apps: Back, titles and the YouTube logo move to the right edge and bottom
 * tabs run from right to left. Position checks are written for the
 * left-to-right layout, so in a mirrored app they read mirrored bounds.
 */
internal object LayoutMirror {
    /**
     * The first bottom tab in the view tree (Home) sits at the leading edge:
     * left of the last tab in left-to-right layouts, right of it when mirrored.
     */
    fun isRightToLeft(firstItemCenterX: Int, lastItemCenterX: Int): Boolean =
        firstItemCenterX > lastItemCenterX

    /** The left edge of [left, right] reflected across the window's vertical axis. */
    fun mirroredLeft(right: Int, windowLeft: Int, windowRight: Int): Int =
        windowLeft + windowRight - right

    /** The right edge of [left, right] reflected across the window's vertical axis. */
    fun mirroredRight(left: Int, windowLeft: Int, windowRight: Int): Int =
        windowLeft + windowRight - left
}
