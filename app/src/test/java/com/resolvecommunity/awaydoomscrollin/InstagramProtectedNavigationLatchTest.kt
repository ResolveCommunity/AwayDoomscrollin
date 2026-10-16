package com.resolvecommunity.awaydoomscrollin

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstagramProtectedNavigationLatchTest {
    private val latch = InstagramProtectedNavigationLatch(holdMs = 2_000L)

    @Test
    fun `queued source scan cannot release curtain before dispatch`() {
        latch.begin(InstagramTab.HOME, sourceWindowId = 7,
            sourceTab = InstagramTab.DIRECT, nowMs = 100L)

        assertTrue(latch.shouldHoldBeforeDispatch(nowMs = 140L, currentWindowId = 7))
        assertTrue(latch.isAwaitingDispatch(nowMs = 140L))
    }

    @Test
    fun `successful dispatch ends the source scan hold`() {
        val request = latch.begin(InstagramTab.HOME, sourceWindowId = 7,
            sourceTab = InstagramTab.DIRECT, nowMs = 100L)

        assertTrue(latch.acceptsDispatch(request, 140L, 7, InstagramTab.DIRECT))
        assertTrue(latch.markDispatched(request))
        assertFalse(latch.shouldHoldBeforeDispatch(nowMs = 150L, currentWindowId = 7))
        assertFalse(latch.isAwaitingDispatch(nowMs = 150L))
        assertTrue(latch.isPending(InstagramTab.HOME, nowMs = 150L))
    }

    @Test
    fun `late callback from an older same-tab request is rejected`() {
        val old = latch.begin(InstagramTab.HOME, sourceWindowId = 7,
            sourceTab = InstagramTab.DIRECT, nowMs = 100L)
        latch.begin(InstagramTab.HOME, sourceWindowId = 7,
            sourceTab = InstagramTab.SEARCH, nowMs = 200L)

        assertFalse(latch.acceptsDispatch(old, 220L, 7, InstagramTab.SEARCH))
    }

    @Test
    fun `navigation to another tab cancels stale request`() {
        val request = latch.begin(InstagramTab.HOME, sourceWindowId = 7,
            sourceTab = InstagramTab.DIRECT, nowMs = 100L)

        assertFalse(latch.acceptsDispatch(request, 140L, 7, InstagramTab.PROFILE))
        assertFalse(latch.isPending(InstagramTab.HOME, nowMs = 150L))
    }

    @Test
    fun `expired request neither blocks scans nor dispatches`() {
        val request = latch.begin(InstagramTab.HOME, sourceWindowId = 7,
            sourceTab = InstagramTab.DIRECT, nowMs = 100L)

        assertFalse(latch.shouldHoldBeforeDispatch(nowMs = 2_101L, currentWindowId = 7))
        assertFalse(latch.acceptsDispatch(request, 2_101L, 7, InstagramTab.DIRECT))
    }

    @Test
    fun `same destination tap is debounced while request is current`() {
        latch.begin(InstagramTab.SEARCH, sourceWindowId = 7,
            sourceTab = InstagramTab.DIRECT, nowMs = 100L)

        assertTrue(latch.isDuplicate(InstagramTab.SEARCH, nowMs = 200L))
        assertFalse(latch.isDuplicate(InstagramTab.HOME, nowMs = 200L))
    }
}
