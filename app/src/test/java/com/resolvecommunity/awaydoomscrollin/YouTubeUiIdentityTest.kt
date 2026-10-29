package com.resolvecommunity.awaydoomscrollin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Geometry comes from uiautomator dumps of YouTube 21.42 on a 1080x2340 window
 * (French, Turkish and English UI). Titles and Back are matched by shape only,
 * so the localized words below never take part in a decision.
 */
class YouTubeUiIdentityTest {
    private val textView = "android.widget.TextView"
    private val imageButton = "android.widget.ImageButton"

    private fun settledTitle(
        text: String?,
        className: String = textView,
        clickable: Boolean = false,
        top: Int = 129,
        left: Int = 180,
        right: Int = 527
    ) = YouTubeUiIdentity.isSettledToolbarTitle(
        className, text, clickable, top, 2_340, left, right, 0, 1_080, 48)

    @Test
    fun `a settled toolbar title is recognized in any language`() {
        // Notifications page: title [180,129][527,210] beside Back [0,98][168,242].
        for (title in listOf("Notifications", "Bildirimler (3)", "Уведомления", "Commentaires", "履歴")) {
            assertTrue(title, settledTitle(title))
        }
    }

    @Test
    fun `clickable, empty, low or non-text nodes are not titles`() {
        assertFalse(settledTitle("Notifications", clickable = true))
        assertFalse(settledTitle(""))
        assertFalse(settledTitle(null))
        assertFalse(settledTitle("Notifications", top = 900))
        // The Home bell is a clickable ImageView at [792,98][936,242].
        assertFalse(settledTitle("Notifications", className = "android.widget.ImageView",
            left = 792, right = 936))
    }

    @Test
    fun `a title sliding away during Back is not settled`() {
        assertFalse(settledTitle("Notifications", left = 350, right = 697))
        assertFalse(settledTitle("Notifications", left = 200, right = 1_150))
        assertFalse(settledTitle("Notifications", left = 1_120, right = 1_467))
        assertFalse(YouTubeUiIdentity.isToolbarTitle(
            textView, "Notifications", false, 129, 2_340, 400, 900, 0, 1_080))
    }

    @Test
    fun `Back is the leading toolbar ImageButton`() {
        // "Revenir en haut de la page" / "Navigate up" / "Yukarı git": [0,98][168,242].
        assertTrue(YouTubeUiIdentity.isLeadingToolbarButton(imageButton, true, 0, 168, 0, 1_080, 48))
        assertTrue(YouTubeUiIdentity.isLeadingToolbarButton(imageButton, true, 20, 188, 0, 1_080, 48))
        // Sliding away, too wide, not clickable or another class.
        assertFalse(YouTubeUiIdentity.isLeadingToolbarButton(imageButton, true, 100, 268, 0, 1_080, 48))
        assertFalse(YouTubeUiIdentity.isLeadingToolbarButton(imageButton, true, 0, 400, 0, 1_080, 48))
        assertFalse(YouTubeUiIdentity.isLeadingToolbarButton(imageButton, false, 0, 168, 0, 1_080, 48))
        assertFalse(YouTubeUiIdentity.isLeadingToolbarButton(
            "android.widget.ImageView", true, 0, 168, 0, 1_080, 48))
    }

    @Test
    fun `feed toolbar launch buttons are named by their slot`() {
        // Home and Subscriptions: menu_item_0 Notifications, menu_item_1 Search.
        assertEquals(YouTubeCurtainScreen.NOTIFICATIONS, YouTubeUiIdentity.feedToolbarLaunchScreen(0, 1))
        assertEquals(YouTubeCurtainScreen.SEARCH, YouTubeUiIdentity.feedToolbarLaunchScreen(1, 1))
        // With a Cast button first: Cast, Notifications, Search.
        assertNull(YouTubeUiIdentity.feedToolbarLaunchScreen(0, 2))
        assertEquals(YouTubeCurtainScreen.NOTIFICATIONS, YouTubeUiIdentity.feedToolbarLaunchScreen(1, 2))
        assertNull(YouTubeUiIdentity.feedToolbarLaunchScreen(-1, 1))
    }

    @Test
    fun `menu item slots are read from their ids`() {
        assertEquals(0, YouTubeUiIdentity.menuItemIndex("menu_item_0"))
        assertEquals(2, YouTubeUiIdentity.menuItemIndex("menu_item_2"))
        assertEquals(-1, YouTubeUiIdentity.menuItemIndex("menu_item_view"))
        assertEquals(-1, YouTubeUiIdentity.menuItemIndex("toolbar"))
    }

    @Test
    fun `a titled page reached from a feed is Notifications`() {
        fun notifications(
            from: YouTubeCurtainScreen,
            requested: Boolean = false,
            visit: Boolean = false,
            titled: Boolean = true,
            structural: Boolean = false
        ) = YouTubeUiIdentity.isNotificationsPage(titled, structural, requested, from, visit)

        assertTrue(notifications(YouTubeCurtainScreen.HOME))
        assertTrue(notifications(YouTubeCurtainScreen.SUBSCRIPTIONS))
        assertTrue(notifications(YouTubeCurtainScreen.UNKNOWN, requested = true))
        // Back from a channel opened in Notifications.
        assertTrue(notifications(YouTubeCurtainScreen.OTHER_ALLOWED, visit = true))
        // History or Playlists opened from the You tab.
        assertFalse(notifications(YouTubeCurtainScreen.YOU))
        // Channel and watch pages keep their own identity.
        assertFalse(notifications(YouTubeCurtainScreen.HOME, structural = true))
        assertFalse(notifications(YouTubeCurtainScreen.HOME, titled = false))
    }

    @Test
    fun `real results toolbar query remains authoritative beside microphone and overflow`() {
        assertTrue(YouTubeUiIdentity.isSettledSearchSurface("search_query", 168, 672, 0, 1080, 24))
        assertTrue(YouTubeUiIdentity.isSettledSearchSurface("search_box", 168, 816, 0, 1080, 24))
        assertFalse(YouTubeUiIdentity.isSettledSearchSurface("search_query", 600, 1104, 0, 1080, 24))
    }

    @Test
    fun `normal watch page remains allowed while its video or preroll ad plays`() {
        assertTrue(YouTubeUiIdentity.isConfirmedWatchSurface(
            watchPlayerVisible = true,
            watchPanelVisible = true,
            inlinePlayerVisible = false
        ))
    }

    @Test
    fun `Home embedded player cannot uncover a protected feed`() {
        assertFalse(YouTubeUiIdentity.isConfirmedWatchSurface(false, false, true))
        // An outgoing watch tree can briefly coexist with the live inline preview.
        assertFalse(YouTubeUiIdentity.isConfirmedWatchSurface(true, true, true))
    }

    @Test
    fun `retained empty watch container alone does not prove video playback`() {
        assertFalse(YouTubeUiIdentity.isConfirmedWatchSurface(false, false, false))
    }

    @Test
    fun `partial watch page during navigation does not release protection`() {
        assertFalse(YouTubeUiIdentity.isConfirmedWatchSurface(true, false, false))
        assertFalse(YouTubeUiIdentity.isConfirmedWatchSurface(false, true, false))
    }

    @Test
    fun `Notifications never sends a second Back merely because its toolbar still animates`() {
        assertFalse(YouTubeUiIdentity.shouldRetryBackAfterIme(
            startedOnSearch = false,
            inputMethodWasVisible = false,
            searchStillVisible = true
        ))
    }

    @Test
    fun `Search only retries Back when a keyboard was present and Search remains`() {
        assertFalse(YouTubeUiIdentity.shouldRetryBackAfterIme(true, false, true))
        assertFalse(YouTubeUiIdentity.shouldRetryBackAfterIme(true, true, false))
        assertTrue(YouTubeUiIdentity.shouldRetryBackAfterIme(true, true, true))
    }

    @Test
    fun `current and legacy search ids are recognized`() {
        assertTrue(YouTubeUiIdentity.isSearchSurfaceId("search_box"))
        assertTrue(YouTubeUiIdentity.isSearchSurfaceId("search_edit_text"))
        assertTrue(YouTubeUiIdentity.isSearchSurfaceId("search_layout"))
        assertTrue(YouTubeUiIdentity.isSearchSurfaceId("search_query"))
    }

    @Test
    fun `Home toolbar search icon is not mistaken for an open search surface`() {
        assertFalse(YouTubeUiIdentity.isSearchSurfaceId("menu_item_view"))
    }

    @Test
    fun `Home logo is authoritative only while meaningfully on screen`() {
        assertTrue(YouTubeUiIdentity.isHomeToolbarLogo(0, 366, 0, 1_080, 216))
        assertFalse(YouTubeUiIdentity.isHomeToolbarLogo(-300, 66, 0, 1_080, 216))
        assertFalse(YouTubeUiIdentity.isHomeToolbarLogo(920, 1_286, 0, 1_080, 216))
    }

    @Test
    fun `You profile header confirms the selected allowed tab`() {
        fun confirmed(
            destination: YouTubeNavigationDestination,
            homeToolbar: Boolean,
            left: Int = 0,
            right: Int = 1_080
        ) = YouTubeUiIdentity.isConfirmedYouSurface(
            destination, homeToolbar, left, 98, right, 698, 0, 0, 1_080, 2_340
        )

        assertTrue(confirmed(YouTubeNavigationDestination.YOU, false))
        assertFalse(confirmed(YouTubeNavigationDestination.HOME, false))
        assertFalse(confirmed(YouTubeNavigationDestination.YOU, true))
        assertFalse(confirmed(YouTubeNavigationDestination.YOU, false, 1_000, 1_080))
    }

    @Test
    fun `Notifications toolbar search button is an allowed secondary identity`() {
        assertTrue(YouTubeUiIdentity.isAllowedSecondarySurfaceId("menu_search"))
    }

    @Test
    fun `wide active search geometry is authoritative`() {
        assertTrue(
            YouTubeUiIdentity.hasMeaningfulHorizontalPresence(
                left = 168,
                right = 960,
                windowLeft = 0,
                windowRight = 1_080
            )
        )
    }

    @Test
    fun `outgoing search sliver is not authoritative`() {
        assertFalse(
            YouTubeUiIdentity.hasMeaningfulHorizontalPresence(
                left = 1_000,
                right = 1_080,
                windowLeft = 0,
                windowRight = 1_080
            )
        )
    }

    @Test
    fun `settled search surface confirms active resting state`() {
        assertTrue(
            YouTubeUiIdentity.isSettledSearchSurface(
                compactId = "search_box",
                left = 168,
                right = 960,
                windowLeft = 0,
                windowRight = 1_080,
                tolerancePx = 48
            )
        )
    }

    @Test
    fun `departing search surface sliding to the right is rejected`() {
        // Exceeding window right bound
        assertFalse(
            YouTubeUiIdentity.isSettledSearchSurface(
                compactId = "search_box",
                left = 300,
                right = 1_200,
                windowLeft = 0,
                windowRight = 1_080,
                tolerancePx = 48
            )
        )
        // Left shifted too far to the right
        assertFalse(
            YouTubeUiIdentity.isSettledSearchSurface(
                compactId = "search_box",
                left = 500,
                right = 1_000,
                windowLeft = 0,
                windowRight = 1_080,
                tolerancePx = 48
            )
        )
        // Slight shift right (+45px from resting 168) during back gesture is rejected
        assertFalse(
            YouTubeUiIdentity.isSettledSearchSurface(
                compactId = "search_box",
                left = 215,
                right = 1_000,
                windowLeft = 0,
                windowRight = 1_080,
                tolerancePx = 48
            )
        )
        // search_layout departing right is rejected
        assertFalse(
            YouTubeUiIdentity.isSettledSearchSurface(
                compactId = "search_layout",
                left = 60,
                right = 1_140,
                windowLeft = 0,
                windowRight = 1_080,
                tolerancePx = 48
            )
        )
    }
}
