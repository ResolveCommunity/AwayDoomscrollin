package com.awaydoomscrollin.app

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

object TelemetryManager {

    private const val TAG = "TelemetryManager"
    private const val PREFS_NAME = "away_doomscroll_prefs"
    private const val KEY_TELEMETRY_ENABLED = "telemetry_enabled"
    private const val KEY_LAST_TELEMETRY_SEND = "telemetry_last_send_ms"
    private const val KEY_INSTALLATION_ID = "telemetry_installation_id"
    private const val VDS_TELEMETRY_ENDPOINT = "https://awaydoomscrollin.com/api/telemetry"

    fun isTelemetryEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_TELEMETRY_ENABLED, true)
    }

    fun setTelemetryEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_TELEMETRY_ENABLED, enabled).apply()
        Log.d(TAG, "Telemetri durumu güncellendi: $enabled")
        
        if (enabled) {
            sendTelemetryAsync(context, force = true)
        }
    }

    fun sendTelemetryAsync(context: Context, force: Boolean = false) {
        if (!isTelemetryEnabled(context)) {
            Log.d(TAG, "Telemetri kapalı, veri gönderimi atlandı.")
            return
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastSend = prefs.getLong(KEY_LAST_TELEMETRY_SEND, 0L)
        val now = System.currentTimeMillis()

        // 24 saatte bir gönder (force true değilse)
        if (!force && (now - lastSend < 24 * 60 * 60 * 1000L)) {
            Log.d(TAG, "Telemetri bugün zaten gönderildi (24 saat dolmadı), atlanıyor.")
            return
        }

        GlobalScope.launch(Dispatchers.IO) {
            val installationId = getOrCreateInstallationId(prefs)
            if (installationId == null) {
                Log.e(TAG, "Rastgele telemetri kurulum kimliği kaydedilemedi; gönderim atlandı.")
                return@launch
            }

            val totalBlocks = prefs.getInt("total_blocks", 0)
            val blocksInsta = prefs.getInt("blocks_instagram", 0)
            val blocksTiktok = prefs.getInt("blocks_tiktok", 0)
            val blocksYt = prefs.getInt("blocks_youtube", 0)
            val streakDays = prefs.getInt("streak_days", 0)
            val userXp = prefs.getLong("user_xp", 0L)

            val manufacturer = Build.MANUFACTURER
            val model = Build.MODEL
            val androidVer = Build.VERSION.RELEASE
            val sdkInt = Build.VERSION.SDK_INT
            val appVersion = runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
            }.getOrDefault("unknown")

            val payload = JSONObject().apply {
                put("type", "PSEUDONYMOUS_TELEMETRY")
                put("device_id", installationId)
                put("totalBlocks", totalBlocks)
                put("instagramBlocks", blocksInsta)
                put("tiktokBlocks", blocksTiktok)
                put("youtubeBlocks", blocksYt)
                put("streakDays", streakDays)
                put("userXp", userXp)
                put("manufacturer", manufacturer)
                put("model", model)
                put("androidVersion", androidVer)
                put("sdkInt", sdkInt)
                put("appVersion", appVersion)
            }

            // VDS Sunucusuna Gönder
            val success = sendHttpPost(VDS_TELEMETRY_ENDPOINT, payload.toString())

            if (success) {
                prefs.edit().putLong(KEY_LAST_TELEMETRY_SEND, now).apply()
                Log.d(TAG, "Takma adlı telemetri raporu başarıyla iletildi!")
            }
        }
    }

    /**
     * Generates a random identifier for this app installation only. It is not
     * derived from Android ID, IMEI, MAC address, account data, or any hardware
     * value. Clearing app data or uninstalling the app removes it.
     */
    private fun getOrCreateInstallationId(prefs: SharedPreferences): String? = synchronized(this) {
        prefs.getString(KEY_INSTALLATION_ID, null)
            ?.takeIf { it.isNotBlank() }
            ?.let { return@synchronized it }

        val generatedId = UUID.randomUUID().toString()
        if (prefs.edit().putString(KEY_INSTALLATION_ID, generatedId).commit()) {
            generatedId
        } else {
            null
        }
    }

    private fun sendHttpPost(endpointUrl: String, jsonString: String): Boolean {
        return try {
            val url = URL(endpointUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            conn.setRequestProperty("Accept", "application/json")
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.doOutput = true

            val os = conn.outputStream
            os.write(jsonString.toByteArray(charset("UTF-8")))
            os.close()

            val responseCode = conn.responseCode
            responseCode in 200..299
        } catch (e: Exception) {
            Log.e(TAG, "HTTP POST hatası: $endpointUrl", e)
            false
        }
    }
}
