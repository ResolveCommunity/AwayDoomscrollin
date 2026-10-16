package com.resolvecommunity.awaydoomscrollin

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class YouTubeProtectionMetricsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val prefs get() = context.getSharedPreferences(ProtectionPreferences.PREFS_NAME, Context.MODE_PRIVATE)
    private val noon get() = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 12); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    @Before fun reset() { prefs.edit().clear().commit() }

    @Test fun repeatedLayoutDoesNotRestartOrDoubleCount() {
        val now = noon
        YouTubeProtectionMetrics.onVisibilityChanged(context, true, now, 1000)
        YouTubeProtectionMetrics.onVisibilityChanged(context, true, now + 10000, 11000)
        assertEquals(30000L, YouTubeProtectionMetrics.totalMs(prefs, 31000))
        YouTubeProtectionMetrics.onVisibilityChanged(context, false, now + 30000, 31000)
        YouTubeProtectionMetrics.onVisibilityChanged(context, false, now + 40000, 41000)
        assertEquals(30000L, YouTubeProtectionMetrics.totalMs(prefs, 41000))
        assertEquals(30000L, YouTubeProtectionMetrics.todayMs(prefs, now + 40000, 41000))
    }

    @Test fun hiddenIntervalIsExcludedAndResumeStartsNewSpan() {
        val now = noon
        YouTubeProtectionMetrics.onVisibilityChanged(context, true, now, 1000)
        YouTubeProtectionMetrics.onVisibilityChanged(context, false, now + 5000, 6000)
        YouTubeProtectionMetrics.onVisibilityChanged(context, true, now + 25000, 26000)
        YouTubeProtectionMetrics.onVisibilityChanged(context, false, now + 30000, 31000)
        assertEquals(10000L, YouTubeProtectionMetrics.totalMs(prefs, 31000))
    }

    @Test fun reconnectDiscardsUntrustedSpanButPreservesStoredData() {
        val now = noon
        prefs.edit().putInt("blocks_youtube", 7).putLong("instagram_protection_total_ms", 123L).commit()
        YouTubeProtectionMetrics.onVisibilityChanged(context, true, now, 1000)
        YouTubeProtectionMetrics.onVisibilityChanged(context, false, now + 5000, 6000)
        YouTubeProtectionMetrics.onVisibilityChanged(context, true, now + 5000, 6000)
        YouTubeProtectionMetrics.discardInterruptedSession(context)
        assertEquals(5000L, YouTubeProtectionMetrics.totalMs(prefs, 31000))
        assertEquals(7, prefs.getInt("blocks_youtube", 0))
        assertEquals(123L, prefs.getLong("instagram_protection_total_ms", 0))
    }

    @Test fun midnightSplitsDurationAcrossDays() {
        val midnight = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        YouTubeProtectionMetrics.onVisibilityChanged(context, true, midnight - 5000, 1000)
        assertEquals(5000L, YouTubeProtectionMetrics.todayMs(prefs, midnight + 5000, 11000))
        YouTubeProtectionMetrics.onVisibilityChanged(context, false, midnight + 5000, 11000)
        assertEquals(5000L, YouTubeProtectionMetrics.dayMs(prefs, InstagramProtectionMetrics.dateString(midnight - 1)))
        assertEquals(5000L, YouTubeProtectionMetrics.dayMs(prefs, InstagramProtectionMetrics.dateString(midnight)))
    }

    @Test fun wallClockChangeCannotInventExtraDuration() {
        YouTubeProtectionMetrics.onVisibilityChanged(context, true, noon, 1000)
        YouTubeProtectionMetrics.onVisibilityChanged(context, false, noon + 3600000, 6000)
        assertEquals(5000L, YouTubeProtectionMetrics.totalMs(prefs, 6000))
    }
}
