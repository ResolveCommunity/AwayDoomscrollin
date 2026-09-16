package com.resolvecommunity.awaydoomscrollin

internal enum class InstagramScreen {
    HOME_FEED,
    REELS,
    EXPLORE,
    NOTIFICATIONS,
    SEARCH_RESULTS,
    DIRECT_MESSAGES,
    DIRECT_SHARED_MEDIA,
    PROFILE,
    COMMENTS_OR_DETAIL,
    STORY,
    CREATION,
    UNKNOWN
}

internal enum class InstagramTab {
    HOME,
    REELS,
    DIRECT,
    SEARCH,
    PROFILE
}

internal enum class InstagramEventKind {
    WINDOW_CHANGED,
    CONTENT_CHANGED,
    CLICK,
    SCROLL
}

internal enum class InstagramGuardAction {
    COVER_FEED,
    EXIT_REELS,
    NAVIGATE_DIRECT,
    ALLOW,
    KEEP_CURRENT
}

internal data class InstagramUiSignals(
    val ids: Set<String> = emptySet(),
    val selectedTabs: Set<InstagramTab> = emptySet(),
    val sourceIds: Set<String> = emptySet(),
    val sourceLabels: Set<String> = emptySet(),
    val sourceTab: InstagramTab? = null,
    val searchFieldFocused: Boolean = false,
    val titles: Set<String> = emptySet(),
    val hasBackNavigation: Boolean = false,
    val hasCameraAction: Boolean = false,
    val hasClipsInteractionBar: Boolean = false,
    val hasFullscreenReelsViewer: Boolean = false
)

internal data class InstagramInteraction(
    val kind: InstagramEventKind,
    val deltaX: Int = 0,
    val deltaY: Int = 0,
    val fromIndex: Int = -1,
    val toIndex: Int = -1
) {
    val isVerticalScroll: Boolean
        get() {
            if (kind != InstagramEventKind.SCROLL) return false
            if (deltaX != 0 || deltaY != 0) {
                return kotlin.math.abs(deltaY) >= kotlin.math.abs(deltaX)
            }
            return false
        }
}

/**
 * Pure decision logic for Instagram protection.
 *
 * Unknown screens deliberately remain UNKNOWN. Treating missing evidence as the
 * home feed caused false positives in DMs, profiles and settings.
 */
internal object InstagramProtectionEngine {

    @Suppress("UNUSED_PARAMETER")
    fun guardAction(
        signals: InstagramUiSignals,
        interaction: InstagramInteraction,
        config: InstagramProtectionConfig = InstagramProtectionConfig(),
        previousScreen: InstagramScreen = InstagramScreen.UNKNOWN,
        navigatedBackFromDm: Boolean = false
    ): InstagramGuardAction {
        val currentScreen = classify(signals, navigatedBackFromDm)

        // A click is reported before Instagram has necessarily replaced its
        // accessibility tree. Catch known media entry points while the tree is
        // still a safe host screen.
        if (isBlockedContentLaunch(signals, interaction, config)) {
            return InstagramGuardAction.EXIT_REELS
        }

        if (navigatedBackFromDm && (signals.ids.hasAny("main_feed_action_bar") || signals.selectedTabs.contains(InstagramTab.HOME))) {
            return InstagramGuardAction.COVER_FEED
        }

        return when (currentScreen) {
            InstagramScreen.HOME_FEED -> {
                if (config.feedMode == InstagramProtectionConfig.FEED_MODE_DIRECTLY_DM && !navigatedBackFromDm) {
                    InstagramGuardAction.NAVIGATE_DIRECT
                } else {
                    InstagramGuardAction.COVER_FEED
                }
            }

            InstagramScreen.EXPLORE -> {
                if (config.hideExploreFeed) {
                    InstagramGuardAction.COVER_FEED
                } else {
                    InstagramGuardAction.ALLOW
                }
            }

            InstagramScreen.NOTIFICATIONS -> InstagramGuardAction.ALLOW

            InstagramScreen.CREATION -> InstagramGuardAction.ALLOW

            InstagramScreen.REELS -> {
                InstagramGuardAction.EXIT_REELS
            }

            InstagramScreen.STORY -> {
                if (config.blockStories) {
                    InstagramGuardAction.EXIT_REELS
                } else {
                    InstagramGuardAction.ALLOW
                }
            }

            InstagramScreen.COMMENTS_OR_DETAIL -> {
                if (config.blockComments) {
                    InstagramGuardAction.EXIT_REELS
                } else {
                    InstagramGuardAction.ALLOW
                }
            }

            InstagramScreen.UNKNOWN -> InstagramGuardAction.KEEP_CURRENT
            else -> InstagramGuardAction.ALLOW
        }
    }

    fun classify(signals: InstagramUiSignals, navigatedBackFromDm: Boolean = false): InstagramScreen {
        val ids = signals.ids

        val hasBackNav = signals.hasBackNavigation || ids.hasBackNavigation()
        val isExplicitReelsViewerTitle = signals.titles.hasAnyContaining(
            "reel",
            "reels",
            "reposted",
            "yeniden paylaşılanlar"
        )
        val hasExploreContext = signals.titles.hasAnyContaining("explore", "keşfet") ||
            ids.hasAny("explore_action_bar")

        val hasStableHomeHost = ids.hasAny("main_feed_action_bar") &&
            signals.selectedTabs.contains(InstagramTab.HOME)
        val reelsIsOnlySelectedTab = signals.selectedTabs.contains(InstagramTab.REELS) &&
            signals.selectedTabs.none { it != InstagramTab.REELS }

        // Instagram can briefly report more than one selected bottom-tab node
        // while replacing an embedded Home video. A large clips player is also
        // used inside the feed, so neither signal may override a verified Home
        // shell unless genuine viewer navigation (Back) is present.
        if (reelsIsOnlySelectedTab ||
            (signals.hasFullscreenReelsViewer && (!hasStableHomeHost || hasBackNav))) {
            return InstagramScreen.REELS
        }

        // A story viewer may retain the host tab beneath it.
        if (ids.hasStoryViewer()) return InstagramScreen.STORY

        // An opened viewer can retain the Home tab/header during its transition.
        // Dedicated viewer chrome plus Back is stronger evidence than that host.
        // clips_ufi or an embedded video alone is NOT enough.
        if (hasBackNav && ids.hasAny("clips_viewer_action_bar") && signals.hasClipsInteractionBar) {
            return InstagramScreen.REELS
        }

        // 2. Notifications screen (allowed)
        val hasNotificationsContext = signals.titles.hasAnyContaining(
            "notifications",
            "bildirimler"
        ) || ids.hasAnyContaining(
            "activity_feed",
            "newsfeed_recycler",
            "notifications_recycler",
            "follow_requests_header"
        )
        if (hasNotificationsContext) {
            return InstagramScreen.NOTIFICATIONS
        }

        // 3. Direct Messages structure (allowed)
        val hasDirectDetails = ids.hasAny("thread_details_pager") &&
            ids.hasAny("thread_details_header", "shared_section")
        if (hasDirectDetails) {
            return if (ids.hasAny("shared_media_list")) {
                InstagramScreen.DIRECT_SHARED_MEDIA
            } else {
                InstagramScreen.DIRECT_MESSAGES
            }
        }

        val hasDirectStructure = ids.hasAny(
            "direct_inbox_action_bar",
            "direct_thread_header",
            "direct_thread_content_below_action_bar",
            "message_composer_bar",
            "row_thread_composer_container",
            "row_thread_composer_edittext"
        ) || ids.hasAny("inbox_refreshable_thread_list_recyclerview") ||
            signals.selectedTabs.contains(InstagramTab.DIRECT)

        if (signals.selectedTabs.contains(InstagramTab.DIRECT) ||
            (hasDirectStructure &&
                !signals.selectedTabs.contains(InstagramTab.SEARCH) &&
                !signals.selectedTabs.contains(InstagramTab.PROFILE) &&
                !signals.selectedTabs.contains(InstagramTab.REELS))
        ) {
            return InstagramScreen.DIRECT_MESSAGES
        }

        // 4. Creation / Publishing flow (+ Button, Media Picker, Camera, Composer)
        // Must have back/cancel navigation or standalone creation views without main feed shell
        val hasCreationViews = ids.hasAny(
            "gallery_grid", "gallery_recycler_view", "gallery_folder_menu_btn",
            "photo_preview", "crop_image_view", "next_button_textview",
            "camera_shutter_button", "quick_capture_bottom_navigation",
            "cam_filter_preview", "caption_edit_text", "composer_edit_text",
            "action_bar_button_next", "publish_button", "media_picker_view",
            "album_thumbnail_view", "gallery_container", "gallery_header"
        )
        val hasCreationTitle = signals.titles.hasAnyContaining(
            "yeni gönderi", "new post", "yeni hikaye", "new story",
            "yeni reels", "new reel", "yeni klip", "yeni video",
            "taslaklar", "drafts"
        )
        val isCreationFlow = (hasBackNav && (hasCreationTitle || hasCreationViews)) ||
            (hasCreationViews && !ids.hasAny("main_feed_action_bar", "reels_tray_container"))

        if (isCreationFlow && !isExplicitReelsViewerTitle && !signals.selectedTabs.contains(InstagramTab.REELS)) {
            return InstagramScreen.CREATION
        }

        // 5. Unambiguous Home Feed Context:
        // When on Home tab or main_feed_action_bar is present, and NOT an explicit post detail,
        // this is DEFINITIVELY the HOME FEED!
        val isPostDetailTitle = signals.titles.any { title ->
            setOf("posts", "gönderiler", "comments", "yorumlar", "post", "gönderi").any {
                title.trim().equals(it, ignoreCase = true)
            }
        }
        val isExplicitPostDetail = !ids.hasAny("main_feed_action_bar") &&
            (ids.hasStructuralCommentView() || isPostDetailTitle)

        val isHomeFeedContext = !reelsIsOnlySelectedTab &&
            signals.sourceTab != InstagramTab.REELS &&
            !isExplicitPostDetail &&
            (ids.hasAny("main_feed_action_bar") ||
                (signals.selectedTabs.contains(InstagramTab.HOME) && !hasBackNav)) &&
            (!isExplicitReelsViewerTitle || signals.selectedTabs.contains(InstagramTab.HOME)) &&
            !signals.titles.hasAnyContaining("explore", "keşfet")

        if (isHomeFeedContext) {
            return InstagramScreen.HOME_FEED
        }

        // 6. Stories viewer is blocked (only when genuine fullscreen viewer, not inside DM or Home shell)
        val isStoryViewer = !hasDirectStructure &&
            !signals.selectedTabs.contains(InstagramTab.DIRECT) &&
            !ids.hasAny(
                "main_feed_action_bar",
                "direct_inbox_action_bar",
                "direct_thread_header",
                "message_composer_bar",
                "inbox_refreshable_thread_list_recyclerview"
            ) &&
            ids.hasStoryViewer()

        if (isStoryViewer) {
            return InstagramScreen.STORY
        }

        // 7. Reels & Full-Screen Video Viewers:
        val isReelsViewer = !isHomeFeedContext && !isCreationFlow && !hasDirectStructure && (
            reelsIsOnlySelectedTab ||
            signals.sourceTab == InstagramTab.REELS ||
            (hasBackNav && isExplicitReelsViewerTitle) ||
            (hasBackNav && hasExploreContext && (
                signals.hasClipsInteractionBar || ids.hasStrongReelsPlayer()
                )) ||
            (hasBackNav && (signals.hasClipsInteractionBar || ids.hasAny("clips_ufi", "row_feed_comment_edit_text"))) ||
            (ids.hasStrongReelsPlayer() && !hasDirectStructure)
        )

        if (isReelsViewer) {
            return InstagramScreen.REELS
        }

        // 8. Post Detail or Comments (only when reading someone else's post, not creating or on home feed or returning from DM)
        val isPostOrCommentDetail = !isHomeFeedContext &&
            !isCreationFlow &&
            !hasDirectStructure &&
            !navigatedBackFromDm &&
            !ids.hasAny("main_feed_action_bar", "reels_tray_container") && (
                ids.hasStructuralCommentView() ||
                (hasBackNav && (isPostDetailTitle || (ids.hasPostViewer() && !ids.hasAny("sticky_header_list", "list"))))
            )
        if (isPostOrCommentDetail) {
            return InstagramScreen.COMMENTS_OR_DETAIL
        }

        // 9. Profile shell (if not a media viewer)
        if (ids.hasAny(
                "profile_header_container",
                "profile_action_bar",
                "user_detail_fragment",
                "profile_viewpager",
                "profile_tabs_container"
            ) &&
            (!signals.selectedTabs.contains(InstagramTab.SEARCH) || hasBackNav)
        ) {
            return InstagramScreen.PROFILE
        }
        if (signals.selectedTabs.contains(InstagramTab.PROFILE)) {
            return InstagramScreen.PROFILE
        }

        // 10. Search results vs Explore
        if (signals.searchFieldFocused || ids.hasActiveSearch() ||
            (hasBackNav && ids.hasAny("action_bar_search_edit_text"))
        ) {
            return InstagramScreen.SEARCH_RESULTS
        }

        if (ids.hasAny(
                "explore_action_bar",
                "layout_recyclerview_parent_container",
                "grid_card_layout_container",
                "image_preview"
            ) || signals.selectedTabs.contains(InstagramTab.SEARCH) ||
            signals.sourceTab == InstagramTab.SEARCH
        ) {
            return InstagramScreen.EXPLORE
        }

        // 11. Home Feed
        val hasHomeStructure = ids.hasAny(
            "main_feed_action_bar",
            "row_feed_profile_header",
            "reels_tray_container",
            "carousel_media_group"
        ) || ids.any { it.startsWith("row_feed_") }

        val hasScrollableHomeShell = ids.hasAny("refreshable_container") &&
            ids.hasAny("sticky_header_list") &&
            ids.hasAny(
                "list",
                "swipeable_nav_view_pager_inner_recycler_view"
            )

        if (signals.selectedTabs.contains(InstagramTab.HOME) ||
            signals.sourceTab == InstagramTab.HOME ||
            ids.hasAny("main_feed_action_bar") ||
            hasHomeStructure ||
            hasScrollableHomeShell
        ) {
            return InstagramScreen.HOME_FEED
        }

        return InstagramScreen.UNKNOWN
    }

    @Suppress("UNUSED_PARAMETER")
    fun shouldBlock(
        signals: InstagramUiSignals,
        interaction: InstagramInteraction,
        previousScreen: InstagramScreen,
        config: InstagramProtectionConfig = InstagramProtectionConfig()
    ): Boolean {
        val screen = classify(signals)

        if (isBlockedContentLaunch(signals, interaction, config)) return true

        if (screen == InstagramScreen.CREATION || screen == InstagramScreen.NOTIFICATIONS) {
            return false
        }

        if (screen == InstagramScreen.STORY && config.blockStories) {
            return true
        }

        if (screen == InstagramScreen.COMMENTS_OR_DETAIL && config.blockComments) {
            return true
        }

        if (screen == InstagramScreen.REELS) {
            if (interaction.kind == InstagramEventKind.WINDOW_CHANGED ||
                interaction.kind == InstagramEventKind.CONTENT_CHANGED
            ) {
                return true
            }
        }

        if (interaction.kind != InstagramEventKind.SCROLL) return false

        val protectedScreen = when (screen) {
            InstagramScreen.HOME_FEED -> true
            InstagramScreen.REELS -> true
            InstagramScreen.EXPLORE -> config.hideExploreFeed
            else -> false
        }
        if (!protectedScreen) return false

        // Safe nested scrollers only apply outside the Home feed.
        if (screen != InstagramScreen.HOME_FEED &&
            signals.sourceIds.hasSafeScrollableSource()
        ) {
            return false
        }

        return interaction.isVerticalScroll
    }

    fun isBlockedContentLaunch(
        signals: InstagramUiSignals,
        interaction: InstagramInteraction,
        config: InstagramProtectionConfig = InstagramProtectionConfig()
    ): Boolean {
        if (interaction.kind != InstagramEventKind.CLICK) return false
        if (signals.sourceTab == InstagramTab.REELS) return true

        val sourceIds = signals.sourceIds
        if (sourceIds.hasAnyContaining(
                "profile_tab_icon_view",
                "profile_tab_layout",
                "profile_tabs_container"
            )
        ) {
            return false
        }

        // Creation buttons or within creation flow
        val isCreationAction = sourceIds.hasAny(
            "action_bar_button_new_post", "creation_tab", "action_bar_button_create"
        ) || sourceIds.hasAnyContaining("new_post", "camera_shutter", "composer") ||
            signals.sourceLabels.any {
                it.equals("oluştur", ignoreCase = true) ||
                it.equals("create", ignoreCase = true) ||
                it.equals("yeni gönderi", ignoreCase = true) ||
                it.equals("new post", ignoreCase = true) ||
                it.equals("yeni hikaye", ignoreCase = true) ||
                it.equals("new story", ignoreCase = true) ||
                it.startsWith("yeni gönderi") ||
                it.startsWith("new post") ||
                it.startsWith("oluştur") ||
                it.startsWith("create")
            }
        if (isCreationAction) {
            return false
        }

        // Direct icon or action bar messaging
        val isDirectAction = sourceIds.hasAny(
            "action_bar_button_action",
            "direct_inbox_action_bar",
            "direct_thread_header",
            "message_composer_bar",
            "direct_tab"
        ) || sourceIds.hasAnyContaining("direct", "inbox") ||
            signals.sourceLabels.hasAnyContaining("direct", "mesajlar", "mesaj", "inbox", "chats", "sohbet")
        if (isDirectAction) {
            return false
        }

        // Notifications icon
        val isNotificationsAction = sourceIds.hasAnyContaining("notification", "activity_feed") ||
            signals.sourceLabels.hasAnyContaining("bildirim", "notification", "hareketler")
        if (isNotificationsAction) {
            return false
        }

        // Back navigation is never a blocked content launch
        val isBackAction = sourceIds.hasAny(
            "action_bar_button_back", "back_button"
        ) || sourceIds.hasAnyContaining("action_bar_back", "navigation_back") ||
            signals.sourceLabels.hasAnyContaining("back", "geri", "navigate up", "yukarı git")
        if (isBackAction) {
            return false
        }

        val hostScreen = classify(signals)
        if (hostScreen == InstagramScreen.CREATION ||
            hostScreen == InstagramScreen.NOTIFICATIONS
        ) {
            return false
        }

        // Inside DM Inbox, navigating between conversations is always safe
        val isInboxList = signals.ids.hasAny("inbox_refreshable_thread_list_recyclerview") ||
            sourceIds.hasAnyContaining("inbox", "thread_row", "row_inbox")
        if (isInboxList) {
            return false
        }

        val isDirectMediaClick = hostScreen == InstagramScreen.DIRECT_MESSAGES &&
            sourceIds.hasAny("message_content_generic_xma_container", "media_container", "clips_grid_item")

        if (hostScreen == InstagramScreen.DIRECT_MESSAGES && !isDirectMediaClick) {
            return false
        }

        // Story viewing or clicking stories tray is safe when stories are allowed
        val isStoryClick = sourceIds.hasAnyContaining(
            "reels_tray",
            "tray_recycler",
            "story_ring",
            "story_view",
            "highlight"
        ) || (!config.blockStories && signals.sourceLabels.hasAnyContaining("story", "hikaye"))
        if (!config.blockStories && isStoryClick) {
            return false
        }

        val hasMediaSource = sourceIds.hasAnyContaining(
            "clips_grid_item",
            "media_grid_item",
            "grid_media",
            "media_container",
            "media_thumbnail",
            "image_preview"
        ) || (config.blockStories && sourceIds.hasAnyContaining("story_ring", "story_view", "highlight")) ||
        (hostScreen == InstagramScreen.PROFILE && sourceIds.hasAnyContaining(
            "image_button",
            "imageview",
            "thumbnail",
            "avatar",
            "clips_grid"
        ))
        val hasMediaLabel = signals.sourceLabels.hasAnyContaining(
            "reel",
            "clip",
            "post",
            "gönderi",
            "video"
        ) || (config.blockStories && signals.sourceLabels.hasAnyContaining("story", "hikaye"))

        return hasMediaSource || hasMediaLabel
    }

    private fun Set<String>.hasStrongReelsPlayer(): Boolean =
        hasAnyContaining(
            "clips_video_container",
            "clips_viewer",
            "clips_player",
            "clips_view_pager",
            "clips_viewer_view_pager",
            "reels_viewer",
            "reel_player",
            "reel_viewer_layout",
            "clips_timeline",
            "clips_swipe_refresh",
            "viewer_media_view_pager",
            "feed_preview_keep_watching",
            "feed_preview_bottom_cta"
        )

    private fun Set<String>.hasStructuralCommentView(): Boolean =
        hasAnyContaining(
            "comments_recycler",
            "comment_thread",
            "layout_comment_thread",
            "comment_list"
        )

    private fun Set<String>.hasStoryViewer(): Boolean =
        hasAny("reel_item_toolbar_container") ||
        (hasAnyContaining("story_viewer_container") && !hasAnyContaining("reels_tray", "inbox"))

    private fun Set<String>.hasPostViewer(): Boolean =
        hasAnyContaining(
            "media_view_pager",
            "carousel_media_group",
            "post_detail",
            "feed_detail",
            "single_media_view"
        )

    private fun Set<String>.hasActiveSearch(): Boolean =
        hasAnyContaining(
            "search_row",
            "echo_text",
            "search_results_list"
        )

    private fun Set<String>.hasBackNavigation(): Boolean =
        hasAnyContaining(
            "action_bar_button_back",
            "action_bar_back",
            "back_button",
            "up_button"
        )

    private fun Set<String>.hasSafeScrollableSource(): Boolean =
        hasAnyContaining(
            "tab_recycler_view",
            "profile_tabs",
            "search_results_list",
            "direct_thread_list"
        )

    private fun Set<String>.hasAny(vararg candidates: String): Boolean =
        candidates.any { contains(it) }

    private fun Set<String>.hasAnyContaining(vararg needles: String): Boolean =
        any { value ->
            needles.any { needle ->
                value.contains(needle, ignoreCase = true)
            }
        }
}
