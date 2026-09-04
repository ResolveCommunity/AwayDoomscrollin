package com.awaydoomscrollin.app

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.os.Bundle

open class BaseClearTaskActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finishAndRemoveTask()
    }
}

class ClearTaskInstagramActivity : BaseClearTaskActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.killBackgroundProcesses("com.instagram.android")
        } catch (_: Exception) {}
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
        finishAndRemoveTask()
    }
}

