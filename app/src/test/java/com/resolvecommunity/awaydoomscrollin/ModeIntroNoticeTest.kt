package com.resolvecommunity.awaydoomscrollin

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ModeIntroNoticeTest {
    private lateinit var prefs: SharedPreferences

    @Before
    fun setUp() {
        prefs = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences(ProtectionPreferences.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    @Test
    fun installUpdatedFromVersionWithoutModesSeesNoticeOnce() {
        prefs.edit().putBoolean("onboarding_completed", true).putInt("total_blocks", 40).commit()
        ModeIntroNotice.classifyOnce(prefs)
        assertTrue(ModeIntroNotice.isPending(prefs))

        ModeIntroNotice.markSeen(prefs)
        ModeIntroNotice.classifyOnce(prefs)
        assertFalse(ModeIntroNotice.isPending(prefs))
    }

    @Test
    fun freshInstallNeverSeesNotice() {
        ModeIntroNotice.classifyOnce(prefs)
        // Finishing setup later must not turn it into an updated install.
        prefs.edit().putBoolean("onboarding_completed", true).commit()
        ModeIntroNotice.classifyOnce(prefs)
        assertFalse(ModeIntroNotice.isPending(prefs))
    }

    @Test
    fun installThatAlreadyChoseAModeSeesNoNotice() {
        prefs.edit().putBoolean("onboarding_completed", true).commit()
        ProtectionPreferences.setMode(prefs, ProtectedApp.YOUTUBE, ProtectionMode.STRICT)
        ModeIntroNotice.classifyOnce(prefs)
        assertFalse(ModeIntroNotice.isPending(prefs))
    }
}
