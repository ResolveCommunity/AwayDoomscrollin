package com.resolvecommunity.awaydoomscrollin

/**
 * How TikTok's foreground surface was classified by the bounded accessibility scan.
 */
internal enum class TikTokSurface {
    /** Short-video feed: For You, Takipte/Following, Topluluk/Community or a watch page. */
    FEED,

    /** Messaging, profile, search, Shop, comment conversations and other non-feed surfaces. */
    SAFE,

    /** No known signal was found; the surface stays usable instead of risking a wrong Back. */
    UNKNOWN
}

/** What the user can type into on the current surface, derived from editable field hints. */
internal enum class TikTokComposer {
    NONE,
    COMMENT,
    MESSAGE,
    SEARCH
}

/**
 * Pure policy for classifying TikTok's foreground surface from a bounded accessibility scan.
 *
 * The intervention contract is intentionally narrow: vertical scrolling inside TikTok's
 * short-video feeds is interrupted, while messaging, profile, search, Shop and comment
 * conversations remain usable. Feed captions can contain arbitrary words, so classification
 * relies on structural signals - the selected bottom tab, the selected top tab, editable
 * composers and video action-rail markers - instead of sampled caption text.
 *
 * Signals were captured from real dumps (TikTok, Turkish locale): bottom tabs expose
 * localized descriptions ("Ana sayfa", "Gelen kutusu", "Profil") with a selected state, top
 * tabs do the same ("Topluluk", "Takipte", "Sizin İçin"), and the video action rail uses
 * running descriptions such as "Video beğenin. 55,2 B beğeni", "Yorum okuyun veya ekleyin",
 * "Videoyu paylaş". Resource ids are obfuscated per release, so matching is label-based.
 */
internal object TikTokSurfacePolicy {
    const val TAB_FEED = "feed"
    const val TAB_INBOX = "inbox"
    const val TAB_PROFILE = "profile"
    const val TAB_SHOP = "shop"

    /** A selected top feed tab whose entry must be undone (Takipte / Topluluk). */
    const val TOP_TAB_BLOCKED = "top_blocked"

    /** The default For You top tab (or LIVE); entry is allowed. */
    const val TOP_TAB_HOME = "top_home"

    private val feedTabIdPrefixes = listOf("home", "friends", "for_you", "foryou")
    private val feedTabLabels = setOf(
        "home",
        "ana sayfa",
        "for you",
        "sana özel",
        "friends",
        "arkadaşlar",
        "arkadaslar"
    )
    private val inboxTabLabels = setOf("inbox", "gelen kutusu")
    private val profileTabLabels = setOf("profile", "profil")
    private val shopTabLabels = setOf("shop", "mağaza", "magaza")

    private val blockedTopTabLabels = setOf(
        "takipte",
        "following",
        "topluluk",
        "community"
    )
    private val homeTopTabLabels = setOf(
        "sizin için",
        "sizin icin",
        "for you"
    )

    // Follower-count strings only appear on profile and profile-preview surfaces ("0 takip
    // ediliyor · 1.5M takipçi", "500 Followers"). Watching and scrolling someone's profile is
    // social interaction, not doomscrolling, so these keep the surface safe even when feed
    // signals bleed into the tree behind an overlay.
    private val profileLabelPatterns = listOf(
        "takipçi",
        "takipci",
        "takip ediliyor",
        "followers"
    )

    /** Video action-rail categories matched by containment inside running descriptions. */
    private val railCategoryPatterns = linkedMapOf(
        "like" to listOf("beğen", "begen", "like"),
        "comment" to listOf("yorum", "comment"),
        "share" to listOf("paylaş", "paylas", "share"),
        "favorite" to listOf("favori", "favorite")
    )

    /**
     * Resolves a selected bottom-tab node into a canonical tab token. Resource ids win over
     * localized labels; nodes that are not bottom tabs (for example a selected feed widget or
     * caption text) resolve to null so they can never mask the real surface.
     */
    fun resolveSelectedTab(idFragment: String?, text: String?, description: String?): String? {
        val id = idFragment.orEmpty()
        if (id.contains("bottom_tab")) {
            val token = id.substringAfter("bottom_tab").trim('_', '-')
            when {
                token.isEmpty() -> Unit
                feedTabIdPrefixes.any(token::startsWith) -> return TAB_FEED
                token.startsWith("inbox") ||
                    token.startsWith("message") ||
                    token.startsWith("notification") -> return TAB_INBOX
                token.startsWith("profile") -> return TAB_PROFILE
                token.startsWith("shop") || token.startsWith("store") -> return TAB_SHOP
            }
        }
        val label = normalizeLabel(text) ?: normalizeLabel(description) ?: return null
        return when {
            feedTabLabels.contains(label) -> TAB_FEED
            inboxTabLabels.contains(label) -> TAB_INBOX
            profileTabLabels.contains(label) -> TAB_PROFILE
            shopTabLabels.contains(label) -> TAB_SHOP
            else -> null
        }
    }

    /**
     * Resolves a selected top-tab node inside the feed header. Only exact localized labels
     * match; "Sizin İçin"/"For You" and unknown labels resolve to home/null.
     */
    fun resolveSelectedTopTab(text: String?, description: String?): String? {
        val label = normalizeLabel(text) ?: normalizeLabel(description) ?: return null
        return when {
            blockedTopTabLabels.contains(label) -> TOP_TAB_BLOCKED
            homeTopTabLabels.contains(label) -> TOP_TAB_HOME
            else -> null
        }
    }

    /** True when the label names a blocked top feed tab (used for click interception). */
    fun isBlockedTopTabLabel(raw: String?): Boolean {
        val label = normalizeLabel(raw) ?: return false
        return blockedTopTabLabels.contains(label)
    }

    /** True when the label names the default For You top tab (the snap-back target). */
    fun isHomeTopTabLabel(raw: String?): Boolean {
        val label = normalizeLabel(raw) ?: return false
        return homeTopTabLabels.contains(label)
    }

    /** True when the label looks like a follower-count line from a profile surface. */
    fun isProfileLabel(raw: String?): Boolean {
        val label = normalizeLabel(raw) ?: return false
        return profileLabelPatterns.any(label::contains)
    }

    /**
     * Classifies an editable field by its hint, text, description or resource id. Comment
     * composers win over the others because the comment sheet can sit on top of the feed.
     */
    fun classifyComposer(
        hint: String?,
        text: String?,
        description: String?,
        idFragment: String?
    ): TikTokComposer {
        val id = idFragment.orEmpty()
        if (id.contains("comment")) return TikTokComposer.COMMENT
        val hay = normalizeLabel(listOfNotNull(hint, description, text).joinToString(" "))
            .orEmpty()
        return when {
            hay.contains("yorum") || hay.contains("comment") -> TikTokComposer.COMMENT
            hay.contains("mesaj") || hay.contains("message") -> TikTokComposer.MESSAGE
            id.contains("search") ||
                hay.contains("search") ||
                hay.contains("arama") ||
                hay == "ara" -> TikTokComposer.SEARCH
            else -> TikTokComposer.NONE
        }
    }

    /**
     * Returns the rail category when the description matches one of TikTok's video action
     * buttons by containment; running descriptions such as "Video beğenin. 114 beğeni" map
     * to "like". Unknown descriptions resolve to null.
     */
    fun railActionCategory(description: String?): String? {
        val label = normalizeLabel(description) ?: return null
        for ((category, patterns) in railCategoryPatterns) {
            if (patterns.any(label::contains)) return category
        }
        return null
    }

    /**
     * Classification precedence:
     * 1. Message or search composers mean a conversation or search surface - safe.
     * 2. Follower-count labels mean a profile surface - safe. Scrolling a profile grid is
     *    social browsing, and friend adding must never be interrupted by a stray Back.
     * 3. The selected bottom tab decides the main surfaces (feed / inbox / profile / shop).
     * 4. A selected blocked top tab (Takipte / Topluluk) is feed territory.
     * 5. A strong video action rail (3+ categories) is the fullscreen video watch page, even
     *    though it keeps a persistent "Yorum ekleyin..." comment bar.
     * 6. A lone comment composer without a strong rail is a comment conversation - safe.
     * 7. Two rail categories, or a pager with one, are accepted as weaker feed evidence.
     * 8. Anything else stays unknown and usable.
     */
    fun classify(signals: TikTokUiSignals): TikTokSurface {
        if (signals.composer == TikTokComposer.MESSAGE ||
            signals.composer == TikTokComposer.SEARCH
        ) {
            return TikTokSurface.SAFE
        }
        if (signals.hasProfileSignals) return TikTokSurface.SAFE
        when (signals.selectedTab) {
            TAB_FEED -> return TikTokSurface.FEED
            TAB_INBOX, TAB_PROFILE, TAB_SHOP -> return TikTokSurface.SAFE
        }
        if (signals.selectedTopTab == TOP_TAB_BLOCKED) return TikTokSurface.FEED
        val railCategoryCount = signals.railCategories.size
        if (railCategoryCount >= 3) return TikTokSurface.FEED
        if (signals.composer == TikTokComposer.COMMENT) return TikTokSurface.SAFE
        if (railCategoryCount >= 2) return TikTokSurface.FEED
        if (signals.hasFeedPager && railCategoryCount >= 1) return TikTokSurface.FEED
        return TikTokSurface.UNKNOWN
    }

    private fun normalizeLabel(raw: String?): String? {
        // Locale.ROOT keeps English intact but lowercases Turkish "İ" to "i" plus a
        // combining dot (U+0307); strip the dot so "Sizin İçin" matches "sizin için".
        val label = raw?.trim()
            ?.replace(Regex("\\s+"), " ")
            ?.lowercase(java.util.Locale.ROOT)
            ?.replace("\u0307", "")
        return label?.takeIf(String::isNotEmpty)
    }
}

/**
 * Structural signals collected from one bounded accessibility-tree scan of TikTok's
 * foreground window.
 */
internal data class TikTokUiSignals(
    val selectedTab: String? = null,
    val selectedTopTab: String? = null,
    val composer: TikTokComposer = TikTokComposer.NONE,
    val hasProfileSignals: Boolean = false,
    val hasFeedPager: Boolean = false,
    val railCategories: Set<String> = emptySet()
)
