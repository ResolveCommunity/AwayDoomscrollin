package com.resolvecommunity.awaydoomscrollin

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.LooperMode
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowAccessibilityNodeInfo
import org.robolectric.shadows.ShadowAccessibilityService

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], shadows = [InstagramFocusedSearchTest.ServiceShadow::class,
    InstagramFocusedSearchTest.NodeShadow::class])
@LooperMode(LooperMode.Mode.PAUSED)
class InstagramFocusedSearchTest {
    @Implements(AccessibilityService::class)
    class ServiceShadow : ShadowAccessibilityService() {
        var root: AccessibilityNodeInfo? = null
        @Implementation fun getRootInActiveWindow(): AccessibilityNodeInfo? = root
    }

    @Implements(AccessibilityNodeInfo::class)
    class NodeShadow : ShadowAccessibilityNodeInfo() {
        var testWindowId = 10
        var inputFocus: AccessibilityNodeInfo? = null
        var lookups = 0
        var focusQueries = 0
        @Implementation override fun getWindowId(): Int = testWindowId
        @Implementation fun findFocus(focus: Int): AccessibilityNodeInfo? {
            focusQueries++
            return inputFocus.takeIf { focus == AccessibilityNodeInfo.FOCUS_INPUT }
        }
        @Suppress("UNUSED_PARAMETER")
        @Implementation fun findAccessibilityNodeInfosByViewId(id: String): List<AccessibilityNodeInfo> {
            lookups++
            return emptyList()
        }
    }

    private lateinit var service: AntiScrollService
    private lateinit var overlay: InstagramFeedOverlayController
    private lateinit var root: AccessibilityNodeInfo
    private lateinit var editor: AccessibilityNodeInfo

    @Before fun prepare() {
        service = Robolectric.buildService(AntiScrollService::class.java).create().get()
        root = node("root", "android.widget.FrameLayout")
        editor = node("action_bar_search_edit_text", "android.widget.EditText").apply { isFocused = true }
        Shadow.extract<NodeShadow>(root).inputFocus = editor
        Shadow.extract<ServiceShadow>(service).root = root
        keyboard(true)
        setField("lastInstagramScreen", InstagramScreen.EXPLORE)
        overlay = AntiScrollService::class.java.getDeclaredMethod("getInstagramOverlay").run {
            isAccessible = true
            invoke(service) as InstagramFeedOverlayController
        }
        assertTrue(overlay.show(Rect(0, 200, 1080, 1800), InstagramScreen.EXPLORE, true))
    }

    @After fun closeService() { service.onDestroy() }

    private fun node(id: String, widget: String): AccessibilityNodeInfo = AccessibilityNodeInfo().apply {
        packageName = "com.instagram.android"
        viewIdResourceName = "com.instagram.android:id/$id"
        className = widget
        isVisibleToUser = true
        setBoundsInScreen(Rect(0, 0, 1080, 1920))
    }

    private fun keyboard(visible: Boolean) {
        val windows = if (visible) listOf(AccessibilityWindowInfo.obtain().apply {
            shadowOf(this).setType(AccessibilityWindowInfo.TYPE_INPUT_METHOD)
        }) else emptyList()
        shadowOf(service).setWindows(windows)
    }

    private fun setField(name: String, value: Any) {
        AntiScrollService::class.java.getDeclaredField(name).apply {
            isAccessible = true
            set(service, value)
        }
    }

    private fun scan() {
        AntiScrollService::class.java.getDeclaredMethod("scanInstagram", AccessibilityEvent::class.java)
            .apply { isAccessible = true }.invoke(service, null)
    }

    @Test fun focusedSearchWithKeyboardReleasesExploreBeforeUnrelatedViewerProbes() {
        scan()
        assertNull(overlay.visibleCurtain())
        val state = AntiScrollService::class.java.getDeclaredField("lastInstagramScreen").run {
            isAccessible = true
            get(service)
        }
        assertEquals(InstagramScreen.SEARCH_RESULTS, state)
        assertTrue("Focused search must not walk unrelated player/profile IDs",
            Shadow.extract<NodeShadow>(root).lookups <= 2)
    }

    @Test fun subsequentTypingScansAlsoAvoidTheGeneralTreeWalk() {
        scan()
        Shadow.extract<NodeShadow>(root).lookups = 0
        scan()
        assertNull(overlay.visibleCurtain())
        assertTrue(Shadow.extract<NodeShadow>(root).lookups <= 2)
    }

    @Test fun unfocusedSearchDoesNotReleaseExplore() {
        editor.isFocused = false
        scan()
        assertNotNull(overlay.visibleCurtain())
    }

    @Test fun keyboardAloneInAnOrdinaryMessageEditorDoesNotReleaseExplore() {
        editor.viewIdResourceName = "com.instagram.android:id/row_thread_composer_edittext"
        scan()
        assertNotNull(overlay.visibleCurtain())
    }

    @Test fun invisibleRetainedEditorDoesNotReleaseExplore() {
        editor.isVisibleToUser = false
        scan()
        assertNotNull(overlay.visibleCurtain())
    }

    @Test fun differentWindowFocusCannotReleaseExplore() {
        Shadow.extract<NodeShadow>(editor).testWindowId = 11
        scan()
        assertNotNull(overlay.visibleCurtain())
    }

    @Test fun closedKeyboardDoesNotEnableTheShortcut() {
        keyboard(false)
        scan()
        assertNotNull(overlay.visibleCurtain())
    }

    @Test fun nonEditableViewWithSearchIdDoesNotReleaseExplore() {
        editor.className = "android.widget.TextView"
        scan()
        assertNotNull(overlay.visibleCurtain())
    }

    @Test fun pendingProtectedNavigationKeepsItsCoverUntilDispatch() {
        val latch = AntiScrollService::class.java.getDeclaredField("instagramProtectedNavigation").run {
            isAccessible = true
            get(service) as InstagramProtectedNavigationLatch
        }
        latch.begin(InstagramTab.HOME, 10, InstagramTab.SEARCH, android.os.SystemClock.uptimeMillis())
        scan()
        assertNotNull(overlay.visibleCurtain())
        assertEquals(0, Shadow.extract<NodeShadow>(root).focusQueries)
    }

    @Test fun otherAppsCannotReleaseInstagramProtection() {
        root.packageName = "example.other"
        // The ordinary foreground-departure path may hide the curtain, but
        // must not query an unrelated app's input focus as Instagram Search.
        scan()
        assertEquals(0, Shadow.extract<NodeShadow>(root).focusQueries)
    }
}
