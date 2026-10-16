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
    fun aCurrentV3OptInIsHonored() {
        preferences().edit()
            .putBoolean("telemetry_enabled", true)
            .putInt("telemetry_choice_version", TelemetryManager.CURRENT_CONSENT_VERSION)
            .commit()

        assertTrue(TelemetryManager.isTelemetryEnabled(context))
        assertFalse(TelemetryManager.requiresRenewedConsent(context))
    }

    @Test
    fun aLegacyV2OptInIsPausedUntilTheUserMakesANewChoice() {
        preferences().edit().putBoolean("telemetry_enabled", true).commit()

        assertFalse(TelemetryManager.isTelemetryEnabled(context))
        assertTrue(TelemetryManager.requiresRenewedConsent(context))
    }

    @Test
    fun disablingTelemetryIsPersistedSynchronously() {
        preferences().edit()
            .putBoolean("telemetry_enabled", true)
            .putString("telemetry_v2_pending_report_id", "pending")
            .putLong("telemetry_v2_pending_sequence", 4L)
            .putString("telemetry_v2_pending_payload", "payload")
            .commit()

        TelemetryManager.setTelemetryEnabled(context, false)

        assertFalse(TelemetryManager.isTelemetryEnabled(context))
        assertFalse(preferences().contains("telemetry_v2_pending_report_id"))
        assertFalse(preferences().contains("telemetry_v2_pending_sequence"))
        assertFalse(preferences().contains("telemetry_v2_pending_payload"))
        assertEquals(
            TelemetryManager.CURRENT_CONSENT_VERSION,
            preferences().getInt("telemetry_choice_version", 0)
        )
        assertFalse(TelemetryManager.requiresRenewedConsent(context))
    }

    @Test
    fun recentAttemptThrottlesAutomaticTriggersBeforeCreatingAnInstallationId() {
        preferences().edit()
            .putBoolean("telemetry_enabled", true)
            .putInt("telemetry_choice_version", TelemetryManager.CURRENT_CONSENT_VERSION)
            .putLong("telemetry_last_attempt_ms", System.currentTimeMillis())
            .commit()

        TelemetryManager.sendTelemetryAsync(context)

        assertFalse(preferences().contains("telemetry_installation_id"))
    }

    @Test
    fun previousSuccessfulSendTimestampStillThrottlesTheNextSnapshot() {
        preferences().edit()
            .putBoolean("telemetry_enabled", true)
            .putInt("telemetry_choice_version", TelemetryManager.CURRENT_CONSENT_VERSION)
            .putLong("telemetry_last_send_ms", System.currentTimeMillis())
            .commit()

        TelemetryManager.sendTelemetryAsync(context)

        assertFalse(preferences().contains("telemetry_installation_id"))
    }

    @Test
    fun v3PayloadIsACompleteVersionedSnapshot() {
        preferences().edit()
            .putInt("total_blocks", 17)
            .putInt("blocks_instagram", 9)
            .putInt("blocks_tiktok", 5)
            .putInt("blocks_youtube", 3)
            .putInt("streak_days", 4)
            .putLong("user_xp", 251L)
            .putLong(InstagramProtectionMetrics.KEY_TOTAL_MS, 42_900L)
            .putLong("youtube_protection_total_ms", 17_900L)
            .commit()

        val payload = TelemetryManager.buildV3Payload(
            context = context,
            prefs = preferences(),
            installationId = "150e8400-e29b-41d4-a716-446655440000",
            reportId = "250e8400-e29b-41d4-a716-446655440000",
            sequence = 7L
        )

        assertEquals("PSEUDONYMOUS_TELEMETRY", payload.getString("type"))
        assertEquals(3, payload.getInt("schemaVersion"))
        assertEquals(28, payload.length())
        assertEquals(7L, payload.getLong("snapshotSequence"))
        assertEquals(17, payload.getInt("totalBlocks"))
        assertEquals(9, payload.getInt("instagramBlocks"))
        assertEquals(5, payload.getInt("tiktokBlocks"))
        assertEquals(3, payload.getInt("youtubeBlocks"))
        assertFalse(payload.has("streakDays"))
        assertFalse(payload.has("userXp"))
        assertEquals(42L, payload.getLong("instagramProtectionSeconds"))
        assertEquals(InstagramProtectionMetrics.MEASUREMENT_VERSION, payload.getInt("instagramMeasurementVersion"))
        assertEquals(17L, payload.getLong("youtubeProtectionSeconds"))
        assertEquals(YouTubeProtectionMetrics.MEASUREMENT_VERSION, payload.getInt("youtubeMeasurementVersion"))
        assertTrue(payload.has("appVersionCode"))
        assertTrue(payload.has("instagramVersionName"))
        assertTrue(payload.has("instagramVersionCode"))
        assertTrue(payload.has("tiktokVersionName"))
        assertTrue(payload.has("youtubeVersionName"))
        assertTrue(payload.getInt("displayWidthPx") > 0)
        assertTrue(payload.getInt("displayHeightPx") > 0)
        assertTrue(payload.getInt("densityDpi") > 0)
    }

    @Test
    fun v3PayloadUsesOnlyThePublishedAllowlist() {
        val payload = TelemetryManager.buildV3Payload(
            context = context,
            prefs = preferences(),
            installationId = "350e8400-e29b-41d4-a716-446655440000",
            reportId = "450e8400-e29b-41d4-a716-446655440000",
            sequence = 1L
        )

        val expected = setOf(
            "type", "schemaVersion", "device_id", "snapshotSequence", "reportId",
            "totalBlocks", "instagramBlocks", "tiktokBlocks", "youtubeBlocks",
            "manufacturer", "model", "androidVersion", "sdkInt",
            "appVersion", "appVersionCode",
            "instagramVersionName", "instagramVersionCode",
            "tiktokVersionName", "tiktokVersionCode",
            "youtubeVersionName", "youtubeVersionCode",
            "displayWidthPx", "displayHeightPx", "densityDpi",
            "instagramProtectionSeconds", "instagramMeasurementVersion",
            "youtubeProtectionSeconds", "youtubeMeasurementVersion"
        )

        assertEquals(expected, payload.keys().asSequence().toSet())
    }

    @Test
    fun v3PayloadRejectsInvalidIdentifiersAndSequence() {
        val invalidInstallation = runCatching {
            TelemetryManager.buildV3Payload(
                context,
                preferences(),
                "not-a-uuid",
                "550e8400-e29b-41d4-a716-446655440000",
                1L
            )
        }
        val invalidReport = runCatching {
            TelemetryManager.buildV3Payload(
                context,
                preferences(),
                "650e8400-e29b-41d4-a716-446655440000",
                "750e8400-e29b-11d4-a716-446655440000",
                1L
            )
        }
        val invalidSequence = runCatching {
            TelemetryManager.buildV3Payload(
                context,
                preferences(),
                "850e8400-e29b-41d4-a716-446655440000",
                "950e8400-e29b-41d4-a716-446655440000",
                0L
            )
        }

        assertTrue(invalidInstallation.isFailure)
        assertTrue(invalidReport.isFailure)
        assertTrue(invalidSequence.isFailure)
    }

    @Test
    fun retryKeepsTheSameReportAndTheNextSnapshotGetsAHigherSequence() {
        val first = TelemetryManager.getOrCreatePendingReport(context, preferences())!!
        val retry = TelemetryManager.getOrCreatePendingReport(context, preferences())!!

        assertEquals(first.reportId, retry.reportId)
        assertEquals(first.sequence, retry.sequence)
        assertEquals(first.payload, retry.payload)

        preferences().edit()
            .remove("telemetry_v2_pending_report_id")
            .remove("telemetry_v2_pending_sequence")
            .remove("telemetry_v2_pending_payload")
            .commit()

        val next = TelemetryManager.getOrCreatePendingReport(context, preferences())!!
        assertEquals(first.sequence + 1L, next.sequence)
        assertFalse(first.reportId == next.reportId)
    }

    @Test
    fun aLegacyV2PendingReportIsReplacedWithoutResettingIdentityOrSequence() {
        val installationId = "a50e8400-e29b-41d4-a716-446655440000"
        val legacyReportId = "b50e8400-e29b-41d4-a716-446655440000"
        val legacyPayload = """
            {
              "type":"PSEUDONYMOUS_TELEMETRY",
              "schemaVersion":2,
              "device_id":"$installationId",
              "snapshotSequence":12,
              "reportId":"$legacyReportId"
            }
        """.trimIndent()
        preferences().edit()
            .putString("telemetry_installation_id", installationId)
            .putLong("telemetry_v2_last_sequence", 12L)
            .putString("telemetry_v2_pending_report_id", legacyReportId)
            .putLong("telemetry_v2_pending_sequence", 12L)
            .putString("telemetry_v2_pending_payload", legacyPayload)
            .putInt("blocks_youtube", 6)
            .commit()

        val replacement = TelemetryManager.getOrCreatePendingReport(context, preferences())!!
        val payload = org.json.JSONObject(replacement.payload)

        assertEquals(13L, replacement.sequence)
        assertEquals(3, payload.getInt("schemaVersion"))
        assertEquals(installationId, payload.getString("device_id"))
        assertEquals(6, payload.getInt("youtubeBlocks"))
        assertFalse(legacyReportId == replacement.reportId)
    }

    private fun preferences() =
        context.getSharedPreferences("away_doomscroll_prefs", Context.MODE_PRIVATE)
}
