package com.resolvecommunity.awaydoomscrollin

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeUiIdentityTest {
    @Test
    fun `notification comments and replies are allowed only as settled toolbar titles`() {
        for (title in listOf("Comments", "Yorumlar", "Replies", "Yanıtlar")) {
            // Actual notification comment page: title [180,129][475,210], window 1080x2340.
            assertTrue(YouTubeUiIdentity.isSettledSecondaryToolbarTitle(
                title, false, 129, 2340, 180, 475, 0, 1080, 24))
            assertFalse(YouTubeUiIdentity.isSettledSecondaryToolbarTitle(
                title, true, 129, 2340, 180, 475, 0, 1080, 24))
            assertFalse(YouTubeUiIdentity.isSettledSecondaryToolbarTitle(
                title, false, 900, 2340, 180, 475, 0, 1080, 24))
            assertFalse(YouTubeUiIdentity.isSettledSecondaryToolbarTitle(
                title, false, 129, 2340, 900, 1195, 0, 1080, 24))
        }
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
    fun `Notifications toolbar identity is allowed`() {
        assertTrue(YouTubeUiIdentity.isAllowedSecondarySurfaceId("menu_search"))
        assertTrue(
            YouTubeUiIdentity.isAllowedSecondaryToolbarTitle(
                rawText = "Notifications",
                isClickable = false,
                top = 129,
                windowHeight = 2_340,
                left = 180,
                right = 527,
                windowLeft = 0,
                windowRight = 1_080
            )
        )
    }

    @Test
    fun `clickable Home bell is not mistaken for Notifications surface title`() {
        assertFalse(
            YouTubeUiIdentity.isAllowedSecondaryToolbarTitle(
                rawText = "Notifications",
                isClickable = true,
                top = 98,
                windowHeight = 2_340,
                left = 792,
                right = 936,
                windowLeft = 0,
                windowRight = 1_080
            )
        )
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
    fun `outgoing Notifications title outside viewport is ignored`() {
        assertFalse(
            YouTubeUiIdentity.isAllowedSecondaryToolbarTitle(
                rawText = "Notifications",
                isClickable = false,
                top = 129,
                windowHeight = 2_340,
                left = 1_120,
                right = 1_467,
                windowLeft = 0,
                windowRight = 1_080
            )
        )
    }

    @Test
    fun `YouTube back controls use exact localized labels`() {
        assertTrue(YouTubeUiIdentity.isBackNavigationLabel("Navigate up", null))
        assertTrue(YouTubeUiIdentity.isBackNavigationLabel(null, "Geri"))
        assertTrue(YouTubeUiIdentity.isBackNavigationLabel("Yukar\u0131 git", null))
        assertTrue(YouTubeUiIdentity.isBackNavigationLabel("kapat", null))
        assertFalse(YouTubeUiIdentity.isBackNavigationLabel("Backstage video", null))
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

    @Test
    fun `notifications toolbar title sliding to the right during back is rejected`() {
        assertFalse(
            YouTubeUiIdentity.isAllowedSecondaryToolbarTitle(
                rawText = "Notifications",
                isClickable = false,
                top = 129,
                windowHeight = 2_340,
                left = 400, // Shifted right > windowLeft + windowWidth / 3
                right = 900,
                windowLeft = 0,
                windowRight = 1_080
            )
        )
    }

    @Test
    fun `notifications toolbar title matches unread badges and Turkish variations`() {
        assertTrue(
            YouTubeUiIdentity.isAllowedSecondaryToolbarTitle(
                rawText = "Bildirimler (3)",
                isClickable = false,
                top = 129,
                windowHeight = 2_340,
                left = 180,
                right = 527,
                windowLeft = 0,
                windowRight = 1_080
            )
        )
    }

    @Test
    fun `settled secondary toolbar title confirms active notifications screen`() {
        assertTrue(
            YouTubeUiIdentity.isSettledSecondaryToolbarTitle(
                rawText = "Notifications",
                isClickable = false,
                top = 129,
                windowHeight = 2_340,
                left = 180,
                right = 527,
                windowLeft = 0,
                windowRight = 1_080,
                tolerancePx = 48
            )
        )
    }

    @Test
    fun `departing secondary toolbar title sliding right is rejected`() {
        // Shifted right beyond max allowed resting anchor
        assertFalse(
            YouTubeUiIdentity.isSettledSecondaryToolbarTitle(
                rawText = "Notifications",
                isClickable = false,
                top = 129,
                windowHeight = 2_340,
                left = 350,
                right = 697,
                windowLeft = 0,
                windowRight = 1_080,
                tolerancePx = 48
            )
        )
        // Translated offscreen to the right
        assertFalse(
            YouTubeUiIdentity.isSettledSecondaryToolbarTitle(
                rawText = "Notifications",
                isClickable = false,
                top = 129,
                windowHeight = 2_340,
                left = 200,
                right = 1_150,
                windowLeft = 0,
                windowRight = 1_080,
                tolerancePx = 48
            )
        )
    }

    @Test
    fun `settled leading back control confirms resting back arrow`() {
        assertTrue(
            YouTubeUiIdentity.isSettledLeadingBackControl(
                rawDescription = "Navigate up",
                rawText = null,
                left = 0,
                right = 168,
                windowLeft = 0,
                windowRight = 1_080,
                tolerancePx = 48
            )
        )
        assertTrue(
            YouTubeUiIdentity.isSettledLeadingBackControl(
                rawDescription = null,
                rawText = "Yukarı git",
                left = 20,
                right = 188,
                windowLeft = 0,
                windowRight = 1_080,
                tolerancePx = 48
            )
        )
    }

    @Test
    fun `departing leading back control sliding right is rejected`() {
        // Shifted away from left edge during back animation
        assertFalse(
            YouTubeUiIdentity.isSettledLeadingBackControl(
                rawDescription = "Navigate up",
                rawText = null,
                left = 100,
                right = 268,
                windowLeft = 0,
                windowRight = 1_080,
                tolerancePx = 48
            )
        )
    }
}
