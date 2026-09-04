package com.awaydoomscrollin.app

import android.app.Activity
import android.os.Bundle

open class BaseClearTaskActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finishAndRemoveTask()
    }
}

class ClearTaskInstagramActivity : BaseClearTaskActivity()
class ClearTaskTiktokActivity : BaseClearTaskActivity()
class ClearTaskYoutubeActivity : BaseClearTaskActivity()

