package com.resolvecommunity.awaydoomscrollin

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Looper
import android.view.View
import android.view.accessibility.AccessibilityEvent
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@LooperMode(LooperMode.Mode.PAUSED)
class InstagramCurtainLifecycleTest {
    class TestService : AccessibilityService() {
        override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
        override fun onInterrupt() = Unit
    }

    private lateinit var service: TestService
    private lateinit var overlay: InstagramFeedOverlayController

    @Before fun prepare() {
        service = Robolectric.buildService(TestService::class.java).create().get()
        overlay = InstagramFeedOverlayController(service, {})
    }

    @After fun closeWindows() { overlay.hideAll() }

    private fun show(heightDp: Int, screen: InstagramScreen = InstagramScreen.HOME_FEED) {
        val density = service.resources.displayMetrics.density
        val height = (heightDp * density).toInt()
        assertTrue(overlay.show(Rect(0, 0, 1080, height), screen, true))
    }

    private fun view(field: String): View =
        InstagramFeedOverlayController::class.java.getDeclaredField(field).run {
            isAccessible = true
            get(overlay) as View
        }

    private fun nextFrame() { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32)) }

    @Test fun shortInitialCurtainHidesCopyImmediately() {
        show(100)
        assertEquals(View.GONE, view("shieldContent").visibility)
    }

    @Test fun shrinkingCurtainHidesCopyInTheSameUpdate() {
        show(500)
        show(100)
        assertEquals(View.GONE, view("shieldContent").visibility)
    }

    @Test fun expandingCurtainRestoresCopyInTheSameUpdate() {
        show(500)
        show(100)
        show(100)
        show(500)
        assertEquals(View.VISIBLE, view("shieldContent").visibility)
    }

    @Test fun identicalUpdatesReuseTheAttachedCurtain() {
        show(500)
        val original = view("overlay")
        repeat(5) { show(500) }
        assertSame(original, view("overlay"))
        assertEquals(Rect(0, 0, 1080, (500 * service.resources.displayMetrics.density).toInt()),
            overlay.visibleCurtain()?.second)
    }

    @Test fun hideAndRestoreCannotDispatchAnOldDrawAction() {
        show(500)
        val original = view("overlay")
        var calls = 0
        overlay.afterCurtainDraw { calls++ }
        original.viewTreeObserver.dispatchOnPreDraw()
        overlay.hide()
        show(500)
        assertSame(original, view("overlay"))
        nextFrame()
        assertEquals(0, calls)
    }

    @Test fun shadeAndResumeCannotDispatchAnOldDrawAction() {
        show(500)
        var calls = 0
        overlay.afterCurtainDraw { calls++ }
        view("overlay").viewTreeObserver.dispatchOnPreDraw()
        overlay.suspendForNotificationShade()
        show(500)
        nextFrame()
        assertEquals(0, calls)
    }

    @Test fun hideBeforePreDrawAlsoCancelsTheOldAction() {
        show(500)
        var calls = 0
        overlay.afterCurtainDraw { calls++ }
        overlay.hide()
        show(500)
        view("overlay").viewTreeObserver.dispatchOnPreDraw()
        nextFrame()
        assertEquals(0, calls)
    }

    @Test fun geometryUpdateOnTheSameSurfacePreservesThePendingAction() {
        show(500)
        var calls = 0
        overlay.afterCurtainDraw { calls++ }
        show(450)
        view("overlay").viewTreeObserver.dispatchOnPreDraw()
        nextFrame()
        assertEquals(1, calls)
    }

    @Test fun surfaceChangeCannotDispatchAnOldDrawAction() {
        show(500)
        var calls = 0
        overlay.afterCurtainDraw { calls++ }
        view("overlay").viewTreeObserver.dispatchOnPreDraw()
        show(500, InstagramScreen.EXPLORE)
        nextFrame()
        assertEquals(0, calls)
    }

    @Test fun currentDrawActionWaitsForFrameAndRunsOnce() {
        show(500)
        var calls = 0
        overlay.afterCurtainDraw { calls++ }
        view("overlay").viewTreeObserver.dispatchOnPreDraw()
        assertEquals(0, calls)
        nextFrame()
        assertEquals(1, calls)
        view("overlay").viewTreeObserver.dispatchOnPreDraw()
        nextFrame()
        assertEquals(1, calls)
    }
}
