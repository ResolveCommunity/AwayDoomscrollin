package com.resolvecommunity.awaydoomscrollin

import java.util.Locale

internal enum class YouTubeNavigationDestination {
    HOME,
    SHORTS,
    SUBSCRIPTIONS,
    YOU,
    UNKNOWN
}

internal enum class YouTubeProtectedSurface {
    HOME,
    SUBSCRIPTIONS
}

/** The curtain screen a navigation destination shows once settled. */
internal fun YouTubeNavigationDestination.curtainScreen(): YouTubeCurtainScreen =
    when (this) {
        YouTubeNavigationDestination.HOME -> YouTubeCurtainScreen.HOME
        YouTubeNavigationDestination.SUBSCRIPTIONS -> YouTubeCurtainScreen.SUBSCRIPTIONS
        YouTubeNavigationDestination.YOU -> YouTubeCurtainScreen.YOU
        YouTubeNavigationDestination.SHORTS -> YouTubeCurtainScreen.SHORTS
        YouTubeNavigationDestination.UNKNOWN -> YouTubeCurtainScreen.UNKNOWN
    }

internal object YouTubeSurfacePolicy {
    /** A loading frame with a Back arrow after Comments/watch is not evidence of Home. */
    fun preserveAllowedSecondaryDuringLoading(
        lastVerifiedScreen: YouTubeCurtainScreen,
        leadingBackVisible: Boolean
    ): Boolean = lastVerifiedScreen == YouTubeCurtainScreen.OTHER_ALLOWED && leadingBackVisible

    fun shouldRetryTabNavigation(
        requested: YouTubeNavigationDestination,
        selected: YouTubeNavigationDestination,
        visibleScreen: YouTubeCurtainScreen
    ): Boolean = selected != requested || visibleScreen in setOf(
        YouTubeCurtainScreen.SEARCH,
        YouTubeCurtainScreen.NOTIFICATIONS,
        YouTubeCurtainScreen.OTHER_ALLOWED
    )

    fun navigationDestination(rawDescription: String?): YouTubeNavigationDestination {
        val description = rawDescription.orEmpty()
            .substringBefore(':')
            .substringBefore(',')
            .trim()
            .lowercase(Locale.ROOT)
        return when {
            description == "home" || description == "ana sayfa" ->
                YouTubeNavigationDestination.HOME
            description == "shorts" -> YouTubeNavigationDestination.SHORTS
            description == "subscriptions" || description.startsWith("subscriptions") ||
                description == "abonelikler" || description.startsWith("abonelikler") ->
                YouTubeNavigationDestination.SUBSCRIPTIONS
            description == "you" || description.startsWith("you") ||
                description == "siz" || description.startsWith("siz") ||
                description == "kitaplık" || description.startsWith("kitaplık") ||
                description == "kitaplik" || description.startsWith("kitaplik") ||
                description == "library" || description.startsWith("library") ||
                description == "account" || description == "hesap" ||
                description == "profil" || description == "profile" ->
                YouTubeNavigationDestination.YOU
            else -> YouTubeNavigationDestination.UNKNOWN
        }
    }

    fun strictProtectedSurface(
        destination: YouTubeNavigationDestination,
        searchSurfaceVisible: Boolean,
        shortsVisible: Boolean,
        allowedSecondarySurfaceVisible: Boolean = false
    ): YouTubeProtectedSurface? {
        // Search and allowed secondary surfaces (Notifications, History, Playlists) are explicitly allowed.
        if (searchSurfaceVisible || allowedSecondarySurfaceVisible) return null
        // The dedicated Shorts tab is not covered by the Home feed curtain.
        if (destination == YouTubeNavigationDestination.SHORTS) return null
        // Home and Subscriptions feeds are strictly protected. Embedded Shorts shelves, ads,
        // or video previews inside the feed must NEVER uncover the feed.
        if (destination == YouTubeNavigationDestination.HOME) return YouTubeProtectedSurface.HOME
        if (destination == YouTubeNavigationDestination.SUBSCRIPTIONS) return YouTubeProtectedSurface.SUBSCRIPTIONS
        if (shortsVisible) return null
        return null
    }

    fun protectedNavigationIntent(
        intent: YouTubeNavigationDestination,
        selectedDestination: YouTubeNavigationDestination
    ): YouTubeProtectedSurface? {
        // YouTube can deliver an old bottom-tab click after a newer tab is already selected.
        if (intent != selectedDestination) return null
        return when (intent) {
            YouTubeNavigationDestination.HOME -> YouTubeProtectedSurface.HOME
            YouTubeNavigationDestination.SUBSCRIPTIONS ->
                YouTubeProtectedSurface.SUBSCRIPTIONS
            else -> null
        }
    }
}
