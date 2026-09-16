package com.resolvecommunity.awaydoomscrollin

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstagramScanScheduleTest {
    @Test fun eventBurstDoesNotPostponePendingScan() {
        val schedule = InstagramScanSchedule()
        assertTrue(schedule.request(0, 16))
        for (time in 1L..1000L) assertFalse(schedule.request(time, 16))
    }
    @Test fun urgentScanCanAdvanceRetryButNotDuplicateIt() {
        val schedule = InstagramScanSchedule()
        assertTrue(schedule.request(0, 250))
        assertTrue(schedule.request(10, 16))
        assertFalse(schedule.request(10, 16))
    }
    @Test fun runningOrCancelledScanAllowsFreshRequest() {
        val schedule = InstagramScanSchedule()
        assertTrue(schedule.request(0, 16))
        schedule.clear()
        assertTrue(schedule.request(20, 16))
    }
}
