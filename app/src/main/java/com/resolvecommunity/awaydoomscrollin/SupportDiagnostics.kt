package com.resolvecommunity.awaydoomscrollin

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The support log the user can copy from About and paste into an email.
 *
 * It stays in app-private storage and is never sent anywhere by the app.
 * Callers on the accessibility thread only append to memory; a background
 * thread writes the file at most every few seconds.
 */
internal object SupportDiagnostics {
    private const val FILE_PATH = "support/diagnostics.log"
    private const val FLUSH_DELAY_MS = 3_000L
    private const val KEY_LAST_EXIT_SEEN = "support_last_exit_seen_ms"
    private const val KEY_ENABLED = "support_log_enabled"

    /** Read once, then kept in step by [setEnabled]; the service and the UI share this process. */
    @Volatile private var enabledCache: Boolean? = null

    private val lock = Any()
    private val buffer = SupportLogBuffer()
    private var loaded = false
    private var dirty = false
    private val flushScheduled = AtomicBoolean(false)
    private val writer = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "support-log-writer").apply { isDaemon = true }
    }

    fun record(
        context: Context,
        event: SupportEvent,
        fields: Map<String, Any?> = emptyMap()
    ) {
        val appContext = runCatching { context.applicationContext }.getOrNull() ?: return
        if (!isEnabled(appContext)) return
        val now = System.currentTimeMillis()
        val entry = SupportLogEntry.of(now, event, fields)
        synchronized(lock) {
            ensureLoaded(appContext, now)
            buffer.add(entry, now)
            dirty = true
        }
        scheduleFlush(appContext)
    }

    /** The log is on unless the user turned it off in About. */
    fun isEnabled(context: Context): Boolean = enabledCache ?: context.applicationContext
        .getSharedPreferences(ProtectionPreferences.PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(KEY_ENABLED, true)
        .also { enabledCache = it }

    /**
     * Turning the log off also deletes what it holds: off means nothing about
     * protection stays on the device for this purpose.
     */
    fun setEnabled(context: Context, enabled: Boolean) {
        val appContext = context.applicationContext
        appContext.getSharedPreferences(ProtectionPreferences.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).apply()
        enabledCache = enabled
        if (!enabled) clear(appContext)
    }

    /**
     * Android 11+ keeps why the app's process last ended. A phone that kills
     * the service in the background leaves no "stopped" line of its own, so
     * the reason is recorded when protection starts again. Each exit once.
     */
    fun recordPreviousExit(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val appContext = context.applicationContext
        val am = appContext.getSystemService(ActivityManager::class.java) ?: return
        val prefs = appContext.getSharedPreferences(ProtectionPreferences.PREFS_NAME, Context.MODE_PRIVATE)
        val seen = prefs.getLong(KEY_LAST_EXIT_SEEN, 0L)
        val exits = runCatching { am.getHistoricalProcessExitReasons(appContext.packageName, 0, 5) }
            .getOrNull().orEmpty()
        val newest = exits.filter { it.timestamp > seen }.maxByOrNull { it.timestamp } ?: return
        prefs.edit().putLong(KEY_LAST_EXIT_SEEN, exits.maxOf { it.timestamp }).apply()
        // The first install has no earlier history worth reporting.
        if (seen == 0L && System.currentTimeMillis() - newest.timestamp > SupportLogBuffer.MAX_AGE_MS) return
        val at = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(newest.timestamp))
        record(appContext, SupportEvent.SERVICE_PREVIOUS_EXIT,
            mapOf("reason" to exitReason(newest.reason), "at" to at))
    }

    private fun exitReason(code: Int): String = when (code) {
        ApplicationExitInfo.REASON_USER_REQUESTED -> "user"
        ApplicationExitInfo.REASON_USER_STOPPED -> "user-stopped"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "low-memory"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "resources"
        ApplicationExitInfo.REASON_SIGNALED -> "killed"
        ApplicationExitInfo.REASON_CRASH, ApplicationExitInfo.REASON_CRASH_NATIVE -> "crash"
        ApplicationExitInfo.REASON_ANR -> "anr"
        ApplicationExitInfo.REASON_PACKAGE_UPDATED -> "updated"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "permission"
        ApplicationExitInfo.REASON_FREEZER -> "frozen"
        else -> "other-$code"
    }

    private var settingsListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    /** Protection switched on/off or its mode changed, from the app or anywhere else. */
    fun watchSettings(context: Context) {
        if (settingsListener != null) return
        val appContext = context.applicationContext
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            val app = ProtectedApp.entries.firstOrNull {
                key == it.preferenceKey || key == "protection_mode_${it.name.lowercase()}"
            } ?: return@OnSharedPreferenceChangeListener
            record(appContext, SupportEvent.SETTINGS_CHANGED, mapOf(
                "app" to app.name.lowercase(),
                "on" to ProtectionPreferences.isEnabled(prefs, app),
                "mode" to ProtectionPreferences.getMode(prefs, app)))
        }
        settingsListener = listener
        appContext.getSharedPreferences(ProtectionPreferences.PREFS_NAME, Context.MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(listener)
    }

    /** The full text the user copies: a header, then one readable line per event. */
    fun buildReport(context: Context, english: Boolean): String {
        val appContext = context.applicationContext
        val now = System.currentTimeMillis()
        val entries = synchronized(lock) {
            ensureLoaded(appContext, now)
            buffer.entries(now)
        }
        return SupportLogFormatter.render(entries, english, header(appContext, english, now))
    }

    fun clear(context: Context) {
        val appContext = context.applicationContext
        synchronized(lock) {
            loaded = true
            buffer.clear()
            dirty = false
        }
        writer.execute { runCatching { file(appContext).delete() } }
    }

    private fun ensureLoaded(context: Context, now: Long) {
        if (loaded) return
        loaded = true
        val text = runCatching { file(context).takeIf { it.exists() }?.readText(Charsets.UTF_8) }
            .getOrNull().orEmpty()
        if (text.isNotEmpty()) buffer.load(text, now)
    }

    private fun scheduleFlush(context: Context) {
        if (!flushScheduled.compareAndSet(false, true)) return
        writer.schedule({
            flushScheduled.set(false)
            val text = synchronized(lock) {
                // A line recorded just before the log was turned off must not
                // bring the deleted file back.
                if (!dirty || enabledCache == false) return@schedule
                dirty = false
                buffer.text()
            }
            runCatching {
                val target = file(context)
                target.parentFile?.mkdirs()
                val temp = File(target.parentFile, target.name + ".tmp")
                temp.writeText(text, Charsets.UTF_8)
                if (!temp.renameTo(target)) {
                    target.writeText(text, Charsets.UTF_8)
                    temp.delete()
                }
            }
        }, FLUSH_DELAY_MS, TimeUnit.MILLISECONDS)
    }

    private fun file(context: Context) = File(context.filesDir, FILE_PATH)

    private fun header(context: Context, en: Boolean, now: Long): List<String> {
        val prefs = context.getSharedPreferences(ProtectionPreferences.PREFS_NAME, Context.MODE_PRIVATE)
        val created = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(now))
        val metrics = context.resources.displayMetrics
        val serviceOn = runCatching {
            Settings.Secure.getString(context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
                .contains("${context.packageName}/", ignoreCase = true)
        }.getOrDefault(false)
        // Phones that stop protection in the background usually do it through
        // battery optimization or a background restriction on the app.
        val batteryUnrestricted = runCatching {
            context.getSystemService(PowerManager::class.java)
                ?.isIgnoringBatteryOptimizations(context.packageName)
        }.getOrNull()
        val backgroundRestricted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) runCatching {
            context.getSystemService(ActivityManager::class.java)?.isBackgroundRestricted
        }.getOrNull() else null
        fun state(value: Boolean?, yes: String, no: String) = when (value) {
            true -> yes
            false -> no
            null -> if (en) "unknown" else "bilinmiyor"
        }
        val batteryLine = if (en) {
            "Battery optimization: ${state(batteryUnrestricted, "off (unrestricted)", "on (Android may stop protection)")}" +
                " · Background restriction: ${state(backgroundRestricted, "on", "off")}"
        } else {
            "Pil optimizasyonu: ${state(batteryUnrestricted, "kapalı (kısıtlama yok)", "açık (Android korumayı durdurabilir)")}" +
                " · Arka plan kısıtlaması: ${state(backgroundRestricted, "var", "yok")}"
        }
        fun version(pkg: String): String = runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(pkg, 0).versionName
        }.getOrNull() ?: if (en) "not installed" else "yüklü değil"
        fun protection(app: ProtectedApp, withMode: Boolean): String {
            if (!ProtectionPreferences.isEnabled(prefs, app)) return if (en) "off" else "kapalı"
            val on = if (en) "on" else "açık"
            if (!withMode) return on
            val mode = when (ProtectionPreferences.getMode(prefs, app)) {
                ProtectionMode.BALANCED -> if (en) "Balanced" else "Dengeli"
                ProtectionMode.STRICT -> if (en) "Strict" else "Sert"
            }
            return "$on ($mode)"
        }
        val appVersion = version(context.packageName)
        val language = appLanguage(prefs, en)
        val screen = "${metrics.widthPixels}x${metrics.heightPixels}"
        val phone = "${Build.MANUFACTURER} ${Build.MODEL}"
        val android = "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"
        return if (en) listOf(
            "AwayDoomscrollin' troubleshooting log ($created)",
            "App version: $appVersion · Phone: $phone · $android",
            "Instagram ${version(INSTAGRAM)} · TikTok ${version(TIKTOK)} · YouTube ${version(YOUTUBE)}",
            "Protection: Instagram ${protection(ProtectedApp.INSTAGRAM, true)} · TikTok ${protection(ProtectedApp.TIKTOK, false)} · YouTube ${protection(ProtectedApp.YOUTUBE, true)}",
            "Accessibility permission: ${if (serviceOn) "on" else "off"} · Screen: $screen · App language: $language",
            batteryLine
        ) else listOf(
            "AwayDoomscrollin' sorun giderme kaydı ($created)",
            "Uygulama sürümü: $appVersion · Telefon: $phone · $android",
            "Instagram ${version(INSTAGRAM)} · TikTok ${version(TIKTOK)} · YouTube ${version(YOUTUBE)}",
            "Koruma: Instagram ${protection(ProtectedApp.INSTAGRAM, true)} · TikTok ${protection(ProtectedApp.TIKTOK, false)} · YouTube ${protection(ProtectedApp.YOUTUBE, true)}",
            "Erişilebilirlik izni: ${if (serviceOn) "açık" else "kapalı"} · Ekran: $screen · Uygulama dili: $language",
            batteryLine
        )
    }

    private fun appLanguage(prefs: android.content.SharedPreferences, en: Boolean): String =
        when (prefs.getString("app_language", null) ?: Locale.getDefault().language) {
            "tr" -> if (en) "Turkish" else "Türkçe"
            "en" -> if (en) "English" else "İngilizce"
            else -> Locale.getDefault().displayLanguage
        }

    private const val INSTAGRAM = "com.instagram.android"
    private const val TIKTOK = "com.zhiliaoapp.musically"
    private const val YOUTUBE = "com.google.android.youtube"
}
