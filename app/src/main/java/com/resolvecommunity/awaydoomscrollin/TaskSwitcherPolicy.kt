package com.resolvecommunity.awaydoomscrollin

/**
 * Detects the system task switcher (Recents) while a protected app still owns
 * the active accessibility window.
 *
 * Pixel's Quickstep keeps the last app "live" inside its Recents card: the
 * app's window stays active, focused and full size while the launcher draws
 * the switcher around it, so an active-root package check never sees the
 * departure and the curtains stay at full-screen coordinates over the cards.
 * A fullscreen protected app hides the launcher, so a visible home-app window
 * behind a still fullscreen protected window is the language-free signal.
 *
 * A protected app in a pop-up window over the home screen keeps its
 * protection. Samsung's pop-up view can cover 95 % of the screen (seen:
 * 0,98-1026,2321 on a 1080x2340 S23), so size alone is not enough: a pop-up
 * starts below the status bar and leaves a side edge, a fullscreen window
 * (and Pixel's live card) starts at the display's top-left corner.
 */
internal object TaskSwitcherPolicy {
    /** Settings' FallbackHome answers the HOME intent but is no task switcher. */
    private val nonSwitcherHomePackages = setOf("com.android.settings")

    /** Minimum share of the display height the active window must cover (system bars). */
    private const val FULLSCREEN_RATIO = 0.9

    /** How far a fullscreen window's edges may sit from the display's edges. */
    private const val EDGE_TOLERANCE_RATIO = 0.01

    fun switcherPackages(homePackages: Collection<String>): Set<String> =
        homePackages.filterTo(mutableSetOf()) { it !in nonSwitcherHomePackages }

    fun isFullscreen(
        left: Int, top: Int, right: Int, bottom: Int,
        displayWidth: Int, displayHeight: Int
    ): Boolean {
        if (displayWidth <= 0 || displayHeight <= 0) return false
        val edge = maxOf(displayWidth, displayHeight) * EDGE_TOLERANCE_RATIO
        return left <= edge && top <= edge && right >= displayWidth - edge &&
            bottom - top >= displayHeight * FULLSCREEN_RATIO
    }

    fun isShowing(
        activeWindowFullscreen: Boolean,
        visibleApplicationPackages: Collection<String?>,
        switcherPackages: Set<String>
    ): Boolean = activeWindowFullscreen &&
        visibleApplicationPackages.any { it != null && it in switcherPackages }
}
