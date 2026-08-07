package com.awaydoomscrollin.app

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AppRule(
    val packageName: String,
    val name: String,
    val enabled: Boolean = true,
    val safeKeywords: List<String> = emptyList(),
    val dangerousViewIds: List<String> = emptyList()
)

data class RemoteConfig(
    val configVersion: Int = 1,
    val latestVersionCode: Int = 1,
    val latestVersionName: String = "1.0.0",
    val updateUrl: String = "https://github.com/ResolveCommunity/AwayDoomscrollin/releases",
    val isForceUpdate: Boolean = false,
    val updateChangelog: String = "",
    val announcementMessage: String = "",
    val announcementMinVersionCode: Int = 0,
    val globalSafeKeywords: List<String> = listOf("Birincil", "Primary", "Genel", "General", "İstekler", "Requests", "Mesajlar", "Messages", "Yorumlar", "Comments"),
    val apps: List<AppRule> = emptyList()
)

object RemoteRuleManager {

    private const val TAG = "RemoteRuleManager"
    private const val PREFS_NAME = "away_doomscroll_prefs"
    private const val KEY_REMOTE_JSON = "remote_rules_json_v1"
    private const val KEY_LAST_FETCH = "remote_rules_last_fetch_ms"

    // Varsayılan Remote Rules URL (GitHub Raw veya özel sunucu)
    private const val DEFAULT_RULES_URL = "https://raw.githubusercontent.com/ResolveCommunity/rules/main/rules.json"

    // Varsayılan Çevrimdışı Kurallar (İnternet olmasa da çalışan yedek)
    private val DEFAULT_CONFIG = RemoteConfig(
        configVersion = 1,
        announcementMessage = "",
        globalSafeKeywords = listOf(
            "Birincil", "Primary", "Genel", "General",
            "İstekler", "Requests", "Mesajlar", "Messages",
            "Yorumlar", "Comments"
        ),
        apps = listOf(
            AppRule(
                packageName = "com.instagram.android",
                name = "Instagram",
                safeKeywords = listOf("Birincil", "Primary", "Genel", "General", "Yorumlar", "Comments"),
                dangerousViewIds = listOf("reels", "reel", "clips", "feed")
            ),
            AppRule(
                packageName = "com.zhiliaoapp.musically",
                name = "TikTok",
                safeKeywords = listOf("Mesajlar", "Direct", "Inbox"),
                dangerousViewIds = listOf("vertical_viewpager", "view_pager")
            ),
            AppRule(
                packageName = "com.google.android.youtube",
                name = "YouTube Shorts",
                safeKeywords = listOf("Aramalar", "Search"),
                dangerousViewIds = listOf("reel", "shorts", "reel_recycler_view")
            )
        )
    )

    private var cachedConfig: RemoteConfig? = null

    fun getConfig(context: Context): RemoteConfig {
        cachedConfig?.let { return it }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedJson = prefs.getString(KEY_REMOTE_JSON, null)

        if (!savedJson.isNullOrBlank()) {
            try {
                val parsed = parseJsonToConfig(savedJson)
                cachedConfig = parsed
                return parsed
            } catch (e: Exception) {
                Log.e(TAG, "Önbellekteki JSON ayrıştırılamadı, varsayılana dönülüyor", e)
            }
        }

        cachedConfig = DEFAULT_CONFIG
        return DEFAULT_CONFIG
    }

    fun fetchRulesAsync(context: Context, force: Boolean = false) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastFetch = prefs.getLong(KEY_LAST_FETCH, 0L)
        val now = System.currentTimeMillis()

        // 6 saatte bir yenile (force true değilse)
        if (!force && (now - lastFetch < 6 * 60 * 60 * 1000L)) {
            Log.d(TAG, "Uzaktan kurallar henüz yeni çekildi (6 saat dolmadı), atlanıyor.")
            return
        }

        GlobalScope.launch(Dispatchers.IO) {
            try {
                val url = URL(DEFAULT_RULES_URL)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 5000
                conn.readTimeout = 5000

                if (conn.responseCode in 200..299) {
                    val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                    if (jsonStr.isNotBlank()) {
                        val parsed = parseJsonToConfig(jsonStr)
                        prefs.edit()
                            .putString(KEY_REMOTE_JSON, jsonStr)
                            .putLong(KEY_LAST_FETCH, now)
                            .apply()

                        cachedConfig = parsed
                        Log.d(TAG, "Uzaktan engelleme kuralları başarıyla güncellendi! Sürüm: ${parsed.configVersion}")
                    }
                } else {
                    Log.w(TAG, "Uzaktan kurallar indirilemedi, HTTP Yanıtı: ${conn.responseCode}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Uzaktan kural çekme hatası (çevrimdışı veya sunucu yanıt vermiyor)", e)
            }
        }
    }

    private fun parseJsonToConfig(jsonStr: String): RemoteConfig {
        val root = JSONObject(jsonStr)
        val version = root.optInt("configVersion", 1)
        val latestVerCode = root.optInt("latestVersionCode", 1)
        val latestVerName = root.optString("latestVersionName", "1.0.0")
        val updateUrl = root.optString("updateUrl", "https://github.com/ResolveCommunity/AwayDoomscrollin/releases")
        val isForceUpdate = root.optBoolean("isForceUpdate", false)
        val updateChangelog = root.optString("updateChangelog", "")
        val announcement = root.optString("announcementMessage", "")
        val minVer = root.optInt("announcementMinVersionCode", 0)

        val globalSafeList = mutableListOf<String>()
        val globalSafeArray = root.optJSONArray("globalSafeKeywords")
        if (globalSafeArray != null) {
            for (i in 0 until globalSafeArray.length()) {
                globalSafeList.add(globalSafeArray.getString(i))
            }
        }

        val appRules = mutableListOf<AppRule>()
        val appsArray = root.optJSONArray("apps")
        if (appsArray != null) {
            for (i in 0 until appsArray.length()) {
                val appObj = appsArray.getJSONObject(i)
                val pkg = appObj.getString("packageName")
                val name = appObj.optString("name", pkg)
                val enabled = appObj.optBoolean("enabled", true)

                val safeList = mutableListOf<String>()
                val safeArr = appObj.optJSONArray("safeKeywords")
                if (safeArr != null) {
                    for (j in 0 until safeArr.length()) {
                        safeList.add(safeArr.getString(j))
                    }
                }

                val dangerousList = mutableListOf<String>()
                val dangArr = appObj.optJSONArray("dangerousViewIds")
                if (dangArr != null) {
                    for (j in 0 until dangArr.length()) {
                        dangerousList.add(dangArr.getString(j))
                    }
                }

                appRules.add(
                    AppRule(
                        packageName = pkg,
                        name = name,
                        enabled = enabled,
                        safeKeywords = safeList,
                        dangerousViewIds = dangerousList
                    )
                )
            }
        }

        return RemoteConfig(
            configVersion = version,
            latestVersionCode = latestVerCode,
            latestVersionName = latestVerName,
            updateUrl = updateUrl,
            isForceUpdate = isForceUpdate,
            updateChangelog = updateChangelog,
            announcementMessage = announcement,
            announcementMinVersionCode = minVer,
            globalSafeKeywords = if (globalSafeList.isNotEmpty()) globalSafeList else DEFAULT_CONFIG.globalSafeKeywords,
            apps = if (appRules.isNotEmpty()) appRules else DEFAULT_CONFIG.apps
        )
    }
}
