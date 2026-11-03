package com.resolvecommunity.awaydoomscrollin

import org.junit.Assert.assertFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InstagramDiagnosticPrivacyTest {
    @Test fun diagnosticSignalsNeverRecordScreenTitles() {
        // Release builds have no recorder storage; only the debug backend logs.
        assumeTrue(ProtectionFlightRecorder.ENABLED)
        val service = Robolectric.buildService(AntiScrollService::class.java).create().get()
        try {
            // Synthetic text only. A real title can contain an account or chat name.
            val privateTitle = "PRIVATE_HEADER_SENTINEL"
            val signals = InstagramUiSignals(ids = setOf("direct_thread_header"),
                titles = setOf(privateTitle))
            AntiScrollService::class.java.getDeclaredMethod("recordInstagramSignals",
                InstagramUiSignals::class.java).apply {
                isAccessible = true
                invoke(service, signals)
            }
            fun recorderField(name: String): Any =
                ProtectionFlightRecorder::class.java.getDeclaredField(name).run {
                    isAccessible = true
                    checkNotNull(get(ProtectionFlightRecorder))
                }
            synchronized(recorderField("lock")) {
                val lines = recorderField("lines") as Collection<*>
                assertFalse(lines.any { it.toString().contains(privateTitle) })
            }
        } finally {
            service.onDestroy()
        }
    }
}
