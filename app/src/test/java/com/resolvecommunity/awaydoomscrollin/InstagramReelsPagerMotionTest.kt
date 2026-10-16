package com.resolvecommunity.awaydoomscrollin
import org.junit.Assert.*
import org.junit.Test
class InstagramReelsPagerMotionTest {
    @Test fun firstPublishedPageIsNotASwipe() {
        val motion = InstagramReelsPagerMotion()
        assertFalse(motion.observe(0, 0, 4, 4000))
        assertFalse(motion.observe(0, 0, 4, 4000))
    }
    @Test fun realVerticalDeltaAndPageChangeCount() {
        assertTrue(InstagramReelsPagerMotion().observe(0, 100, 0, 0))
        val motion = InstagramReelsPagerMotion()
        motion.observe(0, 0, 0, 0)
        assertTrue(motion.observe(0, 0, 1, 0))
    }
    @Test fun horizontalAndInvalidMovementDoNotCount() {
        val motion = InstagramReelsPagerMotion()
        assertFalse(motion.observe(100, 1, -1, -1))
        assertFalse(motion.observe(0, 0, -1, -1))
    }
    @Test fun reopenedVideoGetsAFreshBaseline() {
        val motion = InstagramReelsPagerMotion()
        motion.observe(0, 0, 10, 1000)
        motion.reset()
        assertFalse(motion.observe(0, 0, 0, 0))
    }
}
