package com.resolvecommunity.awaydoomscrollin

import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class YouTubeFeedGeometryTest {

    private val windowTop = 0
    private val windowBottom = 2400
    private val statusBarInset = 98
    private val dp56 = 144
    private val dp48 = 126
    private val dp16 = 42

    @Test
    fun standardHomeFeedWithToolbarIsCalculatedCorrectly() {
        val toolbarBottom = 242 // 98 status bar + 144 dp(56)

        val top = YouTubeFeedGeometry.calculateTop(
            windowTop = windowTop,
            statusBarInset = statusBarInset,
            toolbarBottom = toolbarBottom,
            toolbarContainerBottom = toolbarBottom,
            dp56 = dp56,
            dp16 = dp16
        )

        assertEquals(242, top)
    }

    @Test
    fun youTabCollapsingProfileHeaderNeverExposesFeedTop() {
        // On "You" tab, toolbar is 242, but toolbar_container wraps the profile header down to 698px!
        val toolbarBottom = 242
        val collapsingContainerBottom = 698

        val top = YouTubeFeedGeometry.calculateTop(
            windowTop = windowTop,
            statusBarInset = statusBarInset,
            toolbarBottom = toolbarBottom,
            toolbarContainerBottom = collapsingContainerBottom,
            dp56 = dp56,
            dp16 = dp16
        )

        // Must strictly use 242px (toolbar), NOT 698px
        assertEquals(242, top)
    }

    @Test
    fun youTabWithoutSeparateToolbarCapsContainerToStableToolbar() {
        // Even if primary toolbar node is missing, an oversized toolbar_container (698px)
        // must be rejected in favor of stableToolbarBottom (242px).
        val collapsingContainerBottom = 698

        val top = YouTubeFeedGeometry.calculateTop(
            windowTop = windowTop,
            statusBarInset = statusBarInset,
            toolbarBottom = null,
            toolbarContainerBottom = collapsingContainerBottom,
            dp56 = dp56,
            dp16 = dp16
        )

        // Stable toolbar bottom: 0 + 98 + 144 = 242
        assertEquals(242, top)
    }

    @Test
    fun missingToolbarFallsBackToStableStatusBarPlus56dp() {
        val top = YouTubeFeedGeometry.calculateTop(
            windowTop = windowTop,
            statusBarInset = statusBarInset,
            toolbarBottom = null,
            toolbarContainerBottom = null,
            dp56 = dp56,
            dp16 = dp16
        )

        assertEquals(242, top)
    }

    @Test
    fun bottomBarCalculatedCorrectly() {
        val bottomBarTop = 2151

        val bottom = YouTubeFeedGeometry.calculateBottom(
            windowTop = windowTop,
            windowBottom = windowBottom,
            bottomBarTop = bottomBarTop,
            dp56 = dp56,
            dp48 = dp48
        )

        assertEquals(2151, bottom)
    }

    @Test
    fun missingBottomBarFallsBackToWindowMinus48dp() {
        val bottom = YouTubeFeedGeometry.calculateBottom(
            windowTop = windowTop,
            windowBottom = windowBottom,
            bottomBarTop = null,
            dp56 = dp56,
            dp48 = dp48
        )

        assertEquals(windowBottom - dp48, bottom)
    }

    @Test
    fun onlyProtectedDestinationsMayUpdateReturnBounds() {
        assertTrue(YouTubeFeedGeometry.shouldUpdateReturnBounds(YouTubeNavigationDestination.HOME))
        assertTrue(YouTubeFeedGeometry.shouldUpdateReturnBounds(YouTubeNavigationDestination.SUBSCRIPTIONS))
        assertFalse(YouTubeFeedGeometry.shouldUpdateReturnBounds(YouTubeNavigationDestination.YOU))
        assertFalse(YouTubeFeedGeometry.shouldUpdateReturnBounds(YouTubeNavigationDestination.SHORTS))
        assertFalse(YouTubeFeedGeometry.shouldUpdateReturnBounds(YouTubeNavigationDestination.UNKNOWN))
    }

    @Test
    fun oversizedPrimaryToolbarCannotLeaveAnExposedFeedStrip() {
        assertEquals(242, top(toolbar = 698, container = null))
        assertEquals(242, top(toolbar = 270, container = null))
        assertEquals(242, top(toolbar = null, container = 270))
    }

    @Test
    fun offscreenPrimaryToolbarFallsBackToVerifiedContainer() {
        assertEquals(230, top(toolbar = -10, container = 230))
        assertEquals(230, top(toolbar = 4000, container = 230))
        assertEquals(242, top(toolbar = -10, container = -20))
    }

    @Test
    fun rootsInsideOrBelowStatusBarDoNotDoubleCountInsets() {
        assertEquals(242, top(rootTop = 50))
        assertEquals(242, top(rootTop = 98))
        assertEquals(344, top(rootTop = 200))
        assertEquals(144, top(inset = -10))
    }

    @Test
    fun staleBottomBarsFallBackWithinTheLiveWindow() {
        for (bar in listOf(-50, 0, 200, 2400, 3000)) {
            assertEquals(2274, YouTubeFeedGeometry.calculateBottom(
                windowTop, windowBottom, bar, dp56, dp48
            ))
        }
    }

    @Test
    fun invalidAndTinyRootsDoNotProduceOffscreenOrArtificialOnePixelCurtains() {
        assertNull(viewport(Rect(0, 0, 0, 2400)))
        assertNull(viewport(Rect(100, 0, 0, 2400)))
        assertNull(viewport(Rect(0, 2400, 1080, 2400)))
        assertNull(viewport(Rect(0, 300, 1080, 200)))
        assertNull(viewport(Rect(0, 0, 1080, 100)))
        assertEquals(50, YouTubeFeedGeometry.calculateBottom(50, 50, null, dp56, dp48))
    }

    @Test
    fun viewportUsesOnlyCurrentRootAndRejectsOutgoingAccountGeometry() {
        assertEquals(Rect(100, 344, 900, 1674), viewport(
            Rect(100, 200, 900, 1800), toolbar = 698, bottomBar = 2151
        ))
        assertEquals(Rect(0, 242, 1080, 2274), viewport(
            Rect(0, 0, 1080, 2400), toolbar = 698, bottomBar = null
        ))
    }

    @Test
    fun landscapeSubscriptionsFeedStartsBelowToolbar() {
        assertEquals(Rect(0, 234, 2340, 891), YouTubeFeedGeometry.calculateFeedViewport(
            rootBounds = Rect(0, 0, 2340, 1080),
            statusBarInset = 202,
            toolbarBottom = 234,
            toolbarContainerBottom = 234,
            bottomBarTop = 891,
            dp56 = 144,
            dp48 = 126,
            dp16 = 42
        ))
    }

    private fun top(
        rootTop: Int = windowTop,
        inset: Int = statusBarInset,
        toolbar: Int? = null,
        container: Int? = null
    ) = YouTubeFeedGeometry.calculateTop(rootTop, inset, toolbar, container, dp56, dp16)

    private val window = Rect(0, 0, 1080, 2340)
    private val curtain = Rect(0, 242, 1080, 2151)

    @Test
    fun bottomDockedMiniplayerKeepsEveryFeedPixelCovered() {
        val player = Rect(480, 1803, 1056, 2127)
        val segments = YouTubeFeedGeometry.curtainSegments(curtain, player, tolerancePx = 72)

        assertEquals(Rect(0, 242, 1080, 1803), segments[0])
        assertTrue(segments.contains(Rect(0, 1803, 480, 2151)))
        assertTrue(segments.contains(Rect(1056, 1803, 1080, 2151)))
        assertTrue(segments.contains(Rect(480, 2127, 1056, 2151)))
        assertEquals(curtainArea(), segments.sumOf { area(it) } + area(player))
        assertTrue(segments.none { Rect.intersects(it, player) })
    }

    @Test
    fun topDockedMiniplayerKeepsEveryFeedPixelCovered() {
        val player = Rect(480, 123, 1056, 447)
        val segments = YouTubeFeedGeometry.curtainSegments(curtain, player, tolerancePx = 72)

        assertEquals(Rect(0, 447, 1080, 2151), segments[0])
        assertTrue(segments.contains(Rect(0, 242, 480, 447)))
        assertTrue(segments.contains(Rect(1056, 242, 1080, 447)))
        assertTrue(segments.none { Rect.intersects(it, player) })
        val visiblePlayer = Rect(player).apply { intersect(curtain) }
        assertEquals(curtainArea(), segments.sumOf { area(it) } + area(visiblePlayer))
    }

    @Test
    fun floatingOrMissingMiniplayerLeavesTheCurtainWhole() {
        assertEquals(listOf(curtain), YouTubeFeedGeometry.curtainSegments(curtain, null))
        assertEquals(listOf(curtain),
            YouTubeFeedGeometry.curtainSegments(curtain, Rect(480, 1000, 1056, 1324), 72))
    }

    @Test
    fun expandedPlayerIsNeverAMiniplayerCutout() {
        assertNull(YouTubeFeedGeometry.miniplayerCutout(Rect(0, 98, 1080, 1400), window = window))
        assertNull(YouTubeFeedGeometry.miniplayerCutout(null, window = window))
        assertEquals(Rect(480, 1803, 1056, 2127),
            YouTubeFeedGeometry.miniplayerCutout(Rect(480, 1803, 1056, 2127), window = window))
    }

    private fun area(rect: Rect) = rect.width().toLong() * rect.height()
    private fun curtainArea() = area(curtain)

    private fun viewport(root: Rect, toolbar: Int? = null, bottomBar: Int? = null) =
        YouTubeFeedGeometry.calculateFeedViewport(
            root, statusBarInset, toolbar, null, bottomBar, dp56, dp48, dp16
        )
}
