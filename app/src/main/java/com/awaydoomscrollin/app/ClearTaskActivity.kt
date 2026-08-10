package com.awaydoomscrollin.app

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings

open class BaseClearTaskActivity : Activity() {
    fun launchSettings(pkg: String) {
        try {
            val settingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", pkg, null)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                        Intent.FLAG_ACTIVITY_CLEAR_TASK or 
                        Intent.FLAG_ACTIVITY_NO_ANIMATION
            }
            startActivity(settingsIntent)
        } catch (e: Exception) {}
    }
}

class ClearTaskInstagramActivity : BaseClearTaskActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.killBackgroundProcesses("com.instagram.android")
        } catch (_: Exception) {}
        launchSettings("com.instagram.android")
        finishAndRemoveTask()
    }
}

class ClearTaskTiktokActivity : BaseClearTaskActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.killBackgroundProcesses("com.zhiliaoapp.musically")
        } catch (_: Exception) {}
        launchSettings("com.zhiliaoapp.musically")
        finishAndRemoveTask()
    }
}

class ClearTaskYoutubeActivity : BaseClearTaskActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.killBackgroundProcesses("com.google.android.youtube")
        } catch (_: Exception) {}
        launchSettings("com.google.android.youtube")
        finishAndRemoveTask()
    }
}
