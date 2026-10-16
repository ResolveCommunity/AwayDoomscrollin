package com.resolvecommunity.awaydoomscrollin

/** Temporary geometry only; never grants or revokes access to another screen. */
internal object InstagramNavigationGeometry {
    fun transitionTop(systemTop: Int, searchBottom: Int, instantWindowMoves: Boolean): Int =
        if (instantWindowMoves) maxOf(systemTop, searchBottom) else systemTop

    fun canRestoreSearchChrome(screen: InstagramScreen, sameWindow: Boolean,
        selectedSearch: Boolean): Boolean =
        screen == InstagramScreen.EXPLORE && sameWindow && selectedSearch

    fun top(measuredTop: Int, transitionTop: Int, screen: InstagramScreen,
        sameWindow: Boolean, holdActive: Boolean): Int =
        if ((screen == InstagramScreen.EXPLORE || screen == InstagramScreen.HOME_FEED) &&
            sameWindow && holdActive)
            minOf(measuredTop, transitionTop) else measuredTop
}
