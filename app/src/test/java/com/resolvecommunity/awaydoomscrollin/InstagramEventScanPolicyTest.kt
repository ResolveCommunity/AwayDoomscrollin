package com.resolvecommunity.awaydoomscrollin

import android.view.accessibility.AccessibilityEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class InstagramEventScanPolicyTest {
    @Test fun clickOvertakesTheContentChangeThatPrecedesIt() {
        assertEquals(0L, InstagramEventScanPolicy.delayMs(AccessibilityEvent.TYPE_VIEW_CLICKED))
        assertEquals(64L,
            InstagramEventScanPolicy.delayMs(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED))
    }

    @Test fun scrollAndWindowChangesRemainUrgent() {
        assertEquals(16L, InstagramEventScanPolicy.delayMs(AccessibilityEvent.TYPE_VIEW_SCROLLED))
        assertEquals(16L, InstagramEventScanPolicy.delayMs(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED))
    }
}
