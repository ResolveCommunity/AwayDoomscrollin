package com.resolvecommunity.awaydoomscrollin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InstagramGuardGestureTest {
    @Test fun tapDoesNotBecomeScroll() {
        assertNull(InstagramGuardGesture.verticalScrollDirection(2f, 5f, 12f))
    }

    @Test fun horizontalMovementDoesNotScrollConversation() {
        assertNull(InstagramGuardGesture.verticalScrollDirection(30f, 15f, 12f))
    }

    @Test fun swipeUpScrollsForward() {
        assertEquals(InstagramGuardScrollDirection.FORWARD,
            InstagramGuardGesture.verticalScrollDirection(3f, -24f, 12f))
    }

    @Test fun swipeDownScrollsBackward() {
        assertEquals(InstagramGuardScrollDirection.BACKWARD,
            InstagramGuardGesture.verticalScrollDirection(3f, 24f, 12f))
    }
}
