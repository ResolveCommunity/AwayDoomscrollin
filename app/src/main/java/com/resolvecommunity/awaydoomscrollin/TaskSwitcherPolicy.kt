package com.resolvecommunity.awaydoomscrollin

/**
 * Detects the system task switcher (Recents) while a protected app still owns
 * the active accessibility window.
 *
 * Pixel's Quickstep keeps the last app "live" inside its Recents card: the
 * app's window stays active and focused while the launcher draws the switcher
 * around the scaled task, so an active-root package check never sees the
 * departure and the curtains stay at full-screen coordinates over the cards.
 * A fullscreen protected app hides the launcher, so a visible home-app window
 * behind a still fullscreen protected window is the language-free signal.
 * A protected app in a pop-up window over the home screen is not fullscreen
 * and keeps its protection.
 */
internal object TaskSwitcherPolicy {
    /** Settings' FallbackHome answers the HOME intent but is no task switcher. */
    private val nonSwitcherHomePackages = setOf("com.android.settings")

    /** Minimum share of each display dimension the active window must cover. */
    private const val FULLSCREEN_RATIO = 0.9

    fun switcherPackages(homePackages: Collection<String>): Set<String> =
        homePackages.filterTo(mutableSetOf()) { it !in nonSwitcherHomePackages }

    fun isFullscreen(width: Int, height: Int, displayWidth: Int, displayHeight: Int): Boolean =
        displayWidth > 0 && displayHeight > 0 &&
            width >= displayWidth * FULLSCREEN_RATIO && height >= displayHeight * FULLSCREEN_RATIO

    fun isShowing(
        activeWindowFullscreen: Boolean,
        visibleApplicationPackages: Collection<String?>,
        switcherPackages: Set<String>
    ): Boolean = activeWindowFullscreen &&
        visibleApplicationPackages.any { it != null && it in switcherPackages }
}
