package com.resolvecommunity.awaydoomscrollin

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings

/**
 * Opens Android's accessibility settings with our entry highlighted.
 *
 * The page for a single service (ACTION_ACCESSIBILITY_DETAILS_SETTINGS) needs
 * a system permission, so the list page is opened instead and asked to scroll
 * to and highlight our service. Settings apps that do not support the
 * highlight simply open the list. When the phone has left protection "on but
 * not running", turning our switch off and on there brings it back.
 */
internal object AccessibilitySettingsLink {
    private const val EXTRA_FRAGMENT_ARG_KEY = ":settings:fragment_args_key"
    private const val EXTRA_SHOW_FRAGMENT_ARGS = ":settings:show_fragment_args"

    fun intent(context: Context, newTask: Boolean): Intent {
        val key = ComponentName(context, AntiScrollService::class.java).flattenToString()
        return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .putExtra(EXTRA_FRAGMENT_ARG_KEY, key)
            .putExtra(EXTRA_SHOW_FRAGMENT_ARGS, Bundle().apply { putString(EXTRA_FRAGMENT_ARG_KEY, key) })
            .apply { if (newTask) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    }
}
