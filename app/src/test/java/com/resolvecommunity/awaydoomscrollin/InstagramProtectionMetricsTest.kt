package com.resolvecommunity.awaydoomscrollin

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InstagramProtectionMetricsTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        preferences().edit().clear().commit()
    }

    @After
    fun tearDown() {
        preferences().edit().clear().commit()
    }

    @Test
    fun curtainDurationIsStoredAsMeasuredTime() {
        val start = localNoon()
        InstagramProtectionMetrics.onCurtainShown(context, start)
        InstagramProtectionMetrics.onCurtainHidden(context, start + 65_000L)

        val prefs = preferences()
        assertEquals(65_000L, InstagramProtectionMetrics.totalMs(prefs, start + 65_000L))
        assertEquals(65_000L, InstagramProtectionMetrics.dayMs(
            prefs,
            InstagramProtectionMetrics.dateString(start)
        ))
        assertEquals(
            InstagramProtectionMetrics.MEASUREMENT_VERSION,
            prefs.getInt(InstagramProtectionMetrics.KEY_MEASUREMENT_VERSION, 0)
        )
        assertFalse(prefs.contains(InstagramProtectionMetrics.KEY_ACTIVE_STARTED_AT_MS))
    }

    @Test
    fun repeatedShowDoesNotRestartTheActiveSpan() {
        val start = localNoon()
        InstagramProtectionMetrics.onCurtainShown(context, start)
        InstagramProtectionMetrics.onCurtainShown(context, start + 10_000L)

        assertEquals(30_000L, InstagramProtectionMetrics.totalMs(preferences(), start + 30_000L))
    }

    @Test
    fun interruptedSpanIsDiscardedInsteadOfInventingDuration() {
        val start = localNoon()
        InstagramProtectionMetrics.onCurtainShown(context, start)
        InstagramProtectionMetrics.discardInterruptedSession(context)

        assertEquals(0L, InstagramProtectionMetrics.totalMs(preferences(), start + 30_000L))
    }

    @Test
    fun measuredDurationFormattingIsCompactAndDoesNotEstimateSavedTime() {
        assertEquals("0 sn", formatMeasuredDuration(0L, isEn = false))
        assertEquals("45s", formatMeasuredDuration(45_000L, isEn = true))
        assertEquals("2 dk", formatMeasuredDuration(120_000L, isEn = false))
        assertEquals("1 hr 5 min", formatMeasuredDuration(3_900_000L, isEn = true))
    }

    private fun localNoon(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 12)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun preferences() = context.getSharedPreferences(
        ProtectionPreferences.PREFS_NAME,
        Context.MODE_PRIVATE
    )
}
