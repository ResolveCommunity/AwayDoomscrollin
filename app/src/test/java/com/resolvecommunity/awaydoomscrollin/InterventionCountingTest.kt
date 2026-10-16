package com.resolvecommunity.awaydoomscrollin

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InterventionCountingTest {
    private lateinit var service: AntiScrollService
    private val prefs get() = service.getSharedPreferences(ProtectionPreferences.PREFS_NAME, Context.MODE_PRIVATE)
    @Before fun prepare() {
        service = Robolectric.buildService(AntiScrollService::class.java).create().get()
        prefs.edit().clear().commit()
    }
    private fun invoke(name: String) {
        AntiScrollService::class.java.getDeclaredMethod(name).apply { isAccessible = true }.invoke(service)
    }
    private fun exit(accepted: Boolean) {
        AntiScrollService::class.java.getDeclaredMethod("recordYouTubeViewerIntervention", Boolean::class.javaPrimitiveType)
            .apply { isAccessible = true }.invoke(service, accepted)
    }
    @Test fun failedAndRepeatedExitAttemptsCountOnceAndPreserveHistoricalTotals() {
        prefs.edit().putInt("blocks_youtube", 8).putInt("total_blocks", 12)
            .putInt("blocks_instagram", 4).commit()
        exit(false)
        assertEquals(8, prefs.getInt("blocks_youtube", 0))
        exit(true)
        exit(true)
        assertEquals(9, prefs.getInt("blocks_youtube", 0))
        assertEquals(13, prefs.getInt("total_blocks", 0))
        assertEquals(4, prefs.getInt("blocks_instagram", 0))
    }
    @Test fun separateConsumedShortsTapsEachCountOnce() {
        repeat(3) { invoke("onYouTubeShortsGuardBlocked") }
        assertEquals(3, prefs.getInt("blocks_youtube", 0))
        assertEquals(3, prefs.getInt("total_blocks", 0))
    }
}
