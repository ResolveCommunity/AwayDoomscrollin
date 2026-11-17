package com.resolvecommunity.awaydoomscrollin

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock

/**
 * Tells the user as soon as the phone has stopped protection.
 *
 * Once Xiaomi HyperOS (or another vendor) ends the app's process, none of our
 * code runs, so nothing can notice the stop at that moment. While protection
 * runs, the service keeps pushing a one-shot alarm about a minute ahead; it
 * never fires while the service is alive. If the process is ended, the alarm
 * fires, starts the app again just long enough to check whether the system
 * still runs protection, and posts the "not running" notification if it does
 * not. The alarm does not wake the phone: a stop while the screen is off is
 * reported when the phone is next used.
 *
 * Android restarts a service whose process died on its own within about half
 * a minute, so the first finding is checked once more before the user hears
 * about it. A force stop also cancels the alarm; the app reports that one the
 * next time it is opened (see ProtectionStopWatch).
 */
internal object ProtectionWatchdog {
    const val HEARTBEAT_MS = 30_000L
    private const val GRACE_MS = 35_000L
    private const val RECHECK_MS = 20_000L
    private const val WINDOW_MS = 10_000L
    private const val RESTART_CHECK_MS = 60_000L
    private const val REQUEST_CODE = 4107
    private const val KEY_SUSPECT = "watchdog_stop_suspected"

    /** Pushes the alarm ahead; called on connect and on every heartbeat. */
    fun arm(context: Context, delayMs: Long = GRACE_MS) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        runCatching {
            alarms.setWindow(AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + delayMs, WINDOW_MS, alarmIntent(context))
        }
    }

    /** The service is alive: forget an earlier suspicion and push the alarm ahead. */
    fun heartbeat(context: Context) {
        val prefs = prefs(context)
        if (prefs.contains(KEY_SUSPECT)) prefs.edit().remove(KEY_SUSPECT).apply()
        arm(context)
    }

    /**
     * After a reboot or an update the alarm is gone. Android normally binds
     * the service again within seconds of unlocking; when it does not (seen on
     * HyperOS), the alarm reports it like any other stop.
     */
    fun checkAfterRestart(context: Context) {
        if (!ProtectionServiceStatus.isListed(context, AntiScrollService::class.java)) return
        ProtectionStopWatch.markRunning(context)
        arm(context, RESTART_CHECK_MS)
    }

    /** The user turned protection off: the service already says so itself. */
    fun disarm(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        runCatching { alarms.cancel(alarmIntent(context)) }
        prefs(context).edit().remove(KEY_SUSPECT).apply()
    }

    internal enum class Finding { STILL_RUNNING, USER_TURNED_OFF, CHECK_AGAIN, STOPPED_BY_PHONE }

    fun decide(state: ProtectionServiceState, markSet: Boolean, suspected: Boolean): Finding = when {
        state == ProtectionServiceState.RUNNING -> Finding.STILL_RUNNING
        !markSet -> Finding.USER_TURNED_OFF
        !suspected -> Finding.CHECK_AGAIN
        else -> Finding.STOPPED_BY_PHONE
    }

    fun onAlarm(context: Context) {
        val prefs = prefs(context)
        val state = ProtectionServiceStatus.read(context)
        when (decide(state, ProtectionStopWatch.isMarkSet(context), prefs.getBoolean(KEY_SUSPECT, false))) {
            Finding.STILL_RUNNING -> {
                // Alive, but its heartbeat paused (deep sleep): keep watching.
                prefs.edit().remove(KEY_SUSPECT).apply()
                arm(context)
            }
            Finding.USER_TURNED_OFF -> prefs.edit().remove(KEY_SUSPECT).apply()
            Finding.CHECK_AGAIN -> {
                prefs.edit().putBoolean(KEY_SUSPECT, true).apply()
                arm(context, RECHECK_MS)
            }
            Finding.STOPPED_BY_PHONE -> {
                prefs.edit().remove(KEY_SUSPECT).apply()
                ProtectionStopWatch.consumeUnexpectedStop(context, serviceEnabled = false)
                AppText.init(context)
                NotificationHelper.cancelShieldNotification(context, StatusNotificationPolicy.ACTIVE_ID)
                // A quiet notice with the same id may still be shown; an update
                // of it would not vibrate (only-alert-once), a new post does.
                NotificationHelper.cancelShieldNotification(context, StatusNotificationPolicy.STOPPED_ID)
                if (isXiaomiFamily()) {
                    // Tested on a Xiaomi 14: with Autostart on, Android brings
                    // protection back by itself after HyperOS ends the app.
                    NotificationHelper.showShieldStatusNotification(
                        context,
                        AppText.get(R.string.notif_protection_stopped_title),
                        AppText.get(R.string.notif_xiaomi_stopped_text),
                        StatusNotificationPolicy.STOPPED_ID,
                        openIntent = autostartIntent(context),
                        actions = listOf(
                            AppText.get(R.string.main_xiaomi_autostart_button) to autostartIntent(context),
                            AppText.get(R.string.main_turn_on_protection) to
                                AccessibilitySettingsLink.intent(context, newTask = true)
                        )
                    )
                } else {
                    NotificationHelper.showShieldStatusNotification(
                        context,
                        AppText.get(R.string.notif_protection_stopped_title),
                        AppText.get(
                            if (state == ProtectionServiceState.LISTED_NOT_RUNNING) R.string.main_protection_stuck_hint
                            else R.string.notif_protection_stopped_text
                        ),
                        StatusNotificationPolicy.STOPPED_ID,
                        // Turning the switch off and on is what brings it back.
                        openIntent = AccessibilitySettingsLink.intent(context, newTask = true)
                    )
                }
            }
        }
    }

    private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, REQUEST_CODE, Intent(context, ProtectionWatchdogReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(ProtectionPreferences.PREFS_NAME, Context.MODE_PRIVATE)
}

/** Boot and app updates clear the alarm; this sets it again. */
class ProtectionRestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED ->
                ProtectionWatchdog.checkAfterRestart(context.applicationContext)
        }
    }
}

class ProtectionWatchdogReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ProtectionWatchdog.onAlarm(context.applicationContext)
    }
}
