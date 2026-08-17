package com.awaydoomscrollin.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProtectionPreferencesTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        preferences().edit().clear().commit()
    }

    @After
    fun tearDown() {
        preferences().edit().clear().commit()
    }

    @Test
    fun shieldsAreEnabledByDefault() {
        assertTrue(ProtectionPreferences.isEnabled(context, ProtectedApp.INSTAGRAM))
        assertTrue(ProtectionPreferences.isEnabled(context, ProtectedApp.TIKTOK))
        assertTrue(ProtectionPreferences.isEnabled(context, ProtectedApp.YOUTUBE))
    }

    @Test
    fun instagramDisableIsPersistedBeforeReturning() {
        assertTrue(
            ProtectionPreferences.setEnabled(
                preferences(),
                ProtectedApp.INSTAGRAM,
                enabled = false
            )
        )

        val freshlyOpenedPreferences =
            context.getSharedPreferences(ProtectionPreferences.PREFS_NAME, Context.MODE_PRIVATE)
        assertFalse(ProtectionPreferences.isEnabled(freshlyOpenedPreferences, ProtectedApp.INSTAGRAM))
        assertFalse(
            ProtectionPreferences.isPackageEnabled(
                context,
                ProtectedApp.INSTAGRAM.packageName
            )
        )
    }

    @Test
    fun disablingOneShieldDoesNotChangeOtherShields() {
        assertTrue(
            ProtectionPreferences.setEnabled(
                preferences(),
                ProtectedApp.INSTAGRAM,
                enabled = false,
                resetStreak = true
            )
        )

        assertFalse(ProtectionPreferences.isEnabled(context, ProtectedApp.INSTAGRAM))
        assertTrue(ProtectionPreferences.isEnabled(context, ProtectedApp.TIKTOK))
        assertTrue(ProtectionPreferences.isEnabled(context, ProtectedApp.YOUTUBE))
        assertTrue(preferences().getInt("streak_days", -1) == 0)
    }

    private fun preferences() =
        context.getSharedPreferences(ProtectionPreferences.PREFS_NAME, Context.MODE_PRIVATE)
}
