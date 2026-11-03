package com.resolvecommunity.awaydoomscrollin

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import java.util.concurrent.CancellationException

internal enum class InstagramSearchSurface { HOME, INBOX, INBOX_TRANSITION, EXPLORE, SEARCH, OTHER, UNKNOWN, DEPARTED, FOREGROUND }

/** A copied event's source is fetched on the reader thread, never on the UI thread. */
internal data class InstagramDirectTap(
    val windowId: Int,
    val tab: InstagramGeometrySample?,
    val eventTime: Long,
    val source: () -> AccessibilityNodeInfo?
)

/** Values only: no accessibility nodes, Views, labels or editable text cross threads. */
internal data class InstagramSearchObservation(
    val surface: InstagramSearchSurface,
    val windowId: Int = -1,
    val packageName: String? = null,
    val cover: InstagramGeometrySample? = null,
    val back: InstagramGeometrySample? = null,
    val reelsTab: InstagramGeometrySample? = null,
    val homeTab: InstagramGeometrySample? = null,
    val searchTab: InstagramGeometrySample? = null,
    val searchEntry: InstagramGeometrySample? = null,
    val keyboardVisible: Boolean = false,
    val stories: InstagramGeometrySample? = null,
    val inbox: InstagramGeometrySample? = null,
    val directTab: InstagramGeometrySample? = null,
    val diagnostic: String? = null
)

/** Read-only, per-observation node cache. This reader is confined to one worker. */
internal class InstagramSearchObservationReader(
    private val root: () -> AccessibilityNodeInfo?,
    private val windows: () -> List<AccessibilityWindowInfo>,
    private val fallbackWindow: InstagramGeometrySample,
    private val density: Float,
    private val now: () -> Long = { android.os.SystemClock.uptimeMillis() }
) {
    companion object { private const val PACKAGE = "com.instagram.android" }

    fun checkForeground(): InstagramSearchObservation {
        val current = root() ?: return InstagramSearchObservation(InstagramSearchSurface.UNKNOWN)
        val pkg = current.packageName?.toString()
        return InstagramSearchObservation(
            if (pkg == PACKAGE) InstagramSearchSurface.FOREGROUND else InstagramSearchSurface.DEPARTED,
            current.windowId, pkg)
    }

    fun read(isCurrent: () -> Boolean = { true }, directTap: InstagramDirectTap? = null): InstagramSearchObservation {
        fun checkCurrent() { if (!isCurrent()) throw CancellationException("Superseded search read") }
        checkCurrent()
        val observation = readCurrentRoot(::checkCurrent, directTap)
        checkCurrent()
        if (observation.surface == InstagramSearchSurface.EXPLORE ||
            observation.surface == InstagramSearchSurface.SEARCH ||
            observation.surface == InstagramSearchSurface.HOME || observation.surface == InstagramSearchSurface.INBOX ||
            observation.surface == InstagramSearchSurface.INBOX_TRANSITION) {
            val current = checkForeground()
            if (current.surface == InstagramSearchSurface.DEPARTED) return current
            if (current.surface == InstagramSearchSurface.UNKNOWN ||
                current.windowId != observation.windowId) {
                return InstagramSearchObservation(InstagramSearchSurface.UNKNOWN)
            }
        }
        return observation
    }

    private fun readCurrentRoot(checkCurrent: () -> Unit, directTap: InstagramDirectTap?): InstagramSearchObservation {
        val current = root() ?: return InstagramSearchObservation(InstagramSearchSurface.UNKNOWN)
        val pkg = current.packageName?.toString()
        if (pkg != PACKAGE) return InstagramSearchObservation(
            InstagramSearchSurface.DEPARTED, packageName = pkg)
        val windowId = current.windowId
        val window = Rect().also(current::getBoundsInScreen).takeUnless(Rect::isEmpty)
            ?: fallbackWindow.rect()
        val cache = HashMap<String, List<AccessibilityNodeInfo>>()
        fun nodes(id: String): List<AccessibilityNodeInfo> {
            checkCurrent()
            return cache.getOrPut(id) {
                current.findAccessibilityNodeInfosByViewId("$PACKAGE:id/$id").orEmpty()
            }
        }
        fun visible(id: String) = nodes(id).firstOrNull { it.isVisibleToUser }
        fun bounds(id: String): InstagramGeometrySample? = visible(id)?.let { node ->
            Rect().also(node::getBoundsInScreen).takeIf { it.intersect(window) && !it.isEmpty }?.sample()
        }
        fun selected(id: String): Boolean = nodes(id).any { node ->
            node.isVisibleToUser && (node.isSelected || (0 until node.childCount).any {
                node.getChild(it)?.isSelected == true
            })
        }
        fun result(surface: InstagramSearchSurface, cover: InstagramGeometrySample? = null,
            keyboard: Boolean = false, entry: InstagramGeometrySample? = null,
            stories: InstagramGeometrySample? = null, inbox: InstagramGeometrySample? = null) =
            InstagramSearchObservation(surface, windowId, pkg, cover,
                back = bounds("action_bar_button_back"), reelsTab = bounds("clips_tab"),
                homeTab = if (surface in setOf(InstagramSearchSurface.EXPLORE, InstagramSearchSurface.HOME, InstagramSearchSurface.INBOX)) bounds("feed_tab") else null,
                searchTab = if (surface in setOf(InstagramSearchSurface.EXPLORE, InstagramSearchSurface.HOME, InstagramSearchSurface.INBOX)) bounds("search_tab") else null,
                searchEntry = entry, keyboardVisible = keyboard, stories = stories, inbox = inbox,
                directTab = bounds("direct_tab"))

        // A real, fresh Messages-tab click plus committed native selection is
        // enough to retire the outgoing feed cover. Do not wait for the inbox
        // list and every retained media node to finish laying out. A tap alone
        // never releases the cover, and ordinary typing/row clicks do not qualify.
        if (directTap != null && directTap.windowId == windowId &&
            now() - directTap.eventTime in 0L..500L) {
            checkCurrent()
            var source = directTap.source()
            val sourceBounds = source?.let { Rect().also(it::getBoundsInScreen) }
            val inTab = sourceBounds != null && !sourceBounds.isEmpty &&
                sourceBounds.centerY() > window.centerY() && sourceBounds.width() <= window.width() / 3 &&
                sourceBounds.height() <= window.height() / 3 &&
                (directTap.tab == null || (directTap.tab.rect().contains(sourceBounds.centerX(), sourceBounds.centerY()) &&
                    sourceBounds.width() <= directTap.tab.right - directTap.tab.left &&
                    sourceBounds.height() <= directTap.tab.bottom - directTap.tab.top))
            var matched = false
            if (inTab) repeat(3) {
                checkCurrent()
                val node = source
                if (!matched && node != null && node.packageName?.toString() == PACKAGE &&
                    node.windowId == windowId && node.isVisibleToUser) {
                    val id = node.viewIdResourceName?.substringAfter(":id/")
                    matched = id == "direct_tab" || id == "direct_tab_icon_view"
                    if (!matched) source = node.parent
                }
            }
            val nativeTab = if (matched) bounds("direct_tab") else null
            val nativeBoundsMatch = nativeTab != null && sourceBounds != null &&
                nativeTab.rect().contains(sourceBounds.centerX(), sourceBounds.centerY()) &&
                sourceBounds.width() <= nativeTab.right - nativeTab.left &&
                sourceBounds.height() <= nativeTab.bottom - nativeTab.top
            if (matched && nativeBoundsMatch && selected("direct_tab") && visible("action_bar_button_back") == null &&
                listOf("root_clips_layout", "reel_viewer_root", "story_viewer_container")
                    .none { visible(it) != null } && now() - directTap.eventTime in 0L..500L) {
                return InstagramSearchObservation(InstagramSearchSurface.INBOX_TRANSITION, windowId, pkg)
            }
        }

        // A positive input-focus proof is independent of stale mounted feed IDs.
        checkCurrent()
        val focus = current.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focus != null && focus.windowId == windowId && focus.isVisibleToUser &&
            focus.viewIdResourceName == "$PACKAGE:id/action_bar_search_edit_text" &&
            InstagramSurfacePolicy.isFocusedSearchField("action_bar_search_edit_text",
                focus.className?.toString().orEmpty(), focus.isFocused) &&
            windows().any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }) {
            return result(InstagramSearchSurface.SEARCH, keyboard = true)
        }

        val back = visible("action_bar_button_back")
        // Only complete, selected main tabs qualify. Retained inboxes underneath
        // viewers and translated lists never release a feed curtain.
        if (back == null && selected("direct_tab")) {
            val list = bounds("inbox_refreshable_thread_list_recyclerview")
            val header = visible("direct_inbox_action_bar") != null ||
                visible("direct_inbox_search_bar") != null || visible("search_row") != null
            if (list != null && header && InstagramInboxPolicy.isStableInboxLayout(list, window.sample()) &&
                listOf("root_clips_layout", "clips_viewer", "clips_viewer_view_pager", "clips_view_pager",
                    "thread_details_pager", "story_viewer_container", "reel_item_toolbar_container",
                    "reel_viewer_root", "reel_viewer_content_layout", "direct_thread_header",
                    "message_list", "message_composer_bar").none { visible(it) != null }) {
                return result(InstagramSearchSurface.INBOX, inbox = list)
            }
            return InstagramSearchObservation(InstagramSearchSurface.OTHER, windowId, pkg)
        }
        if (back == null && selected("feed_tab")) {
            if (listOf("reel_viewer_root", "story_viewer_container", "root_clips_layout",
                    "reel_item_toolbar_container", "profile_header_container", "direct_thread_header",
                    "message_composer_bar").any { visible(it) != null }) {
                return InstagramSearchObservation(InstagramSearchSurface.OTHER, windowId, pkg)
            }
            // Attached sane chrome remains measurable when our curtain occludes
            // it. A missing Stories boundary hands off instead of exposing posts.
            val header = bounds("main_feed_action_bar")
            val stories = InstagramHomeGeometry.storiesTrayIds.firstNotNullOfOrNull { id ->
                nodes(id).firstNotNullOfOrNull { node ->
                    Rect().also(node::getBoundsInScreen).takeIf { rect ->
                        !rect.isEmpty && rect.width() >= window.width() / 2 &&
                            rect.left >= window.left && rect.right <= window.right &&
                            rect.top >= window.top && rect.bottom < window.centerY() &&
                            rect.height() >= 32 * density
                    }?.sample()
                }
            }
            val bottom = bounds("tab_bar")?.top
            // Instagram removes its main action bar when Home scrolls. The
            // selected tab and a sane attached Stories tray still identify Home;
            // requiring the absent header sent every update to the UI detector.
            val storiesTop = maxOf(stories?.top ?: window.top, header?.bottom ?: window.top)
            if (stories != null && storiesTop < stories.bottom &&
                bottom != null && bottom > stories.bottom && bottom > window.centerY()) {
                return result(InstagramSearchSurface.HOME,
                    InstagramGeometrySample(window.left, stories.bottom, window.right, bottom),
                    stories = InstagramGeometrySample(stories.left, storiesTop, stories.right, stories.bottom))
            }
            return InstagramSearchObservation(InstagramSearchSurface.OTHER, windowId, pkg,
                diagnostic = "home-geometry-header-${header != null}-stories-${stories != null}-navigation-${bottom != null}")
        }
        val editor = bounds("action_bar_search_edit_text")
        val searchSelected = selected("search_tab")
        if (back == null && searchSelected) {
            val header = bounds("explore_action_bar")
            val grid = bounds("grid_card_layout_container")
            if (header != null && grid != null) {
                val navigationTop = bounds("tab_bar")?.top ?: bounds("search_tab")?.top
                    ?: (window.bottom - (48 * density).toInt())
                val top = maxOf(window.top, header.bottom, editor?.bottom ?: window.top)
                if (navigationTop > top) return result(InstagramSearchSurface.EXPLORE,
                    InstagramGeometrySample(window.left, top, window.right, navigationTop), entry = editor ?: header)
            }
        }

        // A current viewer/profile takes precedence over retained search chrome.
        if (visible("story_viewer_container") != null || visible("root_clips_layout") != null ||
            visible("profile_header_container") != null || visible("direct_thread_header") != null ||
            visible("direct_inbox_action_bar") != null) {
            return InstagramSearchObservation(InstagramSearchSurface.OTHER, windowId, pkg)
        }
        if (editor != null && back != null) {
            val tabs = bounds("search_tab_bar_layout")
            val mediaGrid = visible("grid_card_layout_container") != null || visible("media_previews_rv") != null
            val keywordCover = if (tabs != null && mediaGrid && tabs.top >= editor.bottom &&
                tabs.top - editor.bottom <= (48 * density).toInt()) {
                val bottom = bounds("tab_bar")?.top?.takeIf { it > tabs.bottom } ?: window.bottom
                InstagramGeometrySample(window.left, tabs.bottom, window.right, bottom)
                    .takeIf { it.bottom - it.top > 80 * density }
            } else null
            return result(InstagramSearchSurface.SEARCH, keywordCover)
        }
        if (selected("feed_tab") || selected("direct_tab") || selected("profile_tab") ||
            selected("clips_tab")) {
            return InstagramSearchObservation(InstagramSearchSurface.OTHER, windowId, pkg)
        }
        return InstagramSearchObservation(InstagramSearchSurface.UNKNOWN, windowId, pkg)
    }

    private fun Rect.sample() = InstagramGeometrySample(left, top, right, bottom)
    private fun InstagramGeometrySample.rect() = Rect(left, top, right, bottom)
}
