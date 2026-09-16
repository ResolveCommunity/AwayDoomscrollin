package com.resolvecommunity.awaydoomscrollin

import android.view.accessibility.AccessibilityEvent

/** Lets a click overtake the noisy content event that Instagram emits just before it. */
internal object InstagramEventScanPolicy {
    fun delayMs(eventType: Int): Long = when (eventType) {
        AccessibilityEvent.TYPE_VIEW_CLICKED -> 0L
        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> 64L
        else -> 16L
    }
}
