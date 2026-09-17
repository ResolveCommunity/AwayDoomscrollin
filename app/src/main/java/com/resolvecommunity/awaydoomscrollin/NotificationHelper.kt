package com.resolvecommunity.awaydoomscrollin

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

object NotificationHelper {

    private const val TAG = "NotificationHelper"
    private val VIBRATION_PATTERN = longArrayOf(0, 250, 150, 250)

    // Notification channels freeze their name/description/vibration at first creation.
    // The id therefore carries the version and language flag so updates get a
    // freshly configured channel instead of keeping the previous settings.
    fun statusChannelId(isEnglish: Boolean) =
        if (isEnglish) "shield_status_v3_en" else "shield_status_v3_tr"

    fun showShieldStatusNotification(
        context: Context,
        title: String,
        message: String,
        notificationId: Int = 1002
    ) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

            val prefs = context.getSharedPreferences("away_doomscroll_prefs", Context.MODE_PRIVATE)
            val isEn = prefs.getString("app_language", null) == "en"
            val channelId = statusChannelId(isEn)

            val channelTitle = if (isEn) "Protection and permission status" else "Koruma ve izin durumu"
            val channelDesc = if (isEn) "Protection status and accessibility notifications" else "Koruma durumu ve erişilebilirlik bildirimleri"

            val channel = android.app.NotificationChannel(
                channelId,
                channelTitle,
                android.app.NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = channelDesc
                enableVibration(true)
                vibrationPattern = VIBRATION_PATTERN
                setSound(null, null)
            }
            notificationManager.createNotificationChannel(channel)

            // Remove legacy silent channels if present to keep system settings clean
            runCatching {
                notificationManager.deleteNotificationChannel("shield_status_silent_v2_en")
                notificationManager.deleteNotificationChannel("shield_status_silent_v2_tr")
            }

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent = android.app.PendingIntent.getActivity(
                context, 0, intent,
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
            )

            val builder = androidx.core.app.NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_tier_seed)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)
                .setVibrate(VIBRATION_PATTERN)
                .setSound(null)
                .setOnlyAlertOnce(true)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                notificationManager.notify(notificationId, builder.build())
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
