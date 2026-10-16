package com.resolvecommunity.awaydoomscrollin

import android.accessibilityservice.AccessibilityService
import android.view.View
import android.view.accessibility.AccessibilityEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class YouTubeOverlaySuspensionTest {
    @Test
    fun `metrics follow curtain visibility not guards or repeated updates`() {
        val service = object : AccessibilityService() {
            override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
            override fun onInterrupt() = Unit
        }
        val changes = mutableListOf<Boolean>()
        val overlay = YouTubeProtectionOverlayController(service, {}, {}, {}, {},
            onCurtainVisibilityChanged = { changes.add(it) })
        val field = YouTubeProtectionOverlayController::class.java.getDeclaredField("curtain")
            .apply { isAccessible = true }
        field.set(overlay, View(RuntimeEnvironment.getApplication()))
        overlay.setSystemUiSuspended(true)
        overlay.setSystemUiSuspended(false)
        overlay.setSystemUiSuspended(false)
        overlay.setSystemUiSuspended(true)
        overlay.setSystemUiSuspended(false)
        overlay.hideCurtain()
        overlay.hideCurtain()
        assertEquals(listOf(true, false, true, false), changes)
        overlay.setSystemUiSuspended(true)
        overlay.setSystemUiSuspended(false)
        assertEquals(listOf(true, false, true, false), changes)
    }

    @Test
    fun `allowed page removes curtain and shade resume does not resurrect it`() {
        val service = object : AccessibilityService() {
            override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
            override fun onInterrupt() = Unit
        }
        val overlay = YouTubeProtectionOverlayController(
            service, {}, {}, {}, {}, onAllowedLaunchNavigation = {}
        )
        val view = View(RuntimeEnvironment.getApplication())
        val field = YouTubeProtectionOverlayController::class.java.getDeclaredField("curtain")
            .apply { isAccessible = true }
        field.set(overlay, view)
        overlay.showCurtain(null, null, true)
        assertNull(field.get(overlay))
        overlay.setSystemUiSuspended(true)
        overlay.setSystemUiSuspended(false)
        assertNull(field.get(overlay))
        assertFalse(overlay.isCurtainVisible)
    }

    @Test
    fun `system shade hides windows without destroying them and resume restores same views`() {
        val service = object : AccessibilityService() {
            override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
            override fun onInterrupt() = Unit
        }
        val overlay = YouTubeProtectionOverlayController(
            service, {}, {}, {}, {}, onAllowedLaunchNavigation = {}
        )
        val views = listOf("curtain", "shortsGuard", "allowedBackGuard").map { name ->
            val view = View(RuntimeEnvironment.getApplication())
            YouTubeProtectionOverlayController::class.java.getDeclaredField(name).apply {
                isAccessible = true
                set(overlay, view)
            }
            name to view
        }
        overlay.setSystemUiSuspended(true)
        assertTrue(overlay.isSystemUiSuspended)
        assertFalse(overlay.isCurtainVisible)
        for ((name, view) in views) {
            assertEquals(View.INVISIBLE, view.visibility)
            assertEquals(view, YouTubeProtectionOverlayController::class.java.getDeclaredField(name)
                .apply { isAccessible = true }.get(overlay))
        }
        overlay.setSystemUiSuspended(false)
        assertFalse(overlay.isSystemUiSuspended)
        assertTrue(overlay.isCurtainVisible)
        views.forEach { (_, view) -> assertEquals(View.VISIBLE, view.visibility) }
    }
}
