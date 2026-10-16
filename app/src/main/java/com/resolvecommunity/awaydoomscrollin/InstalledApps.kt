package com.resolvecommunity.awaydoomscrollin

import android.content.Context
import android.content.pm.PackageManager

/**
 * Which protected apps the settings screens should offer. An app that is not
 * installed, or a preinstalled one the user disabled (YouTube on de-Googled
 * phones), is shown dimmed without a switch. Its stored preference is kept,
 * so reinstalling the app brings protection back as the user left it.
 * Visibility comes from the manifest's <queries> entries.
 */
internal object InstalledApps {
    fun isAvailable(context: Context, app: ProtectedApp): Boolean = try {
        context.packageManager.getApplicationInfo(app.packageName, 0).enabled
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    fun available(context: Context): Set<ProtectedApp> =
        ProtectedApp.entries.filterTo(mutableSetOf()) { isAvailable(context, it) }

    fun displayName(app: ProtectedApp): String = when (app) {
        ProtectedApp.INSTAGRAM -> "Instagram"
        ProtectedApp.TIKTOK -> "TikTok"
        ProtectedApp.YOUTUBE -> "YouTube"
    }
}
