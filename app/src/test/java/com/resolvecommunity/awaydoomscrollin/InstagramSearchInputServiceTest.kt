package com.resolvecommunity.awaydoomscrollin

import android.accessibilityservice.AccessibilityService
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
import org.robolectric.annotation.LooperMode
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowAccessibilityNodeInfo
import org.robolectric.shadows.ShadowAccessibilityService

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], shadows = [InstagramSearchInputServiceTest.ServiceShadow::class,
    InstagramSearchInputServiceTest.NodeShadow::class])
@LooperMode(LooperMode.Mode.PAUSED)
class InstagramSearchInputServiceTest {
    @Implements(AccessibilityService::class)
    class ServiceShadow : ShadowAccessibilityService() {
        var root: AccessibilityNodeInfo? = null
        var rootQueries = 0
        @Implementation fun getRootInActiveWindow(): AccessibilityNodeInfo? {
            rootQueries++
            return root
        }
    }
    @Implements(AccessibilityNodeInfo::class)
    class NodeShadow : ShadowAccessibilityNodeInfo() {
        var window = 10
        @Implementation override fun getWindowId(): Int = window
    }
    private lateinit var service: AntiScrollService
    private lateinit var overlay: InstagramFeedOverlayController
    private lateinit var root: AccessibilityNodeInfo
    private val cover = Rect(0, 200, 1080, 1800)

    @Before fun prepare() {
        service = Robolectric.buildService(AntiScrollService::class.java).create().get()
        root = AccessibilityNodeInfo().apply { packageName = "com.instagram.android" }
        Shadow.extract<ServiceShadow>(service).root = root
        set("lastInstagramScreen", InstagramScreen.EXPLORE)
        set("currentInstagramWindowId", 10)
        overlay = AntiScrollService::class.java.getDeclaredMethod("getInstagramOverlay").run {
            isAccessible = true
            invoke(service) as InstagramFeedOverlayController
        }
        assertTrue(overlay.show(Rect(cover), InstagramScreen.EXPLORE, true))
    }
    @After fun close() { service.onDestroy() }
    private fun set(name: String, value: Any) {
        AntiScrollService::class.java.getDeclaredField(name).apply { isAccessible = true }.set(service, value)
    }
    private fun apply(surface: InstagramSearchInputSurface = InstagramSearchInputSurface.FOCUSED_SEARCH,
        geometry: InstagramGeometrySample? = null) {
        AntiScrollService::class.java.getDeclaredMethod("applySearchInputObservation",
            InstagramSearchInputObservation::class.java).apply { isAccessible = true }.invoke(service,
                InstagramSearchInputObservation(surface, 10, geometry))
    }
    @Test fun verifiedFocusedSearchRetiresOnlyTheOutgoingCover() {
        apply()
        assertNull(overlay.visibleCurtain())
        val field = AntiScrollService::class.java.getDeclaredField("lastInstagramScreen").apply { isAccessible = true }
        assertEquals(InstagramScreen.SEARCH_RESULTS, field.get(service))
    }
    @Test fun aForegroundDepartureBeforeApplicationCannotHideTheCover() {
        root.packageName = "example.other"
        apply()
        assertNotNull(overlay.visibleCurtain())
    }
    @Test fun aReplacedWindowBeforeApplicationCannotHideTheCover() {
        Shadow.extract<NodeShadow>(root).window = 11
        apply()
        assertNotNull(overlay.visibleCurtain())
    }
    @Test fun protectedNavigationRejectsAnOlderSearchResult() {
        val latch = AntiScrollService::class.java.getDeclaredField("instagramProtectedNavigation").run {
            isAccessible = true
            get(service) as InstagramProtectedNavigationLatch
        }
        latch.begin(InstagramTab.HOME, 10, InstagramTab.SEARCH, android.os.SystemClock.uptimeMillis())
        apply()
        assertNotNull(overlay.visibleCurtain())
    }
    @Test fun exploreObservationsNeverUncoverOrResizeTheFeed() {
        apply(InstagramSearchInputSurface.EXPLORE, InstagramGeometrySample(0, 200, 1080, 1800))
        assertEquals(cover, overlay.visibleCurtain()?.second)
        apply(InstagramSearchInputSurface.EXPLORE, InstagramGeometrySample(0, 250, 1080, 1800))
        assertEquals(cover, overlay.visibleCurtain()?.second)
    }
    @Test fun backCancelsOldSearchResultsBeforeTheReturnCoverCanBeHidden() {
        set("lastInstagramScreen", InstagramScreen.SEARCH_RESULTS)
        set("instagramExploreSearchSeen", true)
        shadowOf(service).setWindows(listOf(AccessibilityWindowInfo.obtain().apply {
            shadowOf(this).setType(AccessibilityWindowInfo.TYPE_INPUT_METHOD)
        }))
        AntiScrollService::class.java.getDeclaredMethod("onSearchReturnBack").apply {
            isAccessible = true
        }.invoke(service)
        apply()
        assertNotNull(overlay.visibleCurtain())
    }
    @Test fun homeAndMessagesNeverUseTheSearchShortcut() {
        for (screen in listOf(InstagramScreen.HOME_FEED, InstagramScreen.DIRECT_MESSAGES)) {
            set("lastInstagramScreen", screen)
            set("searchInputBypassUntil", 0L)
            apply()
            assertNotNull(screen.toString(), overlay.visibleCurtain())
        }
    }
    @Test fun nativeClicksKeepOfficialHandlingWithoutAForcedSlowPath() {
        set("searchInputActive", true)
        val queued = java.util.ArrayDeque<Runnable>()
        val dispatcher = LatestObservationDispatcher(java.util.concurrent.Executor { queued.add(it) },
            java.util.concurrent.Executor {},
            { _: () -> Boolean -> InstagramSearchInputObservation(InstagramSearchInputSurface.OTHER) }, {})
        set("searchInputDispatcher", lazy { dispatcher })
        val event = android.view.accessibility.AccessibilityEvent.obtain(
            android.view.accessibility.AccessibilityEvent.TYPE_VIEW_CLICKED)
        val handled = AntiScrollService::class.java.getDeclaredMethod("handleSearchInputEvent",
            android.view.accessibility.AccessibilityEvent::class.java).apply {
                isAccessible = true
            }.invoke(service, event) as Boolean
        assertFalse(handled)
        val state = Shadow.extract<ServiceShadow>(service)
        state.rootQueries = 0
        (AntiScrollService::class.java.getDeclaredField("overlayForegroundCheck").apply {
            isAccessible = true
        }.get(service) as Runnable).run()
        assertEquals("A periodic check after the click must not query roots on the owner", 0, state.rootQueries)
        assertEquals(1, queued.size)
        val bypass = AntiScrollService::class.java.getDeclaredField("searchInputBypassUntil").apply {
            isAccessible = true
        }.getLong(service)
        assertEquals(0L, bypass)
        assertNotNull(overlay.visibleCurtain())
        event.recycle()
    }
    @Test fun periodicForegroundChecksStayOffTheOwnerForTheVerifiedSearchScope() {
        val queued = java.util.ArrayDeque<Runnable>()
        val worker = java.util.concurrent.Executor { queued.add(it) }
        val dispatcher = LatestObservationDispatcher(worker, java.util.concurrent.Executor {},
            { _: () -> Boolean -> InstagramSearchInputObservation(InstagramSearchInputSurface.OTHER) }, {})
        set("searchInputDispatcher", lazy { dispatcher })
        set("searchInputActive", true)
        val state = Shadow.extract<ServiceShadow>(service)
        state.rootQueries = 0
        val check = AntiScrollService::class.java.getDeclaredField("overlayForegroundCheck").run {
            isAccessible = true
            get(service) as Runnable
        }
        check.run()
        assertEquals("Foreground IPC must not run on the overlay thread", 0, state.rootQueries)
        assertEquals(1, queued.size)
    }
    @Test fun unsupportedObservationKeepsProtection() {
        apply(InstagramSearchInputSurface.OTHER)
        assertNotNull(overlay.visibleCurtain())
    }
}
