package com.resolvecommunity.awaydoomscrollin

import android.app.ActivityManager
import android.content.Context
import android.os.Build

/**
 * Notices when the phone, not the user, switched protection off.
 *
 * Xiaomi, Redmi and POCO (and some other vendors) stop apps in the background
 * the way a force stop does. Android then removes the accessibility service
 * from the enabled list, and none of our code runs: no unbind, no "stopped"
 * notification, so the user only sees Reels come back. The service sets a mark
 * when it connects and clears it on a normal unbind or destroy (the user
 * turning it off), so a mark that is still set while the service is off can
 * only come from such a stop. It is only noted in the troubleshooting log, so
 * a support request shows the cause; nothing is shown to the user.
 */
internal object ProtectionStopWatch {
    const val KEY_RUNNING = "service_running_mark"

    fun markRunning(context: Context) {
        prefs(context).edit().putBoolean(KEY_RUNNING, true).apply()
    }

    fun markStopped(context: Context) {
        prefs(context).edit().putBoolean(KEY_RUNNING, false).apply()
    }

    fun isMarkSet(context: Context): Boolean = prefs(context).getBoolean(KEY_RUNNING, false)

    fun stoppedUnexpectedly(markSet: Boolean, serviceEnabled: Boolean): Boolean =
        markSet && !serviceEnabled

    /** True once per such stop: the mark is cleared so the same stop is not reported twice. */
    fun consumeUnexpectedStop(context: Context, serviceEnabled: Boolean): Boolean {
        val prefs = prefs(context)
        if (!stoppedUnexpectedly(prefs.getBoolean(KEY_RUNNING, false), serviceEnabled)) return false
        prefs.edit().putBoolean(KEY_RUNNING, false).apply()
        val (reason, description) = lastExit(context)
        SupportDiagnostics.record(context, SupportEvent.SERVICE_STOPPED_BY_PHONE,
            mapOf("reason" to reason, "desc" to description))
        return true
    }

    /** Android's reason and description for the newest exit of our process. */
    private fun lastExit(context: Context): Pair<String, String?> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return "unknown" to null
        val am = context.getSystemService(ActivityManager::class.java) ?: return "unknown" to null
        val newest = runCatching { am.getHistoricalProcessExitReasons(context.packageName, 0, 1) }
            .getOrNull()?.firstOrNull() ?: return "unknown" to null
        return SupportDiagnostics.exitReason(newest.reason) to
            SupportDiagnostics.exitDescription(context, newest.description)
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(ProtectionPreferences.PREFS_NAME, Context.MODE_PRIVATE)
}
