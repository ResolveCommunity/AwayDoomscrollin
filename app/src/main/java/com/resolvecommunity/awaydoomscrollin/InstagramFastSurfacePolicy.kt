package com.resolvecommunity.awaydoomscrollin

/** Fast, conservative classification used before the bounded general tree walk. */
internal object InstagramFastSurfacePolicy {
    fun classify(
        homeSelected: Boolean,
        searchSelected: Boolean,
        hasBackNavigation: Boolean,
        hasHomeHeader: Boolean,
        hasExploreHeader: Boolean,
        hasExploreGrid: Boolean
    ): InstagramScreen? {
        if (hasBackNavigation) return null
        if (homeSelected && hasHomeHeader) return InstagramScreen.HOME_FEED
        if (searchSelected && hasExploreHeader && hasExploreGrid) return InstagramScreen.EXPLORE
        return null
    }
}
