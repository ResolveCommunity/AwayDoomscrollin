package com.resolvecommunity.awaydoomscrollin

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InstalledAppsTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun missingAppGetsNoSwitch() {
        install(ProtectedApp.INSTAGRAM)
        assertTrue(InstalledApps.isAvailable(context, ProtectedApp.INSTAGRAM))
        assertFalse(InstalledApps.isAvailable(context, ProtectedApp.TIKTOK))
        assertEquals(setOf(ProtectedApp.INSTAGRAM), InstalledApps.available(context))
    }

    @Test
    fun disabledPreinstalledAppCountsAsMissing() {
        install(ProtectedApp.YOUTUBE, enabled = false)
        assertFalse(InstalledApps.isAvailable(context, ProtectedApp.YOUTUBE))
    }

    private fun install(app: ProtectedApp, enabled: Boolean = true) {
        val info = ApplicationInfo().apply {
            packageName = app.packageName
            this.enabled = enabled
        }
        shadowOf(context.packageManager).installPackage(PackageInfo().apply {
            packageName = app.packageName
            applicationInfo = info
        })
    }
}
