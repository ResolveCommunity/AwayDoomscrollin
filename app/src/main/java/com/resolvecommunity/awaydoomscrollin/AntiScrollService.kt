package com.resolvecommunity.awaydoomscrollin

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.util.DisplayMetrics
import android.view.WindowManager
import android.view.WindowInsets
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class AntiScrollService : AccessibilityService() {

    companion object {
        private const val TAG = "AntiScrollService"
        private const val INSTAGRAM_PACKAGE = "com.instagram.android"
        private const val TIKTOK_PACKAGE = "com.zhiliaoapp.musically"
        private const val YOUTUBE_PACKAGE = "com.google.android.youtube"
        private const val OVERLAY_ATTEMPT_COOLDOWN_MS = 1_000L
        private const val INSTAGRAM_EXIT_TREE_QUIET_MS = 220L
        private const val INSTAGRAM_VIEWER_EXIT_RETRY_MS = 900L
        private const val PROFILE_ALLOWED_DEPARTURE_QUIET_MS = 450L
        private const val DIRECT_DETAILS_LAUNCH_PENDING_MS = 1_200L
        private const val DIRECT_REEL_LAUNCH_PENDING_MS = 1_500L
        private const val DIRECT_DETAILS_FALLBACK_BACK_MS = 180L
        private const val DIRECT_DETAILS_GUARD_SETTLE_MS = 320L
        private const val DIRECT_REEL_INTENT_DEBOUNCE_MS = 250L
        private const val DIRECT_CONVERSATION_GUARD_HOLD_MS = 1_800L
        private const val DIRECT_CONVERSATION_GUARD_QUIET_MS = 320L
        private const val DIRECT_CONVERSATION_ENTRY_GUARD_TIMEOUT_MS = 2_500L
        private const val DIRECT_CONVERSATION_SURFACE_GUARD_TIMEOUT_MS = 650L
        private const val DIRECT_CONVERSATION_ENTRY_MIN_HOLD_MS = 280L
        private const val DIRECT_NAVIGATION_GRACE_MS = 2_500L
        private const val INSTAGRAM_RESUME_RESCAN_MS = 48L
        private const val INSTAGRAM_USER_LAUNCH_GRACE_MS = 1_200L
        private const val OVERLAY_FOREGROUND_CHECK_MS = 250L
        private const val MAX_TREE_NODES = 240
        private const val PROFILE_ENSURE_MAX_TICKS = 100
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastTikTokExitTime = 0L
    private var lastTikTokTopTabCheckMs = 0L
    private var lastYouTubeExitTime = 0L
    private var lastOverlayAttemptTime = 0L
    private var lastPackage = ""
    private var instagramSessionStartedAt = 0L
    private var lastInstagramScreen = InstagramScreen.UNKNOWN
    private val instagramStateMachine = InstagramProtectionStateMachine()
    private var overlayForegroundCheckScheduled = false
    private var instagramExitInProgress = false
    private var instagramExitTreeQuietUntil = 0L
    private val instagramViewerExitRetry = Runnable {
        if (!instagramExitInProgress) return@Runnable
        if (!ProtectionPreferences.isEnabled(this, ProtectedApp.INSTAGRAM)) {
            cancelInstagramExit()
            return@Runnable
        }
        val root = rootInActiveWindow
        if (root == null || root.packageName?.toString() != INSTAGRAM_PACKAGE) {
            cancelInstagramExit()
            return@Runnable
        }

        // Instagram can accept GLOBAL_ACTION_BACK without acting on it when the
        // main pager has just settled from DM/Home onto Reels. Retry once, but
        // only while current foreground structure still proves a Reel viewer.
        if (hasFastMainReelsTabDestination(root) ||
            hasFastReelsViewer(root) || hasFastReelPreview(root)) {
            Log.d(TAG, "IG_VIEWER_EXIT_RETRY stillVisible=true")
            exitBlockedViewer(root, instagramWindowBounds(root), InstagramScreen.REELS)
        } else {
            cancelInstagramExit()
        }
    }
    private var profileAllowedDepartureUntil = 0L
    private var directDetailsLaunchPendingUntil = 0L
    private var directDetailsFallbackBackAt = 0L
    private var directReelLaunchPendingUntil = 0L
    private var directReelIntentDebounceUntil = 0L
    private var directConversationGuardHoldUntil = 0L
    private var directDetailsGuardHoldUntil = 0L
    private val instagramResumeGeometryGate = InstagramResumeGeometryGate()
    private var instagramResumePending = false
    private var currentInstagramWindowId = -1
    private var lastVerifiedInstagramCurtain: Pair<InstagramScreen, Rect>? = null
    private var directDetailsInteractionBounds: Rect? = null
    private var lastInstagramScanTime = 0L
    private var lastInstagramUserInputAt = Long.MIN_VALUE
    private val instagramOverlay by lazy {
        InstagramFeedOverlayController(
            this,
            ::onInstagramOverlayTouched,
            onProfileSwipeUp = {
                if (lastInstagramScreen == InstagramScreen.PROFILE &&
                    profileScrollExit.request(0, 1, SystemClock.uptimeMillis())) {
                    val accepted = performGlobalAction(GLOBAL_ACTION_BACK)
                    Log.d(TAG, "IG_PROFILE_TOUCH_EXIT accepted=$accepted")
                    queueInstagramScan(16L)
                }
            },
            onProtectedNavigation = ::onInstagramProtectedNavigation,
            onEntryGuardVerticalSwipe = ::onInstagramEntryGuardVerticalSwipe
        )
    }
    private val overlayForegroundCheck = object : Runnable {
        override fun run() {
            overlayForegroundCheckScheduled = false
            if (!instagramOverlay.hasVisibleWindows) return

            val activePkg = rootInActiveWindow?.packageName?.toString()
            if (activePkg != null && activePkg != INSTAGRAM_PACKAGE) {
                suspendInstagramProtection()
                trackPackageTransition("")
                return
            }
            if (!ProtectionPreferences.isEnabled(this@AntiScrollService, ProtectedApp.INSTAGRAM)) {
                hideInstagramProtection()
                return
            }

            scheduleOverlayForegroundCheck()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (!AccessibilityConsent.isAccepted(this)) {
            Log.w(TAG, "Accessibility service disabled: in-app disclosure has not been accepted")
            disableSelf()
            return
        }
        InstagramProtectionMetrics.discardInterruptedSession(this)
        AccessibilityStreakPolicy.onServiceConnected(this)
        serviceShutdownNotified = false
        NotificationHelper.cancelShieldNotification(this, 1002)
        NotificationHelper.cancelShieldNotification(this, 1003)

        val isEn = isEnglish()
        NotificationHelper.showShieldStatusNotification(
            this,
            if (isEn) "Protection active" else "Koruma etkin",
            if (isEn) {
                "AwayDoomscrollin' is monitoring Instagram, TikTok and YouTube for the surfaces you selected."
            } else {
                "AwayDoomscrollin' seçili Instagram, TikTok ve YouTube alanlarını izliyor."
            },
            1001
        )
        Log.d(TAG, "Accessibility protection connected")

        // Auto-return: the user just flipped the service toggle from Settings. Inside
        // the background-start grace period (~10 s after leaving the app) this launch
        // succeeds on stock Android; if an OEM blocks it, the user navigates back as
        // before. Guarded by the foreground package so a boot-time or silent service
        // rebind never pops the app, and wrapped so a blocked launch can never break
        // the service.
        mainHandler.postDelayed({
            val foreground = rootInActiveWindow?.packageName?.toString() ?: return@postDelayed
            if (!foreground.startsWith("com.android.settings")) return@postDelayed
            try {
                startActivity(
                    Intent(this, MainActivity::class.java).addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                    )
                )
            } catch (e: Exception) {
                Log.d(TAG, "Auto-return to app not allowed: ${e.message}")
            }
        }, 250L)

        // Updating the APK or attaching UiAutomation can reconnect this service
        // while Instagram is already foreground. No new accessibility event is
        // guaranteed, so initialize protection from the current root as well.
        mainHandler.postDelayed({
            if (rootInActiveWindow?.packageName?.toString() == INSTAGRAM_PACKAGE) {
                queueInstagramScan(0L)
            }
        }, 120L)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !AccessibilityConsent.isAccepted(this)) return
        maybeRecordHeartbeat()

        val reportedPackage = event.packageName?.toString()
        // TYPE_WINDOWS_CHANGED for our own accessibility overlays can have the
        // Instagram root active. Never reinterpret that event as Instagram.
        if (reportedPackage == this.packageName) return
        val packageName = if (event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED &&
            rootInActiveWindow?.packageName?.toString() == INSTAGRAM_PACKAGE) {
            INSTAGRAM_PACKAGE
        } else reportedPackage ?: return
        if (packageName == this.packageName) return
        val returningToInstagram = packageName == INSTAGRAM_PACKAGE && lastPackage != INSTAGRAM_PACKAGE
        if (packageName != INSTAGRAM_PACKAGE && packageName != this.packageName) {
            suspendInstagramProtection()
        }
        trackPackageTransition(packageName)
        if (returningToInstagram) {
            // The first Instagram event can arrive while SystemUI is still the
            // active accessibility root. Keep the request pending until an
            // actual Instagram root is available in scanInstagram().
            instagramResumePending = true
            beginInstagramResumeProtection()
        }

        when (packageName) {
            INSTAGRAM_PACKAGE -> handleInstagram(event)
            TIKTOK_PACKAGE -> handleTikTok(event)
            YOUTUBE_PACKAGE -> handleYouTube(event)
        }
    }

    private val scanSchedule = InstagramScanSchedule()
    private var lastHeartbeatMs = 0L
    private fun maybeRecordHeartbeat(now: Long = System.currentTimeMillis()) {
        if (now - lastHeartbeatMs >= 60_000L) {
            lastHeartbeatMs = now
            AccessibilityStreakPolicy.recordHeartbeat(this, now)
        }
    }
    private var queuedClick: AccessibilityEvent? = null
    private var directNavigationUntil = 0L
    private var directNavigationSourceWindowId = -1
    private val instagramScan = Runnable {
        scanSchedule.clear()
        val click = queuedClick
        queuedClick = null
        val started = SystemClock.uptimeMillis()
        try { scanInstagram(click) } finally {
            @Suppress("DEPRECATION")
            click?.recycle()
            val elapsed = SystemClock.uptimeMillis() - started
            if (elapsed > 100L) Log.w(TAG, "IG_SLOW_SCAN elapsedMs=$elapsed")
        }
    }
    private val instagramResumeRescan = object : Runnable {
        override fun run() {
            if (!instagramResumeGeometryGate.isActive) return
            queueInstagramScan(0L)
            if (instagramResumeGeometryGate.isActive) {
                mainHandler.postDelayed(this, INSTAGRAM_RESUME_RESCAN_MS)
            }
        }
    }
    private fun queueInstagramScan(delay: Long) {
        if (!scanSchedule.request(SystemClock.uptimeMillis(), delay)) return
        mainHandler.removeCallbacks(instagramScan)
        mainHandler.postDelayed(instagramScan, delay)
    }

    // After returning from an allowed surface (or on entry) Instagram rebuilds the
    // profile fragment and the pager measures partial or offset for a while - sometimes
    // silently, with no further events. This loop drives the curtain to its settled
    // geometry on its own cadence: it waits through the transition, mounts as soon as
    // the gate accepts, then keeps re-measuring until the mounted rect repeats
    // identically twice, so the tail of the slide animation cannot leave a gap.
    private val profileCurtainEnsure = object : Runnable {
        override fun run() {
            if (!profileCurtainEnsureActive) return
            val root = rootInActiveWindow
            if (root == null || root.packageName?.toString() != INSTAGRAM_PACKAGE ||
                !ProtectionPreferences.isEnabled(this@AntiScrollService, ProtectedApp.INSTAGRAM)
            ) {
                stopProfileCurtainEnsure()
                return
            }
            val windowBounds = instagramWindowBounds(root)
            if (hasVisibleProfileNativeSheet(root) || hasVisibleAboutAccountSurface(root)) {
                stopProfileCurtainEnsure()
                return
            }
            val bounds = profileGridBounds(root, windowBounds)
            if (bounds == null) {
                if (!hasVisibleProfileHeaderMarkers(root)) {
                    stopProfileCurtainEnsure()
                    return
                }
                // The pager node is not in the tree yet (Instagram rebuilding the
                // fragment); keep waiting for it at this cadence.
                profileCurtainEnsureAttempts++
                if (profileCurtainEnsureAttempts > PROFILE_ENSURE_MAX_TICKS) {
                    stopProfileCurtainEnsure()
                    return
                }
                mainHandler.postDelayed(this, 64L)
                return
            }
            mountEnsureProfileCurtain(root, windowBounds, bounds)
            // A stable ACTUAL rect is not enough on its own: while the resume geometry
            // gate is active it keeps re-applying retained bounds, so a frozen partial
            // curtain would look "stable". Keep feeding samples until the gate clears.
            if (profileCurtainEnsureStableStreak >= 2 &&
                !instagramResumeGeometryGate.isActive
            ) {
                stopProfileCurtainEnsure()
                return
            }
            profileCurtainEnsureAttempts++
            mainHandler.postDelayed(this, 96L)
        }
    }

    private fun mountEnsureProfileCurtain(
        root: AccessibilityNodeInfo,
        windowBounds: Rect,
        bounds: Rect
    ) {
        profileWindowId = root.windowId
        profileScrollFloor = calculateProfileScrollFloor(root, windowBounds)
        bounds.top = profileScrollCover.measure(
            bounds.top, profileScrollFloor, SystemClock.uptimeMillis())
        observeInstagramScreen(InstagramScreen.PROFILE, root.windowId)
        showInstagramCurtain(bounds, InstagramScreen.PROFILE)
        // Fingerprint what the overlay ACTUALLY shows: the resume geometry gate inside
        // showInstagramCurtain can substitute retained bounds, and comparing against the
        // requested rect would declare victory while a partial curtain stays mounted.
        val actual = instagramOverlay.visibleCurtain()?.second ?: bounds
        val fingerprint = actual.flattenToString()
        profileCurtainEnsureStableStreak =
            if (fingerprint == lastProfileCurtainFingerprint) {
                profileCurtainEnsureStableStreak + 1
            } else 1
        lastProfileCurtainFingerprint = fingerprint
    }

    private fun armProfileCurtainEnsure() {
        if (profileCurtainEnsureActive) return
        profileCurtainEnsureActive = true
        profileCurtainEnsureAttempts = 0
        profileCurtainEnsureStableStreak = 0
        mainHandler.postDelayed(profileCurtainEnsure, 48L)
    }

    private fun stopProfileCurtainEnsure() {
        profileCurtainEnsureActive = false
        profileCurtainEnsureAttempts = 0
        profileCurtainEnsureStableStreak = 0
    }

    private val exitGate = InstagramExitGate()
    private val instagramEntryTargets = mutableListOf<Pair<Rect, Boolean>>()
    private val lastDirectConversationGuards = mutableListOf<Rect>()
    private val directConversationGuardGate = InstagramDynamicGuardGate(
        quietPeriodMs = DIRECT_CONVERSATION_GUARD_QUIET_MS)
    private var verifiedInstagramInboxWindowId = -1
    private var verifiedInstagramInboxListBounds: Rect? = null
    private var directConversationEntryGuardArmedAt = 0L
    private var directConversationEntryGuardUntil = 0L
    private var directConversationSurfaceWindowId = -1
    private val directConversationEntryGuardTimeout = Runnable {
        if (directConversationEntryGuardUntil != 0L &&
            SystemClock.uptimeMillis() >= directConversationEntryGuardUntil) {
            clearDirectConversationEntryGuard()
        }
    }
    private val directConversationEntryGuardRelease = Runnable {
        clearDirectConversationEntryGuard()
    }
    private var scrollCoverTop: Int? = null
    private var scrollCoverUntil = 0L
    private var lastHomeScrollAt = 0L
    private var settledHomeTop: Int? = null
    private var stableHomeSamples = 0
    private val homeScrollSettled = object : Runnable {
        override fun run() {
            val refreshed = refreshHomeScrollBounds(null)
            if (!refreshed && scrollCoverTop != null &&
                SystemClock.uptimeMillis() < scrollCoverUntil) {
                mainHandler.postDelayed(this, 48L)
            }
        }
    }
    private val directDetailsGuardSettled = Runnable {
        directDetailsGuardHoldUntil = 0L
        val root = rootInActiveWindow
        if (root?.packageName?.toString() == INSTAGRAM_PACKAGE &&
            lastInstagramScreen == InstagramScreen.DIRECT_SHARED_MEDIA) {
            refreshDirectDetailsSurface(root, instagramWindowBounds(root))
        }
    }
    private var profileWindowId = -1
    private var profileScrollFloor = 0
    private var profileCurtainEnsureActive = false
    private var profileCurtainEnsureAttempts = 0
    private var profileCurtainEnsureStableStreak = 0
    private var lastProfileCurtainFingerprint: String? = null
    private val profileScrollCover = InstagramProfileScrollCover()
    private val profileScrollExit = InstagramProfileScrollExit()
    private val profileScrollPositions = mutableMapOf<String, Int>()
    private var proxiedNavigationTab: InstagramTab? = null
    private var proxiedNavigationUntil = 0L

    private fun handleInstagram(event: AccessibilityEvent) {
        if (!ProtectionPreferences.isEnabled(this, ProtectedApp.INSTAGRAM)) {
            hideInstagramProtection()
            return
        }
        val eventNow = SystemClock.uptimeMillis()
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            armDirectWindowTransition(event, eventNow)
        }
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            val freshClick = eventNow - event.eventTime in 0L..1_500L
            val openingInboxThread = freshClick && armDirectConversationEntryGuard(event, eventNow)
            val profileCurtainVisible = instagramOverlay.visibleProfileBounds() != null
            val profileDeparture = if (freshClick &&
                (profileCurtainVisible || lastInstagramScreen == InstagramScreen.PROFILE)) {
                allowedProfileDeparture(event.source)
            } else null
            if (profileCurtainVisible && freshClick) {
                // Every click that reaches us while the profile curtain is mounted comes
                // from an uncovered, allowed area (the overlay consumes grid touches) -
                // menu, banner, Threads, highlights, tab bar. Release immediately so the
                // incoming surface never draws under the stale curtain, even when the
                // click carries no source ancestry to recognize.
                profileAllowedDepartureUntil = eventNow + when {
                    profileDeparture == true -> 1_200L
                    profileDeparture != null -> PROFILE_ALLOWED_DEPARTURE_QUIET_MS
                    else -> 600L
                }
                releaseAllowedProfileSurface()
            } else if (profileDeparture != null) {
                // Instagram keeps the profile pager mounted behind Highlights
                // and native action sheets. Release the grid curtain before the
                // destination draws, then let structural checks own the state.
                profileAllowedDepartureUntil = eventNow + if (profileDeparture) {
                    1_200L
                } else {
                    PROFILE_ALLOWED_DEPARTURE_QUIET_MS
                }
                releaseAllowedProfileSurface()
                Log.d(TAG, "IG_PROFILE_ALLOWED_DEPARTURE")
            }
            if (freshClick && isExploreNavigationIntent(event.source) &&
                !isRecentProxiedNavigation(InstagramTab.SEARCH, eventNow)) {
                // The bottom Search tab always opens Explore before its search
                // field is focused. Cover its grid from the click event instead
                // of waiting for the expensive general tree classification.
                val root = rootInActiveWindow
                if (root?.packageName?.toString() == INSTAGRAM_PACKAGE) {
                    clearInstagramResumeProtection()
                    currentInstagramWindowId = root.windowId
                    // Do not carry the source tab's animated window token into
                    // Explore. Samsung can scale that token during the tab slide.
                    instagramOverlay.hide()
                    showInstagramCurtain(
                        calculateExploreBounds(root, instagramWindowBounds(root)),
                        InstagramScreen.EXPLORE)
                    Log.d(TAG,
                        "IG_EXPLORE_INTENT_CURTAIN eventAgeMs=${eventNow - event.eventTime}")
                }
            }
            if (freshClick && isHomeNavigationIntent(event.source) &&
                !isRecentProxiedNavigation(InstagramTab.HOME, eventNow)) {
                // A Profile/DM curtain is either too low or absent for Home.
                // Cover the feed conservatively from the action-bar boundary
                // before Instagram publishes its destination tree. The normal
                // Home scan restores the Stories allowance immediately after.
                val root = rootInActiveWindow
                if (root?.packageName?.toString() == INSTAGRAM_PACKAGE) {
                    val display = currentDisplayBounds()
                    val bottom = stableMainNavigationTop(
                        root, display, InstagramScreen.HOME_FEED)
                    val top = (statusBarInset() + dp(56)).coerceIn(display.top, bottom - 1)
                    clearInstagramResumeProtection()
                    currentInstagramWindowId = root.windowId
                    // Recreate instead of resizing the Profile curtain. Keeping
                    // its token lets Samsung animate and briefly shorten it.
                    instagramOverlay.hide()
                    showInstagramCurtain(
                        Rect(display.left, top, display.right, bottom),
                        InstagramScreen.HOME_FEED)
                    Log.d(TAG,
                        "IG_HOME_INTENT_CURTAIN eventAgeMs=${eventNow - event.eventTime}")
                }
            }
            if (freshClick) refreshDirectDetailsTabIntent(event.source)
            if (freshClick && isDirectDetailsMediaIntent(event.source)) {
                directDetailsLaunchPendingUntil = eventNow + DIRECT_DETAILS_LAUNCH_PENDING_MS
                directDetailsFallbackBackAt = eventNow + DIRECT_DETAILS_FALLBACK_BACK_MS
                queueInstagramScan(0L)
                Log.d(TAG, "IG_DIRECT_DETAILS_MEDIA_INTENT")
            }
            if (freshClick && !openingInboxThread && eventNow >= directReelIntentDebounceUntil &&
                eventNow >= directConversationGuardHoldUntil &&
                directReelLaunchPendingUntil == 0L && !instagramExitInProgress &&
                isDirectReelIntent(event.source)) {
                directReelLaunchPendingUntil = eventNow + DIRECT_REEL_LAUNCH_PENDING_MS
                directReelIntentDebounceUntil = eventNow + DIRECT_REEL_INTENT_DEBOUNCE_MS
                directConversationGuardHoldUntil = eventNow + DIRECT_CONVERSATION_GUARD_HOLD_MS
                queueInstagramScan(0L)
                Log.d(TAG, "IG_DIRECT_REEL_INTENT")
            }
            // Consume intent before the fast player scan can discard the queued
            // click. Never rearm from our own viewer Back action.
            if (freshClick) {
                var candidate = event.source
                var hasSource = false
                var isBack = false
                repeat(8) {
                    val node = candidate ?: return@repeat
                    hasSource = true
                    val id = compactId(node.viewIdResourceName)
                    if (id.contains("back", ignoreCase = true)) isBack = true
                    candidate = node.parent
                }
                if (hasSource && !isBack) {
                    exitGate.userClick(event.eventTime)
                    lastInstagramUserInputAt = event.eventTime
                }
            }
            @Suppress("DEPRECATION")
            queuedClick?.recycle()
            @Suppress("DEPRECATION")
            val copy = AccessibilityEvent.obtain(event)
            queuedClick = copy
            if (instagramOverlay.visibleHomeBounds() != null && freshClick) {
                var source = event.source
                repeat(3) {
                    val node = source ?: return@repeat
                    if (InstagramInboxPolicy.isDirectNavigation(compactId(node.viewIdResourceName),
                            node.contentDescription?.toString().orEmpty())) {
                        clearInstagramResumeProtection()
                        instagramOverlay.hide()
                        instagramOverlay.updateEntryGuards(emptyList(), isEnglish())
                        mainHandler.removeCallbacks(homeScrollSettled)
                        scrollCoverTop = null
                        scrollCoverUntil = 0L
                        directNavigationSourceWindowId = event.windowId
                        directNavigationUntil = SystemClock.uptimeMillis() +
                            DIRECT_NAVIGATION_GRACE_MS
                        Log.d(TAG, "IG_DIRECT_INTENT_RELEASE eventAgeMs=${SystemClock.uptimeMillis() - event.eventTime}")
                        source = null
                    } else source = node.parent
                }
            }
            if (instagramOverlay.visibleHomeBounds() != null && freshClick &&
                isNotificationsIntent(event.source)) {
                clearInstagramResumeProtection()
                instagramOverlay.hide()
                instagramOverlay.updateEntryGuards(emptyList(), isEnglish())
                mainHandler.removeCallbacks(homeScrollSettled)
                scrollCoverTop = null
                scrollCoverUntil = 0L
                directNavigationUntil = 0L
                directNavigationSourceWindowId = -1
                Log.d(TAG, "IG_ALLOWED_NOTIFICATION_INTENT_RELEASE")
            }
        }
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            if (lastInstagramScreen == InstagramScreen.DIRECT_SHARED_MEDIA) {
                directDetailsGuardHoldUntil = SystemClock.uptimeMillis() +
                    DIRECT_DETAILS_GUARD_SETTLE_MS
                mainHandler.removeCallbacks(directDetailsGuardSettled)
                mainHandler.postDelayed(
                    directDetailsGuardSettled, DIRECT_DETAILS_GUARD_SETTLE_MS)
                directDetailsInteractionBounds?.let { guard ->
                    // Keep the already mounted touch guard above the moving
                    // pager while its new geometry is being published.
                    instagramOverlay.showTransientTouchGuard(guard, isEnglish())
                }
                val root = rootInActiveWindow
                if (root?.packageName?.toString() == INSTAGRAM_PACKAGE &&
                    refreshDirectDetailsSurface(root, instagramWindowBounds(root))) {
                    // Update the WindowManager curtain in the scroll callback;
                    // waiting for the general scan leaves a tappable seam.
                    queueInstagramScan(16L)
                    return
                }
            }
            val profileBounds = instagramOverlay.visibleProfileBounds()
            if (profileBounds != null && event.windowId == profileWindowId &&
                SystemClock.uptimeMillis() - event.eventTime in 0L..1500L) {
                val dx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) event.scrollDeltaX else 0
                val dy = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) event.scrollDeltaY else 0
                val profileSource = isProfileScrollSource(event)
                val sourceId = compactId(event.source?.viewIdResourceName)
                val oldPosition = if (profileSource && event.scrollY >= 0)
                    profileScrollPositions.put(sourceId, event.scrollY) else null
                val positionDelta = if (oldPosition != null) event.scrollY - oldPosition else 0
                // Some containers report zero deltas. Compare actual grid motion
                // instead of treating zero as either an upward or horizontal swipe.
                val measuredTop = if (profileSource && dx == 0 && dy == 0) {
                    val root = rootInActiveWindow
                    if (root?.packageName?.toString() == INSTAGRAM_PACKAGE && root.windowId == profileWindowId)
                        profileGridBounds(root, instagramWindowBounds(root))?.top else null
                } else null
                val effectiveDy = if (dx == 0 && dy == 0 && positionDelta != 0) positionDelta
                    else InstagramProfileScrollExit.verticalDelta(dx, dy, profileBounds.top, measuredTop)
                Log.d(TAG, "IG_PROFILE_SCROLL dx=$dx dy=$dy y=${event.scrollY} positionDelta=$positionDelta effectiveDy=$effectiveDy source=$profileSource ageMs=${SystemClock.uptimeMillis() - event.eventTime}")
                if (InstagramProfileScrollCover.shouldCover(
                        dx, dy, effectiveDy, profileSource)) {
                    // No tree walk before covering: use the last verified profile
                    // chrome and resize the existing touch-consuming window.
                    profileBounds.top = profileScrollCover.onScroll(profileBounds.top,
                        profileScrollFloor, SystemClock.uptimeMillis())
                    showInstagramCurtain(profileBounds, InstagramScreen.PROFILE)
                    // Geometry events also occur during profile-to-profile
                    // animations. They may extend the curtain, but only an
                    // actual swipe captured by the curtain can issue BACK.
                    queueInstagramScan(16L)
                    return
                }
            }
            if (refreshHomeScrollBounds(event)) {
                // Let WindowManager render the new bounds before a general tree walk.
                queueInstagramScan(16L)
                return
            }
            if (lastInstagramScreen == InstagramScreen.DIRECT_MESSAGES) {
                val root = rootInActiveWindow
                if (root?.packageName?.toString() == INSTAGRAM_PACKAGE && refreshDirectConversationGuards(root)) {
                    queueInstagramScan(16L)
                    return
                }
                if (lastDirectConversationGuards.isNotEmpty() ||
                    isDirectConversationEntryGuardArmed()) {
                    // Instagram briefly removes the conversation header/list
                    // while opening media. That absence is not proof that the
                    // protected card disappeared. Keep the already attached
                    // windows until the destination tree is classified.
                    Log.d(TAG, "IG_DIRECT_GUARDS retained during unverified scroll transition")
                    queueInstagramScan(16L)
                    return
                }
            }
            instagramOverlay.updateEntryGuards(instagramEntryTargets.filter { it.second }.map { it.first }, isEnglish())
        }
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            val root = rootInActiveWindow
            if (root?.packageName?.toString() == INSTAGRAM_PACKAGE) {
                prearmDirectConversationSurface(root, SystemClock.uptimeMillis())
            }
        }
        // Coalesce Instagram's burst of DM layout notifications. The transparent
        // conversation guard is mounted synchronously above, so forcing every
        // content event into a zero-delay full scan only starves touch dispatch.
        val scanDelay = InstagramEventScanPolicy.delayMs(event.eventType)
        // Never do the full tree walk inline for each queued accessibility event.
        queueInstagramScan(scanDelay)
    }

    private fun isProfileScrollSource(event: AccessibilityEvent): Boolean {
        var node = event.source
        repeat(10) {
            val current = node ?: return false
            if (compactId(current.viewIdResourceName) in setOf(
                    "profile_viewpager", "profile_header_container", "profile_header_fixed_list",
                    "row_profile_header")) return true
            // The collapsing header can report the coordinator itself, rather
            // than a descendant of the profile pager.
            if (compactId(current.viewIdResourceName) in setOf("coordinator_root_layout", "tab_appbar") &&
                current.findAccessibilityNodeInfosByViewId("$INSTAGRAM_PACKAGE:id/profile_header_container")
                    .any { it.isVisibleToUser }) return true
            node = current.parent
        }
        return false
    }

    private fun scanInstagram(event: AccessibilityEvent?) {
        val scanNow = SystemClock.uptimeMillis()
        if (directConversationEntryGuardUntil != 0L &&
            scanNow >= directConversationEntryGuardUntil) {
            clearDirectConversationEntryGuard()
        }
        if (instagramExitInProgress && scanNow < instagramExitTreeQuietUntil) {
            // Instagram is replacing the viewer tree after BACK. Querying this
            // short-lived tree can block Accessibility's main thread until the
            // framework's roughly two-second IPC timeout.
            queueInstagramScan(instagramExitTreeQuietUntil - scanNow)
            return
        }
        lastInstagramScanTime = SystemClock.uptimeMillis()
        if (!ProtectionPreferences.isEnabled(this, ProtectedApp.INSTAGRAM)) {
            hideInstagramProtection()
            return
        }
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != INSTAGRAM_PACKAGE) {
            suspendInstagramProtection()
            // This path is reached when a delayed Instagram event is handled
            // after Recents/SystemUI has already become the active root. Mark
            // the foreground departure just like overlayForegroundCheck does,
            // otherwise the next Instagram event is not recognized as resume.
            trackPackageTransition("")
            return
        }
        val initialWindowBounds = instagramWindowBounds(root)
        currentInstagramWindowId = root.windowId
        refreshProtectedNavigationGuards(root, initialWindowBounds)
        if (instagramResumePending) beginInstagramResumeProtection(root)
        if (directReelLaunchPendingUntil != 0L && scanNow >= directReelLaunchPendingUntil) {
            directReelLaunchPendingUntil = 0L
        }
        // Home/DM -> Reels can be a horizontal main-pager swipe. That route has
        // no click intent, can keep the previous Home/DM hierarchy mounted, and
        // may reuse the same accessibility window. Resolve the selected bottom
        // tab before the verified-Home hold and settled-DM shortcuts so neither
        // can pin the old safe surface while Reels is already playing.
        if (hasFastMainReelsTabDestination(root)) {
            exitBlockedViewer(root, initialWindowBounds, InstagramScreen.REELS)
            return
        }
        if (lastInstagramScreen == InstagramScreen.DIRECT_MESSAGES) {
            val newlyArmed = prearmDirectConversationSurface(root, scanNow)
            if (newlyArmed) {
                queueInstagramScan(48L)
                return
            }
            // Leave the main looper free during the first interaction frame.
            // Once the input surface is attached, a later scan may safely do
            // the more expensive exact-card discovery underneath the guard.
            if (isDirectConversationEntryGuardArmed() &&
                root.windowId == directConversationSurfaceWindowId) {
                val guardAge = scanNow - directConversationEntryGuardArmedAt
                if (guardAge in 0L until DIRECT_CONVERSATION_ENTRY_MIN_HOLD_MS) {
                    queueInstagramScan(DIRECT_CONVERSATION_ENTRY_MIN_HOLD_MS - guardAge)
                    return
                }
            }
            // A settled conversation with exact transparent card guards needs
            // no repeated remote tree walk for every video/layout tick. Scroll
            // callbacks update the guards directly; a viewer has a new window.
            if (!instagramResumePending && !isDirectConversationEntryGuardArmed() &&
                directReelLaunchPendingUntil == 0L &&
                root.windowId == directConversationSurfaceWindowId &&
                lastDirectConversationGuards.isNotEmpty()) {
                currentInstagramWindowId = root.windowId
                observeInstagramScreen(InstagramScreen.DIRECT_MESSAGES, root.windowId)
                return
            }
        }
        // A structurally complete, full-width inbox is safe to recognize from
        // any previous screen. Doing this before the absent-player/header
        // probes removes the 300-500 ms scan which used to remain queued after
        // Home -> DM and delay the first conversation window event.
        if (!isDirectConversationEntryGuardArmed() &&
            releaseVerifiedInstagramInbox(root)) return
        val keepVerifiedHome = InstagramSurfacePolicy.shouldKeepVerifiedHome(
            currentScreen = instagramStateMachine.currentScreen,
            homeCurtainVisible = instagramOverlay.visibleHomeBounds() != null,
            sameWindow = instagramStateMachine.currentWindowId == root.windowId,
            hasFreshUserInput = scanNow - lastInstagramUserInputAt in
                0L..INSTAGRAM_USER_LAUNCH_GRACE_MS
        )
        // Check the player before walking the full conversation tree. A stable
        // Home curtain prevents Instagram's autonomous embedded-video rebuild
        // from being mistaken for a user-opened Reel.
        var stageStarted = SystemClock.uptimeMillis()
        val fastReelsViewer = hasFastReelsViewer(
            root,
            directLaunchPending = scanNow < directReelLaunchPendingUntil)
        logSlowInstagramStage("fast-viewer", stageStarted)
        if (fastReelsViewer && !keepVerifiedHome) {
            exitBlockedViewer(root, instagramWindowBounds(root), InstagramScreen.REELS)
            return
        }
        if (hasFastReelPreview(root) && !keepVerifiedHome) {
            exitBlockedViewer(root, instagramWindowBounds(root), InstagramScreen.REELS)
            return
        }
        val windowBounds = initialWindowBounds
        if (hasFastNotificationsSurface(root)) {
            clearDirectConversationEntryGuard()
            clearDirectDetailsScrollGuard()
            clearInstagramResumeProtection()
            instagramOverlay.updateEntryGuards(emptyList(), isEnglish())
            instagramOverlay.hide()
            cancelInstagramExit()
            observeInstagramScreen(InstagramScreen.NOTIFICATIONS, root.windowId)
            return
        }
        if (hasFastStoryViewer(root)) {
            clearDirectConversationEntryGuard()
            profileAllowedDepartureUntil = 0L
            releaseAllowedProfileSurface()
            observeInstagramScreen(InstagramScreen.STORY, root.windowId)
            return
        }
        // Inbox and conversation are both direct-ID surfaces. Resolve them
        // before profile/general probes so an old tree walk cannot delay the
        // first conversation guard. Both functions reject mounted Reel/details
        // top layers before allowing the surface.
        if (refreshDirectConversationGuards(root)) return
        if (isDirectConversationEntryGuardArmed()) {
            // A cold ModalActivity can exist well before it publishes its
            // message_list/header nodes. Keep discovery alive underneath the
            // already attached transparent guard instead of falling through to
            // UNKNOWN once and silently letting the guard expire.
            queueInstagramScan(64L)
            return
        }
        if ((lastInstagramScreen == InstagramScreen.DIRECT_SHARED_MEDIA ||
                scanNow < directDetailsLaunchPendingUntil) && hasFastPostViewer(root)) {
            directDetailsLaunchPendingUntil = 0L
            directDetailsFallbackBackAt = 0L
            exitBlockedViewer(root, windowBounds, InstagramScreen.COMMENTS_OR_DETAIL)
            return
        }
        if (scanNow < directDetailsLaunchPendingUntil) {
            if (hasVisibleDirectDetailsGrid(root)) {
                if (directDetailsFallbackBackAt != 0L && scanNow >= directDetailsFallbackBackAt) {
                    // A real Instagram click was delivered inside a protected
                    // media region. Some photo viewers keep the details grid
                    // mounted behind them, so structure alone cannot prove the
                    // top layer. Global Back targets that top layer reliably.
                    directDetailsLaunchPendingUntil = 0L
                    directDetailsFallbackBackAt = 0L
                    exitBlockedViewer(
                        root, windowBounds, InstagramScreen.COMMENTS_OR_DETAIL,
                        useGlobalBackOnly = true)
                    return
                }
                // The click was delivered through a transient seam, but the
                // gallery is still foreground. Keep it covered while Instagram
                // replaces the tree; do not send BACK before the viewer exists.
                refreshDirectDetailsSurface(root, windowBounds)
                val nextCheck = if (directDetailsFallbackBackAt > scanNow) {
                    minOf(24L, directDetailsFallbackBackAt - scanNow)
                } else 0L
                queueInstagramScan(nextCheck)
                return
            }
            if (hasFastDirectSafeReturn(root)) {
                directDetailsLaunchPendingUntil = 0L
            } else {
                // The aggregate Media/Reposts gallery is protected regardless
                // of whether the opened item is a photo, post or Reel.
                exitBlockedViewer(root, windowBounds, InstagramScreen.COMMENTS_OR_DETAIL)
                return
            }
        } else if (directDetailsLaunchPendingUntil != 0L) {
            directDetailsLaunchPendingUntil = 0L
            directDetailsFallbackBackAt = 0L
        }
        if (refreshDirectDetailsSurface(root, windowBounds)) return
        if (refreshFastMainProtectedSurface(root, windowBounds)) return

        // The full-screen highlight story picker replaces the profile surface entirely.
        // It classifies as UNKNOWN (no profile ids, no feed ids), which would otherwise
        // leave the profile curtain mounted over it; release it directly by title.
        if (isHighlightPickerForeground(root)) {
            profileAllowedDepartureUntil = 0L
            releaseAllowedProfileSurface()
            return
        }

        // The profile grid is a small set of direct ID lookups. Draw its curtain
        // before inbox/conversation probes and the bounded general tree walk so
        // thumbnails do not wait for the slow classification path.
        val earlyProfileBounds = profileGridBounds(root, windowBounds)
        if (earlyProfileBounds != null) {
            if (hasVisibleProfileNativeSheet(root) || hasVisibleAboutAccountSurface(root)) {
                profileAllowedDepartureUntil = 0L
                releaseAllowedProfileSurface()
                return
            }
            if (scanNow < profileAllowedDepartureUntil) {
                releaseAllowedProfileSurface()
                queueInstagramScan(profileAllowedDepartureUntil - scanNow)
                return
            }
            profileAllowedDepartureUntil = 0L
            profileWindowId = root.windowId
            profileScrollFloor = calculateProfileScrollFloor(root, windowBounds)
            earlyProfileBounds.top = profileScrollCover.measure(
                earlyProfileBounds.top, profileScrollFloor, scanNow)
            observeInstagramScreen(InstagramScreen.PROFILE, root.windowId)
            showInstagramCurtain(earlyProfileBounds, InstagramScreen.PROFILE)
            val mountedFingerprint = earlyProfileBounds.flattenToString()
            if (mountedFingerprint != lastProfileCurtainFingerprint) {
                // The slide-in tail of a transition can leave the curtain a little narrow
                // or short; keep re-measuring until the mounted rect repeats identically.
                armProfileCurtainEnsure()
            }
            lastProfileCurtainFingerprint = mountedFingerprint
        } else if (hasVisibleProfileHeaderMarkers(root)) {
            armProfileCurtainEnsure()
        }
        stageStarted = SystemClock.uptimeMillis()
        val signals = collectInstagramSnapshot(root,
            event?.takeIf { it.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED &&
                it.windowId == root.windowId &&
                SystemClock.uptimeMillis() - it.eventTime in 0L..1_500L }?.source)
        logSlowInstagramStage("snapshot", stageStarted)
        val profileContext = lastInstagramScreen == InstagramScreen.PROFILE || signals.ids.any { id ->
            id in setOf("profile_header_container", "profile_viewpager", "profile_tabs_container")
        }
        // Menu collection screens (Saved / Likes) are full media grids reached through
        // the profile menu; back out on entry, matching the Saved behavior. The grid is
        // Bloks-driven with no resource ids, so the action-bar title is the signal. No
        // profile-context requirement: the menu screens classify as UNKNOWN/HOME_FEED
        // along the way, so lastInstagramScreen cannot be trusted here.
        if (InstagramProfileSurfacePolicy.isBlockedCollectionTitle(signals.titles)) {
            Log.d(TAG, "IG_BLOCKED_COLLECTION_EXIT")
            exitBlockedViewer(root, windowBounds, InstagramScreen.COMMENTS_OR_DETAIL)
            return
        }
        if (profileContext && InstagramProfileSurfacePolicy.hasAllowedLayer(signals.ids, signals.titles)) {
            profileAllowedDepartureUntil = 0L
            releaseAllowedProfileSurface()
            return
        }
        // Source ancestry describes the old screen. Only the current tree classifies
        // the destination; a click may arm a shield, but cannot trigger BACK itself.
        val classifiedScreen = InstagramProtectionEngine.classify(
            signals.copy(sourceIds = emptySet(), sourceLabels = emptySet(), sourceTab = null))
        val screen = if (classifiedScreen == InstagramScreen.REELS && keepVerifiedHome) {
            Log.d(TAG, "IG_HOME_PLAYER_MUTATION_IGNORED window=${root.windowId}")
            InstagramScreen.HOME_FEED
        } else classifiedScreen
        val now = SystemClock.uptimeMillis()
        if (screen != InstagramScreen.DIRECT_MESSAGES && screen != InstagramScreen.UNKNOWN) {
            clearDirectConversationEntryGuard()
            verifiedInstagramInboxWindowId = -1
            verifiedInstagramInboxListBounds = null
        }
        if (screen != InstagramScreen.PROFILE && screen != InstagramScreen.UNKNOWN) {
            profileScrollPositions.clear()
            profileScrollCover.reset()
            profileWindowId = -1
        }
        if (exitGate.observe(
                screen,
                now,
                verifiedProfileReturn = InstagramEntryPolicy.isVerifiedProfileReturn(screen, signals.ids),
                // The fast viewer check above has already rejected a mounted
                // Reel player. A current DM classification therefore proves
                // that a previous viewer encounter ended.
                verifiedConversationReturn = screen == InstagramScreen.DIRECT_MESSAGES
            )) queueInstagramScan(450L)
        val stateDecision = observeInstagramScreen(screen, root.windowId)
        if (stateDecision.action == InstagramProtectionAction.EXIT) {
            exitBlockedViewer(root, windowBounds, screen)
            return
        }
        if (screen == InstagramScreen.HOME_FEED && now < directNavigationUntil) {
            // A native DM click precedes replacement of Home's accessibility tree.
            queueInstagramScan(32L)
            return
        }
        if (screen != InstagramScreen.UNKNOWN && screen != InstagramScreen.HOME_FEED) {
            directNavigationUntil = 0L
            directNavigationSourceWindowId = -1
        }
        if (stateDecision.action == InstagramProtectionAction.KEEP_CURRENT) {
            // Do not alter either the curtain or transparent card guards while
            // Instagram publishes an incomplete transition tree. Clearing the
            // guards here created an 86 ms first-tap opening in the captured DM
            // Reel trace.
            if (instagramOverlay.hasVisibleWindows) {
                queueInstagramScan(80L)
            }
            return
        }
        val mediaEntriesAllowed = screen == InstagramScreen.DIRECT_MESSAGES
        val canGuardEntries = screen != InstagramScreen.REELS && screen != InstagramScreen.STORY &&
            screen != InstagramScreen.CREATION && screen != InstagramScreen.UNKNOWN
        instagramOverlay.updateEntryGuards(if (canGuardEntries) {
            instagramEntryTargets.filter { it.second || mediaEntriesAllowed }.map { it.first }.distinct()
        } else emptyList(), isEnglish())
        cancelInstagramExit()
        when (stateDecision.action) {
            InstagramProtectionAction.SHOW_CURTAIN -> when (screen) {
            InstagramScreen.HOME_FEED ->
                showInstagramCurtain(calculateHomeFeedBounds(root, windowBounds), screen)
            InstagramScreen.EXPLORE ->
                showInstagramCurtain(calculateExploreBounds(root, windowBounds), screen)
            InstagramScreen.PROFILE -> {
                val bounds = profileGridBounds(root, windowBounds)
                // A temporarily missing grid during layout must not uncover an
                // already protected profile. Retry without removing its window.
                if (bounds != null) {
                    profileWindowId = root.windowId
                    profileScrollFloor = calculateProfileScrollFloor(root, windowBounds)
                    bounds.top = profileScrollCover.measure(
                        bounds.top, profileScrollFloor, SystemClock.uptimeMillis())
                    showInstagramCurtain(bounds, screen)
                    if (profileScrollCover.isHolding) queueInstagramScan(48L)
                }
                else queueInstagramScan(80L)
            }
                else -> Unit
            }
            InstagramProtectionAction.ALLOW -> hideInstagramContentOverlay()
            else -> Unit
        }
        if (instagramOverlay.hasVisibleWindows) scheduleOverlayForegroundCheck()
    }

    private fun hasVisibleProfileHeaderMarkers(root: AccessibilityNodeInfo): Boolean =
        listOf("profile_header_container", "profile_tabs_container", "profile_action_bar").any { id ->
            root.findAccessibilityNodeInfosByViewId("$INSTAGRAM_PACKAGE:id/$id")
                .any { it.isVisibleToUser }
        }

    // The picker screen is cheap to identify: one id lookup plus a title match, so the
    // release decision never depends on the bounded snapshot walk reaching the title.
    private fun isHighlightPickerForeground(root: AccessibilityNodeInfo): Boolean =
        root.findAccessibilityNodeInfosByViewId("$INSTAGRAM_PACKAGE:id/action_bar_title").any { node ->
            val title = listOfNotNull(node.text?.toString(), node.contentDescription?.toString())
                .joinToString(" ")
            InstagramProfileSurfacePolicy.isHighlightPicker(setOf(title))
        }

    private fun profileGridBounds(root: AccessibilityNodeInfo, window: Rect): Rect? {
        val pager = root.findAccessibilityNodeInfosByViewId("$INSTAGRAM_PACKAGE:id/profile_viewpager")
            .filter { it.isVisibleToUser }
            .map { Rect().also(it::getBoundsInScreen) }.firstOrNull { !it.isEmpty } ?: return null
        if (!pager.intersect(window)) return null
        val headerBottom = listOf("profile_header_container", "profile_tabs_container", "profile_action_bar")
            .mapNotNull { findVisibleNodeBounds(root, "$INSTAGRAM_PACKAGE:id/$it")?.bottom }.maxOrNull()
        val navigationTop = stableMainNavigationTop(root, window, InstagramScreen.PROFILE)
        // The grid container spans the full window width in every profile state; partial
        // widths are slide-in artifacts and would leave an uncovered side gap. Same for
        // the bottom edge: a partially laid-out pager measures short during rebuilds, so
        // the curtain always runs down to the navigation bar instead of trusting it.
        pager.left = window.left
        pager.right = window.right
        val vertical = InstagramProfileGeometry.clip(pager.top, pager.bottom, headerBottom, navigationTop)
            ?: return null
        pager.top = vertical.first
        pager.bottom = maxOf(vertical.second, navigationTop)
        return pager
    }

    private fun calculateProfileScrollFloor(
        root: AccessibilityNodeInfo,
        window: Rect
    ): Int {
        val actionBarBottom = listOf(
            "profile_action_bar", "profile_action_bar_container", "action_bar_root"
        ).mapNotNull { id ->
            findVisibleNodeBounds(root, "$INSTAGRAM_PACKAGE:id/$id")
        }.filter { bounds ->
            bounds.width() >= window.width() / 2 &&
                bounds.height() in 1..dp(96) &&
                bounds.top < window.top + window.height() / 4 &&
                bounds.bottom <= window.top + window.height() / 3
        }.maxOfOrNull { it.bottom }
        return InstagramProfileGeometry.scrollFloor(
            window.top, actionBarBottom, statusBarInset(), dp(56))
            .coerceIn(window.top, window.bottom)
    }

    private fun refreshFastMainProtectedSurface(
        root: AccessibilityNodeInfo,
        window: Rect
    ): Boolean {
        fun visible(id: String): Boolean = root.findAccessibilityNodeInfosByViewId(
            "$INSTAGRAM_PACKAGE:id/$id").any { it.isVisibleToUser }
        fun selected(id: String): Boolean = root.findAccessibilityNodeInfosByViewId(
            "$INSTAGRAM_PACKAGE:id/$id").any { it.isVisibleToUser && isSelectedRecursive(it, 2) }

        val screen = InstagramFastSurfacePolicy.classify(
            homeSelected = selected("feed_tab"),
            searchSelected = selected("search_tab"),
            hasBackNavigation = visible("action_bar_button_back"),
            hasHomeHeader = visible("main_feed_action_bar"),
            hasExploreHeader = visible("explore_action_bar"),
            hasExploreGrid = visible("grid_card_layout_container")
        ) ?: return false

        clearDirectConversationEntryGuard()
        clearDirectDetailsScrollGuard()
        lastDirectConversationGuards.clear()
        directReelLaunchPendingUntil = 0L
        directDetailsLaunchPendingUntil = 0L
        directDetailsFallbackBackAt = 0L
        directNavigationUntil = 0L
        directNavigationSourceWindowId = -1
        profileAllowedDepartureUntil = 0L
        profileScrollPositions.clear()
        profileScrollCover.reset()
        profileWindowId = -1

        val mainReelsGuard = root.findAccessibilityNodeInfosByViewId(
            "$INSTAGRAM_PACKAGE:id/clips_tab")
            .firstNotNullOfOrNull { node ->
                if (!node.isVisibleToUser) return@firstNotNullOfOrNull null
                Rect().also(node::getBoundsInScreen).takeIf { bounds ->
                    !bounds.isEmpty && bounds.intersect(window) && bounds.top >= window.centerY()
                }
            }
        instagramEntryTargets.clear()
        mainReelsGuard?.let { instagramEntryTargets.add(Rect(it) to true) }
        instagramOverlay.updateEntryGuards(mainReelsGuard?.let(::listOf) ?: emptyList(), isEnglish())
        cancelInstagramExit()
        observeInstagramScreen(screen, root.windowId)
        when (screen) {
            InstagramScreen.HOME_FEED ->
                showInstagramCurtain(calculateHomeFeedBounds(root, window), screen)
            InstagramScreen.EXPLORE ->
                showInstagramCurtain(calculateExploreBounds(root, window), screen)
            else -> return false
        }
        if (instagramOverlay.hasVisibleWindows) scheduleOverlayForegroundCheck()
        return true
    }

    private fun profileThumbnailTargets(root: AccessibilityNodeInfo, window: Rect): List<Rect> {
        // Query only inside the profile media pager: image_button elsewhere may
        // be an avatar, a Story, or a navigation control.
        val targets = mutableListOf<Rect>()
        val pagers = root.findAccessibilityNodeInfosByViewId("$INSTAGRAM_PACKAGE:id/profile_viewpager")
            .filter { it.isVisibleToUser }
        for (pager in pagers) {
            val viewport = Rect().also(pager::getBoundsInScreen)
            if (!viewport.intersect(window)) continue
            findVisibleNodeBounds(root, "$INSTAGRAM_PACKAGE:id/profile_tabs_container")?.let {
                if (it.bottom in viewport.top..viewport.bottom) viewport.top = it.bottom
            }
            stableMainNavigationTop(root, window, InstagramScreen.PROFILE).let {
                if (it in viewport.top..viewport.bottom) viewport.bottom = it
            }
            for (id in InstagramEntryPolicy.profileThumbnailIds) {
                for (node in pager.findAccessibilityNodeInfosByViewId("$INSTAGRAM_PACKAGE:id/$id")) {
                    if (!node.isVisibleToUser ||
                        !InstagramEntryPolicy.isProfileThumbnail(InstagramScreen.PROFILE, id, true)) continue
                    val bounds = Rect().also(node::getBoundsInScreen)
                    // A cell must not expand to a full-width list/container.
                    if (bounds.width() <= 0 || bounds.width() > window.width() / 2) continue
                    if (bounds.intersect(viewport) && !bounds.isEmpty) targets.add(bounds)
                }
            }
        }
        return targets.distinct().take(32)
    }

    private fun hasFastReelsViewer(
        root: AccessibilityNodeInfo,
        directLaunchPending: Boolean = false
    ): Boolean {
        fun visible(id: String): Boolean = root.findAccessibilityNodeInfosByViewId(
            "$INSTAGRAM_PACKAGE:id/$id").any { it.isVisibleToUser }
        if (!visible("root_clips_layout") && !visible("clips_viewer") &&
            !visible("clips_viewer_view_pager") && !visible("clips_view_pager")) return false
        val controlsVisible = visible("clips_ufi") || visible("clips_viewer_action_bar")
        if (!controlsVisible && !directLaunchPending) return false
        // Instagram briefly clears the selected state of feed_tab while an
        // embedded feed video replaces its player tree. The Home action bar is
        // the more stable host signal. Requiring the selected tab here made a
        // normal feed video look like a full-screen Reel at the end of playback
        // and caused an erroneous GLOBAL_ACTION_BACK.
        val homeHostVisible = visible("main_feed_action_bar")
        return InstagramEntryPolicy.isFastViewer(
            playerVisible = true,
            controlsVisible = controlsVisible,
            storyVisible = visible("reel_item_toolbar_container") || visible("story_viewer_container"),
            homeHostVisible = homeHostVisible,
            backVisible = visible("action_bar_button_back"),
            directLaunchPending = directLaunchPending
        )
    }

    private fun hasFastMainReelsTabDestination(root: AccessibilityNodeInfo): Boolean {
        fun selected(id: String): Boolean = root.findAccessibilityNodeInfosByViewId(
            "$INSTAGRAM_PACKAGE:id/$id"
        ).any { it.isVisibleToUser && isSelectedRecursive(it, 2) }
        fun visible(id: String): Boolean = root.findAccessibilityNodeInfosByViewId(
            "$INSTAGRAM_PACKAGE:id/$id"
        ).any { it.isVisibleToUser }

        val reelsSelected = selected("clips_tab")
        if (!reelsSelected) return false
        val anotherMainTabSelected = listOf(
            "feed_tab", "direct_tab", "search_tab", "profile_tab"
        ).any(::selected)
        return InstagramEntryPolicy.isMainReelsTabDestination(
            reelsSelected = true,
            anotherMainTabSelected = anotherMainTabSelected,
            hasBackNavigation = visible("action_bar_button_back")
        )
    }

    private fun hasFastStoryViewer(root: AccessibilityNodeInfo): Boolean =
        listOf("story_viewer_container", "reel_item_toolbar_container").any { id ->
            root.findAccessibilityNodeInfosByViewId("$INSTAGRAM_PACKAGE:id/$id")
                .any { it.isVisibleToUser }
        }

    private fun hasFastNotificationsSurface(root: AccessibilityNodeInfo): Boolean =
        listOf(
            "activity_feed", "activity_feed_root", "newsfeed_you",
            "newsfeed_recycler", "notifications_recycler",
            "follow_requests_header"
        ).any { id ->
            root.findAccessibilityNodeInfosByViewId("$INSTAGRAM_PACKAGE:id/$id")
                .any { it.isVisibleToUser }
        }

    private fun hasFastReelPreview(root: AccessibilityNodeInfo): Boolean {
        fun visible(id: String): Boolean = root.findAccessibilityNodeInfosByViewId(
            "$INSTAGRAM_PACKAGE:id/$id").any { it.isVisibleToUser }
        val previewChrome = visible("feed_preview_keep_watching_backdrop") ||
            visible("feed_preview_keep_watching_button") ||
            visible("feed_preview_bottom_cta_container")
        return previewChrome && visible("video_container") && visible("action_bar_button_back")
    }

    private fun hasVisibleDirectDetailsGrid(root: AccessibilityNodeInfo): Boolean {
        fun visible(id: String): Boolean = root.findAccessibilityNodeInfosByViewId(
            "$INSTAGRAM_PACKAGE:id/$id").any { it.isVisibleToUser }
        return visible("thread_details_pager") && visible("tab_layout") &&
            visible("shared_media_list")
    }

    private fun isDirectDetailsMediaIntent(source: AccessibilityNodeInfo?): Boolean {
        if (lastInstagramScreen != InstagramScreen.DIRECT_SHARED_MEDIA) return false
        if (sourcePathContains(source, "image_button", "media_set_row_content_identifier")) return true
        val curtain = instagramOverlay.visibleCurtain()
            ?.takeIf { it.first == InstagramScreen.DIRECT_SHARED_MEDIA }?.second ?: return false
        val sourceBounds = source?.let { Rect().also(it::getBoundsInScreen) } ?: return false
        return !sourceBounds.isEmpty && curtain.contains(sourceBounds.centerX(), sourceBounds.centerY())
    }

    private fun isDirectReelIntent(source: AccessibilityNodeInfo?): Boolean {
        if (lastInstagramScreen != InstagramScreen.DIRECT_MESSAGES) return false
        val sourceBounds = source?.let { Rect().also(it::getBoundsInScreen) }
        val insideKnownGuard = sourceBounds?.takeUnless(Rect::isEmpty)?.let { bounds ->
            val display = currentDisplayBounds()
            lastDirectConversationGuards.any { guard ->
                val expandedGuard = Rect(guard).apply {
                    inset(-dp(8), -dp(8))
                }
                expandedGuard.intersect(display) &&
                    expandedGuard.contains(bounds.centerX(), bounds.centerY())
            }
        } == true
        val ancestryIds = linkedSetOf<String>()
        val ancestryLabels = linkedSetOf<String>()
        var hasReelDescendant = false
        var current = source
        repeat(8) {
            val node = current ?: return@repeat
            val id = compactId(node.viewIdResourceName)
            val text = node.text?.toString().orEmpty()
            val description = node.contentDescription?.toString().orEmpty()
            if (id.isNotEmpty()) ancestryIds.add(id)
            if (text.isNotBlank()) ancestryLabels.add(text)
            if (description.isNotBlank()) ancestryLabels.add(description)
            if (id in setOf("message_content_portrait_xma_container",
                    "message_content_generic_xma_container")) {
                hasReelDescendant = hasReelDescendant ||
                    node.findAccessibilityNodeInfosByViewId(
                        "$INSTAGRAM_PACKAGE:id/reel_share_item_view")
                        .any { it.isVisibleToUser }
            }
            hasReelDescendant = hasReelDescendant ||
                node.findAccessibilityNodeInfosByViewId(
                    "$INSTAGRAM_PACKAGE:id/direct_reel_share_legibility_gradient_footer")
                    .any { it.isVisibleToUser }
            current = node.parent
        }
        return InstagramEntryPolicy.isDirectReelIntent(
            ancestryIds, ancestryLabels, insideKnownGuard, hasReelDescendant)
    }

    private fun hasFastDirectSafeReturn(root: AccessibilityNodeInfo): Boolean {
        fun visible(id: String): Boolean = root.findAccessibilityNodeInfosByViewId(
            "$INSTAGRAM_PACKAGE:id/$id").any { it.isVisibleToUser }
        val inbox = visible("inbox_refreshable_thread_list_recyclerview") &&
            (visible("direct_inbox_action_bar") || visible("direct_inbox_search_bar") ||
                visible("search_row"))
        val conversation = visible("direct_thread_header") && visible("message_list")
        return inbox || conversation
    }

    private fun refreshDirectDetailsSurface(root: AccessibilityNodeInfo, window: Rect): Boolean {
        val pager = findVisibleNodeBounds(root,
            "$INSTAGRAM_PACKAGE:id/thread_details_pager") ?: return false
        val tabLayout = findVisibleNodeBounds(root, "$INSTAGRAM_PACKAGE:id/tab_layout")
        val hasDetailsChrome = findVisibleNodeBounds(root,
            "$INSTAGRAM_PACKAGE:id/thread_details_header") != null ||
            findVisibleNodeBounds(root, "$INSTAGRAM_PACKAGE:id/shared_section") != null
        if (!hasDetailsChrome || !pager.intersect(window)) return false
        if (tabLayout != null) {
            pager.top = InstagramDirectDetailsPolicy.curtainTop(
                pager.top, tabLayout.bottom, dp(12)).coerceAtLeast(window.top)
        }
        mainHandler.removeCallbacks(homeScrollSettled)
        scrollCoverTop = null
        scrollCoverUntil = 0L
        profileScrollPositions.clear()
        profileScrollCover.reset()
        profileWindowId = -1
        profileAllowedDepartureUntil = 0L
        instagramEntryTargets.clear()
        instagramOverlay.updateEntryGuards(emptyList(), isEnglish())
        cancelInstagramExit()
        directReelLaunchPendingUntil = 0L

        val selectedTab = selectedDirectDetailsTab(root)
        val mediaGridAlreadyVisible = findVisibleNodeBounds(root,
            "$INSTAGRAM_PACKAGE:id/shared_media_list") != null
        val protect = InstagramDirectDetailsPolicy.shouldProtect(
            selectedTab, lastInstagramScreen == InstagramScreen.DIRECT_SHARED_MEDIA ||
                mediaGridAlreadyVisible)
        if (protect) {
            // Use the stable pager, not the tab-specific list. Instagram removes
            // shared_media_list while animating between Media and Reposts.
            showInstagramCurtain(pager, InstagramScreen.DIRECT_SHARED_MEDIA)
            val nextGuard = directDetailsTouchGuardBounds(window, pager)
            val previousGuard = directDetailsInteractionBounds
            directDetailsInteractionBounds = if (SystemClock.uptimeMillis() <
                directDetailsGuardHoldUntil && previousGuard != null && nextGuard != null) {
                Rect(previousGuard).apply { union(nextGuard) }
            } else nextGuard
            directDetailsInteractionBounds?.let { instagramOverlay.showTransientTouchGuard(it, isEnglish()) }
            observeInstagramScreen(InstagramScreen.DIRECT_SHARED_MEDIA, root.windowId)
        } else {
            clearDirectDetailsScrollGuard()
            clearInstagramResumeProtection()
            instagramOverlay.hide()
            observeInstagramScreen(InstagramScreen.DIRECT_MESSAGES, root.windowId)
        }
        exitGate.observe(InstagramScreen.DIRECT_MESSAGES, SystemClock.uptimeMillis(),
            verifiedConversationReturn = true)
        if (instagramOverlay.hasVisibleWindows) scheduleOverlayForegroundCheck()
        return true
    }

    private fun directDetailsTouchGuardBounds(
        window: Rect,
        pager: Rect
    ): Rect? {
        val bottom = pager.bottom.coerceAtMost(window.bottom)
        // The black curtain already overlaps the tab/pager seam by 12dp. The
        // transparent guard adds only a small extra lead and must not cover the
        // centre of the tab buttons.
        val top = (pager.top - dp(4)).coerceAtLeast(window.top)
        if (top >= bottom) return null
        return Rect(window.left, top, window.right, bottom)
    }

    private fun clearDirectDetailsScrollGuard() {
        mainHandler.removeCallbacks(directDetailsGuardSettled)
        directDetailsGuardHoldUntil = 0L
        directDetailsInteractionBounds = null
        instagramOverlay.hideTransientTouchGuard()
    }

    private fun hasFastPostViewer(root: AccessibilityNodeInfo): Boolean {
        fun visible(id: String): Boolean = root.findAccessibilityNodeInfosByViewId(
            "$INSTAGRAM_PACKAGE:id/$id").any { it.isVisibleToUser }
        val window = instagramWindowBounds(root)
        val fullPhotoViewer = listOf(
            "direct_media_viewer", "direct_visual_media_viewer", "media_viewer",
            "media_viewer_container", "photo_viewer", "image_viewer",
            "zoomable_view_container", "fullscreen_media_container"
        ).any { id ->
            root.findAccessibilityNodeInfosByViewId("$INSTAGRAM_PACKAGE:id/$id")
                .any { node ->
                    if (!node.isVisibleToUser) false else {
                        val bounds = Rect().also(node::getBoundsInScreen)
                        bounds.intersect(window) && bounds.width() >= window.width() * 3 / 4 &&
                            bounds.height() >= window.height() / 2
                    }
                }
        }
        if (fullPhotoViewer) return true
        val postChrome = listOf(
            "media_view_pager", "carousel_media_group", "post_detail", "feed_detail",
            "single_media_view", "row_feed_profile_header", "row_feed_button_comment",
            "row_feed_comment_textview_layout"
        ).any(::visible)
        if (!postChrome) return false
        val hasBack = visible("action_bar_button_back")
        val detailsTabsStillForeground = visible("tab_layout")
        return hasBack || !detailsTabsStillForeground
    }

    private fun selectedDirectDetailsTab(root: AccessibilityNodeInfo): Int? {
        val tabs = root.findAccessibilityNodeInfosByViewId("$INSTAGRAM_PACKAGE:id/icon_view")
            .filter { it.isVisibleToUser }
            .map { node -> node to Rect().also(node::getBoundsInScreen) }
            .filter { (_, bounds) -> !bounds.isEmpty }
            .sortedBy { (_, bounds) -> bounds.left }
        val selectedIndex = tabs.indexOfFirst { (node, _) ->
            node.isSelected || hasSelectedAncestor(node, 2)
        }
        return InstagramDirectDetailsPolicy.selectedTabIndex(tabs.size, selectedIndex)
    }

    private fun hasSelectedAncestor(node: AccessibilityNodeInfo, depth: Int): Boolean {
        var current = node.parent
        repeat(depth) {
            val ancestor = current ?: return false
            if (ancestor.isSelected) return true
            current = ancestor.parent
        }
        return false
    }

    private fun refreshDirectDetailsTabIntent(source: AccessibilityNodeInfo?) {
        val node = source ?: return
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != INSTAGRAM_PACKAGE) return
        val pager = findVisibleNodeBounds(root,
            "$INSTAGRAM_PACKAGE:id/thread_details_pager") ?: return
        val tabLayout = findVisibleNodeBounds(root,
            "$INSTAGRAM_PACKAGE:id/tab_layout") ?: return
        val sourceBounds = Rect().also(node::getBoundsInScreen)
        if (sourceBounds.isEmpty || !tabLayout.contains(sourceBounds.centerX(), sourceBounds.centerY())) return

        val relativeX = sourceBounds.centerX() - tabLayout.left
        val tabIndex = (relativeX * 3 / tabLayout.width().coerceAtLeast(1)).coerceIn(0, 2)
        val window = instagramWindowBounds(root)
        if (!pager.intersect(window)) return
        instagramEntryTargets.clear()
        instagramOverlay.updateEntryGuards(emptyList(), isEnglish())
        if (InstagramDirectDetailsPolicy.shouldProtect(tabIndex, false)) {
            showInstagramCurtain(pager, InstagramScreen.DIRECT_SHARED_MEDIA)
            directDetailsInteractionBounds = directDetailsTouchGuardBounds(window, pager)
            directDetailsInteractionBounds?.let { instagramOverlay.showTransientTouchGuard(it, isEnglish()) }
            observeInstagramScreen(InstagramScreen.DIRECT_SHARED_MEDIA, root.windowId)
        } else {
            clearDirectDetailsScrollGuard()
            clearInstagramResumeProtection()
            instagramOverlay.hide()
            observeInstagramScreen(InstagramScreen.DIRECT_MESSAGES, root.windowId)
        }
        Log.d(TAG, "IG_DIRECT_DETAILS_TAB_INTENT index=$tabIndex")
    }

    private fun hasVisibleProfileNativeSheet(root: AccessibilityNodeInfo): Boolean {
        val ids = setOf(
            "bottom_sheet_container", "layout_container_bottom_sheet",
            "bottom_sheet_container_view", "action_sheet_container",
            "action_sheet_row_text_view"
        )
        return ids.any { id ->
            root.findAccessibilityNodeInfosByViewId("$INSTAGRAM_PACKAGE:id/$id")
                .any { it.isVisibleToUser }
        }
    }

    private fun hasVisibleAboutAccountSurface(root: AccessibilityNodeInfo): Boolean {
        val explicitIds = listOf(
            "about_this_account_container", "account_transparency_container",
            "account_transparency_root", "about_account_fragment"
        )
        if (explicitIds.any { id ->
                root.findAccessibilityNodeInfosByViewId("$INSTAGRAM_PACKAGE:id/$id")
                    .any { it.isVisibleToUser }
            }) return true
        return listOf("Bu hesap hakkında", "About this account").any { title ->
            root.findAccessibilityNodeInfosByText(title).any { node ->
                node.isVisibleToUser && node.text?.toString()?.trim()
                    ?.equals(title, ignoreCase = true) == true
            }
        }
    }

    /** Null means this is not an allowed departure; Boolean says whether it is About Account. */
    private fun allowedProfileDeparture(source: AccessibilityNodeInfo?): Boolean? {
        val ids = linkedSetOf<String>()
        val labels = linkedSetOf<String>()
        var current = source
        repeat(8) {
            val node = current ?: return@repeat
            compactId(node.viewIdResourceName).takeIf(String::isNotEmpty)?.let(ids::add)
            node.text?.toString()?.trim()?.lowercase(Locale.US)
                ?.takeIf(String::isNotEmpty)?.let(labels::add)
            node.contentDescription?.toString()?.trim()?.lowercase(Locale.US)
                ?.takeIf(String::isNotEmpty)?.let(labels::add)
            current = node.parent
        }
        if (!InstagramProfileSurfacePolicy.isAllowedDeparture(ids, labels)) return null
        return InstagramProfileSurfacePolicy.isAboutAccountDeparture(ids, labels)
    }

    private fun releaseAllowedProfileSurface() {
        clearDirectDetailsScrollGuard()
        clearInstagramResumeProtection()
        instagramOverlay.hide()
        instagramOverlay.updateEntryGuards(emptyList(), isEnglish())
        profileScrollPositions.clear()
        profileScrollCover.reset()
        profileWindowId = -1
        cancelInstagramExit()
        directDetailsLaunchPendingUntil = 0L
        directDetailsFallbackBackAt = 0L
        directReelLaunchPendingUntil = 0L
        if (instagramOverlay.hasVisibleWindows) scheduleOverlayForegroundCheck()
    }

    private fun releaseVerifiedInstagramInbox(root: AccessibilityNodeInfo): Boolean {
        fun visible(id: String): Boolean = root.findAccessibilityNodeInfosByViewId(
            "$INSTAGRAM_PACKAGE:id/$id").any { it.isVisibleToUser }
        val listBounds = findVisibleNodeBounds(root,
            "$INSTAGRAM_PACKAGE:id/inbox_refreshable_thread_list_recyclerview") ?: return false
        val display = currentDisplayBounds()
        if (!InstagramInboxPolicy.isStableInboxLayout(
                listBounds.toInstagramGeometrySample(),
                display.toInstagramGeometrySample())) return false
        val header = visible("direct_inbox_action_bar") || visible("direct_inbox_search_bar") ||
            visible("search_row")
        // Even a partially opened viewer must not take this inbox-only bypass.
        // Fast Reel and Story checks run before this path. Keep only the two
        // layers which can otherwise leave a full-width inbox mounted behind
        // an opened media/details surface.
        val blockedTopLayer = listOf(
            "root_clips_layout", "clips_viewer", "clips_viewer_view_pager",
            "clips_view_pager", "thread_details_pager", "story_viewer_container",
            "reel_item_toolbar_container"
        ).any(::visible)
        if (!InstagramInboxPolicy.isInbox(true, header, blockedTopLayer)) return false
        verifiedInstagramInboxWindowId = root.windowId
        verifiedInstagramInboxListBounds = Rect(listBounds)
        directConversationSurfaceWindowId = -1
        clearDirectConversationEntryGuard()
        cancelInstagramExit()
        clearDirectDetailsScrollGuard()
        clearInstagramResumeProtection()
        instagramOverlay.hide()
        directNavigationUntil = 0L
        directNavigationSourceWindowId = -1
        instagramEntryTargets.clear()
        // Do not carry Home/media touch regions onto the inbox. Preserve only a
        // currently visible main Reels tab, if this Instagram layout exposes one.
        val window = instagramWindowBounds(root)
        root.findAccessibilityNodeInfosByViewId("$INSTAGRAM_PACKAGE:id/clips_tab")
            .filter { it.isVisibleToUser }.forEach { node ->
                val bounds = Rect().also(node::getBoundsInScreen)
                if (bounds.intersect(window) && bounds.height() in 1 until window.height() / 2) {
                    instagramEntryTargets.add(bounds to true)
                }
            }
        lastDirectConversationGuards.clear()
        instagramOverlay.updateEntryGuards(instagramEntryTargets.map { it.first }, isEnglish())
        if (lastInstagramScreen != InstagramScreen.DIRECT_MESSAGES) {
            Log.d(TAG, "IG_INBOX_RELEASE scanMs=${SystemClock.uptimeMillis() - lastInstagramScanTime}")
        }
        observeInstagramScreen(InstagramScreen.DIRECT_MESSAGES, root.windowId)
        directDetailsLaunchPendingUntil = 0L
        directDetailsFallbackBackAt = 0L
        directReelLaunchPendingUntil = 0L
        if (exitGate.observe(InstagramScreen.DIRECT_MESSAGES, SystemClock.uptimeMillis())) queueInstagramScan(450L)
        if (instagramOverlay.hasVisibleWindows) scheduleOverlayForegroundCheck()
        return true
    }

    private fun refreshDirectConversationGuards(root: AccessibilityNodeInfo): Boolean {
        val header = findVisibleNodeBounds(root, "$INSTAGRAM_PACKAGE:id/direct_thread_header") ?: return false
        val list = findVisibleNodeBounds(root, "$INSTAGRAM_PACKAGE:id/message_list") ?: return false
        // Mounted conversation chrome must not override a viewer transition.
        if (listOf("thread_details_pager", "reel_item_toolbar_container").any {
                findVisibleNodeBounds(root, "$INSTAGRAM_PACKAGE:id/$it") != null }) return false
        val viewport = Rect(list)
        viewport.top = maxOf(viewport.top, header.bottom)
        findVisibleNodeBounds(root, "$INSTAGRAM_PACKAGE:id/message_composer_bar")?.let {
            viewport.bottom = minOf(viewport.bottom, it.top)
        }
        if (viewport.isEmpty) return false
        prearmDirectConversationSurface(root, SystemClock.uptimeMillis(), viewport)
        if (isDirectConversationEntryGuardArmed()) {
            // Replace the provisional inbox rectangle with the conversation's
            // exact message viewport. Header and composer stay usable while
            // Reel-card geometry finishes its first layout.
            instagramOverlay.showConversationEntryGuard(viewport)
        }
        verifiedInstagramInboxWindowId = -1
        verifiedInstagramInboxListBounds = null
        val targets = mutableListOf<Rect>()
        // Direct ID lookup avoids the general BFS node budget and post pruning.
        root.findAccessibilityNodeInfosByViewId("$INSTAGRAM_PACKAGE:id/reel_share_item_view")
            .filter { it.isVisibleToUser }.take(32).forEach { card ->
                if (resolveReelsEntry(card, "reel_share_item_view", "", "") != null) {
                    val bounds = Rect().also(card::getBoundsInScreen)
                    if (bounds.intersect(viewport) && !bounds.isEmpty) targets.add(bounds)
                }
            }
        if (targets.isEmpty()) {
            // Legacy layouts are queried only when the current explicit card
            // ID is absent; avoiding six always-empty searches removes most of
            // the DM main-thread stalls on Instagram 446.
            for (id in listOf("clips_grid_item", "clips_thumbnail")) {
                root.findAccessibilityNodeInfosByViewId("$INSTAGRAM_PACKAGE:id/$id")
                    .filter { it.isVisibleToUser }.take(32).forEach { node ->
                        val entry = resolveReelsEntry(node, id, "", "") ?: return@forEach
                        val bounds = Rect().also(entry::getBoundsInScreen)
                        if (bounds.intersect(viewport) && !bounds.isEmpty) targets.add(bounds)
                    }
            }
        }
        if (targets.isEmpty()) targets.addAll(directReelLabelTargets(root, viewport))
        val current = targets.distinct().take(32)
        val now = SystemClock.uptimeMillis()
        val previousDirectGuards = lastDirectConversationGuards.map(::Rect)
        val currentEnvelope = current.takeIf { it.isNotEmpty() }?.let { cards ->
            Rect(cards.first()).apply { cards.drop(1).forEach(::union) }
        }
        val useBroadMotionGuard = directConversationGuardGate.observe(
            root.windowId, currentEnvelope?.toInstagramGeometrySample(), now)
        if (current.isNotEmpty()) {
            lastDirectConversationGuards.clear()
            lastDirectConversationGuards.addAll(current.map(::Rect))
        } else if (!useBroadMotionGuard && now >= directConversationGuardHoldUntil) {
            lastDirectConversationGuards.clear()
        }
        val exactGuards = if (current.isNotEmpty() ||
            (!useBroadMotionGuard && now >= directConversationGuardHoldUntil)) {
            current
        } else {
            lastDirectConversationGuards.map(::Rect)
        }
        val transitionGuards = if (useBroadMotionGuard) {
            directConversationTransitionGuards(exactGuards, viewport)
        } else emptyList()
        val renderedGuards = (exactGuards + transitionGuards).distinct()
        clearDirectDetailsScrollGuard()
        clearInstagramResumeProtection()
        instagramOverlay.updateEntryGuards(
            renderedGuards,
            isEnglish(),
            forceRelayout = useBroadMotionGuard)
        handoffDirectConversationEntryGuard(
            hasCardGuards = renderedGuards.isNotEmpty() && !useBroadMotionGuard,
            now = now)
        instagramOverlay.hide()
        instagramEntryTargets.clear()
        instagramEntryTargets.addAll(renderedGuards.map { it to false })
        cancelInstagramExit()
        directDetailsLaunchPendingUntil = 0L
        directDetailsFallbackBackAt = 0L
        directNavigationUntil = 0L
        directNavigationSourceWindowId = -1
        observeInstagramScreen(InstagramScreen.DIRECT_MESSAGES, root.windowId)
        // This path has verified the conversation header and message viewport,
        // and rejected mounted Reel viewers. Rearm before another card tap;
        // no click delivery or 400 ms safe-screen dwell is required.
        exitGate.observe(InstagramScreen.DIRECT_MESSAGES, SystemClock.uptimeMillis(),
            verifiedConversationReturn = true)
        if (previousDirectGuards != exactGuards) {
            Log.d(TAG, "IG_DIRECT_CARD_GUARDS window=${root.windowId} " +
                "exact=${exactGuards.size} broad=${transitionGuards.size} " +
                "viewport=$viewport bounds=${renderedGuards.take(4).joinToString()}")
        }
        val remaining = directReelLaunchPendingUntil - now
        if (remaining > 0L) {
            // A card click that escaped its region gets a short fast-player
            // polling burst. This avoids waiting for Instagram's next delayed
            // content event before issuing the viewer Back action.
            queueInstagramScan(if (remaining > 1_150L) 16L else minOf(48L, remaining))
        } else if (useBroadMotionGuard || isDirectConversationEntryGuardArmed()) {
            // Continue until the card stops moving; a fixed one-second timer was
            // shorter than Instagram's observed ModalActivity animation. The
            // pre-armed entry guard also needs bounded discovery scans when a
            // conversation initially publishes no Reel card nodes.
            queueInstagramScan(if (useBroadMotionGuard) 48L else 32L)
        }
        if (instagramOverlay.hasVisibleWindows) scheduleOverlayForegroundCheck()
        return true
    }

    /**
     * A DM conversation window exists before Instagram publishes message_list.
     * Use the previously verified inbox viewport to attach the input surface as
     * soon as WindowManager announces that new window. This path deliberately
     * performs no accessibility-tree query, so it cannot sit behind the slow
     * Instagram IPC calls observed on a cold conversation open.
     */
    private fun armDirectWindowTransition(
        event: AccessibilityEvent,
        now: Long
    ): Boolean {
        val verifiedInboxContext = lastInstagramScreen == InstagramScreen.DIRECT_MESSAGES &&
            verifiedInstagramInboxWindowId != -1
        val homeToDirectContext = now < directNavigationUntil &&
            directNavigationSourceWindowId != -1 &&
            event.windowId != directNavigationSourceWindowId
        if ((!verifiedInboxContext && !homeToDirectContext) || event.windowId == -1 ||
            (verifiedInboxContext && event.windowId == verifiedInstagramInboxWindowId) ||
            isDirectConversationEntryGuardArmed()) return false
        val windowChanges = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            event.windowChanges
        } else {
            0
        }
        if (event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val relevantChanges = AccessibilityEvent.WINDOWS_CHANGE_ADDED or
                AccessibilityEvent.WINDOWS_CHANGE_ACTIVE or
                AccessibilityEvent.WINDOWS_CHANGE_FOCUSED
            if (windowChanges and relevantChanges == 0) return false
        }
        val display = currentDisplayBounds()
        val retainedBottom = lastVerifiedInstagramCurtain?.second?.bottom
            ?.takeIf { it in (display.centerY() + 1)..display.bottom }
            ?: (display.bottom - dp(48))
        val cached = verifiedInstagramInboxListBounds
        val viewport = Rect(cached ?: Rect(
            display.left,
            display.top + statusBarInset() + dp(56),
            display.right,
            retainedBottom
        )).apply {
            left = display.left
            right = display.right
        }
        if (!viewport.intersect(display) || viewport.isEmpty ||
            !instagramOverlay.showConversationEntryGuard(viewport)) return false
        directConversationSurfaceWindowId = event.windowId
        directConversationEntryGuardArmedAt = now
        directConversationEntryGuardUntil = now +
            DIRECT_CONVERSATION_ENTRY_GUARD_TIMEOUT_MS
        mainHandler.removeCallbacks(directConversationEntryGuardRelease)
        mainHandler.removeCallbacks(directConversationEntryGuardTimeout)
        mainHandler.postDelayed(
            directConversationEntryGuardTimeout,
            DIRECT_CONVERSATION_ENTRY_GUARD_TIMEOUT_MS)
        Log.d(TAG, "IG_CONVERSATION_WINDOW_GUARD armed window=${event.windowId} " +
            "type=${event.eventType} changes=$windowChanges " +
            "eventAgeMs=${now - event.eventTime} bounds=$viewport")
        return true
    }

    private fun prearmDirectConversationSurface(
        root: AccessibilityNodeInfo,
        now: Long,
        knownViewport: Rect? = null
    ): Boolean {
        if (root.windowId == directConversationSurfaceWindowId) return false
        fun bounds(id: String): Rect? = findVisibleNodeBounds(
            root, "$INSTAGRAM_PACKAGE:id/$id")
        // Stories are allowed. Test that one exceptional surface first, then
        // arm from message_list before doing any other remote node queries.
        // Reel/media layers are intentionally not an exception: if they mount
        // over a conversation, this transparent guard is useful until the fast
        // viewer path sends Back.
        if (bounds("story_viewer_container") != null) return false
        // message_list is published before direct_thread_header on the first
        // open after process start. Waiting for both left a real 200-400 ms
        // interval in which a Reel card could receive the next tap.
        val list = knownViewport?.let(::Rect) ?: bounds("message_list") ?: return false
        val header = bounds("direct_thread_header")
        if (header == null && lastInstagramScreen != InstagramScreen.DIRECT_MESSAGES) return false
        val display = currentDisplayBounds()
        val viewport = Rect(list).apply {
            top = maxOf(top, header?.bottom ?: top)
            bounds("message_composer_bar")?.let { composer ->
                bottom = minOf(bottom, composer.top)
            }
            // Guard the whole message plane while the ModalActivity slides in;
            // the reported list is horizontally clipped during that animation.
            left = display.left
            right = display.right
        }
        if (!viewport.intersect(display) || viewport.isEmpty ||
            !instagramOverlay.showConversationEntryGuard(viewport)) return false
        directConversationSurfaceWindowId = root.windowId
        directConversationEntryGuardArmedAt = now
        directConversationEntryGuardUntil = now +
            DIRECT_CONVERSATION_SURFACE_GUARD_TIMEOUT_MS
        mainHandler.removeCallbacks(directConversationEntryGuardRelease)
        mainHandler.removeCallbacks(directConversationEntryGuardTimeout)
        mainHandler.postDelayed(
            directConversationEntryGuardTimeout,
            DIRECT_CONVERSATION_SURFACE_GUARD_TIMEOUT_MS)
        Log.d(TAG, "IG_CONVERSATION_SURFACE_GUARD armed bounds=$viewport")
        return true
    }

    private fun armDirectConversationEntryGuard(
        event: AccessibilityEvent,
        now: Long
    ): Boolean {
        val source = event.source ?: return false
        val ids = linkedSetOf<String>()
        val ancestryBounds = mutableListOf<Rect>()
        var ancestryListBounds: Rect? = null
        var current: AccessibilityNodeInfo? = source
        repeat(10) {
            val node = current ?: return@repeat
            val id = compactId(node.viewIdResourceName)
            id.takeIf(String::isNotEmpty)?.let(ids::add)
            val bounds = Rect().also(node::getBoundsInScreen)
            if (!bounds.isEmpty) ancestryBounds.add(bounds)
            if (id == "inbox_refreshable_thread_list_recyclerview") {
                ancestryListBounds = Rect(bounds)
            }
            current = node.parent
        }
        val liveRoot = rootInActiveWindow
        val liveListBounds = if (ancestryListBounds == null &&
            liveRoot?.packageName?.toString() == INSTAGRAM_PACKAGE &&
            liveRoot.windowId == event.windowId) {
            val candidate = findVisibleNodeBounds(liveRoot,
                "$INSTAGRAM_PACKAGE:id/inbox_refreshable_thread_list_recyclerview")
            val hasHeader = listOf(
                "direct_inbox_action_bar", "direct_inbox_search_bar", "search_row"
            ).any { id ->
                findVisibleNodeBounds(liveRoot, "$INSTAGRAM_PACKAGE:id/$id") != null
            }
            candidate?.takeIf { hasHeader &&
                findVisibleNodeBounds(liveRoot, "$INSTAGRAM_PACKAGE:id/root_clips_layout") == null }
        } else null
        val listBounds = ancestryListBounds ?: liveListBounds ?:
            verifiedInstagramInboxListBounds ?: return false
        val sourceBounds = ancestryBounds.asSequence()
            .filter { bounds ->
                bounds.centerX() in listBounds.left until listBounds.right &&
                    bounds.centerY() in listBounds.top until listBounds.bottom &&
                    bounds.height() < listBounds.height() / 2
            }
            .maxByOrNull { it.width() }
            ?: Rect().also(source::getBoundsInScreen)
        val structurallyVerified = ancestryListBounds != null || liveListBounds != null
        val shouldArm = InstagramInboxPolicy.isThreadOpenIntent(
            ids,
            sourceBounds.takeUnless(Rect::isEmpty)?.toInstagramGeometrySample(),
            listBounds.toInstagramGeometrySample(),
            structurallyVerified || event.windowId == verifiedInstagramInboxWindowId)
        if (!shouldArm) return false

        val guardedBounds = Rect(listBounds)
        if (!guardedBounds.intersect(currentDisplayBounds()) || guardedBounds.isEmpty ||
            !instagramOverlay.showConversationEntryGuard(guardedBounds)) return false

        directConversationEntryGuardArmedAt = now
        directConversationEntryGuardUntil = now + DIRECT_CONVERSATION_ENTRY_GUARD_TIMEOUT_MS
        mainHandler.removeCallbacks(directConversationEntryGuardRelease)
        mainHandler.removeCallbacks(directConversationEntryGuardTimeout)
        mainHandler.postDelayed(
            directConversationEntryGuardTimeout,
            DIRECT_CONVERSATION_ENTRY_GUARD_TIMEOUT_MS)
        directReelLaunchPendingUntil = 0L
        Log.d(TAG, "IG_CONVERSATION_ENTRY_GUARD armed bounds=$guardedBounds")
        queueInstagramScan(16L)
        return true
    }

    private fun handoffDirectConversationEntryGuard(hasCardGuards: Boolean, now: Long) {
        if (!isDirectConversationEntryGuardArmed()) return
        // With no published card bounds there is no safe target to hand off to.
        // Keep the temporary viewport shield until a card guard is mounted or
        // the failsafe timeout releases a conversation that contains no Reels.
        if (!hasCardGuards) return
        val releaseAt = directConversationEntryGuardArmedAt +
            DIRECT_CONVERSATION_ENTRY_MIN_HOLD_MS
        mainHandler.removeCallbacks(directConversationEntryGuardTimeout)
        mainHandler.removeCallbacks(directConversationEntryGuardRelease)
        mainHandler.postDelayed(
            directConversationEntryGuardRelease,
            maxOf(16L, releaseAt - now))
        Log.d(TAG, "IG_CONVERSATION_ENTRY_GUARD handoff cards=$hasCardGuards")
    }

    private fun isDirectConversationEntryGuardArmed(): Boolean =
        directConversationEntryGuardUntil > SystemClock.uptimeMillis()

    private fun clearDirectConversationEntryGuard() {
        mainHandler.removeCallbacks(directConversationEntryGuardTimeout)
        mainHandler.removeCallbacks(directConversationEntryGuardRelease)
        directConversationEntryGuardArmedAt = 0L
        directConversationEntryGuardUntil = 0L
        instagramOverlay.hideConversationEntryGuard()
    }

    private fun directConversationTransitionGuards(
        anchors: List<Rect>,
        viewport: Rect
    ): List<Rect> {
        if (anchors.isEmpty()) return emptyList()
        val display = currentDisplayBounds()
        val top = viewport.top.coerceIn(display.top, display.bottom)
        val bottom = viewport.bottom.coerceIn(top, display.bottom)
        if (top >= bottom) return emptyList()
        return anchors.map { anchor ->
            val width = (anchor.width().coerceIn(dp(96), display.width() / 2) + dp(32))
                .coerceAtMost(display.width())
            if (anchor.centerX() >= display.centerX()) {
                Rect(display.right - width, top, display.right, bottom)
            } else {
                Rect(display.left, top, display.left + width, bottom)
            }
        }.distinct()
    }

    private fun directReelLabelTargets(root: AccessibilityNodeInfo, viewport: Rect): List<Rect> {
        val evidence = mutableListOf<AccessibilityNodeInfo>()
        for (query in listOf("Reels Videosu", "Reels video", "instagram.com/reel")) {
            evidence += root.findAccessibilityNodeInfosByText(query)
                .filter { it.isVisibleToUser }
                .take(32)
        }
        evidence += root.findAccessibilityNodeInfosByViewId(
            "$INSTAGRAM_PACKAGE:id/direct_reel_share_legibility_gradient_footer")
            .filter { it.isVisibleToUser }
            .take(32)
        return evidence.mapNotNull { directReelCardBounds(it, viewport) }.distinct()
    }

    private fun directReelCardBounds(node: AccessibilityNodeInfo, viewport: Rect): Rect? {
        var current: AccessibilityNodeInfo? = node
        var best: Rect? = null
        repeat(7) {
            val candidate = current ?: return@repeat
            val id = compactId(candidate.viewIdResourceName)
            if (id in setOf("message_list", "message_content", "direct_thread_content_below_action_bar")) {
                return best
            }
            val bounds = Rect().also(candidate::getBoundsInScreen)
            if (bounds.intersect(viewport) && bounds.width() >= dp(48) && bounds.height() >= dp(48) &&
                bounds.width() <= viewport.width() * 9 / 10 &&
                bounds.height() <= viewport.height() * 3 / 4) {
                best = bounds
            }
            current = candidate.parent
        }
        return best
    }

    private fun exitBlockedViewer(
        root: AccessibilityNodeInfo,
        windowBounds: Rect,
        screen: InstagramScreen,
        useGlobalBackOnly: Boolean = false
    ) {
        clearDirectConversationEntryGuard()
        val now = SystemClock.uptimeMillis()
        val wasDirectConversation = lastInstagramScreen == InstagramScreen.DIRECT_MESSAGES
        val preservedConversationGuards = when {
            lastDirectConversationGuards.isNotEmpty() &&
                (wasDirectConversation || now < directConversationGuardHoldUntil) ->
                lastDirectConversationGuards.map(::Rect)
            wasDirectConversation -> instagramEntryTargets
                .filterNot { it.second }.map { Rect(it.first) }
            else -> emptyList()
        }
        observeInstagramScreen(screen, root.windowId)
        directDetailsLaunchPendingUntil = 0L
        directDetailsFallbackBackAt = 0L
        directReelLaunchPendingUntil = 0L
        clearDirectDetailsScrollGuard()
        clearInstagramResumeProtection()
        exitGate.observe(screen, now)
        // No full-screen exit curtain. Keep the one-shot exit, not a black window.
        mainHandler.removeCallbacks(homeScrollSettled)
        scrollCoverTop = null
        scrollCoverUntil = 0L
        instagramOverlay.hide()
        // Keep the transparent card guards mounted while the DM viewer closes.
        // Removing and rediscovering them created a second-tap race.
        instagramOverlay.updateEntryGuards(preservedConversationGuards, isEnglish())
        val newViewerEncounter = !instagramExitInProgress
        if (newViewerEncounter) {
            instagramExitInProgress = true
            Log.d(TAG, "IG_VIEWER screen=$screen detected scanMs=${now - lastInstagramScanTime}")
            if (screen == InstagramScreen.REELS) {
                mainHandler.removeCallbacks(instagramViewerExitRetry)
                mainHandler.postDelayed(
                    instagramViewerExitRetry, INSTAGRAM_VIEWER_EXIT_RETRY_MS)
            }
        }
        if (exitGate.requestBack(now)) {
            // Prefer the viewer's own Back control when present. Never click a tab
            // or send a second action after an accepted click in the same attempt.
            val back = if (useGlobalBackOnly) null else
                root.findAccessibilityNodeInfosByViewId("$INSTAGRAM_PACKAGE:id/action_bar_button_back")
                    .firstOrNull { node ->
                        val bounds = Rect().also(node::getBoundsInScreen)
                        node.isVisibleToUser && node.isClickable && !bounds.isEmpty &&
                            bounds.top >= windowBounds.top &&
                            bounds.bottom <= windowBounds.top + windowBounds.height() / 4
                    }
            val clickedBack = back?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
            val accepted = clickedBack || performGlobalAction(GLOBAL_ACTION_BACK)
            if (accepted) instagramExitTreeQuietUntil = now + INSTAGRAM_EXIT_TREE_QUIET_MS
            Log.d(TAG, "IG_VIEWER_EXIT screen=$screen singleAction viewerBack=$clickedBack accepted=$accepted")
            // Failsafe exit retries belong to the same viewer encounter and
            // must not inflate the user-facing or API block count.
            if (newViewerEncounter) {
                recordIntervention(INSTAGRAM_PACKAGE, System.currentTimeMillis())
            }
        }
        // Do not force a tree walk after BACK. During rapid re-entry Instagram
        // can invalidate that tree while it is being queried, blocking the
        // accessibility main thread for roughly its two-second IPC timeout.
        // The actual return/window events schedule the next scan; if those are
        // missed, InstagramExitGate's viewer-only failsafe remains available.
    }

    private fun resolveReelsEntry(
        node: AccessibilityNodeInfo, id: String, text: String, description: String
    ): AccessibilityNodeInfo? {
        if (id == "clips_tab") return node
        if (id == "reel_share_item_view") {
            val parentId = compactId(node.parent?.viewIdResourceName)
            val hasFooter = node.findAccessibilityNodeInfosByViewId(
                "$INSTAGRAM_PACKAGE:id/direct_reel_share_legibility_gradient_footer").isNotEmpty()
            return node.takeIf { InstagramEntryPolicy.isDirectReelCard(id, parentId, hasFooter) }
        }
        if (!InstagramEntryPolicy.hasReelEvidence(id, text) &&
            !InstagramEntryPolicy.hasReelEvidence(id, description)) return null
        if (node.isClickable) return node
        // The Reel marker can be a non-clickable child of the shared media card.
        // Never expand to the conversation row, entire list, or profile navigation.
        var parent = node.parent
        repeat(4) {
            val candidate = parent ?: return null
            val parentId = compactId(candidate.viewIdResourceName)
            if (candidate.isClickable) {
                return candidate.takeIf { it.isVisibleToUser && InstagramEntryPolicy.isCardContainer(parentId) }
            }
            if (parentId.contains("thread") || parentId.contains("inbox") ||
                parentId.contains("recycler") || parentId.contains("story") || parentId.contains("profile")) return null
            parent = candidate.parent
        }
        return null
    }

    private fun collectInstagramSnapshot(
        root: AccessibilityNodeInfo,
        source: AccessibilityNodeInfo?
    ): InstagramUiSignals {
        val ids = linkedSetOf<String>()
        instagramEntryTargets.clear()
        val selectedTabs = linkedSetOf<InstagramTab>()
        val titles = linkedSetOf<String>()
        var searchFieldFocused = false
        var hasBackNav = false
        var hasCameraAction = false
        var hasClipsInteractionBar = false
        var hasFullscreenReelsViewer = false
        val rootBounds = instagramWindowBounds(root)
        val pending = ArrayDeque<AccessibilityNodeInfo>()
        pending.add(root)
        var visited = 0

        while (pending.isNotEmpty() && visited < MAX_TREE_NODES) {
            val node = pending.removeFirst()
            visited++
            var shouldPruneSubtree = false

            if (node.isVisibleToUser) {
                val id = compactId(node.viewIdResourceName)
                if (id.isNotEmpty()) ids.add(id)

                val text = node.text?.toString()?.trim()?.lowercase(Locale.US).orEmpty()
                val desc = node.contentDescription?.toString()?.trim()?.lowercase(Locale.US).orEmpty()

                val bounds = Rect().also(node::getBoundsInScreen)
                val entry = if (instagramEntryTargets.size < 32) {
                    resolveReelsEntry(node, id, text, desc)
                } else null
                if (entry != null) {
                    val clipped = Rect().also(entry::getBoundsInScreen)
                    if (id == "reel_share_item_view") {
                        // A scrolled card can extend behind the composer/header.
                        // Never carry its input shield onto those safe controls.
                        val viewport = findVisibleNodeBounds(root, "$INSTAGRAM_PACKAGE:id/message_list")
                        if (viewport == null || !clipped.intersect(viewport)) clipped.setEmpty()
                        findVisibleNodeBounds(root, "$INSTAGRAM_PACKAGE:id/message_composer_bar")?.let {
                            clipped.bottom = minOf(clipped.bottom, it.top)
                        }
                    }
                    if (clipped.intersect(rootBounds) && clipped.width() > 0 && clipped.height() > 0 &&
                        clipped.height() < rootBounds.height() / 2) {
                        instagramEntryTargets.add(clipped to (id == "clips_tab"))
                    }
                }
                if (id in setOf("root_clips_layout", "clips_viewer", "clips_viewer_view_pager", "clips_view_pager") &&
                    bounds.height() >= rootBounds.height() * 0.6 &&
                    bounds.width() >= rootBounds.width() * 0.8) {
                    hasFullscreenReelsViewer = true
                }
                val isTopHeaderArea = bounds.top <= rootBounds.top + (rootBounds.height() / 4)


                // 1. Back button detection: must be an explicit back action/button, not "backdrop" or "background"
                val isExplicitBackId = id == "action_bar_button_back" ||
                    id.contains("action_bar_back") ||
                    id.contains("back_button") ||
                    id.contains("navigation_back") ||
                    (id.contains("back") && !id.contains("background") && !id.contains("backdrop"))
                val isExplicitBackDesc = desc == "back" || desc == "geri" || desc == "navigate up" ||
                    desc == "yukarı git" || desc == "geriye git" ||
                    desc.startsWith("back ") || desc.startsWith("geri ")
                if (isExplicitBackId || isExplicitBackDesc) {
                    hasBackNav = true
                }

                // 2. Camera action icon in header
                if (isTopHeaderArea && (
                    id.contains("camera") ||
                    desc == "camera" || desc == "kamera" || desc == "create" || desc == "oluştur"
                )) {
                    hasCameraAction = true
                }

                // 3. Titles / header text in action bar title views or text views with title/header id
                val isTitleView = id.contains("action_bar_title") ||
                    id.contains("clips_viewer_action_bar_title") ||
                    id == "title_text" || id == "action_bar_title_view" ||
                    (id.contains("title") && !id.contains("container") && !id.contains("tray") && !id.contains("holder"))
                if (isTitleView) {
                    if (text.isNotEmpty()) titles.add(text)
                    if (desc.isNotEmpty()) titles.add(desc)
                }

                // 4. Clips / Reels interaction bar buttons
                if (id.contains("clips_ufi") || id.contains("clips_viewer_action_bar")) {
                    hasClipsInteractionBar = true
                }

                if (node.isSelected || (tabForId(id) != null && isSelectedRecursive(node, 2))) {
                    val explicitTab = tabForId(id)
                    explicitTab?.let(selectedTabs::add)
                    if (explicitTab == null && sourcePathContains(node, "tab_bar")) {
                        tabForLabel(node.contentDescription?.toString())?.let(selectedTabs::add)
                    }
                }

                val className = node.className?.toString().orEmpty()
                if (node.isFocused &&
                    className.contains("EditText", ignoreCase = true) &&
                    (id.contains("search") || node.isEditable)
                ) {
                    searchFieldFocused = true
                }

                // Skip post/tray descendants; their chrome is sufficient for classification.
                if (id.startsWith("row_feed_") ||
                    id == "carousel_media_group" ||
                    id.startsWith("feed_item_") ||
                    id == "reels_tray_container" ||
                    id.contains("reels_tray") ||
                    id.contains("tray_recycler_view") ||
                    id.contains("comments_recycler") ||
                    id.contains("comment_thread") ||
                    id.contains("comment_list")
                ) {
                    shouldPruneSubtree = true
                }
            }

            if (!shouldPruneSubtree) {
                for (index in 0 until node.childCount) {
                    node.getChild(index)?.let(pending::addLast)
                }
            }
        }

        val sourceIds = linkedSetOf<String>()
        val sourceLabels = linkedSetOf<String>()
        var explicitSourceTab: InstagramTab? = null
        var current = source
        var depth = 0
        while (current != null && depth < 8) {
            val id = compactId(current.viewIdResourceName)
            if (id.isNotEmpty()) sourceIds.add(id)
            current.text?.toString()?.trim()?.lowercase(Locale.US)
                ?.takeIf(String::isNotEmpty)
                ?.let(sourceLabels::add)
            current.contentDescription?.toString()?.trim()?.lowercase(Locale.US)
                ?.takeIf(String::isNotEmpty)
                ?.let(sourceLabels::add)
            if (explicitSourceTab == null) explicitSourceTab = tabForId(id)
            current = current.parent
            depth++
        }
        val sourceTab = explicitSourceTab ?: if (sourceIds.any { it.contains("tab_bar") }) {
            sourceLabels.firstNotNullOfOrNull(::tabForLabel)
        } else {
            null
        }

        val signals = InstagramUiSignals(
            ids = ids,
            selectedTabs = selectedTabs,
            sourceIds = sourceIds,
            sourceLabels = sourceLabels,
            sourceTab = sourceTab,
            searchFieldFocused = searchFieldFocused,
            titles = titles,
            hasBackNavigation = hasBackNav,
            hasCameraAction = hasCameraAction,
            hasClipsInteractionBar = hasClipsInteractionBar,
            hasFullscreenReelsViewer = hasFullscreenReelsViewer
        )
        return signals
    }

    private fun handleTikTok(event: AccessibilityEvent) {
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> maybeSnapBackFromBlockedTopTab(force = true)
            AccessibilityEvent.TYPE_VIEW_SCROLLED ->
                if (isVerticalScroll(event)) handleTikTokScroll(event)
                else maybeSnapBackFromBlockedTopTab()
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> maybeSnapBackFromBlockedTopTab()
            else -> Unit
        }
    }

    private fun handleTikTokScroll(event: AccessibilityEvent) {
        val protectionEnabled = ProtectionPreferences.isEnabled(this, ProtectedApp.TIKTOK)
        val root = rootInActiveWindow
        val packageInForeground = root?.packageName?.toString() == TIKTOK_PACKAGE
        if (!protectionEnabled || !packageInForeground) return
        if (sourcePathContains(event.source, "comment", "message", "inbox", "profile")) return
        // Comment sheets and other bottom-anchored lists keep the video action rail in the
        // tree behind them; their scroll sources start low on screen, unlike the fullscreen
        // feed pager, so geometry distinguishes the two.
        if (isBottomAnchoredSource(event.source)) return

        val signals = collectTikTokSignals(root)
        val isSafeSurface = TikTokSurfacePolicy.classify(signals) != TikTokSurface.FEED
        if (!TikTokInterventionPolicy.shouldExit(
                protectionEnabled = protectionEnabled,
                isScrollEvent = true,
                packageInForeground = packageInForeground,
                isVerticalScroll = true,
                isSafeSurface = isSafeSurface
            )
        ) {
            return
        }

        val now = System.currentTimeMillis()
        if (!TikTokInterventionPolicy.cooldownElapsed(now, lastTikTokExitTime)) return

        val accepted = performGlobalAction(GLOBAL_ACTION_BACK)
        if (accepted) {
            lastTikTokExitTime = now
            recordIntervention(TIKTOK_PACKAGE, now)
            scheduleTikTokFeedExitRetry()
        } else {
            // Do not count a block that Android rejected; the next scroll event may retry.
            Log.w(TAG, "TIKTOK_EXIT_REJECTED")
        }
    }

    private fun scheduleTikTokFeedExitRetry() {
        mainHandler.removeCallbacks(tikTokFeedExitRetry)
        mainHandler.postDelayed(tikTokFeedExitRetry, TikTokInterventionPolicy.EXIT_RETRY_DELAY_MS)
    }

    // TikTok may swallow the first Back as an "exit app" confirmation; verify the feed is
    // still foregrounded and press Back once more so the first scroll deterministically exits.
    private val tikTokFeedExitRetry = Runnable {
        val root = rootInActiveWindow ?: return@Runnable
        if (root.packageName?.toString() != TIKTOK_PACKAGE) return@Runnable
        if (TikTokSurfacePolicy.classify(collectTikTokSignals(root)) == TikTokSurface.FEED) {
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
    }

    // Takipte/Topluluk must not be enterable. Clicks and surface changes are checked with a
    // small throttle; when one of those top tabs ends up selected, tap Sizin İçin to undo.
    private fun maybeSnapBackFromBlockedTopTab(force: Boolean = false) {
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != TIKTOK_PACKAGE) return
        if (!ProtectionPreferences.isEnabled(this, ProtectedApp.TIKTOK)) return
        val now = System.currentTimeMillis()
        if (!force && !TikTokInterventionPolicy.topTabCheckElapsed(now, lastTikTokTopTabCheckMs)) {
            return
        }
        lastTikTokTopTabCheckMs = now

        val signals = collectTikTokSignals(root)
        if (signals.selectedTopTab != TikTokSurfacePolicy.TOP_TAB_BLOCKED) return
        if (snapBackToForYouTab(root)) {
            Log.d(TAG, "TIKTOK_TOP_TAB_SNAP_BACK accepted")
        }
    }

    private fun snapBackToForYouTab(root: AccessibilityNodeInfo): Boolean {
        val pending = ArrayDeque<AccessibilityNodeInfo>()
        pending.add(root)
        var visited = 0

        while (pending.isNotEmpty() && visited < MAX_TREE_NODES) {
            val node = pending.removeFirst()
            visited++

            if (node.isVisibleToUser &&
                (TikTokSurfacePolicy.isHomeTopTabLabel(node.text?.toString()) ||
                    TikTokSurfacePolicy.isHomeTopTabLabel(node.contentDescription?.toString()))
            ) {
                var current: AccessibilityNodeInfo? = node
                repeat(4) {
                    val candidate = current ?: return@repeat
                    if (candidate.isClickable) {
                        val accepted = candidate.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        if (accepted) {
                            recordIntervention(TIKTOK_PACKAGE, System.currentTimeMillis())
                        }
                        return accepted
                    }
                    current = candidate.parent
                }
            }

            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(pending::addLast)
            }
        }

        return false
    }

    private fun isBottomAnchoredSource(source: AccessibilityNodeInfo?): Boolean {
        val node = source ?: return false
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        val screenHeight = resources.displayMetrics.heightPixels
        if (screenHeight <= 0) return false
        return bounds.top >= screenHeight / 4
    }

    // Bounded scan mirroring isYouTubeShorts: feed captions can contain arbitrary words, so
    // classification only collects structural signals (tabs, composers, pager, action rail).
    private fun collectTikTokSignals(root: AccessibilityNodeInfo): TikTokUiSignals {
        var selectedTab: String? = null
        var selectedTopTab: String? = null
        var composer = TikTokComposer.NONE
        var hasProfileSignals = false
        var hasFeedPager = false
        val railCategories = linkedSetOf<String>()
        val pending = ArrayDeque<AccessibilityNodeInfo>()
        pending.add(root)
        var visited = 0

        while (pending.isNotEmpty() && visited < MAX_TREE_NODES) {
            val node = pending.removeFirst()
            visited++

            if (node.isVisibleToUser) {
                val id = compactId(node.viewIdResourceName)
                if (node.isSelected) {
                    TikTokSurfacePolicy.resolveSelectedTab(
                        idFragment = id,
                        text = node.text?.toString(),
                        description = node.contentDescription?.toString()
                    )?.let { if (selectedTab == null) selectedTab = it }
                    if (selectedTopTab == null) {
                        TikTokSurfacePolicy.resolveSelectedTopTab(
                            text = node.text?.toString(),
                            description = node.contentDescription?.toString()
                        )?.let { selectedTopTab = it }
                    }
                }
                if (node.isEditable) {
                    val hint = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        node.hintText?.toString()
                    } else null
                    val fieldComposer = TikTokSurfacePolicy.classifyComposer(
                        hint = hint,
                        text = node.text?.toString(),
                        description = node.contentDescription?.toString(),
                        idFragment = id
                    )
                    if (fieldComposer != TikTokComposer.NONE && composer == TikTokComposer.NONE) {
                        composer = fieldComposer
                    }
                }
                if (!hasProfileSignals &&
                    (TikTokSurfacePolicy.isProfileLabel(node.text?.toString()) ||
                        TikTokSurfacePolicy.isProfileLabel(node.contentDescription?.toString()))
                ) {
                    hasProfileSignals = true
                }
                if (id.contains("view_pager") || id.contains("viewpager")) hasFeedPager = true
                node.contentDescription?.toString()
                    ?.let(TikTokSurfacePolicy::railActionCategory)
                    ?.let(railCategories::add)
            }

            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(pending::addLast)
            }
        }

        return TikTokUiSignals(
            selectedTab,
            selectedTopTab,
            composer,
            hasProfileSignals,
            hasFeedPager,
            railCategories
        )
    }

    private fun handleYouTube(event: AccessibilityEvent) {
        // Entering a Short is always allowed; only the swipe toward the next video asks
        // for the exit. Content/state-changed events must never trigger it, or the viewer
        // would close before the user can watch the Short they deliberately opened.
        val isScrollEvent = event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED
        val protectionEnabled = ProtectionPreferences.isEnabled(this, ProtectedApp.YOUTUBE)
        val root = rootInActiveWindow
        val packageInForeground = root?.packageName?.toString() == YOUTUBE_PACKAGE
        if (!isScrollEvent || !protectionEnabled || !packageInForeground) return
        if (!isVerticalScroll(event)) return
        val isSafeSourcePath =
            sourcePathContains(event.source, "comment", "reply", "bottom_sheet")
        if (isSafeSourcePath) return

        val shortsVisible = isYouTubeShorts(root)
        if (!YouTubeShortsPolicy.shouldExit(
                protectionEnabled = protectionEnabled,
                isScrollEvent = isScrollEvent,
                packageInForeground = packageInForeground,
                shortsVisible = shortsVisible,
                isVerticalScroll = true,
                isSafeSourcePath = isSafeSourcePath
            )
        ) {
            return
        }

        performYouTubeShortsExit()
    }

    private fun performYouTubeShortsExit() {
        val now = System.currentTimeMillis()
        if (!YouTubeShortsPolicy.cooldownElapsed(now, lastYouTubeExitTime)) return
        lastYouTubeExitTime = now

        performGlobalAction(GLOBAL_ACTION_BACK)
        recordIntervention(YOUTUBE_PACKAGE, now)

        mainHandler.postDelayed({
            val root = rootInActiveWindow
            if (root?.packageName?.toString() == YOUTUBE_PACKAGE && isYouTubeShorts(root)) {
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
        }, YouTubeShortsPolicy.EXIT_RETRY_DELAY_MS)
    }

    private fun isYouTubeShorts(root: AccessibilityNodeInfo): Boolean {
        val pending = ArrayDeque<AccessibilityNodeInfo>()
        pending.add(root)
        var visited = 0

        while (pending.isNotEmpty() && visited < MAX_TREE_NODES) {
            val node = pending.removeFirst()
            visited++

            if (node.isVisibleToUser) {
                val id = compactId(node.viewIdResourceName)
                if (id.contains("shorts_player") ||
                    id.contains("reel_recycler") ||
                    id.contains("shorts_video") ||
                    id.contains("reel_watch_sequence")
                ) {
                    return true
                }

                if (node.isSelected &&
                    node.contentDescription?.toString()?.trim()?.equals("Shorts", ignoreCase = true) == true
                ) {
                    return true
                }
            }

            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(pending::addLast)
            }
        }

        return false
    }

    private fun isVerticalScroll(event: AccessibilityEvent): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            (event.scrollDeltaX != 0 || event.scrollDeltaY != 0)
        ) {
            return abs(event.scrollDeltaY) >= abs(event.scrollDeltaX)
        }
        return event.fromIndex >= 0 && event.toIndex >= 0 && event.fromIndex != event.toIndex
    }

    private fun sourcePathContains(
        source: AccessibilityNodeInfo?,
        vararg fragments: String
    ): Boolean {
        var current = source
        var depth = 0
        while (current != null && depth < 8) {
            val id = compactId(current.viewIdResourceName)
            if (fragments.any(id::contains)) return true
            current = current.parent
            depth++
        }
        return false
    }

    private fun isNotificationsIntent(source: AccessibilityNodeInfo?): Boolean {
        var current = source
        repeat(8) {
            val node = current ?: return false
            val id = compactId(node.viewIdResourceName)
            val label = listOf(node.text, node.contentDescription)
                .joinToString(" ") { it?.toString().orEmpty() }
                .lowercase(Locale.US)
            if (id.contains("notification") || id.contains("activity_feed") ||
                label.contains("bildirim") || label.contains("notification") ||
                label.contains("hareket")) return true
            current = node.parent
        }
        return false
    }

    private fun isExploreNavigationIntent(source: AccessibilityNodeInfo?): Boolean {
        val ids = linkedSetOf<String>()
        val labels = linkedSetOf<String>()
        var current = source
        repeat(8) {
            val node = current ?: return@repeat
            compactId(node.viewIdResourceName).takeIf(String::isNotEmpty)?.let(ids::add)
            node.text?.toString()?.trim()?.lowercase(Locale.US)
                ?.takeIf(String::isNotEmpty)?.let(labels::add)
            node.contentDescription?.toString()?.trim()?.lowercase(Locale.US)
                ?.takeIf(String::isNotEmpty)?.let(labels::add)
            current = node.parent
        }
        return InstagramEntryPolicy.isExploreTabIntent(ids, labels)
    }

    private fun isHomeNavigationIntent(source: AccessibilityNodeInfo?): Boolean {
        val ids = linkedSetOf<String>()
        val labels = linkedSetOf<String>()
        var current = source
        repeat(8) {
            val node = current ?: return@repeat
            compactId(node.viewIdResourceName).takeIf(String::isNotEmpty)?.let(ids::add)
            node.text?.toString()?.trim()?.lowercase(Locale.US)
                ?.takeIf(String::isNotEmpty)?.let(labels::add)
            node.contentDescription?.toString()?.trim()?.lowercase(Locale.US)
                ?.takeIf(String::isNotEmpty)?.let(labels::add)
            current = node.parent
        }
        return InstagramEntryPolicy.isHomeTabIntent(ids, labels)
    }

    private fun refreshProtectedNavigationGuards(
        root: AccessibilityNodeInfo,
        window: Rect
    ) {
        val targets = linkedMapOf<InstagramTab, Rect>()
        listOf(
            InstagramTab.HOME to "feed_tab",
            InstagramTab.SEARCH to "search_tab"
        ).forEach { (tab, id) ->
            root.findAccessibilityNodeInfosByViewId("$INSTAGRAM_PACKAGE:id/$id")
                .firstNotNullOfOrNull { node ->
                    if (!node.isVisibleToUser) return@firstNotNullOfOrNull null
                    Rect().also(node::getBoundsInScreen).takeIf { bounds ->
                        !bounds.isEmpty && bounds.intersect(window) &&
                            bounds.top >= window.centerY() &&
                            bounds.height() < window.height() / 3
                    }
                }?.let { targets[tab] = it }
        }
        instagramOverlay.updateNavigationGuards(targets)
    }

    private fun onInstagramProtectedNavigation(tab: InstagramTab) {
        if (tab != InstagramTab.HOME && tab != InstagramTab.SEARCH) return
        if (!ProtectionPreferences.isEnabled(this, ProtectedApp.INSTAGRAM)) return
        val now = SystemClock.uptimeMillis()
        if (isRecentProxiedNavigation(tab, now)) return
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != INSTAGRAM_PACKAGE) return

        prearmProtectedNavigationCurtain(root, tab)
        val targetId = if (tab == InstagramTab.HOME) "feed_tab" else "search_tab"
        val accepted = performInstagramTabAction(root, targetId)
        if (accepted) {
            proxiedNavigationTab = tab
            proxiedNavigationUntil = now + 750L
            queueInstagramScan(0L)
        }
        Log.d(TAG, "IG_NAVIGATION_PROXY tab=$tab accepted=$accepted")
    }

    private fun prearmProtectedNavigationCurtain(
        root: AccessibilityNodeInfo,
        tab: InstagramTab
    ) {
        val window = instagramWindowBounds(root)
        val bounds = if (tab == InstagramTab.SEARCH) {
            calculateExploreBounds(root, window).apply {
                top = maxOf(top, statusBarInset() + dp(56))
                top = top.coerceAtMost(bottom - 1)
            }
        } else {
            val bottom = stableMainNavigationTop(
                root, window, InstagramScreen.HOME_FEED)
            val top = (statusBarInset() + dp(56)).coerceIn(window.top, bottom - 1)
            Rect(window.left, top, window.right, bottom)
        }
        clearInstagramResumeProtection()
        currentInstagramWindowId = root.windowId
        instagramOverlay.hide()
        val screen = if (tab == InstagramTab.HOME) {
            InstagramScreen.HOME_FEED
        } else {
            InstagramScreen.EXPLORE
        }
        if (showInstagramCurtain(bounds, screen) && tab == InstagramTab.HOME) {
            val now = SystemClock.uptimeMillis()
            scrollCoverTop = bounds.top
            scrollCoverUntil = now + 1_500L
            lastHomeScrollAt = now
            settledHomeTop = null
            stableHomeSamples = 0
            mainHandler.removeCallbacks(homeScrollSettled)
            mainHandler.postDelayed(homeScrollSettled, 48L)
        }
    }

    private fun performInstagramTabAction(root: AccessibilityNodeInfo, id: String): Boolean {
        root.findAccessibilityNodeInfosByViewId("$INSTAGRAM_PACKAGE:id/$id")
            .filter { it.isVisibleToUser }
            .forEach { node ->
                var candidate: AccessibilityNodeInfo? = node
                var depth = 0
                while (candidate != null && depth++ < 4) {
                    if (candidate.isClickable &&
                        candidate.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
                    candidate = candidate.parent
                }
            }
        return false
    }

    private fun isRecentProxiedNavigation(tab: InstagramTab, now: Long): Boolean {
        if (now >= proxiedNavigationUntil) {
            proxiedNavigationTab = null
            proxiedNavigationUntil = 0L
            return false
        }
        return proxiedNavigationTab == tab
    }

    private fun recordIntervention(targetPackage: String, now: Long) {
        val prefs = getSharedPreferences(ProtectionPreferences.PREFS_NAME, Context.MODE_PRIVATE)
        val appName = when (targetPackage) {
            TIKTOK_PACKAGE -> "TikTok"
            YOUTUBE_PACKAGE -> "YouTube Shorts"
            else -> if (isEnglish()) "Instagram Reels & Feed" else "Instagram Reels ve Akış"
        }

        val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now))
        val dailyBlocksKey = "blocks_$currentDate"
        val appBlocksKey = when (targetPackage) {
            TIKTOK_PACKAGE -> "blocks_tiktok"
            YOUTUBE_PACKAGE -> "blocks_youtube"
            else -> "blocks_instagram"
        }
        val dailyAppKey = when (targetPackage) {
            TIKTOK_PACKAGE -> "blocks_${currentDate}_tiktok"
            YOUTUBE_PACKAGE -> "blocks_${currentDate}_youtube"
            else -> "blocks_${currentDate}_instagram"
        }

        val currentBlocks = prefs.getInt("total_blocks", 0)
        val currentDailyBlocks = prefs.getInt(dailyBlocksKey, 0)
        val currentAppBlocks = prefs.getInt(appBlocksKey, 0)
        val currentDailyAppBlocks = prefs.getInt(dailyAppKey, 0)
        val currentXp = prefs.getLong("user_xp", 150L)

        val lastActiveDay = prefs.getString("last_active_day", "").orEmpty()
        var streakDays = prefs.getInt("streak_days", 0)
        if (lastActiveDay != currentDate) {
            val calendar = Calendar.getInstance().apply {
                timeInMillis = now
                add(Calendar.DAY_OF_MONTH, -1)
            }
            val yesterday = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)
            streakDays = when {
                lastActiveDay == yesterday -> streakDays + 1
                lastActiveDay.isEmpty() -> 1
                else -> 1
            }
        }

        val hour = SimpleDateFormat("HH", Locale.US).format(Date(now))
        val hourlyBlocksKey = "blocks_${currentDate}_hour_$hour"
        val currentHourlyBlocks = prefs.getInt(hourlyBlocksKey, 0)

        val time = SimpleDateFormat("HH:mm", Locale.US).format(Date(now))
        val appDailyCount = currentDailyAppBlocks + 1
        val newLog = "$time|$appName|$appDailyCount"
        val previousLogs = prefs.getString("recent_shield_logs", "").orEmpty()
        val updatedLogs = buildList {
            add(newLog)
            if (previousLogs.isNotEmpty()) addAll(previousLogs.split(';'))
        }.take(10).joinToString(";")

        prefs.edit()
            .putInt("total_blocks", currentBlocks + 1)
            .putLong("user_xp", currentXp + 3L)
            .putInt(dailyBlocksKey, currentDailyBlocks + 1)
            .putInt(appBlocksKey, currentAppBlocks + 1)
            .putInt(dailyAppKey, currentDailyAppBlocks + 1)
            .putInt(hourlyBlocksKey, currentHourlyBlocks + 1)
            .putInt("streak_days", streakDays)
            .putString("last_active_day", currentDate)
            .putString("recent_shield_logs", updatedLogs)
            .putLong(AccessibilityStreakPolicy.KEY_LAST_ACCESSIBILITY_HEARTBEAT_MS, now)
            .remove(AccessibilityStreakPolicy.KEY_ACCESSIBILITY_DISABLED_SINCE_MS)
            .apply()

        // This remains a no-op unless the user explicitly enabled telemetry.
        TelemetryManager.sendTelemetryAsync(this)
    }

    private fun trackPackageTransition(packageName: String) {
        if (packageName == lastPackage) return

        val now = System.currentTimeMillis()
        if (lastPackage == INSTAGRAM_PACKAGE && instagramSessionStartedAt > 0L) {
            val prefs = getSharedPreferences(ProtectionPreferences.PREFS_NAME, Context.MODE_PRIVATE)
            val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now))
            val savedDate = prefs.getString("instagram_time_day", "")
            val previous = if (savedDate == currentDate) {
                prefs.getLong("instagram_daily_time_ms", 0L)
            } else {
                0L
            }
            prefs.edit()
                .putLong("instagram_daily_time_ms", previous + (now - instagramSessionStartedAt))
                .putString("instagram_time_day", currentDate)
                .apply()
            instagramSessionStartedAt = 0L
        }

        if (packageName == INSTAGRAM_PACKAGE) {
            instagramSessionStartedAt = now
        }

        lastPackage = packageName
    }

    private fun findVisibleNodeBounds(root: AccessibilityNodeInfo, viewId: String): Rect? {
        val nodes = root.findAccessibilityNodeInfosByViewId(viewId) ?: return null
        var maxRect: Rect? = null
        var maxArea = 0L
        for (node in nodes) {
            if (node != null && node.isVisibleToUser) {
                val r = Rect()
                node.getBoundsInScreen(r)
                val area = r.width().toLong() * r.height().toLong()
                if (r.width() > 0 && r.height() > 0 && area > maxArea) {
                    maxArea = area
                    maxRect = r
                }
            }
        }
        return maxRect
    }

    private fun isSelectedRecursive(node: AccessibilityNodeInfo, depth: Int): Boolean {
        if (node.isSelected) return true
        if (depth > 0) {
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null && isSelectedRecursive(child, depth - 1)) {
                    return true
                }
            }
        }
        return false
    }

    private fun refreshHomeScrollBounds(event: AccessibilityEvent?): Boolean {
        val previous = instagramOverlay.visibleHomeBounds() ?: return false
        val root = rootInActiveWindow ?: return false
        if (root.packageName?.toString() != INSTAGRAM_PACKAGE ||
            !ProtectionPreferences.isEnabled(this, ProtectedApp.INSTAGRAM)) return false
        if (event != null && (event.windowId != root.windowId ||
                SystemClock.uptimeMillis() - event.eventTime !in 0L..1_500L)) return false
        val window = instagramWindowBounds(root)
        val header = verifiedHomeHeaderBounds(root, window)
        if (header == null && !root.findAccessibilityNodeInfosByViewId("$INSTAGRAM_PACKAGE:id/feed_tab")
                .any { it.isVisibleToUser && isSelectedRecursive(it, 2) }) return false
        // Never turn an old Home event into a new overlay on another screen.
        if (window.left != previous.left || window.right != previous.right) return false
        val heldTop = scrollCoverTop
        scrollCoverTop = null
        val measured = calculateHomeFeedBounds(root, window)
        val now = SystemClock.uptimeMillis()
        if (event != null) {
            val dx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) event.scrollDeltaX else 0
            val dy = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) event.scrollDeltaY else 0
            measured.top = InstagramScrollGeometry.top(previous.top, measured.top, header?.bottom ?: window.top, dx, dy, dp(48))
            lastHomeScrollAt = now
            settledHomeTop = null
            stableHomeSamples = 0
            scrollCoverTop = measured.top
            scrollCoverUntil = now + 300L
            mainHandler.removeCallbacks(homeScrollSettled)
            mainHandler.postDelayed(homeScrollSettled, 48L)
        } else if (heldTop != null) {
            // A fixed 140 ms timer could reopen the edge during fling inertia.
            // Release only after quiet input and repeated matching tray measurements.
            stableHomeSamples = if (settledHomeTop == measured.top) stableHomeSamples + 1 else 1
            settledHomeTop = measured.top
            if (now - lastHomeScrollAt < 200L || stableHomeSamples < 3) {
                scrollCoverTop = minOf(heldTop, measured.top)
                measured.top = scrollCoverTop!!
                scrollCoverUntil = now + 300L
                mainHandler.postDelayed(homeScrollSettled, 48L)
            } else {
                scrollCoverUntil = 0L
                // The measurement above was taken while the temporary cover was
                // still active, so it may contain that conservative top. Measure
                // once more after clearing the hold or the curtain can remain
                // unnecessarily expanded until another accessibility event.
                measured.set(calculateHomeFeedBounds(root, window))
                settledHomeTop = null
                stableHomeSamples = 0
            }
        }
        showInstagramCurtain(measured, InstagramScreen.HOME_FEED)
        return true
    }

    private fun calculateHomeFeedBounds(root: AccessibilityNodeInfo, displayBounds: Rect): Rect {
        val stories = findVisibleNodeBounds(root, "com.instagram.android:id/reels_tray_container")
            ?: findVisibleNodeBounds(root, "com.instagram.android:id/tray_recycler_view")
            ?: findVisibleNodeBounds(root, "com.instagram.android:id/stories_tray")
            // A conservative navigation curtain can temporarily occlude the
            // Stories tray and make isVisibleToUser false. HOME has already
            // been structurally verified before this method is called, so an
            // attached, sane tray rectangle is safe to use without uncovering
            // the feed for a measurement frame.
            ?: findAttachedHomeStoriesBounds(root, displayBounds)
        val bounds = contentBounds(root, displayBounds, stories?.bottom, home = true)
        if (SystemClock.uptimeMillis() < scrollCoverUntil && instagramOverlay.visibleHomeBounds() != null) {
            scrollCoverTop?.let { bounds.top = minOf(bounds.top, it) }
        }
        return bounds
    }

    private fun findAttachedHomeStoriesBounds(
        root: AccessibilityNodeInfo,
        window: Rect
    ): Rect? {
        val ids = listOf("reels_tray_container", "tray_recycler_view", "stories_tray")
        return ids.firstNotNullOfOrNull { id ->
            root.findAccessibilityNodeInfosByViewId("$INSTAGRAM_PACKAGE:id/$id")
                .firstNotNullOfOrNull { node ->
                    Rect().also(node::getBoundsInScreen).takeIf { bounds ->
                        !bounds.isEmpty && bounds.intersect(window) &&
                            bounds.top >= window.top && bounds.bottom < window.centerY() &&
                            bounds.height() >= dp(32)
                    }
                }
        }
    }

    private fun verifiedHomeHeaderBounds(root: AccessibilityNodeInfo, window: Rect): Rect? {
        val header = root.findAccessibilityNodeInfosByViewId("$INSTAGRAM_PACKAGE:id/main_feed_action_bar")
            .firstOrNull { it.isVisibleToUser } ?: return null
        val bounds = Rect().also(header::getBoundsInScreen)
        if (bounds.isEmpty || bounds.top < window.top || bounds.bottom >= window.centerY()) return null
        // A cached/translated header container can remain in the tree after its
        // controls disappear. Reserve space only for an actually visible control.
        val pending = ArrayDeque<AccessibilityNodeInfo>()
        for (i in 0 until minOf(header.childCount, 12)) header.getChild(i)?.let(pending::addLast)
        var visited = 0
        while (pending.isNotEmpty() && visited++ < 12) {
            val child = pending.removeFirst()
            val control = Rect().also(child::getBoundsInScreen)
            if (child.isVisibleToUser && child.isClickable && !control.isEmpty && bounds.contains(control)) {
                return bounds.takeIf { it.intersect(window) }
            }
            if (child.isVisibleToUser) {
                for (i in 0 until minOf(child.childCount, 12)) child.getChild(i)?.let(pending::addLast)
            }
        }
        return null
    }

    private fun calculateExploreBounds(root: AccessibilityNodeInfo, displayBounds: Rect): Rect {
        val search = findVisibleNodeBounds(root, "com.instagram.android:id/explore_action_bar")
            ?: findVisibleNodeBounds(root, "com.instagram.android:id/action_bar_search_edit_text")
        return contentBounds(root, displayBounds, search?.bottom, home = false)
    }

    private fun contentBounds(root: AccessibilityNodeInfo, window: Rect, anchor: Int?, home: Boolean): Rect {
        val header = if (home) verifiedHomeHeaderBounds(root, window) else null
        val screen = if (home) InstagramScreen.HOME_FEED else InstagramScreen.EXPLORE
        val bottom = stableMainNavigationTop(root, window, screen)
        // Only chrome/tray boundaries determine the mask. Never use a post/list's
        // moving bounds, and never reuse Explore coordinates for Home.
        val visibleAnchor = anchor?.takeIf { it > window.top && it < window.centerY() }
        val top = if (home) InstagramHomeGeometry.top(window.top, visibleAnchor, header?.bottom)
            else visibleAnchor ?: (window.top + dp(56))
        return Rect(window.left, top.coerceAtMost(bottom - 1), window.right, bottom)
    }

    private fun stableMainNavigationTop(
        root: AccessibilityNodeInfo,
        window: Rect,
        screen: InstagramScreen
    ): Int {
        val retainedTop = instagramOverlay.visibleCurtain()
            ?.takeIf { (retainedScreen, bounds) ->
                retainedScreen == screen && bounds.left == window.left &&
                    bounds.right == window.right && bounds.bottom in
                    (window.centerY() + 1) until window.bottom
            }
            ?.second?.bottom

        fun attachedBounds(id: String): List<Rect> =
            root.findAccessibilityNodeInfosByViewId("$INSTAGRAM_PACKAGE:id/$id")
                .map { Rect().also(it::getBoundsInScreen) }
                .filter { bounds ->
                    !bounds.isEmpty && bounds.top > window.centerY() &&
                        bounds.top < window.bottom && bounds.bottom > bounds.top &&
                        bounds.bottom <= window.bottom + dp(8) &&
                        bounds.height() <= dp(120)
                }

        val barTop = attachedBounds("tab_bar")
            .filter { it.width() >= window.width() / 2 }
            .minOfOrNull { it.top }
        val tabTop = if (barTop == null) {
            listOf("feed_tab", "clips_tab", "direct_tab", "search_tab", "profile_tab")
                .flatMap(::attachedBounds)
                .minOfOrNull { it.top }
        } else null

        return InstagramBottomNavigationGeometry.resolveTop(
            windowTop = window.top,
            windowBottom = window.bottom,
            retainedTop = retainedTop,
            detectedTop = barTop ?: tabTop,
            fallbackInset = dp(48)
        )
    }

    private fun onInstagramOverlayTouched() {
        if (!ProtectionPreferences.isEnabled(this, ProtectedApp.INSTAGRAM)) return
        val now = System.currentTimeMillis()
        if (now - lastOverlayAttemptTime < OVERLAY_ATTEMPT_COOLDOWN_MS) return
        lastOverlayAttemptTime = now
        recordIntervention(INSTAGRAM_PACKAGE, now)
    }

    private fun onInstagramEntryGuardVerticalSwipe(
        startX: Float,
        startY: Float,
        direction: InstagramGuardScrollDirection
    ) {
        if (lastInstagramScreen != InstagramScreen.DIRECT_MESSAGES) return
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != INSTAGRAM_PACKAGE) return
        val list = root.findAccessibilityNodeInfosByViewId(
            "$INSTAGRAM_PACKAGE:id/message_list")
            .firstOrNull { node ->
                val bounds = Rect().also(node::getBoundsInScreen)
                !bounds.isEmpty && bounds.contains(startX.toInt(), startY.toInt())
            } ?: return
        val action = when (direction) {
            InstagramGuardScrollDirection.FORWARD -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            InstagramGuardScrollDirection.BACKWARD -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        }
        var accepted = false
        var scrollTarget: AccessibilityNodeInfo? = list
        repeat(4) {
            val candidate = scrollTarget ?: return@repeat
            if (!accepted) accepted = candidate.performAction(action)
            scrollTarget = candidate.parent
        }
        Log.d(TAG, "IG_DIRECT_GUARD_SCROLL direction=$direction accepted=$accepted")
        if (accepted) queueInstagramScan(16L)
    }

    private fun cancelInstagramExit() {
        if (!instagramExitInProgress) return
        instagramExitInProgress = false
        instagramExitTreeQuietUntil = 0L
        mainHandler.removeCallbacks(instagramViewerExitRetry)
    }

    private fun logSlowInstagramStage(stage: String, started: Long) {
        val elapsed = SystemClock.uptimeMillis() - started
        if (elapsed > 100L) Log.w(TAG, "IG_SLOW_STAGE stage=$stage elapsedMs=$elapsed")
    }

    private fun observeInstagramScreen(
        screen: InstagramScreen,
        windowId: Int
    ): InstagramProtectionDecision {
        val previous = instagramStateMachine.currentScreen
        val decision = instagramStateMachine.observe(screen, windowId)
        if (decision.changed) {
            Log.d(TAG, "IG_V2_STATE $previous -> ${decision.screen} " +
                "window=$windowId revision=${decision.revision} action=${decision.action}")
        }
        if (screen != InstagramScreen.UNKNOWN) lastInstagramScreen = decision.screen
        return decision
    }

    private fun scheduleOverlayForegroundCheck() {
        if (overlayForegroundCheckScheduled) return
        overlayForegroundCheckScheduled = true
        mainHandler.postDelayed(overlayForegroundCheck, OVERLAY_FOREGROUND_CHECK_MS)
    }

    private fun showInstagramCurtain(bounds: Rect?, screen: InstagramScreen): Boolean {
        if (bounds == null) return false
        val now = SystemClock.uptimeMillis()
        val retained = instagramOverlay.visibleCurtain()
        if (instagramResumeGeometryGate.isActive && retained != null && retained.first != screen) {
            // A real surface change after resume must not inherit the old
            // surface's geometry hold.
            clearInstagramResumeProtection()
        }
        if (!instagramResumeGeometryGate.accept(screen, currentInstagramWindowId,
                bounds.toInstagramGeometrySample(), now)) {
            // WindowManager can keep the Recents animation transform even when
            // the numeric LayoutParams did not change. Re-applying the last
            // verified rectangle each frame snaps the attached window back to
            // application coordinates without exposing the Instagram surface.
            return retained?.let { (retainedScreen, retainedBounds) ->
                instagramOverlay.show(
                    retainedBounds, retainedScreen, isEnglish(), forceRelayout = true)
            } ?: false
        }
        mainHandler.removeCallbacks(instagramResumeRescan)
        return instagramOverlay.show(bounds, screen, isEnglish()).also { shown ->
            if (shown) lastVerifiedInstagramCurtain = screen to Rect(bounds)
        }
    }

    private fun beginInstagramResumeProtection(root: AccessibilityNodeInfo? = rootInActiveWindow) {
        if (!ProtectionPreferences.isEnabled(this, ProtectedApp.INSTAGRAM)) return
        root ?: return
        if (root.packageName?.toString() != INSTAGRAM_PACKAGE) return
        val screen = lastInstagramScreen.takeIf(InstagramSurfacePolicy::needsCurtain) ?: run {
            instagramResumePending = false
            return
        }
        val window = instagramWindowBounds(root)
        val display = currentDisplayBounds()
        val retained = lastVerifiedInstagramCurtain
            ?.takeIf { (retainedScreen, retainedBounds) ->
                retainedScreen == screen && retainedBounds.width() == display.width() &&
                    display.contains(retainedBounds)
            }
            ?.second
        val bounds = retained ?: conservativeInstagramResumeBounds(root, window, screen) ?: return
        currentInstagramWindowId = root.windowId
        val now = SystemClock.uptimeMillis()
        instagramResumeGeometryGate.begin(screen, root.windowId, now)
        if (instagramOverlay.show(bounds, screen, isEnglish(), forceRelayout = true)) {
            instagramResumePending = false
            mainHandler.removeCallbacks(instagramResumeRescan)
            mainHandler.postDelayed(instagramResumeRescan, INSTAGRAM_RESUME_RESCAN_MS)
            scheduleOverlayForegroundCheck()
            Log.d(TAG, "IG_V2_RESUME_GUARD screen=$screen window=${root.windowId} " +
                "retained=${retained != null} bounds=$bounds")
        }
    }

    private fun conservativeInstagramResumeBounds(
        root: AccessibilityNodeInfo,
        window: Rect,
        screen: InstagramScreen
    ): Rect? {
        val app = findVisibleNodeBounds(root, "$INSTAGRAM_PACKAGE:id/layout_container_main")
            ?.takeIf { it.intersect(window) } ?: Rect(window)
        val actionBottom = findVisibleNodeBounds(root,
            "$INSTAGRAM_PACKAGE:id/action_bar_container")?.bottom
            ?: findVisibleNodeBounds(root, "$INSTAGRAM_PACKAGE:id/main_feed_action_bar")?.bottom
            ?: app.top + dp(56)
        val bottom = stableMainNavigationTop(root, window, screen)
            .takeIf { it > app.centerY() }
            ?: app.bottom
        val top = actionBottom.coerceIn(app.top, bottom)
        return Rect(app.left, top, app.right, bottom).takeUnless {
            it.width() <= 0 || it.height() <= dp(60)
        }
    }

    private fun Rect.toInstagramGeometrySample() = InstagramGeometrySample(
        left = left, top = top, right = right, bottom = bottom)

    private fun clearInstagramResumeProtection() {
        instagramResumePending = false
        instagramResumeGeometryGate.clear()
        mainHandler.removeCallbacks(instagramResumeRescan)
    }

    private fun hideInstagramContentOverlay() {
        clearDirectDetailsScrollGuard()
        clearInstagramResumeProtection()
        instagramOverlay.hide()
        if (instagramOverlay.hasVisibleWindows) {
            scheduleOverlayForegroundCheck()
        }
    }

    private fun resetInstagramProtectionState() {
        proxiedNavigationTab = null
        proxiedNavigationUntil = 0L
        clearDirectConversationEntryGuard()
        clearDirectDetailsScrollGuard()
        clearInstagramResumeProtection()
        currentInstagramWindowId = -1
        instagramExitTreeQuietUntil = 0L
        profileAllowedDepartureUntil = 0L
        directDetailsLaunchPendingUntil = 0L
        directDetailsFallbackBackAt = 0L
        directReelLaunchPendingUntil = 0L
        directReelIntentDebounceUntil = 0L
        directConversationGuardHoldUntil = 0L
        directConversationGuardGate.reset()
        directConversationSurfaceWindowId = -1
        verifiedInstagramInboxWindowId = -1
        verifiedInstagramInboxListBounds = null
        profileScrollPositions.clear()
        profileScrollExit.reset()
        profileScrollCover.reset()
        profileWindowId = -1
        exitGate.reset()
        scanSchedule.clear()
        directNavigationUntil = 0L
        directNavigationSourceWindowId = -1
        lastInstagramUserInputAt = Long.MIN_VALUE
        @Suppress("DEPRECATION")
        queuedClick?.recycle()
        queuedClick = null
        mainHandler.removeCallbacks(homeScrollSettled)
        scrollCoverTop = null
        scrollCoverUntil = 0L
        instagramEntryTargets.clear()
        lastDirectConversationGuards.clear()
        cancelInstagramExit()
        mainHandler.removeCallbacks(instagramViewerExitRetry)
        mainHandler.removeCallbacks(instagramScan)
        mainHandler.removeCallbacks(instagramResumeRescan)
        mainHandler.removeCallbacks(overlayForegroundCheck)
        overlayForegroundCheckScheduled = false
    }

    private fun suspendInstagramProtection() {
        instagramOverlay.visibleCurtain()?.let { (screen, bounds) ->
            lastVerifiedInstagramCurtain = screen to Rect(bounds)
        }
        resetInstagramProtectionState()
        instagramStateMachine.leaveInstagram()
        // Samsung scales an attached accessibility window together with the
        // Recents task card. Remove the window entirely; resume recreates it
        // from the last verified application-space rectangle.
        instagramOverlay.hideAll()
    }

    private fun hideInstagramProtection() {
        resetInstagramProtectionState()
        instagramStateMachine.reset()
        lastInstagramScreen = InstagramScreen.UNKNOWN
        lastVerifiedInstagramCurtain = null
        instagramOverlay.hideAll()
    }

    private fun isPackageInForeground(packageName: String): Boolean =
        rootInActiveWindow?.packageName?.toString() == packageName

    private fun instagramWindowBounds(root: AccessibilityNodeInfo): Rect {
        val bounds = Rect()
        root.window?.getBoundsInScreen(bounds)
        if (bounds.isEmpty) root.getBoundsInScreen(bounds)
        return bounds.takeUnless { it.isEmpty } ?: currentDisplayBounds()
    }

    private fun currentDisplayBounds(): Rect {
        val windowManager = getSystemService(WindowManager::class.java)
        if (windowManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                return Rect(windowManager.maximumWindowMetrics.bounds)
            }
            @Suppress("DEPRECATION")
            val realMetrics = DisplayMetrics().also(windowManager.defaultDisplay::getRealMetrics)
            return Rect(0, 0, realMetrics.widthPixels, realMetrics.heightPixels)
        }
        val fallback = resources.displayMetrics
        return Rect(0, 0, fallback.widthPixels, fallback.heightPixels)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    @SuppressLint("InternalInsetResource", "DiscouragedApi")
    private fun statusBarInset(): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowManager = getSystemService(WindowManager::class.java)
            if (windowManager != null) {
                return windowManager.currentWindowMetrics.windowInsets
                    .getInsetsIgnoringVisibility(WindowInsets.Type.statusBars())
                    .top
                    .coerceAtLeast(0)
            }
        }
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id != 0) resources.getDimensionPixelSize(id) else dp(24)
    }

    private fun compactId(resourceId: String?): String =
        resourceId.orEmpty().substringAfterLast('/').lowercase(Locale.US)

    private fun tabForId(id: String): InstagramTab? = when (id) {
        "feed_tab" -> InstagramTab.HOME
        "clips_tab" -> InstagramTab.REELS
        "direct_tab" -> InstagramTab.DIRECT
        "search_tab" -> InstagramTab.SEARCH
        "profile_tab" -> InstagramTab.PROFILE
        else -> null
    }

    private fun tabForLabel(label: String?): InstagramTab? {
        val normalized = label.orEmpty()
            .substringBefore(',')
            .trim()
            .lowercase(Locale.US)
        return when (normalized) {
            "home", "ana sayfa" -> InstagramTab.HOME
            "reels" -> InstagramTab.REELS
            "messages", "mesajlar", "direct" -> InstagramTab.DIRECT
            "search and explore", "ara ve keşfet", "search", "ara" -> InstagramTab.SEARCH
            "profile", "profil" -> InstagramTab.PROFILE
            else -> null
        }
    }

    private fun isEnglish(): Boolean {
        val prefs = getSharedPreferences(ProtectionPreferences.PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString("app_language", null) == "en"
    }

    private var serviceShutdownNotified = false

    override fun onUnbind(intent: Intent?): Boolean {
        hideInstagramProtection()
        AccessibilityStreakPolicy.onServiceDisconnected(this)
        notifyProtectionDisabled()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        hideInstagramProtection()
        AccessibilityStreakPolicy.onServiceDisconnected(this)
        mainHandler.removeCallbacksAndMessages(null)
        notifyProtectionDisabled()
        super.onDestroy()
    }

    private fun notifyProtectionDisabled() {
        if (serviceShutdownNotified) return
        serviceShutdownNotified = true
        val isEn = isEnglish()
        NotificationHelper.showShieldStatusNotification(
            this,
            if (isEn) "Protection is not running" else "Koruma çalışmıyor",
            if (isEn) "Protection is disabled. If not re-enabled, your daily streak will reset in 24 hours."
            else "Koruma devre dışı bırakıldı. Eğer açmazsanız mevcut seriniz 24 saat sonra sıfırlanacak.",
            1002
        )
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted")
    }


}
