package com.resolvecommunity.awaydoomscrollin

import org.junit.Assert.*
import org.junit.Test

class InstagramNotificationReturnStateTest {
    @Test fun onlyVerifiedHomeOriginMayPrecoverBack() {
        for (origin in InstagramScreen.entries) {
            val state = InstagramNotificationReturnState()
            state.observe(InstagramScreen.NOTIFICATIONS, origin)
            assertEquals(origin == InstagramScreen.HOME_FEED, state.returnsHome)
        }
    }

    @Test fun loadingAndRepeatedNotificationReportsKeepOrigin() {
        val state = fromHome()
        state.observe(InstagramScreen.UNKNOWN, InstagramScreen.NOTIFICATIONS)
        state.observe(InstagramScreen.NOTIFICATIONS, InstagramScreen.NOTIFICATIONS)
        assertTrue(state.returnsHome)
    }

    @Test fun oneTapOwnsBoundedTransition() {
        val state = fromHome()
        val token = state.begin(100)!!
        assertTrue(state.accepts(token, 101))
        assertNull(state.begin(102))
        assertFalse(state.accepts(token, 1_600))
        val next = state.begin(1_600)!!
        assertFalse(state.accepts(token, 1_601))
        assertTrue(state.accepts(next, 1_601))
    }

    @Test fun nestedCommentPageMustNotReceiveHomeReturnGuard() {
        val state = fromHome()
        state.observe(InstagramScreen.COMMENTS_OR_DETAIL, InstagramScreen.NOTIFICATIONS)
        state.observe(InstagramScreen.NOTIFICATIONS, InstagramScreen.COMMENTS_OR_DETAIL)
        assertFalse(state.returnsHome)
        assertNull(state.begin(100))
    }

    @Test fun destinationOrAppDepartureInvalidatesDeferredBack() {
        for (destination in listOf(InstagramScreen.HOME_FEED, InstagramScreen.PROFILE, InstagramScreen.DIRECT_MESSAGES)) {
            val state = fromHome()
            val token = state.begin(100)!!
            state.observe(destination, InstagramScreen.NOTIFICATIONS)
            assertFalse(state.accepts(token, 101))
        }
        val state = fromHome()
        val token = state.begin(100)!!
        state.reset()
        assertFalse(state.accepts(token, 101))
    }

    @Test fun repeatedHomeNotificationCyclesNeverReuseOldBack() {
        val state = InstagramNotificationReturnState()
        repeat(100) { index ->
            val now = index * 100L + 100
            state.observe(InstagramScreen.NOTIFICATIONS, InstagramScreen.HOME_FEED)
            val token = state.begin(now)!!
            assertTrue(state.accepts(token, now + 1))
            state.observe(InstagramScreen.HOME_FEED, InstagramScreen.NOTIFICATIONS)
            assertFalse(state.accepts(token, now + 2))
        }
    }

    private fun fromHome() = InstagramNotificationReturnState().apply {
        observe(InstagramScreen.NOTIFICATIONS, InstagramScreen.HOME_FEED)
    }
}
