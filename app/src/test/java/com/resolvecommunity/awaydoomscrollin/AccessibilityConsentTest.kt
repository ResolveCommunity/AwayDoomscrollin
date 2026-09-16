package com.resolvecommunity.awaydoomscrollin

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AccessibilityConsentTest {
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
    fun disclosureIsNotAcceptedByDefault() {
        assertFalse(AccessibilityConsent.isAccepted(context))
        assertFalse(AccessibilityConsent.isAccepted(preferences()))
    }

    @Test
    fun affirmativeChoicePersistsTheCurrentDisclosureVersion() {
        assertTrue(AccessibilityConsent.accept(context))
        assertTrue(AccessibilityConsent.isAccepted(context))
        assertTrue(AccessibilityConsent.isAccepted(preferences()))
    }

    private fun preferences() =
        context.getSharedPreferences(AccessibilityConsent.PREFS_NAME, Context.MODE_PRIVATE)
}
