package com.resolvecommunity.awaydoomscrollin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutMirrorTest {
    @Test fun homeTabOnTheRightMeansRightToLeft() {
        // Arabic YouTube on a 1080px S23: Home at the right edge, You at the left.
        assertTrue(LayoutMirror.isRightToLeft(firstItemCenterX = 972, lastItemCenterX = 108))
        assertFalse(LayoutMirror.isRightToLeft(firstItemCenterX = 108, lastItemCenterX = 972))
    }

    @Test fun mirroredBackButtonLandsAtTheLeadingEdge() {
        // Arabic Notifications Back arrow [912,98][1080,242] reads as LTR [0,98][168,242].
        assertEquals(0, LayoutMirror.mirroredLeft(1080, 0, 1080))
        assertEquals(168, LayoutMirror.mirroredRight(912, 0, 1080))
    }

    @Test fun mirroringTwiceRestoresTheBounds() {
        val left = LayoutMirror.mirroredLeft(right = 700, windowLeft = 40, windowRight = 1040)
        val right = LayoutMirror.mirroredRight(left = 300, windowLeft = 40, windowRight = 1040)
        assertEquals(300, LayoutMirror.mirroredLeft(right, 40, 1040))
        assertEquals(700, LayoutMirror.mirroredRight(left, 40, 1040))
    }
}
