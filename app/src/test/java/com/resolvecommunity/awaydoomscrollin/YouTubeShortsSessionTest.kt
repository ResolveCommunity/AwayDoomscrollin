package com.resolvecommunity.awaydoomscrollin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeShortsSessionTest {
    @Test
    fun `one hundred rapid reopen cycles remain protected without browse snapshots`() {
        val session = YouTubeShortsSession()
        repeat(100) { index ->
            val now = 1_000L + index * 300L
            session.observe(observation(nowMs = now, structure = true))
            assertEquals(YouTubeShortsDecision.EXIT, session.observe(
                observation(nowMs = now + 10L, structure = true, scroll = true, vertical = true, pager = true)
            ))
            val token = session.generation
            assertTrue(session.isExitPending(token, now + 100L))
            session.finishExit(token)
            assertEquals(YouTubeShortsSessionState.OUTSIDE, session.state)
        }
    }

    @Test
    fun `missed completion and missed launch cannot disable protection forever`() {
        val session = YouTubeShortsSession()
        assertEquals(YouTubeShortsDecision.EXIT, session.observe(
            observation(structure = true, scroll = true, vertical = true, pager = true)
        ))
        val oldToken = session.generation
        assertEquals(YouTubeShortsDecision.ALLOW, session.observe(
            observation(nowMs = 1_100L, structure = true, scroll = true, vertical = true, pager = true)
        ))
        assertEquals(YouTubeShortsDecision.EXIT, session.observe(
            observation(nowMs = 1_000L + YouTubeShortsSession.EXIT_PENDING_MS, structure = true, scroll = true, vertical = true, pager = true)
        ))
        assertFalse(session.isExitPending(oldToken, 1_750L))
    }

    @Test
    fun `old completion cannot reset a reopened viewer or its next exit`() {
        val session = YouTubeShortsSession()
        session.observe(observation(structure = true, scroll = true, vertical = true, pager = true))
        val oldToken = session.generation
        session.observe(observation(nowMs = 1_100L, launch = true))
        assertFalse(session.isExitPending(oldToken, 1_110L))
        session.finishExit(oldToken)
        assertEquals(YouTubeShortsSessionState.ENTERING, session.state)
        assertEquals(YouTubeShortsDecision.EXIT, session.observe(
            observation(nowMs = 1_120L, structure = true, scroll = true, vertical = true, pager = true)
        ))
        val newToken = session.generation
        session.finishExit(oldToken)
        assertTrue(session.isExitPending(newToken, 1_130L))
    }

    @Test
    fun `exit timeout recovery still allows comments and zero movement`() {
        val session = YouTubeShortsSession()
        session.observe(observation(structure = true, scroll = true, vertical = true, pager = true))
        assertEquals(YouTubeShortsDecision.ALLOW, session.observe(
            observation(nowMs = 2_000L, structure = true, sheet = true, scroll = true, vertical = true, pager = true)
        ))
        assertEquals(YouTubeShortsDecision.ALLOW, session.observe(
            observation(nowMs = 2_010L, structure = true, scroll = true, pager = true)
        ))
        assertEquals(YouTubeShortsDecision.EXIT, session.observe(
            observation(nowMs = 2_020L, structure = true, scroll = true, vertical = true, pager = true)
        ))
    }
    @Test
    fun `feed tab entry exits immediately and repeated frames cannot exit twice`() {
        val session = YouTubeShortsSession()
        val entry = observation(structure = true).copy(feedTabEntry = true)
        assertEquals(YouTubeShortsDecision.EXIT, session.observe(entry))
        assertEquals(YouTubeShortsDecision.ALLOW, session.observe(entry))
    }

    @Test
    fun `intentional entry allows comments cycle then rejects video swipe`() {
        val session = YouTubeShortsSession()
        assertEquals(YouTubeShortsDecision.ALLOW, session.observe(observation(launch = true)))
        assertEquals(YouTubeShortsDecision.ALLOW, session.observe(observation(structure = true)))
        assertEquals(YouTubeShortsDecision.ALLOW, session.observe(observation(structure = true, sheet = true)))
        assertEquals(YouTubeShortsDecision.ALLOW, session.observe(observation(structure = true, sheet = true, scroll = true, vertical = true, pager = true)))
        assertEquals(YouTubeShortsDecision.ALLOW, session.observe(observation(structure = true, scroll = true, pager = true)))
        assertEquals(YouTubeShortsDecision.EXIT, session.observe(observation(structure = true, scroll = true, vertical = true, pager = true)))
    }
    private fun observation(
        nowMs: Long = 1_000L,
        structure: Boolean = false,
        launch: Boolean = false,
        outside: Boolean = false,
        sheet: Boolean = false,
        scroll: Boolean = false,
        vertical: Boolean = false,
        pager: Boolean = false
    ) = YouTubeShortsObservation(
        nowMs = nowMs,
        shortsStructureVisible = structure,
        launchRequested = launch,
        definitelyOutsideShorts = outside,
        safeBottomSheetOpen = sheet,
        isScrollEvent = scroll,
        hasVerticalMovement = vertical,
        sourceIsShortsPager = pager
    )

    @Test
    fun `explicit launch survives the transient browse tree`() {
        val session = YouTubeShortsSession()

        val decision = session.observe(observation(launch = true, outside = true))
        session.observe(observation(nowMs = 1_100L, outside = true))

        assertEquals(YouTubeShortsDecision.ALLOW, decision)
        assertEquals(YouTubeShortsSessionState.ENTERING, session.state)
    }

    @Test
    fun `unconfirmed launch expires without arming another YouTube surface`() {
        val session = YouTubeShortsSession()
        session.observe(observation(launch = true, outside = true))

        val laterScroll = session.observe(
            observation(nowMs = 3_001L, scroll = true, vertical = true)
        )

        assertEquals(YouTubeShortsDecision.ALLOW, laterScroll)
        assertEquals(YouTubeShortsSessionState.OUTSIDE, session.state)
    }

    @Test
    fun `pending launch needs pager evidence before an immediate swipe can exit`() {
        val session = YouTubeShortsSession()
        session.observe(observation(launch = true, outside = true))

        val unrelatedScroll = session.observe(
            observation(nowMs = 1_100L, scroll = true, vertical = true)
        )
        val pagerScroll = session.observe(
            observation(nowMs = 1_200L, scroll = true, vertical = true, pager = true)
        )

        assertEquals(YouTubeShortsDecision.ALLOW, unrelatedScroll)
        assertEquals(YouTubeShortsDecision.EXIT, pagerScroll)
    }

    @Test
    fun `zero movement opening event starts watching without exiting`() {
        val session = YouTubeShortsSession()
        session.observe(observation(launch = true, outside = true))

        val decision = session.observe(
            observation(structure = true, scroll = true, vertical = false, pager = true)
        )

        assertEquals(YouTubeShortsDecision.ALLOW, decision)
        assertEquals(YouTubeShortsSessionState.WATCHING, session.state)
    }

    @Test
    fun `first real vertical pager movement exits`() {
        val session = YouTubeShortsSession()
        session.observe(observation(structure = true))

        val decision = session.observe(
            observation(structure = true, scroll = true, vertical = true, pager = true)
        )

        assertEquals(YouTubeShortsDecision.EXIT, decision)
        assertEquals(YouTubeShortsSessionState.EXITING, session.state)
    }

    @Test
    fun `confirmed viewer survives a transient missing tree and still exits`() {
        val session = YouTubeShortsSession()
        session.observe(observation(structure = true))

        val decision = session.observe(
            observation(nowMs = 30_000L, scroll = true, vertical = true, pager = true)
        )

        assertEquals(YouTubeShortsDecision.EXIT, decision)
        assertEquals(YouTubeShortsSessionState.EXITING, session.state)
    }

    @Test
    fun `comment sheet scrolling stays safe`() {
        val session = YouTubeShortsSession()
        session.observe(observation(structure = true))

        val decision = session.observe(
            observation(structure = true, sheet = true, scroll = true, vertical = true)
        )

        assertEquals(YouTubeShortsDecision.ALLOW, decision)
        assertEquals(YouTubeShortsSessionState.SAFE_SHEET, session.state)
    }

    @Test
    fun `comment list movement stays safe when sheet markers disappear`() {
        val session = YouTubeShortsSession()
        session.observe(observation(structure = true, sheet = true))

        val decision = session.observe(
            observation(structure = true, scroll = true, vertical = true, pager = false)
        )

        assertEquals(YouTubeShortsDecision.ALLOW, decision)
        assertEquals(YouTubeShortsSessionState.WATCHING, session.state)
    }

    @Test
    fun `non pager vertical movement never exits a confirmed viewer`() {
        val session = YouTubeShortsSession()
        session.observe(observation(structure = true))

        val decision = session.observe(
            observation(structure = true, scroll = true, vertical = true, pager = false)
        )

        assertEquals(YouTubeShortsDecision.ALLOW, decision)
        assertEquals(YouTubeShortsSessionState.WATCHING, session.state)
    }

    @Test
    fun `bottom sheet outside Shorts never arms a session`() {
        val session = YouTubeShortsSession()

        val sheetDecision = session.observe(
            observation(sheet = true, scroll = true, vertical = true)
        )
        val laterScroll = session.observe(
            observation(nowMs = 2_000L, scroll = true, vertical = true)
        )

        assertEquals(YouTubeShortsDecision.ALLOW, sheetDecision)
        assertEquals(YouTubeShortsDecision.ALLOW, laterScroll)
        assertEquals(YouTubeShortsSessionState.OUTSIDE, session.state)
    }

    @Test
    fun `closing sheet with zero movement returns to watching`() {
        val session = YouTubeShortsSession()
        session.observe(observation(structure = true, sheet = true))

        val closeDecision = session.observe(
            observation(structure = true, scroll = true, vertical = false)
        )
        val swipeDecision = session.observe(
            observation(structure = true, scroll = true, vertical = true, pager = true)
        )

        assertEquals(YouTubeShortsDecision.ALLOW, closeDecision)
        assertEquals(YouTubeShortsDecision.EXIT, swipeDecision)
    }

    @Test
    fun `repeated events cannot request another exit`() {
        val session = YouTubeShortsSession()
        session.observe(observation(structure = true))
        session.observe(observation(structure = true, scroll = true, vertical = true, pager = true))

        val repeated = session.observe(
            observation(structure = true, scroll = true, vertical = true, pager = true)
        )

        assertEquals(YouTubeShortsDecision.ALLOW, repeated)
        assertEquals(YouTubeShortsSessionState.EXITING, session.state)
    }

    @Test
    fun `definite browse surface resets a completed session`() {
        val session = YouTubeShortsSession()
        session.observe(observation(structure = true))
        session.observe(observation(structure = true, scroll = true, vertical = true))

        session.observe(observation(nowMs = 2_000L, outside = true))

        assertEquals(YouTubeShortsSessionState.OUTSIDE, session.state)
    }
}
