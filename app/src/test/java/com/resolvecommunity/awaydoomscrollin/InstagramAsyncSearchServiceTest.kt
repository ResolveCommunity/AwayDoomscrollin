package com.resolvecommunity.awaydoomscrollin

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Looper
import android.view.accessibility.AccessibilityRecord
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.LooperMode
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowAccessibilityService

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], shadows = [InstagramAsyncSearchServiceTest.ServiceShadow::class,
    InstagramSearchObservationTest.NodeShadow::class, InstagramAsyncSearchServiceTest.RecordShadow::class])
@LooperMode(LooperMode.Mode.PAUSED)
class InstagramAsyncSearchServiceTest {
    @Implements(AccessibilityService::class)
    class ServiceShadow : ShadowAccessibilityService() {
        val queriedOnUi = AtomicBoolean(false)
        var root: AccessibilityNodeInfo? = null
        @Implementation fun getRootInActiveWindow(): AccessibilityNodeInfo? {
            if (Looper.myLooper() == Looper.getMainLooper()) queriedOnUi.set(true)
            return root
        }
    }

    @Implements(AccessibilityRecord::class)
    class RecordShadow : org.robolectric.shadows.ShadowAccessibilityRecord() {
        companion object {
            var source: AccessibilityNodeInfo? = null
            val queriedOnUi = AtomicBoolean(false)
        }
        @Implementation override fun getWindowId(): Int = 10
        @Implementation override fun getSource(): AccessibilityNodeInfo? {
            if (Looper.myLooper() == Looper.getMainLooper()) queriedOnUi.set(true)
            return source
        }
    }

    private lateinit var service: AntiScrollService
    private lateinit var overlay: InstagramFeedOverlayController
    private lateinit var serviceShadow: ServiceShadow

    @Before fun prepare() {
        RecordShadow.source = null
        RecordShadow.queriedOnUi.set(false)
        service = Robolectric.buildService(AntiScrollService::class.java).create().get()
        AccessibilityConsent.accept(service)
        serviceShadow = Shadow.extract(service)
        field("lastInstagramScreen", InstagramScreen.EXPLORE)
        field("lastPackage", "com.instagram.android")
        overlay = AntiScrollService::class.java.getDeclaredMethod("getInstagramOverlay").run {
            isAccessible = true
            invoke(service) as InstagramFeedOverlayController
        }
        assertTrue(overlay.show(Rect(0, 200, 1080, 1800), InstagramScreen.EXPLORE, true))
    }

    @After fun closeService() { service.onDestroy() }

    private fun field(name: String, value: Any) {
        AntiScrollService::class.java.getDeclaredField(name).apply {
            isAccessible = true
            set(service, value)
        }
    }

    private fun apply(observation: InstagramSearchObservation) {
        AntiScrollService::class.java.getDeclaredMethod("applySearchObservation",
            InstagramSearchObservation::class.java).apply {
            isAccessible = true
            invoke(service, observation)
        }
    }

    @Test fun queuedExploreScanDoesNotReadTheAccessibilityRootOnTheUiThread() {
        val scan = AntiScrollService::class.java.getDeclaredField("instagramScan").run {
            isAccessible = true
            get(service) as Runnable
        }
        scan.run()
        assertFalse(serviceShadow.queriedOnUi.get())
        var tapHandled = false
        android.os.Handler(Looper.getMainLooper()).post { tapHandled = true }
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(tapHandled)
        assertFalse(serviceShadow.queriedOnUi.get())
    }

    @Test fun exploreClickCallbackDoesNotQueryTheRootOrSourceInline() {
        val event = AccessibilityEvent(AccessibilityEvent.TYPE_VIEW_CLICKED).apply {
            packageName = "com.instagram.android"
        }
        service.onAccessibilityEvent(event)
        assertFalse(serviceShadow.queriedOnUi.get())
    }

    @Test fun immutableSearchAndExploreResultsRenderWithoutUiTreeQueries() {
        apply(InstagramSearchObservation(InstagramSearchSurface.SEARCH, 10, "com.instagram.android",
            keyboardVisible = true))
        assertNull(overlay.visibleCurtain())
        val bounds = InstagramGeometrySample(0, 180, 1080, 1800)
        apply(InstagramSearchObservation(InstagramSearchSurface.EXPLORE, 10,
            "com.instagram.android", cover = bounds))
        assertEquals(Rect(0, 180, 1080, 1800), overlay.visibleCurtain()?.second)
        assertFalse(serviceShadow.queriedOnUi.get())
    }

    @Test fun incompleteObservationDoesNotExposeTheCoveredFeed() {
        apply(InstagramSearchObservation(InstagramSearchSurface.UNKNOWN))
        assertNotNull(overlay.visibleCurtain())
        assertFalse(serviceShadow.queriedOnUi.get())
    }

    @Test fun searchBackPrecoversFromCachedGeometryWithoutUiTreeQueries() {
        field("searchObservationActive", true)
        val cover = InstagramGeometrySample(0, 200, service.resources.displayMetrics.widthPixels,
            service.resources.displayMetrics.heightPixels - 100)
        apply(InstagramSearchObservation(InstagramSearchSurface.EXPLORE, 10, "com.instagram.android", cover = cover))
        apply(InstagramSearchObservation(InstagramSearchSurface.SEARCH, 10, "com.instagram.android"))
        AntiScrollService::class.java.getDeclaredMethod("onSearchReturnBack").apply {
            isAccessible = true
            invoke(service)
            invoke(service) // Repeated tap must not dispatch a second navigation.
        }
        assertNotNull(overlay.visibleCurtain())
        assertFalse(serviceShadow.queriedOnUi.get())
    }

    @Test fun keyboardBackAlsoRunsItsVerificationOnTheWorker() {
        field("searchObservationActive", true)
        apply(InstagramSearchObservation(InstagramSearchSurface.SEARCH, 10, "com.instagram.android",
            keyboardVisible = true))
        AntiScrollService::class.java.getDeclaredMethod("onSearchReturnBack").apply {
            isAccessible = true
            invoke(service)
        }
        assertFalse(serviceShadow.queriedOnUi.get())
    }
    @Test fun homeEventsAndScheduledScansDoNotBlockUiOnTreeQueries() {
        field("lastInstagramScreen", InstagramScreen.HOME_FEED)
        service.onAccessibilityEvent(AccessibilityEvent(AccessibilityEvent.TYPE_VIEW_CLICKED).apply {
            packageName = "com.instagram.android"
        })
        AntiScrollService::class.java.getDeclaredField("instagramScan").apply { isAccessible = true }
            .get(service).let { (it as Runnable).run() }
        assertFalse(serviceShadow.queriedOnUi.get())
    }
    @Test fun verifiedInboxRetiresCoverAndSearchControlsWithoutUiTreeQueries() {
        apply(InstagramSearchObservation(InstagramSearchSurface.INBOX, 10, "com.instagram.android",
            inbox = InstagramGeometrySample(0, 200, 1080, 1800)))
        assertNull(overlay.visibleCurtain())
        assertFalse(serviceShadow.queriedOnUi.get())
        assertEquals(InstagramScreen.DIRECT_MESSAGES,
            AntiScrollService::class.java.getDeclaredField("lastInstagramScreen")
                .apply { isAccessible = true }.get(service))
    }

    @Test fun verifiedMainInboxContentAndReturnEventsStayOffTheUiTree() {
        apply(InstagramSearchObservation(InstagramSearchSurface.INBOX, 10, "com.instagram.android",
            inbox = InstagramGeometrySample(0, 200, 1080, 1800)))
        for (kind in listOf(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED, AccessibilityEvent.TYPE_VIEW_CLICKED)) {
            service.onAccessibilityEvent(AccessibilityEvent(kind).apply { packageName = "com.instagram.android" })
        }
        assertFalse(serviceShadow.queriedOnUi.get())
        assertFalse(RecordShadow.queriedOnUi.get())
    }

    @Test fun settlingHomeUsesTheWorkerInsteadOfTheOldUiGeometryProbe() {
        field("lastInstagramScreen", InstagramScreen.HOME_FEED)
        AntiScrollService::class.java.getDeclaredField("homeScrollSettled").apply { isAccessible = true }
            .get(service).let { (it as Runnable).run() }
        assertFalse(serviceShadow.queriedOnUi.get())
    }

    private fun pendingHomeReturn(): InstagramProtectedNavigationLatch {
        val latch = AntiScrollService::class.java.getDeclaredField("instagramProtectedNavigation")
            .apply { isAccessible = true }.get(service) as InstagramProtectedNavigationLatch
        val request = latch.begin(InstagramTab.HOME, 10, InstagramTab.DIRECT, android.os.SystemClock.uptimeMillis())
        assertTrue(latch.markDispatched(request))
        return latch
    }

    @Test fun aSourceInboxUpdateCannotRemoveTheCoverDuringGuardedHomeReturn() {
        pendingHomeReturn()
        apply(InstagramSearchObservation(InstagramSearchSurface.INBOX, 10, "com.instagram.android",
            inbox = InstagramGeometrySample(0, 200, 1080, 1800)))
        assertNotNull(overlay.visibleCurtain())
        assertFalse(serviceShadow.queriedOnUi.get())
    }

    @Test fun verifiedHomeCompletesReturnSoAnotherHomeTapIsNotSuppressed() {
        val latch = pendingHomeReturn()
        apply(InstagramSearchObservation(InstagramSearchSurface.HOME, 10, "com.instagram.android",
            cover = InstagramGeometrySample(0, 400, 1080, 1800)))
        assertFalse(latch.isDuplicate(InstagramTab.HOME, android.os.SystemClock.uptimeMillis()))
        assertNotNull(overlay.visibleCurtain())
    }

    @Test fun aFreshCommittedMessagesTapSupersedesThePendingHomeReturn() {
        val latch = pendingHomeReturn()
        apply(InstagramSearchObservation(InstagramSearchSurface.INBOX_TRANSITION, 10, "com.instagram.android"))
        assertNull(overlay.visibleCurtain())
        assertFalse(latch.isDuplicate(InstagramTab.HOME, android.os.SystemClock.uptimeMillis()))
    }

    @Test fun strictHomeCannotUseTheBalancedStoriesGeometry() {
        ProtectionPreferences.setMode(service.getSharedPreferences(ProtectionPreferences.PREFS_NAME, 0),
            ProtectedApp.INSTAGRAM, ProtectionMode.STRICT)
        val oldCover = overlay.visibleCurtain()!!.second
        apply(InstagramSearchObservation(InstagramSearchSurface.HOME, 10, "com.instagram.android",
            cover = InstagramGeometrySample(0, 400, 1080, 1800),
            stories = InstagramGeometrySample(0, 100, 1080, 400)))
        assertEquals(oldCover, overlay.visibleCurtain()!!.second)
    }

    @Test fun newNativeClickInvalidatesAQueuedSearchBack() {
        field("searchObservationActive", true)
        apply(InstagramSearchObservation(InstagramSearchSurface.SEARCH, 10, "com.instagram.android",
            keyboardVisible = true))
        AntiScrollService::class.java.getDeclaredMethod("onSearchReturnBack").apply {
            isAccessible = true; invoke(service)
        }
        val epoch = AntiScrollService::class.java.getDeclaredField("searchBackEpoch")
            .apply { isAccessible = true }.get(service) as java.util.concurrent.atomic.AtomicLong
        val before = epoch.get()
        service.onAccessibilityEvent(AccessibilityEvent(AccessibilityEvent.TYPE_VIEW_CLICKED).apply {
            packageName = "com.instagram.android"
        })
        assertTrue(epoch.get() > before)
        assertFalse(AntiScrollService::class.java.getDeclaredField("searchBackPending")
            .apply { isAccessible = true }.getBoolean(service))
    }

    @Test fun committedMessagesHandoffParksOnlyTheContentCoverWithoutUiQueries() {
        overlay.updateReelsTabGuard(Rect(300, 1800, 450, 1900))
        apply(InstagramSearchObservation(InstagramSearchSurface.INBOX_TRANSITION, 10, "com.instagram.android"))
        assertNull(overlay.visibleCurtain())
        assertTrue(overlay.hasVisibleWindows) // The protected Reels tab is retained.
        assertFalse(serviceShadow.queriedOnUi.get())
    }

    @Test fun messagesClickDefersSourceRetrievalToTheReaderThread() {
        apply(InstagramSearchObservation(InstagramSearchSurface.EXPLORE, 10, "com.instagram.android",
            cover = InstagramGeometrySample(0, 200, 1080, 1800),
            directTab = InstagramGeometrySample(400, 1800, 600, 1900)))
        service.onAccessibilityEvent(AccessibilityEvent(AccessibilityEvent.TYPE_VIEW_CLICKED).apply {
            packageName = "com.instagram.android"
            eventTime = android.os.SystemClock.uptimeMillis()
        })
        assertFalse(RecordShadow.queriedOnUi.get())
        assertFalse(serviceShadow.queriedOnUi.get())
    }
    @Test fun aWindowEventRetainsTheOneTapHintUntilItExpires() {
        apply(InstagramSearchObservation(InstagramSearchSurface.EXPLORE, 10, "com.instagram.android",
            cover = InstagramGeometrySample(0, 200, 1080, 1800),
            directTab = InstagramGeometrySample(400, 1800, 600, 1900)))
        service.onAccessibilityEvent(AccessibilityEvent(AccessibilityEvent.TYPE_VIEW_CLICKED).apply {
            packageName = "com.instagram.android"
            eventTime = android.os.SystemClock.uptimeMillis()
        })
        val slot = AntiScrollService::class.java.getDeclaredField("pendingDirectTap").apply { isAccessible = true }
            .get(service) as java.util.concurrent.atomic.AtomicReference<*>
        val hint = slot.get()
        assertNotNull(hint)
        service.onAccessibilityEvent(AccessibilityEvent(AccessibilityEvent.TYPE_WINDOWS_CHANGED).apply {
            packageName = "com.instagram.android"
        })
        assertSame(hint, slot.get())
        assertFalse(RecordShadow.queriedOnUi.get())
        // This request is bounded even if Instagram publishes no further events.
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(501))
        assertNull(slot.get())
    }

    @Test fun aScrolledHomeFallbackCanStillQueueTheFastMessagesCheckWithoutUiQueries() {
        field("lastInstagramScreen", InstagramScreen.HOME_FEED)
        field("currentInstagramWindowId", 10)
        field("searchObservationBypassUntil", android.os.SystemClock.uptimeMillis() + 500L)
        service.onAccessibilityEvent(AccessibilityEvent(AccessibilityEvent.TYPE_VIEW_CLICKED).apply {
            packageName = "com.instagram.android"
            eventTime = android.os.SystemClock.uptimeMillis()
        })
        val slot = AntiScrollService::class.java.getDeclaredField("pendingDirectTap").apply { isAccessible = true }
            .get(service) as java.util.concurrent.atomic.AtomicReference<*>
        assertNotNull(slot.get())
        assertFalse(RecordShadow.queriedOnUi.get())
        assertFalse(serviceShadow.queriedOnUi.get())
    }

}
