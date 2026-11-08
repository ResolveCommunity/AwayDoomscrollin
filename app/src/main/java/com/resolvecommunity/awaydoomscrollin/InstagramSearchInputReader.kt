package com.resolvecommunity.awaydoomscrollin

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import java.util.concurrent.CancellationException

internal enum class InstagramSearchInputSurface { EXPLORE, FOCUSED_SEARCH, OTHER }

/** Only geometry and a structural classification leave the serial reader. */
internal data class InstagramSearchInputObservation(
    val surface: InstagramSearchInputSurface,
    val windowId: Int = -1,
    val cover: InstagramGeometrySample? = null,
    val back: InstagramGeometrySample? = null,
    val reelsTab: InstagramGeometrySample? = null
)

/** Narrow fast path for an already covered Explore tab and its focused search editor. */
internal class InstagramSearchInputReader(
    private val root: () -> AccessibilityNodeInfo?,
    private val windows: () -> List<AccessibilityWindowInfo>
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
        checkCurrent()
        // Do not apply a positive result after another app/window took foreground.
        val latest = root() ?: return other()
        checkCurrent()
        if (latest.packageName?.toString() != PACKAGE || latest.windowId != windowId) return other()
        val focusedApp = windows().firstOrNull {
            it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.isFocused
        } ?: return other()
        checkCurrent()
        return if (focusedApp.id == windowId) observation else other()
    }

    private fun other() = InstagramSearchInputObservation(InstagramSearchInputSurface.OTHER)
    private fun Rect.sample() = InstagramGeometrySample(left, top, right, bottom)
    private companion object { const val PACKAGE = "com.instagram.android" }
}
