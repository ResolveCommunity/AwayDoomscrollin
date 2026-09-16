package com.resolvecommunity.awaydoomscrollin

internal object InstagramSurfacePolicy {
    fun needsCurtain(screen: InstagramScreen): Boolean =
        screen == InstagramScreen.HOME_FEED || screen == InstagramScreen.EXPLORE ||
            screen == InstagramScreen.PROFILE || screen == InstagramScreen.DIRECT_SHARED_MEDIA

    fun needsExit(screen: InstagramScreen): Boolean =
        screen == InstagramScreen.REELS || screen == InstagramScreen.COMMENTS_OR_DETAIL

    /**
     * Instagram rebuilds a large clips/player subtree when an embedded Home
     * video ends. During that mutation the Home header and selected tab can
     * both disappear for a frame. A mounted, touch-consuming Home curtain plus
     * the existing Home state are stronger evidence than that transient tree.
     * A real user launch is allowed through so the normal viewer exit can run.
     */
    fun shouldKeepVerifiedHome(
        currentScreen: InstagramScreen,
        homeCurtainVisible: Boolean,
        sameWindow: Boolean,
        hasFreshUserInput: Boolean
    ): Boolean = currentScreen == InstagramScreen.HOME_FEED &&
        homeCurtainVisible && sameWindow && !hasFreshUserInput
}
