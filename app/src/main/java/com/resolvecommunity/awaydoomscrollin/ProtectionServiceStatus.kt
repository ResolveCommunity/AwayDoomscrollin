package com.resolvecommunity.awaydoomscrollin

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityManager

/**
 * Whether protection really runs, not only whether its switch is on.
 *
 * Xiaomi HyperOS can leave the service in the enabled list while the system
 * holds it as crashed and its accessibility master switch is 0: Settings shows
 * the switch on, nothing runs, and only turning it off and on brings it back.
 * The enabled list alone then reads "on". AccessibilityManager lists only the
 * services the system has actually bound, so both are read.
 */
internal enum class ProtectionServiceState { RUNNING, LISTED_NOT_RUNNING, OFF }

internal object ProtectionServiceStatus {

    /** [bound] is null when the system could not be asked; the enabled list then decides. */
    fun state(listed: Boolean, bound: Boolean?): ProtectionServiceState = when {
        !listed -> ProtectionServiceState.OFF
        bound == false -> ProtectionServiceState.LISTED_NOT_RUNNING
        else -> ProtectionServiceState.RUNNING
    }

    fun read(context: Context, serviceClass: Class<*> = AntiScrollService::class.java): ProtectionServiceState =
        state(isListed(context, serviceClass), isBound(context, serviceClass))

    fun isListed(context: Context, serviceClass: Class<*>): Boolean {
        val expected = ComponentName(context, serviceClass)
        val setting = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        }.getOrNull() ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(setting)
        while (splitter.hasNext()) {
            if (ComponentName.unflattenFromString(splitter.next()) == expected) return true
        }
        return false
    }

    fun isBound(context: Context, serviceClass: Class<*>): Boolean? = runCatching {
        val manager = context.getSystemService(AccessibilityManager::class.java) ?: return null
        manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any {
            val info = it.resolveInfo?.serviceInfo
            info != null && info.packageName == context.packageName && info.name == serviceClass.name
        }
    }.getOrNull()

    /** The accessibility master switch; HyperOS writes 0 when it drops the service. */
    fun masterSwitch(context: Context): Int? = runCatching {
        Settings.Secure.getInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED)
    }.getOrNull()
}
