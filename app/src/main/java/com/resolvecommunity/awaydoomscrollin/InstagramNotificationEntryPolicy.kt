package com.resolvecommunity.awaydoomscrollin

import java.util.Locale

internal enum class InstagramNotificationTap { WAIT, BLOCK, PROFILE, ALLOW }

internal object InstagramNotificationEntryPolicy {
    /**
     * Strict only. A row which shows a media thumbnail, or whose action is about
     * a post, Reel, Story or comment, opens that media when tapped; Strict never
     * opens it. Connection rows (follows, requests) and the row's own verified
     * profile picture stay usable.
     */
    fun routeTap(
        summaries: List<String>,
        verifiedProfile: Boolean,
        hasMediaThumbnail: Boolean = false
    ): InstagramNotificationTap {
        if (verifiedProfile) return InstagramNotificationTap.PROFILE
        if (hasMediaThumbnail || summaries.any(::isBlockedMediaSummary)) return InstagramNotificationTap.BLOCK
        if (summaries.none(String::isNotBlank)) return InstagramNotificationTap.WAIT
        return InstagramNotificationTap.ALLOW
    }
    private val whitespace = Regex("\\s+")
    private val englishCommentLike = Regex("\\bliked your comment\\b")
    private val turkishCommentLike = Regex("\\byorumunu beğend(?:i|iler)\\b")
    private val commentReply = Regex("\\breplied to your (?:comment|reply)\\b|\\byorumuna (?:yanıt|cevap) verdi\\b")
    fun blocksCommentLikes(mode: ProtectionMode): Boolean = mode == ProtectionMode.STRICT

    fun isCommentLikeSummary(label: String): Boolean {
        // Ignore the quoted comment after the notification's action summary.
        val summary = label.substringBefore(':').replace('İ', 'I').lowercase(Locale.ROOT)
            .replace(whitespace, " ")
        return englishCommentLike.containsMatchIn(summary) || turkishCommentLike.containsMatchIn(summary)
    }

    // Actions whose notification opens a post, Reel, Story or comment thread.
    private val englishMediaActivity = Regex(
        "\\bliked your (?:reel|photo|post|video|story|carousel|thread)\\b|" +
            "\\bcommented(?: on your| on)?\\b|\\bmentioned you\\b|\\btagged you\\b|" +
            "\\bshared (?:a|your) (?:reel|post|video|photo)\\b|\\bposted (?:a )?(?:reel|photo|video|post)\\b|" +
            "\\bremixed your\\b|\\bused your (?:audio|sound)\\b")
    // Turkish words end in letters such as "ı" that "\b" does not treat as word
    // characters, and Android's ICU engine rejects the "(?U)" flag (it crashed
    // the service on the device). Explicit letter lookarounds work on both.
    private const val TR_START = "(?<![\\p{L}\\p{N}])"
    private const val TR_END = "(?![\\p{L}\\p{N}])"
    private val turkishMediaActivity = Regex(
        TR_START + "(?:reel(?:'?in)?i|gönderini|gönderilerini|fotoğrafını|videonu|hikayeni|hikâyeni|" +
            "paylaşımını)\\s+beğend(?:i|iler)" + TR_END + "|" +
            TR_START + "(?:bahsetti|etiketledi|yorum yaptı|yorumda|remix|sesini kullandı|" +
            "(?:reel|gönderi|fotoğraf|video) paylaştı)" + TR_END)

    fun isMediaActivitySummary(label: String): Boolean {
        val summary = label.substringBefore(':').replace('İ', 'I').lowercase(Locale.ROOT)
            .replace(whitespace, " ")
        return englishMediaActivity.containsMatchIn(summary) || turkishMediaActivity.containsMatchIn(summary)
    }

    fun isBlockedMediaSummary(label: String): Boolean {
        val summary = label.substringBefore(':').replace('İ', 'I').lowercase(Locale.ROOT).replace(whitespace, " ")
        return isCommentLikeSummary(label) || commentReply.containsMatchIn(summary) ||
            isMediaActivitySummary(label)
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
