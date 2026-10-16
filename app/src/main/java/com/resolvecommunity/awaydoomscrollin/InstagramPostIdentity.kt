package com.resolvecommunity.awaydoomscrollin

import java.util.Locale

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

    fun isPostTitle(title: String): Boolean = title.trim().lowercase(Locale.ROOT) in setOf(
        "photo", "photos", "fotoğraf", "fotoğraflar", "post", "posts", "gönderi", "gönderiler", "video", "videos"
    )

    fun isViewer(ids: Set<String>, titles: Set<String>, hasBack: Boolean): Boolean =
        hasBack && "main_feed_action_bar" !in ids && titles.any(::isPostTitle) &&
            "row_feed_profile_header" in ids && ids.any {
                it in setOf("row_feed_photo_imageview", "media_group", "row_feed_view_group_buttons")
            }
}
