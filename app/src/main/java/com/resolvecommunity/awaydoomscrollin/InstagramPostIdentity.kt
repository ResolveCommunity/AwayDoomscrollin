package com.resolvecommunity.awaydoomscrollin

internal object InstagramPostIdentity {
    /** Feed rows use the same media IDs as a detail page, but are not viewers. */
    fun isGenericViewer(
        fullPhotoViewer: Boolean,
        hasHomeHeader: Boolean,
        postChrome: Boolean,
        hasBack: Boolean,
        detailsTabsVisible: Boolean
    ): Boolean = fullPhotoViewer ||
        (!hasHomeHeader && postChrome && (hasBack || !detailsTabsVisible))

    /**
     * The opened post list has exactly the ids of the Following/Favorites
     * feeds; only its localized title would tell them apart, and titles are
     * never used (they work for the listed languages only). The navigation
     * that opened it does in every language: a thumbnail chosen on a verified
     * profile or collection, or a post opened from a notification.
     */
    fun isFastViewer(
        chosen: Boolean,
        hasBack: Boolean,
        hasHomeHeader: Boolean,
        hasPostHeader: Boolean,
        hasPostMedia: Boolean
    ): Boolean = chosen && hasBack && !hasHomeHeader && hasPostHeader && hasPostMedia
}
