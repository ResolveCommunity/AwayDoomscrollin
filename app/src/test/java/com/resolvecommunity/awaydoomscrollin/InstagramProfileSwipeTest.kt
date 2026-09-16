package com.resolvecommunity.awaydoomscrollin

import org.junit.Assert.*
import org.junit.Test

class InstagramProfileSwipeTest {
    @Test fun upwardFingerMovementDoesNotRequireInstagramScrollEvents() {
        assertTrue(InstagramProfileSwipe.isUp(1f, -13f, 12f))
        assertTrue(InstagramProfileSwipe.isUp(20f, -200f, 12f))
    }
    @Test fun tapsDownwardAndHorizontalMovementsDoNotExit() {
        assertFalse(InstagramProfileSwipe.isUp(0f, -2f, 12f))
        assertFalse(InstagramProfileSwipe.isUp(0f, 50f, 12f))
        assertFalse(InstagramProfileSwipe.isUp(100f, -20f, 12f))
    }
}
