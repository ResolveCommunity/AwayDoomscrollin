package com.resolvecommunity.awaydoomscrollin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskSwitcherPolicyTest {
    private val switchers = TaskSwitcherPolicy.switcherPackages(
        listOf("com.google.android.apps.nexuslauncher", "app.olauncher", "com.android.settings")
    )

    @Test
    fun `Settings fallback home is not a task switcher`() {
        assertEquals(setOf("com.google.android.apps.nexuslauncher", "app.olauncher"), switchers)
    }

    @Test
    fun `launcher window behind a live fullscreen app is the task switcher`() {
        assertTrue(TaskSwitcherPolicy.isShowing(
            activeWindowFullscreen = true,
            visibleApplicationPackages = listOf("com.instagram.android", "com.google.android.apps.nexuslauncher"),
            switcherPackages = switchers
        ))
    }

    @Test
    fun `a fullscreen app alone or beside another app keeps its protection`() {
        assertFalse(TaskSwitcherPolicy.isShowing(true, listOf("com.instagram.android"), switchers))
        assertFalse(TaskSwitcherPolicy.isShowing(
            true, listOf("com.instagram.android", "com.whatsapp", null), switchers))
    }

    @Test
    fun `a pop-up window over the home screen keeps its protection`() {
        val popUp = TaskSwitcherPolicy.isFullscreen(width = 720, height = 1400,
            displayWidth = 1080, displayHeight = 2340)
        assertFalse(popUp)
        assertFalse(TaskSwitcherPolicy.isShowing(
            popUp, listOf("com.instagram.android", "com.google.android.apps.nexuslauncher"), switchers))
    }

    @Test
    fun `fullscreen tolerates cutouts and system bars`() {
        assertTrue(TaskSwitcherPolicy.isFullscreen(1080, 2340, 1080, 2340))
        assertTrue(TaskSwitcherPolicy.isFullscreen(1080, 2214, 1080, 2340))
        assertFalse(TaskSwitcherPolicy.isFullscreen(1080, 1160, 1080, 2340))
        assertFalse(TaskSwitcherPolicy.isFullscreen(1080, 2340, 0, 0))
    }
}
