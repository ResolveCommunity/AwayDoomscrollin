package com.resolvecommunity.awaydoomscrollin

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

object NotificationHelper {

    private const val TAG = "NotificationHelper"
    // Notification channels freeze their name/description at first creation.
    // The id therefore carries the language flag so a language switch gets a
    // freshly named channel instead of keeping the previous language's text.
    fun statusChannelId(isEnglish: Boolean) =
        if (isEnglish) "shield_status_silent_v2_en" else "shield_status_silent_v2_tr"

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
                android.app.NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = channelDesc
                enableVibration(false)
                setSound(null, null)
            }
            notificationManager.createNotificationChannel(channel)

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent = android.app.PendingIntent.getActivity(
                context, 0, intent,
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
            )

            val builder = androidx.core.app.NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
                .setSilent(true)
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
