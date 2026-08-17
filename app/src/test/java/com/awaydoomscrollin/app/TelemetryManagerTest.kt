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
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TelemetryManagerTest {

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
    fun telemetryIsDisabledByDefaultAndDoesNotCreateAnInstallationId() {
        assertFalse(TelemetryManager.isTelemetryEnabled(context))

        TelemetryManager.sendTelemetryAsync(context)

        assertFalse(preferences().contains("telemetry_installation_id"))
    }

    @Test
    fun anExplicitStoredOptInIsHonored() {
        preferences().edit().putBoolean("telemetry_enabled", true).commit()

        assertTrue(TelemetryManager.isTelemetryEnabled(context))
    }

    @Test
    fun disablingTelemetryIsPersistedSynchronously() {
        preferences().edit().putBoolean("telemetry_enabled", true).commit()

        TelemetryManager.setTelemetryEnabled(context, false)

        assertFalse(TelemetryManager.isTelemetryEnabled(context))
    }

    @Test
    fun recentAttemptThrottlesAutomaticTriggersBeforeCreatingAnInstallationId() {
        preferences().edit()
            .putBoolean("telemetry_enabled", true)
            .putLong("telemetry_last_attempt_ms", System.currentTimeMillis())
            .commit()

        TelemetryManager.sendTelemetryAsync(context)

        assertFalse(preferences().contains("telemetry_installation_id"))
    }

    @Test
    fun legacySuccessfulSendTimestampIsHonoredAfterUpgrade() {
        preferences().edit()
            .putBoolean("telemetry_enabled", true)
            .putLong("telemetry_last_send_ms", System.currentTimeMillis())
            .commit()

        TelemetryManager.sendTelemetryAsync(context)

        assertFalse(preferences().contains("telemetry_installation_id"))
    }

    private fun preferences() =
        context.getSharedPreferences("away_doomscroll_prefs", Context.MODE_PRIVATE)
}
