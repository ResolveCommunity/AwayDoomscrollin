package com.resolvecommunity.awaydoomscrollin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Values come from scroll events and uiautomator dumps of TikTok 47.4.3 on a
 * 1080x2340 screen with a French UI. No label or obfuscated id is involved, so
 * the same values hold for every language.
 */
class TikTokSurfacePolicyTest {
    private val screenWidth = 1080
    private val screenHeight = 2340
    private val viewPager = "androidx.viewpager.widget.ViewPager"

    private fun swipe(
        className: String?,
        id: String?,
        vertical: Boolean,
        width: Int,
        height: Int,
        top: Int = 0
    ) = TikTokSurfacePolicy.isFeedSwipe(
        className, id, vertical, top, width, height, screenWidth, screenHeight)

    @Test
    fun `vertical scroll of the fullscreen video pager is a feed swipe`() {
        // For You / Following pager: bounds [0,0][1080,2148], dy=626.
        assertTrue(swipe(viewPager, "viewpager", vertical = true, width = 1080, height = 2148))
    }

    @Test
    fun `a renamed pager class is still recognized by its id, and the reverse`() {
        assertTrue(swipe("X.05rH", "viewpager", vertical = true, width = 1080, height = 2148))
        assertTrue(swipe(viewPager, "q9x", vertical = true, width = 1080, height = 2148))
    }

    @Test
    fun `horizontal top tab swipes never exit`() {
        // Top-tab pager: class X.05rH, id viewpager, bounds [0,0][1080,2295], dx=-279 dy=0.
        assertFalse(swipe("X.05rH", "viewpager", vertical = false, width = 1080, height = 2295))
    }

    @Test
    fun `lists, grids and sheets never exit`() {
        // Profile tab pager [0,958][1080,2295]: 57% of the screen.
        assertFalse(swipe(viewPager, "v_y", vertical = true, width = 1080, height = 1337, top = 958))
        // Search results pager [0,374][1080,2295] starts below the search tabs.
        assertFalse(swipe(viewPager, "viewpager_search", vertical = true,
            width = 1080, height = 1921, top = 374))
        // Comment sheet, message list, search results and profile grid are RecyclerViews.
        assertFalse(swipe("androidx.recyclerview.widget.RecyclerView", "jwa",
            vertical = true, width = 1080, height = 2148))
        assertFalse(swipe("androidx.recyclerview.widget.RecyclerView", null,
            vertical = true, width = 1080, height = 1400))
    }

    @Test
    fun `a recycled source with empty bounds is not a feed swipe`() {
        assertFalse(swipe(viewPager, "viewpager", vertical = true, width = 0, height = 2148))
        assertFalse(TikTokSurfacePolicy.isFeedPagerBounds(0, 1080, 2148, 0, 0))
        assertFalse(TikTokSurfacePolicy.isFeedPagerBounds(-1, 1080, 2148, screenWidth, screenHeight))
    }

    @Test
    fun `the top tab strip sits near the top of the screen`() {
        assertTrue(TikTokSurfacePolicy.isTopTabStrip(top = 98, screenHeight = screenHeight))
        assertFalse(TikTokSurfacePolicy.isTopTabStrip(top = 900, screenHeight = screenHeight))
    }

    @Test
    fun `every other Home top tab snaps back to For You, the last tab`() {
        // "Communauté, Suivis, Pour toi" with Suivis selected.
        assertEquals(2, TikTokSurfacePolicy.snapBackTarget(1, 3, homeTabSelected = true))
        // "Сообщество" is a post grid, not a video pager; it is undone too.
        assertEquals(2, TikTokSurfacePolicy.snapBackTarget(0, 3, homeTabSelected = true))
        // "Communauté, Nord, Suivis, Boutique, Pour toi" with Boutique selected.
        assertEquals(4, TikTokSurfacePolicy.snapBackTarget(3, 5, homeTabSelected = true))
    }

    @Test
    fun `For You itself and strips outside Home are left alone`() {
        assertNull(TikTokSurfacePolicy.snapBackTarget(2, 3, homeTabSelected = true))
        // Search results: "Top, Utilisateurs, Vidéos, ..." with no bottom bar.
        assertNull(TikTokSurfacePolicy.snapBackTarget(0, 8, homeTabSelected = false))
        assertNull(TikTokSurfacePolicy.snapBackTarget(-1, 3, homeTabSelected = true))
        assertNull(TikTokSurfacePolicy.snapBackTarget(0, 1, homeTabSelected = true))
    }

    @Test
    fun `the bottom bar is a full-width row of items along the bottom edge`() {
        // [0,2148][1080,2295] with Home, Friends, Create, Inbox, Profile.
        assertTrue(TikTokSurfacePolicy.isBottomBar(2148, 1080, 5, screenWidth, screenHeight))
        // The profile's tab row [0,836][1080,956] is not at the bottom.
        assertFalse(TikTokSurfacePolicy.isBottomBar(836, 1080, 4, screenWidth, screenHeight))
        // A message composer row at the bottom has fewer items.
        assertFalse(TikTokSurfacePolicy.isBottomBar(2148, 1080, 3, screenWidth, screenHeight))
    }
}
