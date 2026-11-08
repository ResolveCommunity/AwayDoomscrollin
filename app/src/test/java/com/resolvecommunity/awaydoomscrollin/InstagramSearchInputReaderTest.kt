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
import java.util.concurrent.CancellationException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], shadows = [InstagramSearchInputReaderTest.NodeShadow::class])
class InstagramSearchInputReaderTest {
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
        @Implementation override fun getText(): CharSequence = error("Search must not read query text")
        @Implementation fun getContentDescription(): CharSequence = error("Search must not read labels")
    }

    private lateinit var current: AccessibilityNodeInfo
    private lateinit var state: NodeShadow
    private var keyboard = false
    private var foregroundWindow = 10
    private lateinit var reader: InstagramSearchInputReader

    @Before fun prepare() {
        current = node("root", Rect(0, 0, 1080, 1920))
        state = Shadow.extract(current)
        reader = InstagramSearchInputReader({ current }, {
            listOf(window(AccessibilityWindowInfo.TYPE_APPLICATION, foregroundWindow, true)) +
                if (keyboard) listOf(window(AccessibilityWindowInfo.TYPE_INPUT_METHOD, 20, false)) else emptyList()
        })
    }
    private fun window(type: Int, id: Int, focused: Boolean) = AccessibilityWindowInfo.obtain().apply {
        shadowOf(this).setType(type)
        shadowOf(this).setId(id)
        shadowOf(this).setFocused(focused)
    }
    private fun node(id: String, bounds: Rect = Rect(0, 50, 1080, 150)) = AccessibilityNodeInfo().apply {
        packageName = "com.instagram.android"
        viewIdResourceName = "com.instagram.android:id/$id"
        className = "android.widget.EditText"
        isVisibleToUser = true
        setBoundsInScreen(bounds)
    }
    private fun add(id: String, bounds: Rect = Rect(0, 50, 1080, 150)) =
        node(id, bounds).also { state.ids[id] = it }
    private fun explore() {
        add("search_tab").isSelected = true
        add("explore_action_bar", Rect(0, 50, 1080, 180))
        add("grid_card_layout_container", Rect(0, 180, 1080, 1800))
        add("tab_bar", Rect(0, 1800, 1080, 1900))
    }
    private fun focus() = add("action_bar_search_edit_text").apply {
        isFocused = true
        state.focus = this
        add("action_bar_button_back")
        keyboard = true
    }
    private fun read() = reader.read { true }

    @Test fun unchangedExploreReturnsOnlyItsProtectedGeometry() {
        explore()
        val result = read()
        assertEquals(InstagramSearchInputSurface.EXPLORE, result.surface)
        assertEquals(InstagramGeometrySample(0, 180, 1080, 1800), result.cover)
    }
    @Test fun focusedSearchWithKeyboardWinsOverRetainedExploreWithoutReadingText() {
        explore(); focus()
        val result = read()
        assertEquals(InstagramSearchInputSurface.FOCUSED_SEARCH, result.surface)
        assertNull(result.cover)
        assertFalse(state.queries.contains("grid_card_layout_container"))
        assertFalse(state.queries.contains("profile_header_container"))
    }
    @Test fun keywordRecommendationTabsKeepTheOfficialDetectorInControl() {
        focus(); add("search_tab_bar_layout")
        assertEquals(InstagramSearchInputSurface.OTHER, read().surface)
    }
    @Test fun occludedKeywordTabsStillCannotReleaseTheGrid() {
        focus(); add("search_tab_bar_layout").isVisibleToUser = false
        assertEquals(InstagramSearchInputSurface.OTHER, read().surface)
    }
    @Test fun focusedSearchAndBackDoNotWaitForTheKeyboardWindow() {
        focus(); keyboard = false
        assertEquals(InstagramSearchInputSurface.FOCUSED_SEARCH, read().surface)
    }
    @Test fun focusWithoutTheNativeSearchBackCannotReleaseTheCover() {
        focus(); state.ids.remove("action_bar_button_back")
        assertEquals(InstagramSearchInputSurface.OTHER, read().surface)
    }
    @Test fun ordinaryMessageOrStoryEditorCannotReleaseAnExploreCover() {
        for (id in listOf("row_thread_composer_edittext", "story_search", "search_edit_text")) {
            focus().viewIdResourceName = "com.instagram.android:id/$id"
            assertEquals(id, InstagramSearchInputSurface.OTHER, read().surface)
        }
    }
    @Test fun retainedInvisibleOrUnfocusedOrNonEditableSearchIsNotProof() {
        val editor = focus()
        editor.isVisibleToUser = false
        assertEquals(InstagramSearchInputSurface.OTHER, read().surface)
        editor.isVisibleToUser = true; editor.isFocused = false
        assertEquals(InstagramSearchInputSurface.OTHER, read().surface)
        editor.isFocused = true; editor.className = "android.widget.TextView"
        assertEquals(InstagramSearchInputSurface.OTHER, read().surface)
    }
    @Test fun anotherPackageOrWindowsEditorCannotReleaseTheCover() {
        val editor = focus()
        editor.packageName = "example.other"
        assertEquals(InstagramSearchInputSurface.OTHER, read().surface)
        editor.packageName = "com.instagram.android"
        Shadow.extract<NodeShadow>(editor).window = 11
        assertEquals(InstagramSearchInputSurface.OTHER, read().surface)
    }
    @Test fun anotherFocusedApplicationWindowRejectsRetainedInputFocus() {
        focus(); foregroundWindow = 11
        assertEquals(InstagramSearchInputSurface.OTHER, read().surface)
    }
    @Test fun foregroundDepartureDuringReadRejectsAnEarlierPositiveResult() {
        focus()
        var calls = 0
        val other = node("root").apply { packageName = "example.other" }
        val reader = InstagramSearchInputReader({ if (++calls == 1) current else other }, {
            listOf(window(AccessibilityWindowInfo.TYPE_INPUT_METHOD, 20, false))
        })
        assertEquals(InstagramSearchInputSurface.OTHER, reader.read { true }.surface)
    }
    @Test fun rootWindowReplacementDuringReadRejectsAnEarlierPositiveResult() {
        focus()
        var calls = 0
        val replacement = node("root").also { Shadow.extract<NodeShadow>(it).window = 11 }
        val reader = InstagramSearchInputReader({ if (++calls == 1) current else replacement }, {
            listOf(window(AccessibilityWindowInfo.TYPE_INPUT_METHOD, 20, false))
        })
        assertEquals(InstagramSearchInputSurface.OTHER, reader.read { true }.surface)
    }
    @Test fun aBackControlOrUnselectedExploreHandsOff() {
        explore(); add("action_bar_button_back")
        assertEquals(InstagramSearchInputSurface.OTHER, read().surface)
        state.ids.remove("action_bar_button_back"); state.ids["search_tab"]!!.isSelected = false
        assertEquals(InstagramSearchInputSurface.OTHER, read().surface)
    }
    @Test fun missingOrInvalidExploreGeometryHandsOff() {
        explore(); state.ids.remove("tab_bar")
        assertEquals(InstagramSearchInputSurface.OTHER, read().surface)
        explore(); state.ids["explore_action_bar"]!!.setBoundsInScreen(Rect(0, 1000, 1080, 1400))
        assertEquals(InstagramSearchInputSurface.OTHER, read().surface)
    }
    @Test(expected = CancellationException::class)
    fun supersededReadsStopBeforeAnyPositiveResult() {
        focus()
        var checks = 0
        reader.read { ++checks < 3 }
    }
    @Test fun foreignRootDoesNotQueryInstagramNodes() {
        current.packageName = "example.other"
        assertEquals(InstagramSearchInputSurface.OTHER, read().surface)
        assertTrue(state.queries.isEmpty())
    }
}
