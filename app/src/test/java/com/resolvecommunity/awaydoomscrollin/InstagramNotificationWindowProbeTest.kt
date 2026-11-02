package com.resolvecommunity.awaydoomscrollin

import android.graphics.Rect
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
import org.robolectric.annotation.RealObject
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowAccessibilityNodeInfo
import org.robolectric.shadows.ShadowAccessibilityWindowInfo

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], shadows = [InstagramNotificationWindowProbeTest.WindowShadow::class,
    InstagramNotificationWindowProbeTest.NodeShadow::class])
class InstagramNotificationWindowProbeTest {
    @Implements(AccessibilityWindowInfo::class)
    class WindowShadow : ShadowAccessibilityWindowInfo() {
        var testRoot: AccessibilityNodeInfo? = null
        var reads = 0
        var unavailable = false
        @Implementation override fun getRoot(): AccessibilityNodeInfo? {
            reads++
            if (unavailable) throw IllegalStateException("Window disappeared")
            return testRoot
        }
    }

    @Implements(AccessibilityNodeInfo::class)
    class NodeShadow : ShadowAccessibilityNodeInfo() {
        @RealObject private lateinit var node: AccessibilityNodeInfo
        var testWindowId = -1
        var viewer = false
        @Implementation override fun getWindowId(): Int = testWindowId
        @Implementation fun findAccessibilityNodeInfosByViewId(id: String): List<AccessibilityNodeInfo> =
            if (viewer && id == "com.instagram.android:id/comments_recycler") listOf(node)
            else emptyList()
    }

    private lateinit var service: AntiScrollService
    @Before fun prepare() {
        service = Robolectric.buildService(AntiScrollService::class.java).create().get()
    }
    @After fun closeService() { service.onDestroy() }

    private fun node(id: Int, viewer: Boolean = false): AccessibilityNodeInfo =
        AccessibilityNodeInfo().apply {
            packageName = "com.instagram.android"
            isVisibleToUser = true
            setBoundsInScreen(Rect(0, 0, 1080, 1920))
            Shadow.extract<NodeShadow>(this).also { it.testWindowId = id; it.viewer = viewer }
        }

    private fun window(root: AccessibilityNodeInfo, focused: Boolean = false): AccessibilityWindowInfo =
        AccessibilityWindowInfo.obtain().apply {
            shadowOf(this).setFocused(focused)
            Shadow.extract<WindowShadow>(this).testRoot = root
        }

    @Suppress("UNCHECKED_CAST")
    private fun probe(fallback: AccessibilityNodeInfo): Pair<AccessibilityNodeInfo, InstagramScreen>? =
        AntiScrollService::class.java.getDeclaredMethod("findInstagramNotificationViewerRoot",
            AccessibilityNodeInfo::class.java).run {
            isAccessible = true
            invoke(service, fallback) as Pair<AccessibilityNodeInfo, InstagramScreen>?
        }

    @Test fun focusedViewerDoesNotFetchBackgroundRoots() {
        val background = window(node(1, viewer = true))
        val focusedRoot = node(2, viewer = true)
        val focused = window(focusedRoot, focused = true)
        shadowOf(service).setWindows(listOf(background, focused))
        assertSame(focusedRoot, probe(node(3))?.first)
        assertEquals(1, Shadow.extract<WindowShadow>(focused).reads)
        assertEquals(0, Shadow.extract<WindowShadow>(background).reads)
    }

    @Test fun viewerInSecondaryWindowIsStillFound() {
        val focused = window(node(1), focused = true)
        val viewer = node(2, viewer = true)
        shadowOf(service).setWindows(listOf(window(viewer), focused))
        assertSame(viewer, probe(node(3))?.first)
    }

    @Test fun missingWindowsStillUseFallbackRoot() {
        val fallback = node(1, viewer = true)
        assertSame(fallback, probe(fallback)?.first)
    }

    @Test fun disappearingWindowDoesNotPreventFallbackDetection() {
        val missing = window(node(1), focused = true)
        Shadow.extract<WindowShadow>(missing).unavailable = true
        shadowOf(service).setWindows(listOf(missing))
        val fallback = node(2, viewer = true)
        assertSame(fallback, probe(fallback)?.first)
    }

    @Test fun staleFallbackCannotReplaceFreshRootForTheSameWindow() {
        shadowOf(service).setWindows(listOf(window(node(1), focused = true)))
        assertNull(probe(node(1, viewer = true)))
    }

    @Test fun unrelatedApplicationCannotBecomeInstagramViewer() {
        val otherRoot = node(1, viewer = true).apply { packageName = "example.other" }
        shadowOf(service).setWindows(listOf(window(otherRoot, focused = true)))
        assertNull(probe(node(2)))
    }
}
