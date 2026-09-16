package com.resolvecommunity.awaydoomscrollin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstagramProtectionEngineTest {

    @Test
    fun openingHomeFeedDoesNotBlockUntilVerticalScroll() {
        val signals = homeFeed()

        assertEquals(InstagramScreen.HOME_FEED, InstagramProtectionEngine.classify(signals))
        assertEquals(
            InstagramGuardAction.COVER_FEED,
            InstagramProtectionEngine.guardAction(
                signals,
                InstagramInteraction(InstagramEventKind.CONTENT_CHANGED)
            )
        )
        assertFalse(
            InstagramProtectionEngine.shouldBlock(
                signals,
                InstagramInteraction(InstagramEventKind.CONTENT_CHANGED),
                InstagramScreen.UNKNOWN
            )
        )
        assertTrue(
            InstagramProtectionEngine.shouldBlock(
                signals,
                InstagramInteraction(InstagramEventKind.SCROLL, deltaY = 1),
                InstagramScreen.HOME_FEED
            )
        )
    }

    @Test
    fun refreshableContainerPresenceAloneDoesNotBlockAppLaunch() {
        val home = homeFeed()
        val signals = home.copy(ids = home.ids + "refreshable_container")

        assertFalse(
            InstagramProtectionEngine.shouldBlock(
                signals,
                InstagramInteraction(InstagramEventKind.CONTENT_CHANGED),
                InstagramScreen.UNKNOWN
            )
        )
    }

    @Test
    fun metriclessHomeLayoutEventDoesNotBlockAppLaunch() {
        val signals = homeFeed(sourceIds = setOf("refreshable_container"))

        assertFalse(
            InstagramProtectionEngine.shouldBlock(
                signals,
                InstagramInteraction(InstagramEventKind.SCROLL),
                InstagramScreen.HOME_FEED
            )
        )
    }

    @Test
    fun metriclessHomeCarouselLayoutEventDoesNotBlock() {
        val signals = homeFeed(sourceIds = setOf("carousel_media_group"))

        assertFalse(
            InstagramProtectionEngine.shouldBlock(
                signals,
                InstagramInteraction(InstagramEventKind.SCROLL),
                InstagramScreen.HOME_FEED
            )
        )
    }

    @Test
    fun indexChangeWithoutPixelDeltaIsNotTreatedAsUserScroll() {
        assertFalse(
            InstagramProtectionEngine.shouldBlock(
                homeFeed(),
                InstagramInteraction(
                    InstagramEventKind.SCROLL,
                    fromIndex = 0,
                    toIndex = 1
                ),
                InstagramScreen.HOME_FEED
            )
        )
    }

    @Test
    fun currentInstagramFeedShellClassifiesWithoutVisibleBottomTabs() {
        val signals = InstagramUiSignals(
            ids = setOf(
                "layout_container_swipeable",
                "list",
                "refreshable_container",
                "sticky_header_list",
                "swipeable_nav_view_pager_inner_recycler_view",
                "swipeable_tab_view_pager"
            )
        )
        val scroll = InstagramInteraction(
            InstagramEventKind.SCROLL,
            deltaY = 255,
            fromIndex = 1,
            toIndex = 2
        )

        assertEquals(InstagramScreen.HOME_FEED, InstagramProtectionEngine.classify(signals))
        assertTrue(
            InstagramProtectionEngine.shouldBlock(
                signals,
                scroll,
                InstagramScreen.UNKNOWN
            )
        )
    }

    @Test
    fun actualUserDeltaRemainsAProtectedHomeScroll() {
        val signals = InstagramUiSignals(
            ids = setOf(
                "list",
                "refreshable_container",
                "sticky_header_list",
                "swipeable_nav_view_pager_inner_recycler_view"
            )
        )

        assertTrue(
            InstagramProtectionEngine.shouldBlock(
                signals,
                InstagramInteraction(
                    InstagramEventKind.SCROLL,
                    deltaY = 255,
                    fromIndex = 1,
                    toIndex = 2
                ),
                InstagramScreen.HOME_FEED
            )
        )
    }

    @Test
    fun exploreStructureWinsWithoutVisibleSelectedTab() {
        val signals = InstagramUiSignals(
            ids = setOf(
                "explore_action_bar",
                "grid_card_layout_container",
                "refreshable_container",
                "sticky_header_list",
                "list"
            )
        )

        assertEquals(InstagramScreen.EXPLORE, InstagramProtectionEngine.classify(signals))
    }

    @Test
    fun horizontalCarouselMovementIsAllowed() {
        assertFalse(
            InstagramProtectionEngine.shouldBlock(
                homeFeed(sourceIds = setOf("carousel_viewpager")),
                InstagramInteraction(InstagramEventKind.SCROLL, deltaX = 40, deltaY = 2),
                InstagramScreen.HOME_FEED
            )
        )
    }

    @Test
    fun homeTabTapDoesNotBlockBecauseOverlayProtectsFeed() {
        val click = InstagramInteraction(InstagramEventKind.CLICK)
        val signals = homeFeed(sourceTab = InstagramTab.HOME)

        assertFalse(
            InstagramProtectionEngine.shouldBlock(
                signals,
                click,
                InstagramScreen.HOME_FEED
            )
        )
        assertFalse(
            InstagramProtectionEngine.shouldBlock(
                signals,
                click,
                InstagramScreen.PROFILE
            )
        )
    }

    @Test
    fun reelsTabClickAndPlayerAppearanceBlockImmediately() {
        val tabClickSignals = InstagramUiSignals(sourceTab = InstagramTab.REELS)
        assertEquals(
            InstagramGuardAction.EXIT_REELS,
            InstagramProtectionEngine.guardAction(
                tabClickSignals,
                InstagramInteraction(InstagramEventKind.CLICK)
            )
        )
        assertTrue(
            InstagramProtectionEngine.shouldBlock(
                tabClickSignals,
                InstagramInteraction(InstagramEventKind.CLICK),
                InstagramScreen.HOME_FEED
            )
        )

        val player = InstagramUiSignals(ids = setOf("clips_video_container"))
        assertEquals(InstagramScreen.REELS, InstagramProtectionEngine.classify(player))
        assertTrue(
            InstagramProtectionEngine.shouldBlock(
                player,
                InstagramInteraction(InstagramEventKind.CONTENT_CHANGED),
                InstagramScreen.EXPLORE
            )
        )
    }

    @Test
    fun profileReelsGridRemainsProfile() {
        val signals = InstagramUiSignals(
            ids = setOf(
                "profile_header_container",
                "profile_viewpager",
                "clips_grid_recyclerview"
            ),
            selectedTabs = setOf(InstagramTab.PROFILE)
        )

        assertEquals(InstagramScreen.PROFILE, InstagramProtectionEngine.classify(signals))
        assertFalse(
            InstagramProtectionEngine.shouldBlock(
                signals,
                InstagramInteraction(InstagramEventKind.SCROLL, deltaY = 30),
                InstagramScreen.PROFILE
            )
        )
    }

    @Test
    fun fullScreenReelWinsOverMountedProfileHierarchy() {
        val signals = InstagramUiSignals(
            ids = setOf(
                "profile_header_container",
                "profile_viewpager",
                "clips_viewer_view_pager",
                "root_clips_layout"
            ),
            selectedTabs = setOf(InstagramTab.PROFILE)
        )

        assertEquals(InstagramScreen.REELS, InstagramProtectionEngine.classify(signals))
        assertEquals(
            InstagramGuardAction.EXIT_REELS,
            InstagramProtectionEngine.guardAction(
                signals,
                InstagramInteraction(InstagramEventKind.CONTENT_CHANGED)
            )
        )
    }

    @Test
    fun profileReelsGridTabClickRemainsSafeWhenItIsNotTheMainReelsTab() {
        val signals = InstagramUiSignals(
            ids = setOf(
                "profile_header_container",
                "profile_viewpager",
                "clips_grid_recyclerview"
            ),
            selectedTabs = setOf(InstagramTab.PROFILE),
            sourceIds = setOf("clips_profile_tab")
        )

        assertEquals(InstagramScreen.PROFILE, InstagramProtectionEngine.classify(signals))
        assertEquals(
            InstagramGuardAction.ALLOW,
            InstagramProtectionEngine.guardAction(
                signals,
                InstagramInteraction(InstagramEventKind.CLICK)
            )
        )
        assertFalse(
            InstagramProtectionEngine.shouldBlock(
                signals,
                InstagramInteraction(InstagramEventKind.CLICK),
                InstagramScreen.PROFILE
            )
        )
    }

    @Test
    fun reelPreviewInsideDirectMessagesDoesNotReclassifyConversation() {
        val signals = InstagramUiSignals(
            ids = setOf("message_composer_bar", "clips_video_container"),
            selectedTabs = setOf(InstagramTab.DIRECT)
        )

        assertEquals(InstagramScreen.DIRECT_MESSAGES, InstagramProtectionEngine.classify(signals))
        assertFalse(
            InstagramProtectionEngine.shouldBlock(
                signals,
                InstagramInteraction(InstagramEventKind.SCROLL, deltaY = 20),
                InstagramScreen.DIRECT_MESSAGES
            )
        )
    }

    @Test
    fun exploreGridScrollBlocksButSearchResultsRemainSafe() {
        val explore = InstagramUiSignals(
            ids = setOf("explore_action_bar", "grid_card_layout_container"),
            selectedTabs = setOf(InstagramTab.SEARCH)
        )
        val search = explore.copy(searchFieldFocused = true)
        val scroll = InstagramInteraction(InstagramEventKind.SCROLL, deltaY = 25)

        assertEquals(InstagramScreen.EXPLORE, InstagramProtectionEngine.classify(explore))
        assertEquals(
            InstagramGuardAction.COVER_FEED,
            InstagramProtectionEngine.guardAction(explore, scroll)
        )
        assertTrue(InstagramProtectionEngine.shouldBlock(explore, scroll, InstagramScreen.EXPLORE))
        assertEquals(InstagramScreen.SEARCH_RESULTS, InstagramProtectionEngine.classify(search))
        assertEquals(
            InstagramGuardAction.ALLOW,
            InstagramProtectionEngine.guardAction(search, scroll)
        )
        assertFalse(InstagramProtectionEngine.shouldBlock(search, scroll, InstagramScreen.SEARCH_RESULTS))
    }

    @Test
    fun commentsAndStoriesAreSafeWhenDisabled() {
        val scroll = InstagramInteraction(InstagramEventKind.SCROLL, deltaY = 15)
        val comments = InstagramUiSignals(ids = setOf("comments_recycler_view"))
        val story = InstagramUiSignals(ids = setOf("story_viewer_container"))
        val disabledConfig = InstagramProtectionConfig(blockStories = false, blockComments = false)

        assertEquals(InstagramScreen.COMMENTS_OR_DETAIL, InstagramProtectionEngine.classify(comments))
        assertEquals(InstagramScreen.STORY, InstagramProtectionEngine.classify(story))
        assertFalse(InstagramProtectionEngine.shouldBlock(comments, scroll, InstagramScreen.UNKNOWN, config = disabledConfig))
        assertFalse(InstagramProtectionEngine.shouldBlock(story, scroll, InstagramScreen.UNKNOWN, config = disabledConfig))
    }

    @Test
    fun commentsAndStoriesAreBlockedByDefaultInFullMode() {
        val scroll = InstagramInteraction(InstagramEventKind.SCROLL, deltaY = 15)
        val comments = InstagramUiSignals(ids = setOf("comments_recycler_view"))
        val story = InstagramUiSignals(ids = setOf("story_viewer_container"))

        assertEquals(InstagramScreen.COMMENTS_OR_DETAIL, InstagramProtectionEngine.classify(comments))
        assertEquals(InstagramScreen.STORY, InstagramProtectionEngine.classify(story))
        assertTrue(InstagramProtectionEngine.shouldBlock(comments, scroll, InstagramScreen.UNKNOWN))
        assertFalse(InstagramProtectionEngine.shouldBlock(story, scroll, InstagramScreen.UNKNOWN))
        assertTrue(
            InstagramProtectionEngine.shouldBlock(
                story,
                scroll,
                InstagramScreen.UNKNOWN,
                config = InstagramProtectionConfig(blockStories = true)
            )
        )
    }

    @Test
    fun unknownScreenNeverFallsBackToFeed() {
        val unknown = InstagramUiSignals(ids = setOf("unrecognized_container"))

        assertEquals(InstagramScreen.UNKNOWN, InstagramProtectionEngine.classify(unknown))
        assertFalse(
            InstagramProtectionEngine.shouldBlock(
                unknown,
                InstagramInteraction(InstagramEventKind.SCROLL, deltaY = 100),
                InstagramScreen.UNKNOWN
            )
        )
    }

    @Test
    fun directlyDmModeRoutesToDirectUnlessNavigatedBack() {
        val home = homeFeed()
        val interaction = InstagramInteraction(InstagramEventKind.CONTENT_CHANGED)
        val config = InstagramProtectionConfig(feedMode = InstagramProtectionConfig.FEED_MODE_DIRECTLY_DM)

        // First launch/appearance: should redirect directly to DM
        assertEquals(
            InstagramGuardAction.NAVIGATE_DIRECT,
            InstagramProtectionEngine.guardAction(
                home,
                interaction,
                config = config,
                navigatedBackFromDm = false
            )
        )

        // After pressing back from DM: should fall back to covering the feed to avoid an infinite loop
        assertEquals(
            InstagramGuardAction.COVER_FEED,
            InstagramProtectionEngine.guardAction(
                home,
                interaction,
                config = config,
                navigatedBackFromDm = true
            )
        )
    }

    @Test
    fun dmReelsAreBlockedWithoutAnOriginException() {
        val reelFromDm = InstagramUiSignals(
            ids = setOf("clips_video_container"),
            selectedTabs = emptySet()
        )
        val scroll = InstagramInteraction(InstagramEventKind.SCROLL, deltaY = 10)
        val windowChange = InstagramInteraction(InstagramEventKind.WINDOW_CHANGED)

        val allowConfig = InstagramProtectionConfig(allowDmReels = true)
        val disallowConfig = InstagramProtectionConfig(allowDmReels = false)

        assertEquals(InstagramScreen.REELS, InstagramProtectionEngine.classify(reelFromDm))

        // The current product policy has no DM media exception, even if a
        // legacy preference still contains the old allow value.
        assertEquals(
            InstagramGuardAction.EXIT_REELS,
            InstagramProtectionEngine.guardAction(
                reelFromDm,
                windowChange,
                config = allowConfig,
                previousScreen = InstagramScreen.DIRECT_MESSAGES
            )
        )
        assertTrue(
            InstagramProtectionEngine.shouldBlock(
                reelFromDm,
                scroll,
                previousScreen = InstagramScreen.DIRECT_MESSAGES,
                config = allowConfig
            )
        )

        // When not allowed
        assertEquals(
            InstagramGuardAction.EXIT_REELS,
            InstagramProtectionEngine.guardAction(
                reelFromDm,
                windowChange,
                config = disallowConfig,
                previousScreen = InstagramScreen.DIRECT_MESSAGES
            )
        )
        assertTrue(
            InstagramProtectionEngine.shouldBlock(
                reelFromDm,
                windowChange,
                previousScreen = InstagramScreen.DIRECT_MESSAGES,
                config = disallowConfig
            )
        )

        // If explicitly opened via Reels tab, allowDmReels does not bypass
        val reelsTabSignals = reelFromDm.copy(sourceTab = InstagramTab.REELS)
        assertEquals(
            InstagramGuardAction.EXIT_REELS,
            InstagramProtectionEngine.guardAction(
                reelsTabSignals,
                windowChange,
                config = allowConfig,
                previousScreen = InstagramScreen.DIRECT_MESSAGES
            )
        )
    }

    @Test
    fun blockStoriesConfigControlsStoryViewer() {
        val story = InstagramUiSignals(ids = setOf("story_viewer_container"))
        val interaction = InstagramInteraction(InstagramEventKind.WINDOW_CHANGED)

        assertEquals(InstagramScreen.STORY, InstagramProtectionEngine.classify(story))

        // When blockStories is enabled
        val blockingConfig = InstagramProtectionConfig(blockStories = true)
        assertEquals(
            InstagramGuardAction.EXIT_REELS,
            InstagramProtectionEngine.guardAction(story, interaction, config = blockingConfig)
        )
        assertTrue(
            InstagramProtectionEngine.shouldBlock(
                story,
                interaction,
                previousScreen = InstagramScreen.HOME_FEED,
                config = blockingConfig
            )
        )

        // When blockStories is disabled (default)
        val allowingConfig = InstagramProtectionConfig(blockStories = false)
        assertEquals(
            InstagramGuardAction.ALLOW,
            InstagramProtectionEngine.guardAction(story, interaction, config = allowingConfig)
        )
        assertFalse(
            InstagramProtectionEngine.shouldBlock(
                story,
                interaction,
                previousScreen = InstagramScreen.HOME_FEED,
                config = allowingConfig
            )
        )
    }

    @Test
    fun blockCommentsConfigControlsCommentsScreen() {
        val comments = InstagramUiSignals(ids = setOf("comments_recycler_view"))
        val interaction = InstagramInteraction(InstagramEventKind.WINDOW_CHANGED)

        assertEquals(InstagramScreen.COMMENTS_OR_DETAIL, InstagramProtectionEngine.classify(comments))

        // When blockComments is enabled
        val blockingConfig = InstagramProtectionConfig(blockComments = true)
        assertEquals(
            InstagramGuardAction.EXIT_REELS,
            InstagramProtectionEngine.guardAction(comments, interaction, config = blockingConfig)
        )
        assertTrue(
            InstagramProtectionEngine.shouldBlock(
                comments,
                interaction,
                previousScreen = InstagramScreen.HOME_FEED,
                config = blockingConfig
            )
        )

        // When blockComments is disabled (default)
        val allowingConfig = InstagramProtectionConfig(blockComments = false)
        assertEquals(
            InstagramGuardAction.ALLOW,
            InstagramProtectionEngine.guardAction(comments, interaction, config = allowingConfig)
        )
        assertFalse(
            InstagramProtectionEngine.shouldBlock(
                comments,
                interaction,
                previousScreen = InstagramScreen.HOME_FEED,
                config = allowingConfig
            )
        )
    }

    @Test
    fun hideExploreFeedConfigControlsExploreScreen() {
        val explore = InstagramUiSignals(
            ids = setOf("explore_action_bar", "grid_card_layout_container"),
            selectedTabs = setOf(InstagramTab.SEARCH)
        )
        val scroll = InstagramInteraction(InstagramEventKind.SCROLL, deltaY = 25)

        assertEquals(InstagramScreen.EXPLORE, InstagramProtectionEngine.classify(explore))

        // When hideExploreFeed is enabled (default)
        val hiddenConfig = InstagramProtectionConfig(hideExploreFeed = true)
        assertEquals(
            InstagramGuardAction.COVER_FEED,
            InstagramProtectionEngine.guardAction(explore, scroll, config = hiddenConfig)
        )
        assertTrue(
            InstagramProtectionEngine.shouldBlock(
                explore,
                scroll,
                previousScreen = InstagramScreen.EXPLORE,
                config = hiddenConfig
            )
        )

        // When hideExploreFeed is disabled
        val visibleConfig = InstagramProtectionConfig(hideExploreFeed = false)
        assertEquals(
            InstagramGuardAction.ALLOW,
            InstagramProtectionEngine.guardAction(explore, scroll, config = visibleConfig)
        )
        assertFalse(
            InstagramProtectionEngine.shouldBlock(
                explore,
                scroll,
                previousScreen = InstagramScreen.EXPLORE,
                config = visibleConfig
            )
        )
    }

    @Test
    fun exploreVideoViewerClassifiedAsReelsAndExitedWithBack() {
        val signals = InstagramUiSignals(
            hasBackNavigation = true,
            titles = setOf("explore"),
            hasClipsInteractionBar = true
        )
        val interaction = InstagramInteraction(InstagramEventKind.CONTENT_CHANGED)

        assertEquals(InstagramScreen.REELS, InstagramProtectionEngine.classify(signals))
        assertEquals(
            InstagramGuardAction.EXIT_REELS,
            InstagramProtectionEngine.guardAction(signals, interaction)
        )
        assertTrue(
            InstagramProtectionEngine.shouldBlock(
                signals,
                interaction,
                previousScreen = InstagramScreen.EXPLORE
            )
        )
    }

    @Test
    fun profileReelsViewerClassifiedAsReelsAndExitedWithBack() {
        val signals = InstagramUiSignals(
            ids = setOf("profile_header_container", "user_detail_fragment"),
            selectedTabs = setOf(InstagramTab.PROFILE),
            titles = setOf("reels"),
            hasBackNavigation = true,
            hasCameraAction = true,
            hasClipsInteractionBar = true
        )
        val interaction = InstagramInteraction(InstagramEventKind.CONTENT_CHANGED)

        assertEquals(InstagramScreen.REELS, InstagramProtectionEngine.classify(signals))
        assertEquals(
            InstagramGuardAction.EXIT_REELS,
            InstagramProtectionEngine.guardAction(signals, interaction)
        )
        assertTrue(
            InstagramProtectionEngine.shouldBlock(
                signals,
                interaction,
                previousScreen = InstagramScreen.PROFILE
            )
        )
    }

    @Test
    fun profileRepostsViewerClassifiedAsReelsAndExitedWithBack() {
        val signals = InstagramUiSignals(
            ids = setOf("profile_header_container"),
            selectedTabs = setOf(InstagramTab.PROFILE),
            titles = setOf("reposted"),
            hasBackNavigation = true,
            hasCameraAction = true,
            hasClipsInteractionBar = true
        )
        val interaction = InstagramInteraction(InstagramEventKind.CONTENT_CHANGED)

        assertEquals(InstagramScreen.REELS, InstagramProtectionEngine.classify(signals))
        assertEquals(
            InstagramGuardAction.EXIT_REELS,
            InstagramProtectionEngine.guardAction(signals, interaction)
        )
        assertTrue(
            InstagramProtectionEngine.shouldBlock(
                signals,
                interaction,
                previousScreen = InstagramScreen.PROFILE
            )
        )
    }

    @Test
    fun normalProfileBrowsingAllowed() {
        val signals = InstagramUiSignals(
            ids = setOf("profile_header_container", "profile_tabs_container"),
            selectedTabs = setOf(InstagramTab.PROFILE),
            titles = setOf("john_doe"),
            hasBackNavigation = false,
            hasCameraAction = false,
            hasClipsInteractionBar = false
        )
        val interaction = InstagramInteraction(InstagramEventKind.SCROLL, deltaY = 20)

        assertEquals(InstagramScreen.PROFILE, InstagramProtectionEngine.classify(signals))
        assertEquals(
            InstagramGuardAction.ALLOW,
            InstagramProtectionEngine.guardAction(signals, interaction)
        )
        assertFalse(
            InstagramProtectionEngine.shouldBlock(
                signals,
                interaction,
                previousScreen = InstagramScreen.PROFILE
            )
        )
    }

    @Test
    fun searchTabWithoutSearchFocusClassifiedAsExploreImmediately() {
        val signals = InstagramUiSignals(
            selectedTabs = setOf(InstagramTab.SEARCH),
            searchFieldFocused = false
        )
        val interaction = InstagramInteraction(InstagramEventKind.CONTENT_CHANGED)

        assertEquals(InstagramScreen.EXPLORE, InstagramProtectionEngine.classify(signals))
        assertEquals(
            InstagramGuardAction.COVER_FEED,
            InstagramProtectionEngine.guardAction(signals, interaction)
        )
    }

    @Test
    fun searchTabWithSearchFocusClassifiedAsSearchResults() {
        val signals = InstagramUiSignals(
            selectedTabs = setOf(InstagramTab.SEARCH),
            searchFieldFocused = true
        )
        val interaction = InstagramInteraction(InstagramEventKind.CONTENT_CHANGED)

        assertEquals(InstagramScreen.SEARCH_RESULTS, InstagramProtectionEngine.classify(signals))
        assertEquals(
            InstagramGuardAction.ALLOW,
            InstagramProtectionEngine.guardAction(signals, interaction)
        )
    }

    @Test
    fun homeFeedWithEmbeddedVideoClipsRemainsHomeFeedAndDoesNotTriggerReelsExit() {
        val signals = InstagramUiSignals(
            ids = setOf("main_feed_action_bar", "row_feed_profile_header", "clips_video_container"),
            selectedTabs = setOf(InstagramTab.HOME),
            hasBackNavigation = false
        )
        val interaction = InstagramInteraction(InstagramEventKind.CONTENT_CHANGED)

        assertEquals(InstagramScreen.HOME_FEED, InstagramProtectionEngine.classify(signals))
        assertEquals(
            InstagramGuardAction.COVER_FEED,
            InstagramProtectionEngine.guardAction(signals, interaction)
        )
    }

    @Test
    fun returnFromDmToHomeFeedClassifiedAsHomeFeedImmediately() {
        val signals = InstagramUiSignals(
            selectedTabs = setOf(InstagramTab.HOME),
            hasBackNavigation = false
        )
        val interaction = InstagramInteraction(InstagramEventKind.CONTENT_CHANGED)

        assertEquals(InstagramScreen.HOME_FEED, InstagramProtectionEngine.classify(signals))
        assertEquals(
            InstagramGuardAction.COVER_FEED,
            InstagramProtectionEngine.guardAction(signals, interaction)
        )
    }

    @Test
    fun profileReelsViewerExitsImmediately() {
        val signals = InstagramUiSignals(
            hasBackNavigation = true,
            titles = setOf("reels"),
            hasCameraAction = true,
            hasClipsInteractionBar = true,
            ids = setOf("action_bar_button_back", "clips_ufi")
        )
        val interaction = InstagramInteraction(InstagramEventKind.CONTENT_CHANGED)

        assertEquals(InstagramScreen.REELS, InstagramProtectionEngine.classify(signals))
        assertEquals(
            InstagramGuardAction.EXIT_REELS,
            InstagramProtectionEngine.guardAction(signals, interaction)
        )
    }

    @Test
    fun profileRepostsViewerExitsImmediately() {
        val signals = InstagramUiSignals(
            hasBackNavigation = true,
            titles = setOf("reposted"),
            hasCameraAction = true,
            hasClipsInteractionBar = true,
            ids = setOf("action_bar_button_back", "clips_ufi")
        )
        val interaction = InstagramInteraction(InstagramEventKind.CONTENT_CHANGED)

        assertEquals(InstagramScreen.REELS, InstagramProtectionEngine.classify(signals))
        assertEquals(
            InstagramGuardAction.EXIT_REELS,
            InstagramProtectionEngine.guardAction(signals, interaction)
        )
    }

    @Test
    fun exploreReelsViewerExitsImmediately() {
        val signals = InstagramUiSignals(
            hasBackNavigation = true,
            titles = setOf("explore"),
            hasClipsInteractionBar = true,
            ids = setOf("action_bar_button_back", "clips_ufi")
        )
        val interaction = InstagramInteraction(InstagramEventKind.CONTENT_CHANGED)

        assertEquals(InstagramScreen.REELS, InstagramProtectionEngine.classify(signals))
        assertEquals(
            InstagramGuardAction.EXIT_REELS,
            InstagramProtectionEngine.guardAction(signals, interaction)
        )
    }

    @Test
    fun profileScreenWithoutReelsViewerAllowsNavigation() {
        val signals = InstagramUiSignals(
            selectedTabs = setOf(InstagramTab.PROFILE),
            ids = setOf("profile_header_container", "profile_action_bar")
        )
        val interaction = InstagramInteraction(InstagramEventKind.CONTENT_CHANGED)

        assertEquals(InstagramScreen.PROFILE, InstagramProtectionEngine.classify(signals))
        assertEquals(
            InstagramGuardAction.ALLOW,
            InstagramProtectionEngine.guardAction(signals, interaction)
        )
    }

    @Test
    fun mainReelsTabNeverAllowedEvenIfFromDmHistory() {
        val signals = InstagramUiSignals(
            selectedTabs = setOf(InstagramTab.REELS),
            ids = setOf("inbox_refreshable_thread_list_recyclerview", "tab_bar")
        )
        val interaction = InstagramInteraction(InstagramEventKind.CONTENT_CHANGED)

        assertEquals(InstagramScreen.REELS, InstagramProtectionEngine.classify(signals))
        assertEquals(
            InstagramGuardAction.EXIT_REELS,
            InstagramProtectionEngine.guardAction(
                signals,
                interaction,
                config = InstagramProtectionConfig(allowDmReels = true),
                previousScreen = InstagramScreen.DIRECT_MESSAGES
            )
        )
    }

    @Test
    fun homeFeedReceivingLateClipsTabClickNeverExitsReels() {
        val signals = homeFeed(
            sourceIds = setOf("clips_tab", "tab_bar"),
            sourceTab = null
        )
        val interaction = InstagramInteraction(InstagramEventKind.CLICK)

        assertEquals(InstagramScreen.HOME_FEED, InstagramProtectionEngine.classify(signals))
        assertEquals(
            InstagramGuardAction.COVER_FEED,
            InstagramProtectionEngine.guardAction(signals, interaction)
        )
    }

    @Test
    fun mediaClickInsideDmIsBlockedBeforeTheViewerTreeAppears() {
        val signals = InstagramUiSignals(
            ids = setOf("direct_thread_header", "message_composer_bar"),
            sourceIds = setOf("media_container", "message_content_generic_xma_container")
        )
        val click = InstagramInteraction(InstagramEventKind.CLICK)

        assertEquals(InstagramScreen.DIRECT_MESSAGES, InstagramProtectionEngine.classify(signals))
        assertTrue(InstagramProtectionEngine.isBlockedContentLaunch(signals, click))
        assertEquals(
            InstagramGuardAction.EXIT_REELS,
            InstagramProtectionEngine.guardAction(signals, click)
        )
    }

    @Test
    fun profileGridTabItselfRemainsAvailable() {
        val signals = InstagramUiSignals(
            ids = setOf("profile_header_container", "profile_tabs_container"),
            selectedTabs = setOf(InstagramTab.PROFILE),
            sourceIds = setOf("profile_tab_icon_view", "profile_tab_layout"),
            sourceLabels = setOf("reels")
        )
        val click = InstagramInteraction(InstagramEventKind.CLICK)

        assertFalse(InstagramProtectionEngine.isBlockedContentLaunch(signals, click))
        assertEquals(
            InstagramGuardAction.ALLOW,
            InstagramProtectionEngine.guardAction(signals, click)
        )
    }

    @Test
    fun postViewerWinsOverMountedProfileHierarchy() {
        val signals = InstagramUiSignals(
            ids = setOf(
                "profile_header_container",
                "profile_viewpager",
                "action_bar_button_back",
                "row_feed_profile_header",
                "carousel_media_group"
            ),
            selectedTabs = setOf(InstagramTab.PROFILE),
            hasBackNavigation = true,
            titles = setOf("posts")
        )
        val event = InstagramInteraction(InstagramEventKind.CONTENT_CHANGED)

        assertEquals(InstagramScreen.COMMENTS_OR_DETAIL, InstagramProtectionEngine.classify(signals))
        assertEquals(
            InstagramGuardAction.EXIT_REELS,
            InstagramProtectionEngine.guardAction(signals, event)
        )
    }

    @Test
    fun profileOpenedFromExploreRemainsAllowedWithoutPlayerSignals() {
        val signals = InstagramUiSignals(
            ids = setOf(
                "explore_action_bar",
                "profile_header_container",
                "profile_viewpager",
                "action_bar_button_back"
            ),
            selectedTabs = setOf(InstagramTab.SEARCH),
            hasBackNavigation = true
        )

        assertEquals(InstagramScreen.PROFILE, InstagramProtectionEngine.classify(signals))
    }

    @Test
    fun tappingAProfileGridImageIsABlockedContentLaunch() {
        val signals = InstagramUiSignals(
            ids = setOf("profile_header_container", "profile_tabs_container"),
            selectedTabs = setOf(InstagramTab.PROFILE),
            sourceIds = setOf("image_button", "grid_item")
        )

        assertTrue(
            InstagramProtectionEngine.isBlockedContentLaunch(
                signals,
                InstagramInteraction(InstagramEventKind.CLICK)
            )
        )
    }

    @Test
    fun transientSearchScreenWithBackButtonIsAllowedBeforeFocusSettles() {
        val signals = InstagramUiSignals(
            ids = setOf("action_bar_search_edit_text", "action_bar_button_back"),
            hasBackNavigation = true,
            searchFieldFocused = false
        )

        assertEquals(InstagramScreen.SEARCH_RESULTS, InstagramProtectionEngine.classify(signals))
        assertEquals(
            InstagramGuardAction.ALLOW,
            InstagramProtectionEngine.guardAction(
                signals,
                InstagramInteraction(InstagramEventKind.CONTENT_CHANGED)
            )
        )
    }

    @Test
    fun notificationsIsAllowedWhileHomeTabRemainsSelected() {
        val signals = InstagramUiSignals(
            ids = setOf("activity_feed", "newsfeed_recycler", "tab_bar"),
            selectedTabs = setOf(InstagramTab.HOME),
            titles = setOf("bildirimler")
        )

        assertEquals(InstagramScreen.NOTIFICATIONS, InstagramProtectionEngine.classify(signals))
        assertEquals(
            InstagramGuardAction.ALLOW,
            InstagramProtectionEngine.guardAction(
                signals,
                InstagramInteraction(InstagramEventKind.CONTENT_CHANGED)
            )
        )
    }

    @Test
    fun creationFlowIsAllowedAndNotTreatedAsCommentOrPostDetail() {
        val signals = InstagramUiSignals(
            ids = setOf("gallery_grid", "gallery_folder_menu_btn", "main_feed_action_bar"),
            selectedTabs = setOf(InstagramTab.HOME),
            titles = setOf("Yeni gönderi"),
            hasBackNavigation = true
        )

        assertEquals(InstagramScreen.CREATION, InstagramProtectionEngine.classify(signals))
        assertEquals(
            InstagramGuardAction.ALLOW,
            InstagramProtectionEngine.guardAction(
                signals,
                InstagramInteraction(InstagramEventKind.CONTENT_CHANGED)
            )
        )
    }

    @Test
    fun creationClickIsNotBlockedContentLaunch() {
        val signals = InstagramUiSignals(
            ids = setOf("main_feed_action_bar"),
            sourceIds = setOf("action_bar_button_new_post"),
            sourceLabels = setOf("Yeni gönderi")
        )

        assertFalse(
            InstagramProtectionEngine.isBlockedContentLaunch(
                signals,
                InstagramInteraction(InstagramEventKind.CLICK)
            )
        )
    }

    @Test
    fun directMessagesClassifiedInstantlyEvenIfMainFeedActionBarStillMounted() {
        val signals = InstagramUiSignals(
            ids = setOf("direct_inbox_action_bar", "main_feed_action_bar", "inbox_refreshable_thread_list_recyclerview"),
            selectedTabs = setOf(InstagramTab.HOME)
        )

        assertEquals(InstagramScreen.DIRECT_MESSAGES, InstagramProtectionEngine.classify(signals))
        assertEquals(
            InstagramGuardAction.ALLOW,
            InstagramProtectionEngine.guardAction(
                signals,
                InstagramInteraction(InstagramEventKind.CONTENT_CHANGED)
            )
        )
    }

    @Test
    fun selectedHomeWinsOverStaleMainReelsHierarchyAfterExit() {
        val signals = InstagramUiSignals(
            ids = setOf("clips_viewer", "clips_video_container", "main_feed_action_bar"),
            selectedTabs = setOf(InstagramTab.HOME),
            titles = setOf("reels"),
            hasClipsInteractionBar = true
        )

        assertEquals(InstagramScreen.HOME_FEED, InstagramProtectionEngine.classify(signals))
        assertEquals(
            InstagramGuardAction.COVER_FEED,
            InstagramProtectionEngine.guardAction(
                signals,
                InstagramInteraction(InstagramEventKind.CONTENT_CHANGED)
            )
        )
    }

    @Test
    fun selectedSearchWinsOverStaleDirectInboxHierarchy() {
        val signals = InstagramUiSignals(
            ids = setOf(
                "explore_action_bar",
                "inbox_refreshable_thread_list_recyclerview",
                "layout_recyclerview_parent_container"
            ),
            selectedTabs = setOf(InstagramTab.SEARCH)
        )

        assertEquals(InstagramScreen.EXPLORE, InstagramProtectionEngine.classify(signals))
        assertEquals(
            InstagramGuardAction.COVER_FEED,
            InstagramProtectionEngine.guardAction(
                signals,
                InstagramInteraction(InstagramEventKind.CONTENT_CHANGED)
            )
        )
    }

    @Test
    fun selectedSearchWinsOverStaleProfileShellWithoutBackNavigation() {
        val signals = InstagramUiSignals(
            ids = setOf("profile_viewpager", "profile_header_container", "explore_action_bar"),
            selectedTabs = setOf(InstagramTab.SEARCH),
            hasBackNavigation = false
        )

        assertEquals(InstagramScreen.EXPLORE, InstagramProtectionEngine.classify(signals))
    }

    @Test
    fun homeFeedWithShareButtonAndCreationTabIsClassifiedAsHomeFeed() {
        val signals = InstagramUiSignals(
            ids = setOf(
                "main_feed_action_bar",
                "row_feed_profile_header",
                "creation_tab",
                "share_button",
                "action_bar_button_new_post",
                "feed_recycler_view",
                "row_feed_button_like",
                "row_feed_button_comment"
            ),
            selectedTabs = setOf(InstagramTab.HOME),
            titles = setOf("Instagram"),
            hasBackNavigation = false
        )
        assertEquals(InstagramScreen.HOME_FEED, InstagramProtectionEngine.classify(signals))
        assertEquals(
            InstagramGuardAction.COVER_FEED,
            InstagramProtectionEngine.guardAction(
                signals,
                InstagramInteraction(InstagramEventKind.CONTENT_CHANGED)
            )
        )
    }

    @Test
    fun creationFlowWithBackNavAndNewPostTitleIsClassifiedAsCreation() {
        val signals = InstagramUiSignals(
            ids = setOf("action_bar_button_back", "gallery_grid", "crop_image_view"),
            titles = setOf("Yeni gönderi"),
            hasBackNavigation = true
        )
        assertEquals(InstagramScreen.CREATION, InstagramProtectionEngine.classify(signals))
        assertEquals(
            InstagramGuardAction.ALLOW,
            InstagramProtectionEngine.guardAction(
                signals,
                InstagramInteraction(InstagramEventKind.CONTENT_CHANGED)
            )
        )
    }

    @Test
    fun tappingNewPostButtonDoesNotTriggerBlockedContentLaunch() {
        val signals = InstagramUiSignals(
            sourceIds = setOf("action_bar_button_new_post"),
            sourceLabels = setOf("yeni gönderi")
        )
        val click = InstagramInteraction(InstagramEventKind.CLICK)
        assertFalse(InstagramProtectionEngine.isBlockedContentLaunch(signals, click))
    }

    @Test
    fun postDetailWithCommentsIsClassifiedAsCommentsOrDetail() {
        val signals = InstagramUiSignals(
            ids = setOf("action_bar_button_back", "row_feed_photo"),
            titles = setOf("Gönderiler"),
            hasBackNavigation = true
        )
        assertEquals(InstagramScreen.COMMENTS_OR_DETAIL, InstagramProtectionEngine.classify(signals))
        assertEquals(
            InstagramGuardAction.EXIT_REELS,
            InstagramProtectionEngine.guardAction(
                signals,
                InstagramInteraction(InstagramEventKind.CONTENT_CHANGED)
            )
        )
    }

    @Test
    fun dmInboxWithNotesTrayOrStoryHolderIsClassifiedAsDirectMessages() {
        val signals = InstagramUiSignals(
            ids = setOf(
                "direct_inbox_action_bar",
                "inbox_refreshable_thread_list_recyclerview",
                "story_item_holder",
                "reel_viewer_layout"
            ),
            titles = setOf("Sohbetler")
        )
        assertEquals(InstagramScreen.DIRECT_MESSAGES, InstagramProtectionEngine.classify(signals))
        assertEquals(
            InstagramGuardAction.ALLOW,
            InstagramProtectionEngine.guardAction(
                signals,
                InstagramInteraction(InstagramEventKind.CONTENT_CHANGED)
            )
        )
    }

    @Test
    fun clickingDirectActionBarButtonIsNotBlocked() {
        val signals = InstagramUiSignals(
            ids = setOf("main_feed_action_bar"),
            sourceIds = setOf("action_bar_button_action"),
            sourceLabels = setOf("direct", "1 yeni mesaj")
        )
        val click = InstagramInteraction(InstagramEventKind.CLICK)
        assertFalse(InstagramProtectionEngine.isBlockedContentLaunch(signals, click))
    }

    @Test
    fun realStoryViewerIsStillClassifiedAsStoryAndExited() {
        val signals = InstagramUiSignals(
            ids = setOf("story_viewer_container", "reel_viewer_layout"),
            hasBackNavigation = true
        )
        assertEquals(InstagramScreen.STORY, InstagramProtectionEngine.classify(signals))
        assertEquals(
            InstagramGuardAction.ALLOW,
            InstagramProtectionEngine.guardAction(
                signals,
                InstagramInteraction(InstagramEventKind.CONTENT_CHANGED)
            )
        )
        assertEquals(
            InstagramGuardAction.EXIT_REELS,
            InstagramProtectionEngine.guardAction(
                signals,
                InstagramInteraction(InstagramEventKind.CONTENT_CHANGED),
                config = InstagramProtectionConfig(blockStories = true)
            )
        )
    }

    @Test
    fun returningFromDmToHomeFeedIsClassifiedAsHomeFeedEvenWithLingeringBackNav() {
        val signals = InstagramUiSignals(
            ids = setOf("main_feed_action_bar", "row_feed_profile_header", "action_bar_button_back"),
            selectedTabs = setOf(InstagramTab.HOME),
            hasBackNavigation = true
        )
        // Must NEVER classify as COMMENTS_OR_DETAIL when main_feed_action_bar is present!
        assertEquals(InstagramScreen.HOME_FEED, InstagramProtectionEngine.classify(signals))
        assertEquals(
            InstagramGuardAction.COVER_FEED,
            InstagramProtectionEngine.guardAction(
                signals,
                InstagramInteraction(InstagramEventKind.CONTENT_CHANGED)
            )
        )
    }

    @Test
    fun clickingBackNavButtonIsNotBlockedContentLaunch() {
        val signals = InstagramUiSignals(
            ids = setOf("action_bar_button_back"),
            sourceIds = setOf("action_bar_button_back"),
            sourceLabels = setOf("geri", "back")
        )
        val click = InstagramInteraction(InstagramEventKind.CLICK)
        assertFalse(InstagramProtectionEngine.isBlockedContentLaunch(signals, click))
    }

    @Test
    fun navigatedBackFromDmWithMainFeedActionBarAlwaysCoversFeed() {
        val signals = InstagramUiSignals(
            ids = setOf("main_feed_action_bar", "row_feed_profile_header"),
            selectedTabs = setOf(InstagramTab.HOME)
        )
        val action = InstagramProtectionEngine.guardAction(
            signals = signals,
            interaction = InstagramInteraction(InstagramEventKind.CONTENT_CHANGED),
            previousScreen = InstagramScreen.DIRECT_MESSAGES,
            navigatedBackFromDm = true
        )
        assertEquals(InstagramGuardAction.COVER_FEED, action)
    }
    @Test
    fun dmGalleryReelPreviewIsAReelsViewer() {
        val signals = InstagramUiSignals(
            ids = setOf(
                "action_bar_button_back", "video_container",
                "feed_preview_keep_watching_backdrop",
                "feed_preview_keep_watching_button",
                "row_feed_profile_header"
            ),
            titles = setOf("reel"),
            hasBackNavigation = true
        )

        assertEquals(InstagramScreen.REELS, InstagramProtectionEngine.classify(signals))
        assertTrue(InstagramSurfacePolicy.needsExit(
            InstagramProtectionEngine.classify(signals)))
    }

    private fun homeFeed(
        sourceIds: Set<String> = emptySet(),
        sourceTab: InstagramTab? = null
    ) = InstagramUiSignals(
        ids = setOf("main_feed_action_bar", "row_feed_profile_header"),
        selectedTabs = setOf(InstagramTab.HOME),
        sourceIds = sourceIds,
        sourceTab = sourceTab
    )
}
