package com.resolvecommunity.awaydoomscrollin

import org.junit.Assert.assertEquals
import org.junit.Test

class InstagramHomeGeometryTest {
    @Test fun collapsedChromeDoesNotLeaveArtificialTopStrip() {
        assertEquals(0, InstagramHomeGeometry.top(0, null, null))
    }
    @Test fun applicationWindowOffsetIsRespected() {
        assertEquals(80, InstagramHomeGeometry.top(80, null, null))
    }
    @Test fun visibleStoriesRemainAccessible() {
        assertEquals(420, InstagramHomeGeometry.top(80, 420, 180))
    }
    @Test fun verifiedToolbarRemainsAccessible() {
        assertEquals(180, InstagramHomeGeometry.top(80, null, 180))
    }
    @Test fun offscreenTrayDoesNotReserveSpace() {
        assertEquals(80, InstagramHomeGeometry.top(80, 40, null))
    }
}
