package com.resolvecommunity.awaydoomscrollin

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@LooperMode(LooperMode.Mode.PAUSED)
class InstagramSearchKeyboardLayerTest {
    class TestService : AccessibilityService() {
        override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
        override fun onInterrupt() = Unit
    }
    private lateinit var controller: InstagramFeedOverlayController
    private val bounds = Rect(0, 200, 1080, 1800)
    @Before fun prepare() {
        val service = Robolectric.buildService(TestService::class.java).create().get()
        controller = InstagramFeedOverlayController(service, {})
    }
    @After fun close() { controller.hideAll() }
    private fun view(name: String = "overlay") = InstagramFeedOverlayController::class.java
        .getDeclaredField(name).apply { isAccessible = true }.get(controller) as View
    private fun params(name: String = "overlay") = view(name).layoutParams as WindowManager.LayoutParams
    private fun show(screen: InstagramScreen) {
        assertTrue(controller.show(Rect(bounds), screen, true))
    }
    private fun belowKeyboard(p: WindowManager.LayoutParams = params()) {
        assertTrue(p.flags and WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM != 0)
        assertTrue(p.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0)
    }
    @Test fun exploreKeepsTheKeyboardAboveItsTouchableFeedCover() {
        show(InstagramScreen.EXPLORE)
        belowKeyboard()
        assertEquals(0, params().flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        assertEquals(bounds, controller.visibleCurtain()?.second)
    }
    @Test fun homeKeepsItsOriginalKeyboardLayer() {
        show(InstagramScreen.HOME_FEED)
        assertEquals(0, params().flags and WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
    }
    @Test fun changingFromHomeToExploreUpdatesFlagsEvenAtIdenticalBounds() {
        show(InstagramScreen.HOME_FEED)
        val original = view()
        show(InstagramScreen.EXPLORE)
        assertSame(original, view())
        belowKeyboard()
    }
    @Test fun changingFromExploreToHomeRestoresFlagsEvenAtIdenticalBounds() {
        show(InstagramScreen.EXPLORE)
        show(InstagramScreen.HOME_FEED)
        assertEquals(0, params().flags and WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
    }
    @Test fun parkedExploreWindowDoesNotMaskTheKeyboardOrTakeTouches() {
        show(InstagramScreen.EXPLORE)
        controller.hide()
        val p = params("parkedOverlay")
        belowKeyboard(p)
        assertEquals(0f, p.alpha)
        assertTrue(p.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0)
        assertNull(controller.visibleCurtain())
        show(InstagramScreen.HOME_FEED)
        assertEquals(0, params().flags and WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
    }
    @Test fun sideNavigationRefreshPreservesTheExploreKeyboardLayer() {
        show(InstagramScreen.EXPLORE)
        controller.updateSideNavigation(Rect(0, 0, 90, 1920))
        belowKeyboard()
        assertEquals(bounds, controller.visibleCurtain()?.second)
    }
}
