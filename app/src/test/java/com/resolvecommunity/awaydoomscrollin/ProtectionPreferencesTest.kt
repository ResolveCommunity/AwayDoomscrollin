package com.resolvecommunity.awaydoomscrollin

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
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
    fun instagramModeDefaultsToBalancedWithoutMigrationWrites() {
        val before = preferences().all.toMap()
        assertEquals(ProtectionMode.BALANCED, ProtectionPreferences.getMode(context, ProtectedApp.INSTAGRAM))
        assertEquals(before, preferences().all)
    }

    @Test
    fun instagramModePersistsWithoutChangingExistingUserData() {
        preferences().edit().putInt("streak_days", 25).putInt("blocks_instagram", 37)
            .putString(ProtectionPreferences.KEY_IG_FEED_MODE, InstagramProtectionConfig.FEED_MODE_DIRECTLY_DM)
            .putBoolean(ProtectionPreferences.KEY_IG_BLOCK_STORIES, false).commit()
        val before = preferences().all.toMap()
        assertTrue(ProtectionPreferences.setMode(preferences(), ProtectedApp.INSTAGRAM, ProtectionMode.STRICT))
        val reopened = context.getSharedPreferences(ProtectionPreferences.PREFS_NAME, Context.MODE_PRIVATE)
        assertEquals(ProtectionMode.STRICT, ProtectionPreferences.getMode(reopened, ProtectedApp.INSTAGRAM))
        before.forEach { (key, value) -> assertEquals(value, reopened.all[key]) }
        assertEquals(ProtectionMode.BALANCED, ProtectionPreferences.getMode(reopened, ProtectedApp.YOUTUBE))
        assertTrue(ProtectionPreferences.setMode(reopened, ProtectedApp.INSTAGRAM, ProtectionMode.BALANCED))
        before.forEach { (key, value) -> assertEquals(value, reopened.all[key]) }
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
        preferences().edit().putInt("streak_days", 15).commit()

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
        assertEquals(0, preferences().getInt("streak_days", -1))
    }

    @Test
    fun shieldToggleWithoutResetStreakPreservesExistingStreak() {
        preferences().edit().putInt("streak_days", 25).commit()

        // Enabling/updating without resetStreak should keep 25
        assertTrue(
            ProtectionPreferences.setEnabled(
                preferences(),
                ProtectedApp.TIKTOK,
                enabled = true,
                resetStreak = false
            )
        )
        assertEquals(25, preferences().getInt("streak_days", -1))
    }

    @Test
    fun isPackageEnabledWorksForSupportedAndUnknownPackages() {
        assertTrue(ProtectionPreferences.isPackageEnabled(context, "com.instagram.android"))
        assertTrue(ProtectionPreferences.isPackageEnabled(context, "com.zhiliaoapp.musically"))
        assertTrue(ProtectionPreferences.isPackageEnabled(context, "com.google.android.youtube"))

        // Unknown packages must never be acted on by the accessibility service.
        assertFalse(ProtectionPreferences.isPackageEnabled(context, "com.some.other.app"))

        // Disable TikTok
        ProtectionPreferences.setEnabled(preferences(), ProtectedApp.TIKTOK, enabled = false)
        assertFalse(ProtectionPreferences.isPackageEnabled(context, "com.zhiliaoapp.musically"))
        assertTrue(ProtectionPreferences.isPackageEnabled(context, "com.instagram.android"))
    }

    private fun preferences() =
        context.getSharedPreferences(ProtectionPreferences.PREFS_NAME, Context.MODE_PRIVATE)
}
