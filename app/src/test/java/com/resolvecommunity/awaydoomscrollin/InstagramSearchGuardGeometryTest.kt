package com.resolvecommunity.awaydoomscrollin

import org.junit.Assert.*
import org.junit.Test

class InstagramSearchGuardGeometryTest {
    private val results = InstagramGeometrySample(0, 362, 1080, 2196)
    @Test fun keyboardNeverFallsInsideSearchGuard() {
        assertEquals(results.copy(bottom = 1314), InstagramSearchGuardGeometry.clip(results, 1314))
    }
    @Test fun closedKeyboardRestoresOriginalResults() {
        assertEquals(results, InstagramSearchGuardGeometry.clip(results, null))
    }
    @Test fun fullyCoveredResultsHaveNoTouchableArea() {
        assertNull(InstagramSearchGuardGeometry.clip(results, 362))
        assertNull(InstagramSearchGuardGeometry.clip(results, 200))
    }
    @Test fun keyboardBelowResultsCannotEnlargeGuard() {
        assertEquals(results, InstagramSearchGuardGeometry.clip(results, 2300))
    }
}
