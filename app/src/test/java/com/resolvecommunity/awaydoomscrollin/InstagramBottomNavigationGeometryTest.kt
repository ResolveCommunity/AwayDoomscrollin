package com.resolvecommunity.awaydoomscrollin

import org.junit.Assert.assertEquals
import org.junit.Test

class InstagramBottomNavigationGeometryTest {
    @Test fun firstCurtainUsesMeasuredNavigationTop() {
        assertEquals(2196, InstagramBottomNavigationGeometry.resolveTop(
            0, 2340, retainedTop = null, detectedTop = 2196, fallbackInset = 144))
    }

    @Test fun mountedCurtainIgnoresTransientLowerNavigationReading() {
        assertEquals(2196, InstagramBottomNavigationGeometry.resolveTop(
            0, 2340, retainedTop = 2196, detectedTop = 2290, fallbackInset = 144))
    }

    @Test fun mountedCurtainIgnoresTransientUpperNavigationReading() {
        assertEquals(2196, InstagramBottomNavigationGeometry.resolveTop(
            0, 2340, retainedTop = 2196, detectedTop = 2100, fallbackInset = 144))
    }

    @Test fun invalidSamplesUseSafeFallback() {
        assertEquals(2196, InstagramBottomNavigationGeometry.resolveTop(
            0, 2340, retainedTop = 300, detectedTop = 2400, fallbackInset = 144))
    }
}
