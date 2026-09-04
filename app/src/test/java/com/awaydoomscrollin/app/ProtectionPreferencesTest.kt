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

    @Test
    fun testFocusImpactCalculationsAndFormatting() {
        // 0 blocks
        val initialBook = getFocusCategoryImpact(FocusImpactCategory.BOOK, 0, isEn = false)
        assertTrue(initialBook.headline.contains("Kalkan Devrede"))
        val formattedZero = formatSavedFocusTime(0, isEn = false)
        assertTrue(formattedZero == "0 Dakika")

        // 20 blocks = 10 minutes (0.16h)
        val book10m = getFocusCategoryImpact(FocusImpactCategory.BOOK, 20, isEn = false)
        assertTrue(book10m.headline.contains("Sayfa"))
        assertTrue(book10m.description.contains("sayfa kitap"))

        val lang10m = getFocusCategoryImpact(FocusImpactCategory.LANGUAGE, 20, isEn = false)
        assertTrue(lang10m.headline.contains("Yeni Kelime"))

        val skill10m = getFocusCategoryImpact(FocusImpactCategory.SKILL, 20, isEn = false)
        assertTrue(skill10m.headline.contains("Alıştırma"))

        val walk10m = getFocusCategoryImpact(FocusImpactCategory.WALK, 20, isEn = false)
        assertTrue(walk10m.headline.contains("Metre") || walk10m.headline.contains("km"))

        // 120 blocks = 60 minutes (1 hour) -> movie & skill lessons
        val movie1h = getFocusCategoryImpact(FocusImpactCategory.MOVIE, 120, isEn = false)
        assertTrue(movie1h.headline.contains("Belgesel") || movie1h.headline.contains("Film"))

        val skill1h = getFocusCategoryImpact(FocusImpactCategory.SKILL, 120, isEn = false)
        assertTrue(skill1h.headline.contains("Beceri Dersi") || skill1h.headline.contains("Dersi"))

        // 600 blocks = 300 minutes (5 hours) -> full book victory tier (website standard: 1 book = 5 hours)
        val book5h = getFocusCategoryImpact(FocusImpactCategory.BOOK, 600, isEn = false)
        assertTrue(book5h.headline.contains("Tam Kitap"))
        assertTrue(book5h.description.contains("tam ~1 kitap"))
        val formatted5h = formatSavedFocusTime(600, isEn = false)
        assertTrue(formatted5h == "5 Saat")
    }

    private fun preferences() =
        context.getSharedPreferences(ProtectionPreferences.PREFS_NAME, Context.MODE_PRIVATE)
}
