package com.resolvecommunity.awaydoomscrollin

internal enum class InstagramProtectionAction {
    SHOW_CURTAIN,
    ALLOW,
    EXIT,
    KEEP_CURRENT
}

internal data class InstagramProtectionDecision(
    val screen: InstagramScreen,
    val action: InstagramProtectionAction,
    val revision: Long,
    val changed: Boolean
)

/** The single owner of Instagram surface transitions; geometry is deliberately absent. */
internal class InstagramProtectionStateMachine {
    var currentScreen: InstagramScreen = InstagramScreen.UNKNOWN
        private set
    var currentWindowId: Int = -1
        private set
    private var revision = 0L

    fun observe(screen: InstagramScreen, windowId: Int,
        allowChosenReel: Boolean = false,
        allowProfile: Boolean = false,
        allowChosenProfilePost: Boolean = false,
        allowUserCollection: Boolean = false): InstagramProtectionDecision {
        if (screen == InstagramScreen.UNKNOWN) {
            return InstagramProtectionDecision(
                currentScreen, InstagramProtectionAction.KEEP_CURRENT, revision, changed = false)
        }
        val changed = screen != currentScreen || windowId != currentWindowId
        if (changed) revision++
        currentScreen = screen
        currentWindowId = windowId
        val action = when {
            screen == InstagramScreen.REELS && allowChosenReel -> InstagramProtectionAction.ALLOW
            screen == InstagramScreen.PROFILE && allowProfile -> InstagramProtectionAction.ALLOW
            screen == InstagramScreen.SAVED_COLLECTION && allowUserCollection ->
                InstagramProtectionAction.ALLOW
            screen == InstagramScreen.COMMENTS_OR_DETAIL && allowChosenProfilePost ->
                InstagramProtectionAction.ALLOW
            else -> actionFor(screen)
        }
        return InstagramProtectionDecision(screen, action, revision, changed)
    }

    fun leaveInstagram() {
        if (currentScreen != InstagramScreen.UNKNOWN || currentWindowId != -1) revision++
        currentScreen = InstagramScreen.UNKNOWN
        currentWindowId = -1
    }

    fun reset() {
        currentScreen = InstagramScreen.UNKNOWN
        currentWindowId = -1
        revision = 0L
    }

    private fun actionFor(screen: InstagramScreen): InstagramProtectionAction = when (screen) {
        InstagramScreen.HOME_FEED,
        InstagramScreen.EXPLORE,
        InstagramScreen.PROFILE,
        InstagramScreen.DIRECT_SHARED_MEDIA -> InstagramProtectionAction.SHOW_CURTAIN

        InstagramScreen.SAVED_COLLECTION -> InstagramProtectionAction.EXIT

        InstagramScreen.REELS,
        InstagramScreen.COMMENTS_OR_DETAIL -> InstagramProtectionAction.EXIT

        InstagramScreen.UNKNOWN -> InstagramProtectionAction.KEEP_CURRENT
        else -> InstagramProtectionAction.ALLOW
    }
}
