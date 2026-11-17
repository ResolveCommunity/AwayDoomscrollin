package com.resolvecommunity.awaydoomscrollin

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import java.util.concurrent.CancellationException

internal enum class InstagramSearchInputSurface { EXPLORE, FOCUSED_SEARCH, HOME, OTHER }

/** Only geometry and a structural classification leave the serial reader. */
internal data class InstagramSearchInputObservation(
    val surface: InstagramSearchInputSurface,
    val windowId: Int = -1,
    val cover: InstagramGeometrySample? = null,
    val back: InstagramGeometrySample? = null,
    val reelsTab: InstagramGeometrySample? = null
)

/**
 * Narrow fast path for an already covered Explore tab and its focused search
 * editor, and for an already covered Home feed. Home is read only when
 * [expectHome] says the service is on a covered Home; the feed under the cover
 * keeps changing, and the full scan of each change held the main thread for
 * 350–1100 ms, so a tap on another tab waited for it.
 */
internal class InstagramSearchInputReader(
    private val root: () -> AccessibilityNodeInfo?,
    private val windows: () -> List<AccessibilityWindowInfo>,
    private val expectHome: () -> Boolean = { false },
    private val homeStoriesBlocked: () -> Boolean = { false }
) {
    fun read(isCurrent: () -> Boolean): InstagramSearchInputObservation {
        fun checkCurrent() {
            if (!isCurrent()) throw CancellationException("Superseded search input read")
        }
        checkCurrent()
        val current = root() ?: return other()
        if (current.packageName?.toString() != PACKAGE) return other()
        val windowId = current.windowId
        val window = Rect().also(current::getBoundsInScreen)
        if (window.isEmpty) return other()
        val cache = HashMap<String, List<AccessibilityNodeInfo>>()
        fun nodes(id: String): List<AccessibilityNodeInfo> {
            checkCurrent()
            return cache.getOrPut(id) {
                current.findAccessibilityNodeInfosByViewId("$PACKAGE:id/$id").orEmpty()
            }
        }
        fun visible(id: String) = nodes(id).firstOrNull { it.isVisibleToUser }
        fun bounds(id: String) = visible(id)?.let {
            Rect().also(it::getBoundsInScreen).takeIf { rect ->
                !rect.isEmpty && rect.intersect(window)
            }?.sample()
        }
        checkCurrent()
        if (expectHome()) {
            val home = readCoveredHome(window, windowId, ::visible, ::bounds, ::checkCurrent)
                ?: return other()
            return confirmForeground(windowId, home, ::checkCurrent)
        }
        val focus = current.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        val focusedSearch = focus != null && focus.packageName?.toString() == PACKAGE &&
            focus.windowId == windowId && focus.isVisibleToUser && focus.isFocused &&
            focus.viewIdResourceName == "$PACKAGE:id/action_bar_search_edit_text" &&
            focus.className?.toString()?.contains("EditText", ignoreCase = true) == true
        val observation = if (focusedSearch) {
            // Focus plus Search's native Back is positive destination evidence,
            // as in the official classifier. The IME window can still be absent
            // at this point; requiring it delayed the tested search transition.
            // Attached keyword tabs retain the protected recommendation grid.
            if (nodes("search_tab_bar_layout").isNotEmpty()) return other()
            val back = visible("action_bar_button_back") ?: return other()
            if (back.windowId != windowId) return other()
            val backBounds = bounds("action_bar_button_back") ?: return other()
            InstagramSearchInputObservation(InstagramSearchInputSurface.FOCUSED_SEARCH,
                windowId, back = backBounds, reelsTab = bounds("clips_tab"))
        } else {
            if (visible("action_bar_button_back") != null) return other()
            val tab = visible("search_tab") ?: return other()
            val selected = tab.isSelected || (0 until minOf(tab.childCount, 3)).any {
                checkCurrent()
                tab.getChild(it)?.isSelected == true
            }
            if (!selected || visible("grid_card_layout_container") == null) return other()
            val header = bounds("explore_action_bar") ?: return other()
            val bottom = bounds("tab_bar")?.top ?: return other()
            if (header.left < window.left || header.right > window.right ||
                header.right - header.left < window.width() / 2 ||
                header.bottom >= window.centerY() || bottom <= window.centerY()) return other()
            InstagramSearchInputObservation(InstagramSearchInputSurface.EXPLORE, windowId,
                cover = InstagramGeometrySample(window.left, header.bottom, window.right, bottom))
        }
        return confirmForeground(windowId, observation, ::checkCurrent)
    }

    /** Do not apply a positive result after another app/window took foreground. */
    private fun confirmForeground(
        windowId: Int,
        observation: InstagramSearchInputObservation,
        checkCurrent: () -> Unit
    ): InstagramSearchInputObservation {
        checkCurrent()
        val latest = root() ?: return other()
        checkCurrent()
        if (latest.packageName?.toString() != PACKAGE || latest.windowId != windowId) return other()
        val focusedApp = windows().firstOrNull {
            it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.isFocused
        } ?: return other()
        checkCurrent()
        return if (focusedApp.id == windowId) observation else other()
    }

    /**
     * The plain Home feed and the cover the official geometry gives it: from
     * the Stories tray's bottom (the header's in Strict, where Stories are
     * covered too) down to the bottom tab bar. Anything else, or any doubt,
     * returns null and the full scan decides, exactly as before.
     */
    private fun readCoveredHome(
        window: Rect,
        windowId: Int,
        visible: (String) -> AccessibilityNodeInfo?,
        bounds: (String) -> InstagramGeometrySample?,
        checkCurrent: () -> Unit
    ): InstagramSearchInputObservation? {
        if (visible("action_bar_button_back") != null) return null
        val tab = visible("feed_tab") ?: return null
        val selected = tab.isSelected || (0 until minOf(tab.childCount, 3)).any {
            checkCurrent()
            tab.getChild(it)?.isSelected == true
        }
        if (!selected) return null
        val header = bounds("main_feed_action_bar") ?: return null
        if (header.top < window.top || header.bottom >= window.centerY()) return null
        // A viewer, Messages sliding in, or Explore: not the plain Home feed.
        if (NOT_PLAIN_HOME_IDS.any { visible(it) != null }) return null
        val bar = bounds("tab_bar") ?: return null
        if (bar.right - bar.left < window.width() / 2 || bar.top <= window.centerY()) return null
        val stories = if (homeStoriesBlocked()) null else InstagramHomeGeometry.storiesTrayIds
            .firstNotNullOfOrNull { bounds(it) }?.bottom
            ?.takeIf { it > window.top && it < window.centerY() }
        val top = InstagramHomeGeometry.top(window.top, stories, header.bottom)
        return InstagramSearchInputObservation(InstagramSearchInputSurface.HOME, windowId,
            cover = InstagramGeometrySample(window.left, minOf(top, bar.top - 1), window.right, bar.top))
    }

    private fun other() = InstagramSearchInputObservation(InstagramSearchInputSurface.OTHER)
    private fun Rect.sample() = InstagramGeometrySample(left, top, right, bottom)
    private companion object {
        const val PACKAGE = "com.instagram.android"
        val NOT_PLAIN_HOME_IDS = listOf(
            "reel_viewer_root", "story_viewer_container", "direct_inbox_action_bar", "explore_action_bar")
    }
}
