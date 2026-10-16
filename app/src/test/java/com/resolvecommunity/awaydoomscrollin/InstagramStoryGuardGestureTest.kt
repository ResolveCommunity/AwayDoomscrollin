package com.resolvecommunity.awaydoomscrollin

import org.junit.Assert.*
import org.junit.Test

class InstagramStoryGuardGestureTest {
    private val gesture = InstagramStoryGuardGesture(12f)
    @Test fun stillTouchIsABlockedTap() {
        gesture.begin(100f, 100f)
        assertNull(gesture.update(104f, 104f))
        assertTrue(gesture.isTap)
    }
    @Test fun verticalSwipeScrollsProfileNotStoryAndIsNotCountedAsTap() {
        gesture.begin(100f, 100f)
        assertEquals(InstagramStoryGuardSwipe.PROFILE_FORWARD, gesture.update(101f, 70f))
        assertFalse(gesture.isTap)
        assertNull(gesture.update(100f, 40f))
    }
    @Test fun downwardSwipeScrollsBackToProfileHeader() {
        gesture.begin(100f, 100f)
        assertEquals(InstagramStoryGuardSwipe.PROFILE_BACKWARD, gesture.update(100f, 130f))
        assertFalse(gesture.isTap)
    }
    @Test fun highlightsSupportHorizontalScrollInBothDirections() {
        gesture.begin(100f, 100f)
        assertEquals(InstagramStoryGuardSwipe.HIGHLIGHTS_FORWARD, gesture.update(70f, 102f))
        gesture.begin(100f, 100f)
        assertEquals(InstagramStoryGuardSwipe.HIGHLIGHTS_BACKWARD, gesture.update(140f, 100f))
        assertFalse(gesture.isTap)
    }
    @Test fun cancelledOrMultiPointerGestureNeverBecomesBlockedTap() {
        gesture.begin(100f, 100f)
        gesture.cancel()
        assertNull(gesture.update(100f, 140f))
        assertFalse(gesture.isTap)
    }
    @Test fun ANewGestureResetsPreviousScrollWithoutDuplicatingIt() {
        gesture.begin(100f, 100f)
        gesture.update(100f, 40f)
        gesture.cancel()
        gesture.begin(100f, 100f)
        assertTrue(gesture.isTap)
        assertEquals(InstagramStoryGuardSwipe.PROFILE_BACKWARD, gesture.update(100f, 150f))
        assertNull(gesture.update(100f, 10f))
    }
}
