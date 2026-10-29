package com.resolvecommunity.awaydoomscrollin

internal enum class InstagramNotificationTap { WAIT, BLOCK, PROFILE, ALLOW }

internal object InstagramNotificationEntryPolicy {
    /**
     * Strict only. A row which shows a media thumbnail opens that media when
     * tapped; Strict never opens it. Connection rows (follows, requests) and
     * the row's own verified profile picture stay usable. The row's localized
     * summary text is never matched: it worked for Turkish/English only.
     */
    fun routeTap(
        summaries: List<String>,
        verifiedProfile: Boolean,
        hasMediaThumbnail: Boolean = false
    ): InstagramNotificationTap {
        if (verifiedProfile) return InstagramNotificationTap.PROFILE
        if (hasMediaThumbnail) return InstagramNotificationTap.BLOCK
        // A row whose text has not been published yet may still gain a thumbnail.
        if (summaries.none(String::isNotBlank)) return InstagramNotificationTap.WAIT
        return InstagramNotificationTap.ALLOW
    }

    fun blocksCommentLikes(mode: ProtectionMode): Boolean = mode == ProtectionMode.STRICT

    /**
     * A row's trailing media preview: a text-less, non-button view in the
     * row's right third. Follow/Message buttons carry a caption; avatars sit
     * on the left. Language-independent by construction.
     */
    fun isMediaThumbnail(
        id: String,
        className: String,
        hasText: Boolean,
        bounds: InstagramGeometrySample,
        row: InstagramGeometrySample,
        minSidePx: Int
    ): Boolean {
        if (hasText || id in setOf("igds_button", "ig_text") ||
            className.contains("Button") || className.contains("TextView")) return false
        val width = bounds.right - bounds.left
        val height = bounds.bottom - bounds.top
        val rowWidth = row.right - row.left
        if (width < minSidePx || height < minSidePx || width * 2 > rowWidth) return false
        val centerX = bounds.left + width / 2
        val centerY = bounds.top + height / 2
        return centerX in row.left until row.right && centerY in row.top until row.bottom &&
            bounds.left >= row.left + rowWidth * 2 / 3
    }

    /** Cover the row's media action but leave a verified, independent profile button usable. */
    fun guardRegions(row: InstagramGeometrySample, profile: InstagramGeometrySample?): List<InstagramGeometrySample> {
        if (row.right <= row.left || row.bottom <= row.top) return emptyList()
        if (profile == null) return listOf(row)
        val left = maxOf(row.left, profile.left)
        val top = maxOf(row.top, profile.top)
        val right = minOf(row.right, profile.right)
        val bottom = minOf(row.bottom, profile.bottom)
        if (left >= right || top >= bottom) return listOf(row)
        return listOf(
            InstagramGeometrySample(row.left, row.top, row.right, top),
            InstagramGeometrySample(row.left, bottom, row.right, row.bottom),
            InstagramGeometrySample(row.left, top, left, bottom),
            InstagramGeometrySample(right, top, row.right, bottom)
        ).filter { it.left < it.right && it.top < it.bottom }
    }
}
