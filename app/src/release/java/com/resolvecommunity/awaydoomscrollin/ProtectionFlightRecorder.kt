package com.resolvecommunity.awaydoomscrollin

import android.content.Context

/** Release builds contain no diagnostic storage behavior. */
internal object ProtectionFlightRecorder {
    const val ENABLED = false

    fun record(context: Context, category: String, detail: String = "") = Unit
    fun startSession(context: Context, versionName: String) = Unit
}
