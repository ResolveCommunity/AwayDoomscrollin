package com.resolvecommunity.awaydoomscrollin

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProtectedDayStreakTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        prefs().edit().clear().commit()
    }

    @After
    fun tearDown() {
        prefs().edit().clear().commit()
    }

    @Test
    fun quietDayWithoutBlocksKeepsTheStreak() {
        seed(streak = 3, lastActiveDay = "2026-10-14")

        AccessibilityStreakPolicy.syncState(context, isAccessibilityActive = true, now = at(2026, 10, 15, 10))
        assertEquals(4, streak())

        // 16 October: protection stays on, but no protected app is opened and nothing is blocked.
        AccessibilityStreakPolicy.recordHeartbeat(context, at(2026, 10, 17, 9))

        assertEquals(6, streak())
        assertEquals("2026-10-17", prefs().getString("last_active_day", null))
    }

    @Test
    fun repeatedChecksOnTheSameDayCountItOnce() {
        seed(streak = 3, lastActiveDay = "2026-10-14")

        AccessibilityStreakPolicy.onServiceConnected(context, at(2026, 10, 15, 8))
        AccessibilityStreakPolicy.recordHeartbeat(context, at(2026, 10, 15, 12))
        AccessibilityStreakPolicy.syncState(context, isAccessibilityActive = true, now = at(2026, 10, 15, 23))

        assertEquals(4, streak())
    }

    @Test
    fun disabledForTwentyFourHoursResetsTheStreak() {
        seed(streak = 8, lastActiveDay = "2026-10-14")
        AccessibilityStreakPolicy.onServiceDisconnected(context, at(2026, 10, 14, 20))

        val resetWhileOff = AccessibilityStreakPolicy.syncState(
            context, isAccessibilityActive = false, now = at(2026, 10, 15, 20)
        )

        assertTrue(resetWhileOff)
        assertEquals(0, streak())

        // Turning protection back on starts a new streak with today as its first day.
        val resetOnReconnect = AccessibilityStreakPolicy.onServiceConnected(context, at(2026, 10, 16, 9))
        assertFalse(resetOnReconnect)
        assertEquals(1, streak())
    }

    @Test
    fun reconnectingAfterTwentyFourHoursOffResetsBeforeCountingToday() {
        seed(streak = 8, lastActiveDay = "2026-10-14")
        AccessibilityStreakPolicy.onServiceDisconnected(context, at(2026, 10, 14, 20))

        val reset = AccessibilityStreakPolicy.onServiceConnected(context, at(2026, 10, 16, 9))

        assertTrue(reset)
        assertEquals(1, streak())
    }

    @Test
    fun shortOffPeriodAcrossMidnightKeepsTheStreak() {
        seed(streak = 8, lastActiveDay = "2026-10-14")
        AccessibilityStreakPolicy.onServiceDisconnected(context, at(2026, 10, 14, 22))

        val reset = AccessibilityStreakPolicy.onServiceConnected(context, at(2026, 10, 15, 8))

        assertFalse(reset)
        assertEquals(9, streak())
    }

    @Test
    fun upgradeFromBlockBasedDataKeepsTheStoredValue() {
        // State written by v1.3.0 after a block yesterday; no new keys exist yet.
        prefs().edit()
            .putInt("streak_days", 12)
            .putString("last_active_day", "2026-10-16")
            .putInt("total_blocks", 140)
            .putLong("user_xp", 570L)
            .commit()

        AccessibilityStreakPolicy.onServiceConnected(context, at(2026, 10, 16, 23))
        assertEquals(12, streak())

        AccessibilityStreakPolicy.syncState(context, isAccessibilityActive = true, now = at(2026, 10, 17, 7))
        assertEquals(13, streak())
        assertEquals("2026-10-17", prefs().getString("last_active_day", null))
        assertEquals(140, prefs().getInt("total_blocks", 0))
        assertEquals(570L, prefs().getLong("user_xp", 0L))
    }

    @Test
    fun upgradeWithAnOlderLastBlockDayDoesNotResetTheStreak() {
        seed(streak = 12, lastActiveDay = "2026-10-13")

        AccessibilityStreakPolicy.syncState(context, isAccessibilityActive = true, now = at(2026, 10, 17, 7))

        assertEquals(16, streak())
    }

    @Test
    fun streakResetEarlierTodayRestartsTomorrow() {
        seed(streak = 5, lastActiveDay = "2026-10-15")
        AccessibilityStreakPolicy.recordHeartbeat(context, at(2026, 10, 16, 9))
        ProtectionPreferences.setEnabled(prefs(), ProtectedApp.TIKTOK, enabled = false, resetStreak = true)

        AccessibilityStreakPolicy.recordHeartbeat(context, at(2026, 10, 16, 18))
        assertEquals(0, streak())

        AccessibilityStreakPolicy.recordHeartbeat(context, at(2026, 10, 17, 9))
        assertEquals(1, streak())
    }

    @Test
    fun clockMovedBackwardsDoesNotCountDaysTwice() {
        val update = AccessibilityStreakPolicy.advanceForProtectedDay(
            currentStreak = 10,
            lastActiveDay = "2026-10-20",
            today = LocalDate.of(2026, 10, 17)
        )

        assertEquals(10, update.streak)
        assertEquals("2026-10-20", update.lastActiveDay)
    }

    @Test
    fun unreadableLastDayKeepsThePositiveStreak() {
        val update = AccessibilityStreakPolicy.advanceForProtectedDay(
            currentStreak = 7,
            lastActiveDay = "not-a-date",
            today = LocalDate.of(2026, 10, 17)
        )

        assertEquals(7, update.streak)
        assertEquals("2026-10-17", update.lastActiveDay)
    }

    @Test
    fun firstProtectedDayStartsAtOne() {
        AccessibilityStreakPolicy.onServiceConnected(context, at(2026, 10, 17, 9))

        assertEquals(1, streak())
        assertEquals("2026-10-17", prefs().getString("last_active_day", null))
    }

    private fun seed(streak: Int, lastActiveDay: String) {
        prefs().edit()
            .putInt("streak_days", streak)
            .putString("last_active_day", lastActiveDay)
            .commit()
    }

    private fun streak(): Int = prefs().getInt("streak_days", -1)

    private fun at(year: Int, month: Int, day: Int, hour: Int): Long =
        LocalDateTime.of(year, month, day, hour, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

    private fun prefs(): SharedPreferences =
        context.getSharedPreferences(ProtectionPreferences.PREFS_NAME, Context.MODE_PRIVATE)
}
