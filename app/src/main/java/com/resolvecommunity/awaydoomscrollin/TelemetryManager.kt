package com.resolvecommunity.awaydoomscrollin

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

internal data class PendingTelemetryReport(
    val reportId: String,
    val sequence: Long,
    val payload: String
)

internal data class TelemetryHttpResult(val statusCode: Int?) {
    val isSuccessful: Boolean
        get() = statusCode in 200..299
}

object TelemetryManager {

    private const val TAG = "TelemetryManager"
    private const val PREFS_NAME = "away_doomscroll_prefs"
    private const val KEY_TELEMETRY_ENABLED = "telemetry_enabled"
    private const val KEY_LAST_TELEMETRY_ATTEMPT = "telemetry_last_attempt_ms"
    private const val KEY_LAST_TELEMETRY_SEND = "telemetry_last_send_ms"
    private const val KEY_INSTALLATION_ID = "telemetry_installation_id"
    private const val KEY_LAST_SEQUENCE = "telemetry_v2_last_sequence"
    private const val KEY_PENDING_REPORT_ID = "telemetry_v2_pending_report_id"
    private const val KEY_PENDING_SEQUENCE = "telemetry_v2_pending_sequence"
    private const val KEY_PENDING_PAYLOAD = "telemetry_v2_pending_payload"
    private const val VDS_TELEMETRY_ENDPOINT = "https://awaydoomscrollin.com/api/telemetry"
    private const val TELEMETRY_ATTEMPT_INTERVAL_MS = 24 * 60 * 60 * 1000L
    private const val SCHEMA_VERSION = 2
    private const val MAX_SAFE_INTEGER = 9_007_199_254_740_991L
    private const val INSTAGRAM_PACKAGE = "com.instagram.android"
    private const val TIKTOK_PACKAGE = "com.zhiliaoapp.musically"
    private const val YOUTUBE_PACKAGE = "com.google.android.youtube"
    private val telemetryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var sendInFlight = false

    fun isTelemetryEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_TELEMETRY_ENABLED, false)
    }

    fun setTelemetryEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit().putBoolean(KEY_TELEMETRY_ENABLED, enabled)
        if (!enabled) {
            // A future opt-in must report a fresh snapshot rather than retrying
            // data that was prepared before the user opted out.
            editor
                .remove(KEY_PENDING_REPORT_ID)
                .remove(KEY_PENDING_SEQUENCE)
                .remove(KEY_PENDING_PAYLOAD)
        }
        if (!editor.commit()) {
            Log.e(TAG, "Telemetri tercihi kaydedilemedi; veri gönderimi başlatılmadı.")
            return
        }

        if (enabled) {
            // Explicit opt-in may send immediately. Startup and intervention
            // triggers share the persisted 24-hour attempt window.
            sendTelemetryAsync(context, bypassAttemptInterval = true)
        }
    }

    fun sendTelemetryAsync(context: Context) {
        sendTelemetryAsync(context, bypassAttemptInterval = false)
    }

    private fun sendTelemetryAsync(context: Context, bypassAttemptInterval: Boolean) {
        if (!isTelemetryEnabled(context)) return

        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()

        val attemptReserved = synchronized(this) {
            if (sendInFlight) {
                false
            } else {
                val lastAttempt = maxOf(
                    prefs.getLong(KEY_LAST_TELEMETRY_ATTEMPT, 0L),
                    prefs.getLong(KEY_LAST_TELEMETRY_SEND, 0L)
                )
                if (!bypassAttemptInterval && now - lastAttempt < TELEMETRY_ATTEMPT_INTERVAL_MS) {
                    false
                } else {
                    val saved = prefs.edit().putLong(KEY_LAST_TELEMETRY_ATTEMPT, now).commit()
                    if (saved) sendInFlight = true
                    saved
                }
            }
        }
        if (!attemptReserved) return

        telemetryScope.launch {
            try {
                if (!isTelemetryEnabled(appContext)) return@launch
                val pending = getOrCreatePendingReport(appContext, prefs) ?: return@launch
                if (!isTelemetryEnabled(appContext)) return@launch

                val result = sendHttpPost(VDS_TELEMETRY_ENDPOINT, pending.payload)
                if (result.isSuccessful) {
                    completePendingReport(prefs, pending.reportId, now)
                }
            } catch (error: Exception) {
                Log.w(TAG, "Telemetry report could not be prepared or sent", error)
            } finally {
                synchronized(this@TelemetryManager) {
                    sendInFlight = false
                }
            }
        }
    }

    @Synchronized
    internal fun getOrCreatePendingReport(
        context: Context,
        prefs: SharedPreferences
    ): PendingTelemetryReport? {
        readPendingReport(prefs)?.let { return it }

        val installationId = getOrCreateInstallationId(prefs) ?: return null
        val currentSequence = prefs.getLong(KEY_LAST_SEQUENCE, 0L).coerceAtLeast(0L)
        if (currentSequence >= MAX_SAFE_INTEGER) {
            Log.e(TAG, "Telemetri sıra numarası güvenli tamsayı sınırına ulaştı.")
            return null
        }

        val sequence = currentSequence + 1L
        val reportId = UUID.randomUUID().toString()
        val payload = buildV2Payload(
            context = context,
            prefs = prefs,
            installationId = installationId,
            reportId = reportId,
            sequence = sequence
        ).toString()

        val saved = prefs.edit()
            .putLong(KEY_LAST_SEQUENCE, sequence)
            .putString(KEY_PENDING_REPORT_ID, reportId)
            .putLong(KEY_PENDING_SEQUENCE, sequence)
            .putString(KEY_PENDING_PAYLOAD, payload)
            .commit()
        return if (saved) PendingTelemetryReport(reportId, sequence, payload) else null
    }

    private fun readPendingReport(prefs: SharedPreferences): PendingTelemetryReport? {
        val reportId = prefs.getString(KEY_PENDING_REPORT_ID, null)?.takeIf(::isUuidV4)
            ?: return null
        val sequence = prefs.getLong(KEY_PENDING_SEQUENCE, 0L)
            .takeIf { it in 1..MAX_SAFE_INTEGER } ?: return null
        val payload = prefs.getString(KEY_PENDING_PAYLOAD, null)?.takeIf { it.isNotBlank() }
            ?: return null

        return runCatching {
            val json = JSONObject(payload)
            require(json.getInt("schemaVersion") == SCHEMA_VERSION)
            require(json.getString("reportId") == reportId)
            require(json.getLong("snapshotSequence") == sequence)
            PendingTelemetryReport(reportId, sequence, payload)
        }.getOrNull()
    }

    @Synchronized
    private fun completePendingReport(
        prefs: SharedPreferences,
        reportId: String,
        sentAt: Long
    ) {
        val editor = prefs.edit().putLong(KEY_LAST_TELEMETRY_SEND, sentAt)
        if (prefs.getString(KEY_PENDING_REPORT_ID, null) == reportId) {
            editor
                .remove(KEY_PENDING_REPORT_ID)
                .remove(KEY_PENDING_SEQUENCE)
                .remove(KEY_PENDING_PAYLOAD)
        }
        editor.apply()
    }

    /**
     * Generates a random identifier for this app installation only. It is not
     * derived from Android ID, IMEI, MAC address, account data, or hardware IDs.
     */
    private fun getOrCreateInstallationId(prefs: SharedPreferences): String? = synchronized(this) {
        prefs.getString(KEY_INSTALLATION_ID, null)
            ?.takeIf(::isUuidV4)
            ?.lowercase()
            ?.let { return@synchronized it }

        val generatedId = UUID.randomUUID().toString()
        if (prefs.edit().putString(KEY_INSTALLATION_ID, generatedId).commit()) generatedId else null
    }

    internal fun buildV2Payload(
        context: Context,
        prefs: SharedPreferences,
        installationId: String,
        reportId: String,
        sequence: Long
    ): JSONObject {
        require(isUuidV4(installationId))
        require(isUuidV4(reportId))
        require(sequence in 1..MAX_SAFE_INTEGER)

        val ownVersion = installedAppVersion(context, context.packageName)
        val instagramVersion = installedAppVersion(context, INSTAGRAM_PACKAGE)
        val tiktokVersion = installedAppVersion(context, TIKTOK_PACKAGE)
        val youtubeVersion = installedAppVersion(context, YOUTUBE_PACKAGE)
        val display = context.resources.displayMetrics

        return JSONObject().apply {
            put("type", "PSEUDONYMOUS_TELEMETRY")
            put("schemaVersion", SCHEMA_VERSION)
            put("device_id", installationId.lowercase())
            put("snapshotSequence", sequence)
            put("reportId", reportId.lowercase())
            put("totalBlocks", nonNegative(prefs.getInt("total_blocks", 0)))
            put("instagramBlocks", nonNegative(prefs.getInt("blocks_instagram", 0)))
            put("tiktokBlocks", nonNegative(prefs.getInt("blocks_tiktok", 0)))
            put("youtubeBlocks", nonNegative(prefs.getInt("blocks_youtube", 0)))
            put("manufacturer", safeText(Build.MANUFACTURER, 64, "Unknown"))
            put("model", safeText(Build.MODEL, 128, "Unknown"))
            put("androidVersion", safeText(Build.VERSION.RELEASE, 32, "N/A"))
            put("sdkInt", nonNegative(Build.VERSION.SDK_INT))
            put("appVersion", safeText(ownVersion.name, 32, "unknown"))
            put("appVersionCode", safeInteger(ownVersion.code))
            put("instagramVersionName", safeText(instagramVersion.name, 64, "not_installed"))
            put("instagramVersionCode", safeInteger(instagramVersion.code))
            put("tiktokVersionName", safeText(tiktokVersion.name, 64, "not_installed"))
            put("tiktokVersionCode", safeInteger(tiktokVersion.code))
            put("youtubeVersionName", safeText(youtubeVersion.name, 64, "not_installed"))
            put("youtubeVersionCode", safeInteger(youtubeVersion.code))
            put("displayWidthPx", nonNegative(display.widthPixels))
            put("displayHeightPx", nonNegative(display.heightPixels))
            put("densityDpi", nonNegative(display.densityDpi))
            put(
                "instagramProtectionSeconds",
                safeInteger(InstagramProtectionMetrics.totalMs(prefs) / 1000L)
            )
            put("instagramMeasurementVersion", InstagramProtectionMetrics.MEASUREMENT_VERSION)
        }
    }

    private data class InstalledAppVersion(val name: String, val code: Long)

    private fun installedAppVersion(context: Context, packageName: String): InstalledAppVersion =
        runCatching {
            val info = context.packageManager.getPackageInfo(packageName, 0)
            InstalledAppVersion(
                name = info.versionName ?: "unknown",
                code = packageVersionCode(info)
            )
        }.getOrElse {
            InstalledAppVersion(name = "not_installed", code = 0L)
        }

    @Suppress("DEPRECATION")
    private fun packageVersionCode(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode
        else info.versionCode.toLong()

    private fun safeText(value: String?, maxLength: Int, fallback: String): String {
        val cleaned = value.orEmpty().trim().filterNot(Char::isISOControl).take(maxLength)
        return cleaned.ifBlank { fallback }
    }

    private fun nonNegative(value: Int): Int = value.coerceAtLeast(0)

    private fun safeInteger(value: Long): Long = value.coerceIn(0L, MAX_SAFE_INTEGER)

    private fun isUuidV4(value: String): Boolean = runCatching {
        val canonical = value.trim().lowercase()
        UUID.fromString(canonical).toString() == canonical && canonical[14] == '4'
    }.getOrDefault(false)

    private fun sendHttpPost(endpointUrl: String, jsonString: String): TelemetryHttpResult {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(endpointUrl)
            require(url.protocol == "https") { "Telemetry requires HTTPS" }

            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            connection.setRequestProperty("Accept", "application/json")
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            connection.instanceFollowRedirects = false
            connection.doOutput = true
            connection.outputStream.use { output ->
                output.write(jsonString.toByteArray(Charsets.UTF_8))
            }
            TelemetryHttpResult(connection.responseCode)
        } catch (error: Exception) {
            Log.w(TAG, "Telemetry request failed", error)
            TelemetryHttpResult(null)
        } finally {
            connection?.disconnect()
        }
    }
}
