package com.resolvecommunity.awaydoomscrollin

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowAccessibilityNodeInfo

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], shadows = [InstagramSearchObservationTest.NodeShadow::class])
class InstagramSearchObservationTest {
    @Implements(AccessibilityNodeInfo::class)
    class NodeShadow : ShadowAccessibilityNodeInfo() {
        var window = 10
        var focus: AccessibilityNodeInfo? = null
        val ids = mutableMapOf<String, AccessibilityNodeInfo>()
        val queries = mutableListOf<String>()
        @Implementation override fun getWindowId(): Int = window
        @Implementation fun findFocus(kind: Int): AccessibilityNodeInfo? =
            focus.takeIf { kind == AccessibilityNodeInfo.FOCUS_INPUT }
        @Implementation fun findAccessibilityNodeInfosByViewId(id: String): List<AccessibilityNodeInfo> {
            val compact = id.substringAfter(":id/")
            queries.add(compact)
            return listOfNotNull(ids[compact])
        }
    }
    private lateinit var root: AccessibilityNodeInfo
    private lateinit var state: NodeShadow
    private var keyboard = false
    private lateinit var reader: InstagramSearchObservationReader

    @Before fun prepare() {
        root = node("root", Rect(0, 0, 1080, 1920))
        state = Shadow.extract(root)
        reader = InstagramSearchObservationReader({ root }, {
            if (keyboard) listOf(AccessibilityWindowInfo.obtain().apply {
                shadowOf(this).setType(AccessibilityWindowInfo.TYPE_INPUT_METHOD)
            }) else emptyList()
        }, InstagramGeometrySample(0, 0, 1080, 1920), 1f)
    }

    private fun node(id: String, bounds: Rect): AccessibilityNodeInfo = AccessibilityNodeInfo().apply {
        packageName = "com.instagram.android"
        viewIdResourceName = "com.instagram.android:id/$id"
        className = "android.widget.EditText"
        isVisibleToUser = true
        setBoundsInScreen(bounds)
    }
    private fun add(id: String, bounds: Rect = Rect(0, 0, 1080, 100)): AccessibilityNodeInfo =
        node(id, bounds).also { state.ids[id] = it }
    private fun explore() {
        add("search_tab", Rect(600, 1800, 800, 1900)).isSelected = true
        add("explore_action_bar", Rect(0, 40, 1080, 180))
        add("grid_card_layout_container", Rect(0, 180, 1080, 1800))
        add("tab_bar", Rect(0, 1800, 1080, 1900))
    }

    @Test fun exploreGeometryCoversTheGridAndPreservesChrome() {
        explore()
        val result = reader.read()
        assertEquals(InstagramSearchSurface.EXPLORE, result.surface)
        assertEquals(InstagramGeometrySample(0, 180, 1080, 1800), result.cover)
        assertEquals(10, result.windowId)
    }

    @Test fun focusedInputWinsOverStaleExploreNodesWithoutWalkingTheirSubtrees() {
        explore(); keyboard = true
        state.focus = add("action_bar_search_edit_text").apply { isFocused = true }
        val result = reader.read()
        assertEquals(InstagramSearchSurface.SEARCH, result.surface)
        assertNull(result.cover)
        assertTrue(result.keyboardVisible)
        assertFalse(state.queries.contains("profile_header_container"))
        assertFalse(state.queries.contains("grid_card_layout_container"))
    }

    @Test fun ordinaryMessageEditorCannotReleaseAnExploreCover() {
        explore(); keyboard = true
        state.focus = add("row_thread_composer_edittext").apply { isFocused = true }
        assertEquals(InstagramSearchSurface.EXPLORE, reader.read().surface)
    }

    @Test fun closedKeyboardCannotTurnUnfocusedExploreIntoSearch() {
        explore()
        state.focus = add("action_bar_search_edit_text").apply { isFocused = true }
        assertEquals(InstagramSearchSurface.EXPLORE, reader.read().surface)
    }

    @Test fun anotherWindowsFocusedEditorIsNotCurrentSearch() {
        explore(); keyboard = true
        state.focus = add("action_bar_search_edit_text").apply { isFocused = true }
        Shadow.extract<NodeShadow>(state.focus!!).window = 11
        assertEquals(InstagramSearchSurface.EXPLORE, reader.read().surface)
    }

    @Test fun searchBackAndEditorRecognizeSearchBeforeKeyboardIsPublished() {
        add("action_bar_search_edit_text", Rect(100, 50, 1000, 140))
        add("action_bar_button_back", Rect(0, 50, 100, 140))
        val result = reader.read()
        assertEquals(InstagramSearchSurface.SEARCH, result.surface)
        assertNull(result.cover)
    }

    @Test fun keywordRecommendationGridRemainsCoveredWithKeyboardClosed() {
        add("action_bar_search_edit_text", Rect(100, 50, 1000, 140))
        add("action_bar_button_back", Rect(0, 50, 100, 140))
        add("search_tab_bar_layout", Rect(0, 140, 1080, 220))
        add("media_previews_rv", Rect(0, 220, 1080, 1800))
        add("tab_bar", Rect(0, 1800, 1080, 1900))
        val result = reader.read()
        assertEquals(InstagramSearchSurface.SEARCH, result.surface)
        assertEquals(InstagramGeometrySample(0, 220, 1080, 1800), result.cover)
    }

    @Test fun profileOrViewerWithRetainedSearchChromeHandsOffToTheFullDetector() {
        for (id in listOf("profile_header_container", "root_clips_layout", "story_viewer_container")) {
            state.ids.clear()
            add("action_bar_search_edit_text"); add("action_bar_button_back"); add(id)
            assertEquals(id, InstagramSearchSurface.OTHER, reader.read().surface)
        }
    }

    @Test fun aSelectedDifferentTabHandsOffEvenWithoutACompleteTree() {
        add("direct_tab").isSelected = true
        assertEquals(InstagramSearchSurface.OTHER, reader.read().surface)
    }

    @Test fun incompleteTreesKeepTheExistingCover() {
        assertEquals(InstagramSearchSurface.UNKNOWN, reader.read().surface)
    }

    @Test fun anotherPackageIsAPlainDeparture() {
        root.packageName = "example.other"
        assertEquals(InstagramSearchSurface.DEPARTED, reader.read().surface)
        assertTrue(state.queries.isEmpty())
    }

    @Test fun foregroundWatchDoesNotClassifyOrTraverseTheTree() {
        assertEquals(InstagramSearchSurface.FOREGROUND, reader.checkForeground().surface)
        assertTrue(state.queries.isEmpty())
    }

    @Test fun anAppDepartureDuringDetectionCannotPublishAnExploreCover() {
        explore()
        val other = node("other", Rect(0, 0, 1080, 1920)).apply { packageName = "example.other" }
        var reads = 0
        val changingReader = InstagramSearchObservationReader({ if (reads++ == 0) root else other },
            { emptyList() }, InstagramGeometrySample(0, 0, 1080, 1920), 1f)
        assertEquals(InstagramSearchSurface.DEPARTED, changingReader.read().surface)
    }

    @Test fun aWindowSwapDuringDetectionRequiresFreshClassification() {
        explore()
        val other = node("new_window", Rect(0, 0, 1080, 1920))
        Shadow.extract<NodeShadow>(other).window = 11
        var reads = 0
        val changingReader = InstagramSearchObservationReader({ if (reads++ == 0) root else other },
            { emptyList() }, InstagramGeometrySample(0, 0, 1080, 1920), 1f)
        assertEquals(InstagramSearchSurface.UNKNOWN, changingReader.read().surface)
    }

    @Test fun readerDoesNotReadEditableTextOrLabels() {
        keyboard = true
        state.focus = add("action_bar_search_edit_text").apply {
            isFocused = true
            text = "SYNTHETIC_PRIVATE_QUERY"
            contentDescription = "SYNTHETIC_PRIVATE_LABEL"
        }
        assertFalse(reader.read().toString().contains("SYNTHETIC_PRIVATE"))
    }

    @Test fun supersededReadsStopBetweenQueries() {
        explore()
        org.junit.Assert.assertThrows(java.util.concurrent.CancellationException::class.java) {
            reader.read(isCurrent = { state.queries.size < 2 })
        }
        assertEquals(2, state.queries.size)
    }
    private fun home() {
        add("feed_tab", Rect(0, 1800, 200, 1900)).isSelected = true
        add("main_feed_action_bar", Rect(0, 40, 1080, 180))
        add("reels_tray_container", Rect(0, 180, 1080, 420))
        add("tab_bar", Rect(0, 1800, 1080, 1900))
    }
    private fun inbox() {
        add("direct_tab").isSelected = true
        add("direct_inbox_action_bar")
        add("inbox_refreshable_thread_list_recyclerview", Rect(0, 200, 1080, 1800))
    }
    @Test fun selectedHomeMeasuresStoriesAndFeedWithoutReadingPosts() {
        home()
        val result = reader.read()
        assertEquals(InstagramSearchSurface.HOME, result.surface)
        assertEquals(InstagramGeometrySample(0, 420, 1080, 1800), result.cover)
        assertEquals(InstagramGeometrySample(0, 180, 1080, 420), result.stories)
        assertFalse(state.queries.any { it.startsWith("row_feed_") })
    }
    @Test fun missingOrOffscreenStoriesNeverUncoverFeedUsingGuessedGeometry() {
        home(); state.ids.remove("reels_tray_container")
        assertEquals(InstagramSearchSurface.OTHER, reader.read().surface)
        add("reels_tray_container", Rect(0, -200, 1080, 200))
        assertEquals(InstagramSearchSurface.OTHER, reader.read().surface)
    }
    @Test fun aStoryOverMountedHomeCannotPublishHomeCover() {
        home(); add("reel_viewer_root")
        assertEquals(InstagramSearchSurface.OTHER, reader.read().surface)
    }
    @Test fun collapsedHomeHeaderStillUsesMeasuredStoriesBoundary() {
        home(); state.ids.remove("main_feed_action_bar")
        add("reels_tray_container", Rect(0, 98, 1080, 345))
        val result = reader.read()
        assertEquals(InstagramSearchSurface.HOME, result.surface)
        assertEquals(InstagramGeometrySample(0, 345, 1080, 1800), result.cover)
        assertEquals(InstagramGeometrySample(0, 98, 1080, 345), result.stories)
    }
    @Test fun partiallyCollapsedStoriesAreClippedBelowRemainingHeader() {
        home(); add("reels_tray_container", Rect(0, 98, 1080, 345))
        val result = reader.read()
        assertEquals(InstagramSearchSurface.HOME, result.surface)
        assertEquals(InstagramGeometrySample(0, 180, 1080, 345), result.stories)
        assertEquals(InstagramGeometrySample(0, 345, 1080, 1800), result.cover)
    }
    @Test fun collapsedHeaderWithoutTrayOrWithActiveProfileCannotBeHome() {
        home(); state.ids.remove("main_feed_action_bar"); state.ids.remove("reels_tray_container")
        assertEquals(InstagramSearchSurface.OTHER, reader.read().surface)
        home(); state.ids.remove("main_feed_action_bar"); add("profile_header_container")
        assertEquals(InstagramSearchSurface.OTHER, reader.read().surface)
    }
    @Test fun conversationOverRetainedInboxCannotRemountMainTabGuards() {
        for (id in listOf("direct_thread_header", "message_list", "message_composer_bar")) {
            state.ids.clear(); inbox(); add(id)
            assertEquals(id, InstagramSearchSurface.OTHER, reader.read().surface)
        }
    }
    @Test fun completeSelectedInboxYieldsAnImmutableRelease() {
        inbox()
        val result = reader.read()
        assertEquals(InstagramSearchSurface.INBOX, result.surface)
        assertNotNull(result.inbox)
        assertNull(result.cover)
    }
    @Test fun retainedOrTranslatedInboxAndMountedViewersCannotReleaseFeed() {
        for (id in listOf("root_clips_layout", "clips_viewer", "clips_viewer_view_pager",
                "clips_view_pager", "thread_details_pager", "story_viewer_container",
                "reel_item_toolbar_container", "reel_viewer_root", "reel_viewer_content_layout")) {
            state.ids.clear(); inbox(); add(id)
            assertEquals(id, InstagramSearchSurface.OTHER, reader.read().surface)
        }
        state.ids.clear(); inbox()
        add("inbox_refreshable_thread_list_recyclerview", Rect(400, 200, 1480, 1800))
        assertEquals(InstagramSearchSurface.OTHER, reader.read().surface)
        state.ids.clear(); inbox(); state.ids["direct_tab"]!!.isSelected = false
        assertNotEquals(InstagramSearchSurface.INBOX, reader.read().surface)
    }
    @Test fun inboxDepartureDuringReadCannotPublishRelease() {
        inbox()
        val other = node("other", Rect(0, 0, 1080, 1920)).apply { packageName = "example.other" }
        var reads = 0
        val changing = InstagramSearchObservationReader({ if (reads++ == 0) root else other },
            { emptyList() }, InstagramGeometrySample(0, 0, 1080, 1920), 1f)
        assertEquals(InstagramSearchSurface.DEPARTED, changing.read().surface)
    }

    private val directBounds = InstagramGeometrySample(400, 1800, 600, 1900)
    private fun directTap(source: AccessibilityNodeInfo, windowId: Int = 10,
        eventTime: Long = android.os.SystemClock.uptimeMillis()) =
        InstagramDirectTap(windowId, directBounds, eventTime) { source }
    private fun directSource() = node("direct_tab_icon_view", Rect(430, 1820, 570, 1880))
    private fun selectedMessagesTab() = add("direct_tab", Rect(400, 1800, 600, 1900)).apply { isSelected = true }

    @Test fun committedMessagesTapDoesNotWaitForInboxBodyOrBroadViewerScan() {
        selectedMessagesTab()
        val result = reader.read(directTap = directTap(directSource()))
        assertEquals(InstagramSearchSurface.INBOX_TRANSITION, result.surface)
        assertNull(result.cover)
        assertFalse(state.queries.contains("inbox_refreshable_thread_list_recyclerview"))
        assertFalse(state.queries.contains("profile_header_container"))
        assertFalse(state.queries.contains("action_bar_search_edit_text"))
    }
    @Test fun aTapWithoutNativeMessagesCommitmentDoesNotReleaseHome() {
        home(); selectedMessagesTab().isSelected = false
        assertEquals(InstagramSearchSurface.HOME, reader.read(directTap = directTap(directSource())).surface)
    }
    @Test fun unrelatedAndOversizedClickSourcesDoNotGetTheMessagesShortcut() {
        selectedMessagesTab()
        for (source in listOf(node("media_container", Rect(430, 1820, 570, 1880)),
                node("direct_tab", Rect(0, 0, 1080, 1920)),
                node("direct_tab", Rect(100, 100, 300, 180)))) {
            assertNotEquals(InstagramSearchSurface.INBOX_TRANSITION, reader.read(directTap = directTap(source)).surface)
        }
    }
    @Test fun hiddenForeignAndWrongWindowSourcesCannotReleaseTheCover() {
        selectedMessagesTab()
        val source = directSource()
        source.isVisibleToUser = false
        assertNotEquals(InstagramSearchSurface.INBOX_TRANSITION, reader.read(directTap = directTap(source)).surface)
        source.isVisibleToUser = true; source.packageName = "example.other"
        assertNotEquals(InstagramSearchSurface.INBOX_TRANSITION, reader.read(directTap = directTap(source)).surface)
        source.packageName = "com.instagram.android"; Shadow.extract<NodeShadow>(source).window = 11
        assertNotEquals(InstagramSearchSurface.INBOX_TRANSITION, reader.read(directTap = directTap(source)).surface)
    }
    @Test fun staleOrWrongWindowTapDoesNotEvenFetchItsSource() {
        selectedMessagesTab()
        var sourceReads = 0
        val clocked = InstagramSearchObservationReader({ root }, { emptyList() },
            InstagramGeometrySample(0, 0, 1080, 1920), 1f, now = { 1_000L })
        for (hint in listOf(InstagramDirectTap(10, directBounds, 400L) { sourceReads++; directSource() },
                InstagramDirectTap(11, directBounds, 1_000L) { sourceReads++; directSource() })) {
            assertNotEquals(InstagramSearchSurface.INBOX_TRANSITION, clocked.read(directTap = hint).surface)
        }
        assertEquals(0, sourceReads)
    }
    @Test fun aBackStackOrVisibleViewerPreventsTheEarlyHandoff() {
        for (id in listOf("action_bar_button_back", "root_clips_layout", "reel_viewer_root", "story_viewer_container")) {
            state.ids.clear(); selectedMessagesTab(); add(id)
            assertNotEquals(id, InstagramSearchSurface.INBOX_TRANSITION, reader.read(directTap = directTap(directSource())).surface)
        }
    }
    @Test fun leavingInstagramDuringTapVerificationCannotPublishAHandoff() {
        selectedMessagesTab()
        val other = node("other", Rect(0, 0, 1080, 1920)).apply { packageName = "example.other" }
        var reads = 0
        val changing = InstagramSearchObservationReader({ if (reads++ == 0) root else other },
            { emptyList() }, InstagramGeometrySample(0, 0, 1080, 1920), 1f)
        assertEquals(InstagramSearchSurface.DEPARTED, changing.read(directTap = directTap(directSource())).surface)
    }
    @Test fun messagesTapHintDoesNotReadOrRetainPrivateLabels() {
        selectedMessagesTab()
        val source = directSource().apply {
            text = "SYNTHETIC_PRIVATE_MESSAGE"
            contentDescription = "SYNTHETIC_PRIVATE_ACCOUNT"
        }
        assertFalse(reader.read(directTap = directTap(source)).toString().contains("SYNTHETIC_PRIVATE"))
    }

    @Test fun messagesTapCanUseCurrentNativeBoundsWhenHomeLayoutHadNoCachedTab() {
        selectedMessagesTab()
        val hint = InstagramDirectTap(10, null, android.os.SystemClock.uptimeMillis()) { directSource() }
        assertEquals(InstagramSearchSurface.INBOX_TRANSITION, reader.read(directTap = hint).surface)
    }
    @Test fun noCachedTabDoesNotAllowASourceOutsideTheActualNativeTab() {
        selectedMessagesTab()
        val source = node("direct_tab", Rect(700, 1800, 900, 1900))
        val hint = InstagramDirectTap(10, null, android.os.SystemClock.uptimeMillis()) { source }
        assertNotEquals(InstagramSearchSurface.INBOX_TRANSITION, reader.read(directTap = hint).surface)
    }

}
