package com.resolvecommunity.awaydoomscrollin

import kotlin.math.abs

internal enum class ScrollDirection {
    VERTICAL,
    HORIZONTAL,

    /** Both deltas are -1: Android's value for "never set". */
    UNDEFINED,

    /** No deltas (Android 8) but the event names a new item. */
    PAGE_CHANGE,
    NONE
}

/**
 * Direction of a TYPE_VIEW_SCROLLED event.
 *
 * A view that scrolled through onScrollChanged sets both deltas (Android 9+);
 * a pager that only announces its page leaves them at -1. On a Galaxy S9+
 * (Android 10, TikTok 47.6.3) both the vertical video pager and the
 * horizontal top-tab pager sent -1/-1, so such an event says nothing about
 * the direction by itself.
 */
internal object ScrollEventPolicy {
    const val UNDEFINED_DELTA = -1

    fun direction(dx: Int, dy: Int, fromIndex: Int, toIndex: Int): ScrollDirection {
        if (dx == UNDEFINED_DELTA && dy == UNDEFINED_DELTA) {
            return if (fromIndex >= 0 && toIndex >= 0 && fromIndex != toIndex) {
                ScrollDirection.PAGE_CHANGE
            } else ScrollDirection.UNDEFINED
        }
        if (dx != 0 || dy != 0) {
            return if (abs(dy) >= abs(dx)) ScrollDirection.VERTICAL else ScrollDirection.HORIZONTAL
        }
        return if (fromIndex >= 0 && toIndex >= 0 && fromIndex != toIndex) {
            ScrollDirection.PAGE_CHANGE
        } else ScrollDirection.NONE
    }

    /**
     * TikTok's routing: anything but a known horizontal or still scroll may be
     * a feed swipe; [TikTokSurfacePolicy.isFeedSwipe] then checks the pager.
     */
    fun mayBeVertical(direction: ScrollDirection): Boolean =
        direction == ScrollDirection.VERTICAL || direction == ScrollDirection.UNDEFINED ||
            direction == ScrollDirection.PAGE_CHANGE

    /**
     * A move to the next YouTube Short. Galaxy S9+ (Android 10, YouTube
     * 21.43): opening a Short from search sent a reel_recycler scroll with
     * -1/-1, and reading that as a move closed the one Short Balanced allows.
     * A real swipe there reports its vertical delta.
     */
    fun isShortsMovement(direction: ScrollDirection): Boolean =
        direction == ScrollDirection.VERTICAL || direction == ScrollDirection.PAGE_CHANGE
}
