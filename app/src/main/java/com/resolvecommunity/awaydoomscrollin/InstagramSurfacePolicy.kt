package com.resolvecommunity.awaydoomscrollin

internal object InstagramSurfacePolicy {
    // Inbox rows cannot establish the foreground screen. Preserve the list's
    // own ID and inspect sibling headers, tabs and viewer layers instead.
    fun skipInboxRows(id: String): Boolean =
        id == "inbox_refreshable_thread_list_recyclerview"

    // Only Instagram's own search editors carry a "search" id. Story text,
    // captions and the Bloks settings search (no id at all) are plain editable
    // fields; treating them as search mounted the Explore precover per key.
    fun isFocusedSearchField(id: String, className: String, focused: Boolean): Boolean =
        focused && className.contains("EditText", ignoreCase = true) && id.contains("search")

    // Explore's search editor (action_bar_search_edit_text) proves the search
    // leaves onto Explore. Close friends/settings searches use other editors.
    // A fresh search visit starts unproven; results scrolled past the editor
    // keep the proof of the same visit.
    fun isExploreSearchSession(
        hasExploreSearchEditor: Boolean,
        continuingSearch: Boolean,
        previouslySeen: Boolean
    ): Boolean = hasExploreSearchEditor || (continuingSearch && previouslySeen)

    /** Instagram's own modal dialog (promo/what's-new) is foreground; ids only. */
    fun isInstagramDialog(dialogContainerVisible: Boolean, promoHeadlineVisible: Boolean): Boolean =
        dialogContainerVisible || promoHeadlineVisible

    /**
     * A notification's single post collapses its action bar (and Back) once
     * scrolled, leaving only feed-row ids: the classifier then reads Home.
     * Balanced keeps the notification item while the same window shows no
     * tab bar, no Home header and at most one post; a second post is a feed.
     */
    fun keepsNotificationItem(
        mode: ProtectionMode,
        classified: InstagramScreen,
        notificationItemActive: Boolean,
        sameWindow: Boolean,
        tabBarVisible: Boolean,
        homeHeaderVisible: Boolean,
        visiblePosts: Int
    ): Boolean = mode == ProtectionMode.BALANCED && classified == InstagramScreen.HOME_FEED &&
        notificationItemActive && sameWindow && !tabBarVisible && !homeHeaderVisible &&
        visiblePosts <= 1

    // An unclassified tree is re-checked quickly while a transition may still
    // be publishing; once it has persisted it is a real (unknown) surface and
    // an 80 ms rescan loop would only occupy the main looper.
    fun unknownRescanDelayMs(unknownForMs: Long): Long =
        if (unknownForMs < 1_500L) 80L else 400L

    fun yieldFeedSnapshot(elapsedMs: Long, previousYields: Int): Boolean =
        elapsedMs >= 100L && previousYields < 2

    fun skipProtectedProfileGridChildren(id: String, measuredProfileGrid: Boolean,
        profileHeaderVisible: Boolean): Boolean =
        id == "profile_viewpager" && measuredProfileGrid && profileHeaderVisible

    fun canReleaseProfileToAccountSearch(profileCurtainVisible: Boolean,
        searchEditorVisible: Boolean, accountRowVisible: Boolean,
        profileSurfaceVisible: Boolean): Boolean =
        profileCurtainVisible && searchEditorVisible && accountRowVisible && !profileSurfaceVisible

    fun needsCurtain(screen: InstagramScreen): Boolean =
        screen == InstagramScreen.HOME_FEED || screen == InstagramScreen.EXPLORE ||
            screen == InstagramScreen.PROFILE || screen == InstagramScreen.DIRECT_SHARED_MEDIA

    fun needsExit(screen: InstagramScreen): Boolean =
        screen == InstagramScreen.REELS || screen == InstagramScreen.COMMENTS_OR_DETAIL

    /**
     * Instagram rebuilds a large clips/player subtree when an embedded Home
     * video ends. During that mutation the Home header and selected tab can
     * both disappear for a frame. A mounted, touch-consuming Home curtain plus
     * the existing Home state are stronger evidence than that transient tree.
     * A real user launch is allowed through so the normal viewer exit can run.
     */
    fun shouldKeepVerifiedHome(
        currentScreen: InstagramScreen,
        homeCurtainVisible: Boolean,
        sameWindow: Boolean,
        hasFreshUserInput: Boolean
    ): Boolean = currentScreen == InstagramScreen.HOME_FEED &&
        homeCurtainVisible && sameWindow && !hasFreshUserInput
}
