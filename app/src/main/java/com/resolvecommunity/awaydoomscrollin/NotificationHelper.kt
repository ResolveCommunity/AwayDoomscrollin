package com.resolvecommunity.awaydoomscrollin

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Which status notification may vibrate. Only "protection is not running":
 * it warns about something the user did not do and may not see. "Protection
 * active" is posted on every service reconnect, which some phones (vivo) do
 * often, and "X protection is off" follows the user's own tap in the app.
 * Vibration comes from the notification channel alone, so Android's own
 * notification settings can turn it off (vivo V2023 ticket, November 2026).
 *
 * [quiet] is for the "not running" notice posted when the service is unbound:
 * that is mostly the user turning the switch off, who needs no buzz for it.
 * Only the watchdog, which finds stops the user did not make, vibrates.
 */
internal object StatusNotificationPolicy {
    const val ACTIVE_ID = 1001
    const val STOPPED_ID = 1002
    const val APP_OFF_ID = 1004

    fun vibrates(notificationId: Int, quiet: Boolean = false): Boolean =
        !quiet && notificationId == STOPPED_ID
}

object NotificationHelper {

    private const val TAG = "NotificationHelper"
    private val VIBRATION_PATTERN = longArrayOf(0, 250, 150, 250)

    // Notification channels freeze their name/description/vibration at first creation.
    // The id therefore carries the version and the language code so updates get a
    // freshly configured channel instead of keeping the previous settings.
    /** Silent status channel: protection active, an app's protection turned off. */
    fun statusChannelId(language: AppLanguage) = "shield_status_v5_${language.code}"

    /** Vibrating channel for "protection is not running" only. */
    fun alertChannelId(language: AppLanguage) = "shield_alert_v5_${language.code}"

    /** Channels of earlier versions; their vibration settings can no longer be changed. */
    private fun legacyChannelIds(): List<String> =
        listOf("shield_status_silent_v2_en", "shield_status_silent_v2_tr",
            "shield_status_v3_en", "shield_status_v3_tr") +
            AppLanguage.entries.map { "shield_status_v4_${it.code}" }

    private fun ensureChannels(
        notificationManager: android.app.NotificationManager,
        language: AppLanguage
    ) {
        val status = android.app.NotificationChannel(
            statusChannelId(language),
            AppText.get(R.string.notif_protection_and_permission_status),
            android.app.NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = AppText.get(R.string.notif_protection_status_and_accessibility_notification)
            enableVibration(false)
            setSound(null, null)
        }
        val alert = android.app.NotificationChannel(
            alertChannelId(language),
            AppText.get(R.string.notif_channel_stopped_name),
            android.app.NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = AppText.get(R.string.notif_channel_stopped_desc)
            enableVibration(true)
            vibrationPattern = VIBRATION_PATTERN
            setSound(null, null)
        }
        notificationManager.createNotificationChannel(status)
        notificationManager.createNotificationChannel(alert)

        // Remove earlier channels, and these channels in other languages, so the
        // system settings list them once, in the language in use.
        runCatching {
            legacyChannelIds().forEach(notificationManager::deleteNotificationChannel)
            AppLanguage.entries.filter { it != language }.forEach {
                notificationManager.deleteNotificationChannel(statusChannelId(it))
                notificationManager.deleteNotificationChannel(alertChannelId(it))
            }
        }
    }

    /**
     * Builds a status notification without posting it. [openIntent] replaces
     * the default tap action (opening the app); [actions] adds buttons.
     */
    fun buildShieldStatusNotification(
        context: Context,
        title: String,
        message: String,
        notificationId: Int,
        openIntent: Intent? = null,
        actions: List<Pair<String, Intent>> = emptyList(),
        quiet: Boolean = false
    ): android.app.Notification {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

        val prefs = context.getSharedPreferences("away_doomscroll_prefs", Context.MODE_PRIVATE)
        val language = appLanguage(prefs)
        ensureChannels(notificationManager, language)
        val vibrates = StatusNotificationPolicy.vibrates(notificationId, quiet)
        val channelId = if (vibrates) alertChannelId(language) else statusChannelId(language)

        val intent = openIntent ?: Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            context, notificationId, intent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = androidx.core.app.NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_leaf)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)
            .setSound(null)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
        if (vibrates) builder.setVibrate(VIBRATION_PATTERN) else builder.setSilent(true)
        actions.forEachIndexed { index, (label, target) ->
            builder.addAction(0, label, android.app.PendingIntent.getActivity(
                context, notificationId * 10 + index + 1, target,
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT))
        }
        return builder.build()
    }

    fun showShieldStatusNotification(
        context: Context,
        title: String,
        message: String,
        notificationId: Int = StatusNotificationPolicy.STOPPED_ID,
        openIntent: Intent? = null,
        actions: List<Pair<String, Intent>> = emptyList(),
        quiet: Boolean = false
    ) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            val notification = buildShieldStatusNotification(context, title, message, notificationId, openIntent, actions, quiet)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                notificationManager.notify(notificationId, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Notification posting error", e)
        }
    }

    fun cancelShieldNotification(context: Context, notificationId: Int) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
            notificationManager?.cancel(notificationId)
        } catch (e: Exception) {
            Log.e(TAG, "Notification cancel error", e)
        }
    }
}
