package com.resolvecommunity.awaydoomscrollin

import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.os.Looper
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@LooperMode(LooperMode.Mode.PAUSED)
class YouTubeCurtainDrawTest {
    private lateinit var activityController: ActivityController<Activity>
    private lateinit var container: FrameLayout
    private lateinit var curtain: View
    private lateinit var overlay: YouTubeProtectionOverlayController

    @Before
    fun attachCurtainToARealActivityWindow() {
        activityController = Robolectric.buildActivity(Activity::class.java).setup().visible()
        val activity = activityController.get()
        container = FrameLayout(activity)
        curtain = View(activity)
        container.addView(curtain, FrameLayout.LayoutParams(300, 500))
        activity.setContentView(container)
        val service = object : AccessibilityService() {
            override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
            override fun onInterrupt() = Unit
        }
        overlay = YouTubeProtectionOverlayController(
            service, {}, {}, {}, {}, onAllowedLaunchNavigation = {}
        )
        setCurtain(curtain)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
        assertTrue(curtain.isAttachedToWindow)
    }

    @After
    fun closeActivity() {
        activityController.pause().stop().destroy()
    }

    @Test
    fun navigationWaitsForPreDrawAndNextAnimationFrameAndRunsOnce() {
        var calls = 0
        overlay.afterCurtainDraw { calls++ }
        assertEquals(0, calls)
        curtain.viewTreeObserver.dispatchOnPreDraw()
        assertEquals(0, calls)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
        assertEquals(1, calls)
        curtain.viewTreeObserver.dispatchOnPreDraw()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
        assertEquals(1, calls)
    }

    @Test
    fun replacedCurtainCannotDispatchOldNavigation() {
        var calls = 0
        overlay.afterCurtainDraw { calls++ }
        curtain.viewTreeObserver.dispatchOnPreDraw()
        val replacement = View(activityController.get())
        container.addView(replacement, FrameLayout.LayoutParams(300, 500))
        setCurtain(replacement)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
        assertEquals(0, calls)
    }

    @Test
    fun systemShadeSuspensionBeforeAnimationFrameCancelsNavigation() {
        var calls = 0
        overlay.afterCurtainDraw { calls++ }
        curtain.viewTreeObserver.dispatchOnPreDraw()
        overlay.setSystemUiSuspended(true)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
        assertEquals(0, calls)
        overlay.setSystemUiSuspended(false)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
        assertEquals(0, calls)
    }

    @Test
    fun hiddenCurtainCannotDispatchNavigation() {
        var calls = 0
        overlay.afterCurtainDraw { calls++ }
        curtain.viewTreeObserver.dispatchOnPreDraw()
        curtain.visibility = View.INVISIBLE
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
        assertEquals(0, calls)
    }

    @Test
    fun absentCurtainDoesNotDispatchNavigation() {
        setCurtain(null)
        var calls = 0
        overlay.afterCurtainDraw { calls++ }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
        assertEquals(0, calls)
    }

    private fun setCurtain(view: View?) {
        YouTubeProtectionOverlayController::class.java.getDeclaredField("curtain").apply {
            isAccessible = true
            set(overlay, view)
        }
    }
}
