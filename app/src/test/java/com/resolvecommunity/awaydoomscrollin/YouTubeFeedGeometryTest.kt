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

    @Test
    fun `an unverified frame never lowers the curtain under a collapsing toolbar`() {
        // Device log: toolbar collapsed, curtain at top 129; the next frame
        // lost the half-hidden logo and proposed the default top 266.
        val mounted = Rect(0, 129, 1080, 2151)
        val proposed = Rect(0, 266, 1080, 2151)
        assertEquals(Rect(0, 129, 1080, 2151),
            YouTubeFeedGeometry.retainCurtainTop(proposed, mounted, frameVerified = false))
    }

    @Test
    fun `a feed whose toolbar scrolled away is covered from the status bar`() {
        // Device dump: no toolbar node at all, results [0,0][1080,2340].
        val root = Rect(0, windowTop, 1080, windowBottom)
        val collapsed = YouTubeFeedGeometry.calculateFeedViewport(
            root, statusBarInset, null, null, 2151, dp56, dp48, dp16, toolbarScrolledAway = true)
        assertEquals(statusBarInset, collapsed?.top)
        // A toolbar that is only loading keeps the default toolbar height.
        assertEquals(statusBarInset + dp56, viewport(root, bottomBar = 2151)?.top)
        // A visible toolbar always wins.
        val visible = YouTubeFeedGeometry.calculateFeedViewport(
            root, statusBarInset, 242, null, 2151, dp56, dp48, dp16, toolbarScrolledAway = true)
        assertEquals(242, visible?.top)
    }

    @Test
    fun `a verified feed frame and an upward move apply as proposed`() {
        val mounted = Rect(0, 129, 1080, 2151)
        // The toolbar came back: a verified Home frame lowers the curtain.
        assertEquals(Rect(0, 242, 1080, 2151), YouTubeFeedGeometry.retainCurtainTop(
            Rect(0, 242, 1080, 2151), mounted, frameVerified = true))
        // Raising the curtain never exposes anything.
        assertEquals(Rect(0, 98, 1080, 2151), YouTubeFeedGeometry.retainCurtainTop(
            Rect(0, 98, 1080, 2151), mounted, frameVerified = false))
        assertEquals(Rect(0, 266, 1080, 2151), YouTubeFeedGeometry.retainCurtainTop(
            Rect(0, 266, 1080, 2151), null, frameVerified = false))
        assertNull(YouTubeFeedGeometry.retainCurtainTop(null, mounted, frameVerified = false))
    }

    @Test
    fun dragFromBottomBarHidingBothBarsCoversTheWholeFeed() {
        // S23 Home after a drag that started on the bottom tabs: no toolbar,
        // no bottom bar, no selected tab. The curtain stopped at 242 and 2196
        // and the feed showed through both strips.
        val toolbarGone = YouTubeFeedGeometry.toolbarScrolledAway(
            toolbarVisible = false, bottomBarVisible = false,
            destination = YouTubeNavigationDestination.UNKNOWN)
        val barGone = YouTubeFeedGeometry.bottomBarScrolledAway(
            bottomBarVisible = false)
        assertTrue(toolbarGone)
        assertTrue(barGone)
        val curtain = YouTubeFeedGeometry.calculateFeedViewport(
            Rect(0, 0, 1080, 2340), statusBarInset, null, null, null, dp56, dp48, dp16,
            toolbarScrolledAway = toolbarGone, bottomBarScrolledAway = barGone,
            tappableBottomInset = 0
        )
        assertEquals(Rect(0, statusBarInset, 1080, 2340), curtain)
    }

    @Test
    fun hiddenBottomBarStillLeavesThreeButtonNavigationUsable() {
        val curtain = YouTubeFeedGeometry.calculateFeedViewport(
            Rect(0, 0, 1080, 2340), statusBarInset, null, null, null, dp56, dp48, dp16,
            toolbarScrolledAway = true, bottomBarScrolledAway = true,
            tappableBottomInset = 126
        )
        assertEquals(2340 - 126, curtain?.bottom)
    }

    @Test
    fun tabsGoneWhileToolbarSlidesOutStillCoverTheBottom() {
        // Polish S23, repeated flings: the toolbar was still in the tree while
        // the tabs were gone, and the curtain stopped 48dp short at 2196.
        val curtain = YouTubeFeedGeometry.calculateFeedViewport(
            Rect(0, 0, 1080, 2340), statusBarInset, 242, null, null, dp56, dp48, dp16,
            bottomBarScrolledAway = YouTubeFeedGeometry.bottomBarScrolledAway(bottomBarVisible = false),
            tappableBottomInset = 0
        )
        assertEquals(2340, curtain?.bottom)
    }

    @Test
    fun visibleBottomBarIsNeverTreatedAsScrolledAway() {
        // The toolbar alone collapses on an ordinary feed scroll; the bottom
        // tabs stay and the curtain keeps stopping at them.
        assertFalse(YouTubeFeedGeometry.bottomBarScrolledAway(
            bottomBarVisible = true))
        assertFalse(YouTubeFeedGeometry.toolbarScrolledAway(
            toolbarVisible = false, bottomBarVisible = true,
            destination = YouTubeNavigationDestination.YOU))
        assertTrue(YouTubeFeedGeometry.toolbarScrolledAway(
            toolbarVisible = false, bottomBarVisible = true,
            destination = YouTubeNavigationDestination.HOME))
        assertFalse(YouTubeFeedGeometry.toolbarScrolledAway(
            toolbarVisible = true, bottomBarVisible = false,
            destination = YouTubeNavigationDestination.UNKNOWN))
    }

    private fun viewport(root: Rect, toolbar: Int? = null, bottomBar: Int? = null) =
        YouTubeFeedGeometry.calculateFeedViewport(
            root, statusBarInset, toolbar, null, bottomBar, dp56, dp48, dp16
        )
}
