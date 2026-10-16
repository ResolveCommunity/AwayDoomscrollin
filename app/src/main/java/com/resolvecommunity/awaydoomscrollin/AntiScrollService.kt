package com.resolvecommunity.awaydoomscrollin

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.util.DisplayMetrics
import android.view.WindowManager
import android.view.WindowInsets
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
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
        private const val YOUTUBE_SOURCE_PATH_DEPTH = 24
        private const val INSTAGRAM_EXIT_TREE_QUIET_MS = 220L
        private const val INSTAGRAM_VIEWER_EXIT_RETRY_MS = 500L
        private const val DIRECT_MODAL_COVER_MAX_MS = 1_500L
        private const val PRECOVER_DRAW_HOLD_MS = 150L
        private const val INSTAGRAM_STORY_LAUNCH_WINDOW_MS =1_500L
        private const val INSTAGRAM_NOTIFICATION_MAX_BACKS = 4
        private const val INSTAGRAM_NOTIFICATION_COMMENT_GRACE_MS = 700L
        private const val PROFILE_ALLOWED_DEPARTURE_QUIET_MS = 450L
        private const val DIRECT_DETAILS_LAUNCH_PENDING_MS = 1_200L
        private const val DIRECT_REEL_LAUNCH_PENDING_MS = 1_500L
        private const val DIRECT_DETAILS_FALLBACK_BACK_MS = 180L
        private const val DIRECT_DETAILS_GUARD_SETTLE_MS = 320L
        private const val DIRECT_REEL_INTENT_DEBOUNCE_MS = 250L
        private const val DIRECT_CONVERSATION_GUARD_HOLD_MS = 1_800L
        private const val DIRECT_CONVERSATION_ENTRY_GUARD_TIMEOUT_MS = 2_500L
        private const val DIRECT_CONVERSATION_SURFACE_GUARD_TIMEOUT_MS = 650L
        private const val DIRECT_NAVIGATION_GRACE_MS = 2_500L
        private const val DIRECT_RECOVER_COVER_MS = 500L
        private const val INSTAGRAM_RESUME_RESCAN_MS = 48L
        private const val INSTAGRAM_USER_LAUNCH_GRACE_MS = 1_200L
        private const val INSTAGRAM_PROTECTED_NAVIGATION_HOLD_MS = 2_000L
        private const val INSTAGRAM_PROTECTED_NAVIGATION_FALLBACK_MS = 160L
        private const val PROFILE_COVER_JITTER_PX = 24
        private const val PROFILE_COVER_UP_STEP_PX = 60
        // The protective body hold releases as soon as the row position stops
        // changing: two stable samples plus this quiet window. A stopped finger
        // therefore sees the exact pose again after roughly a fifth of a second.
        private const val PROFILE_FULL_COVER_SETTLE_HOLD_MS = 150L
        private const val PROFILE_FULL_COVER_MAX_HOLD_MS = 700L
        private const val YOUTUBE_SURFACE_SCAN_DELAY_MS = 24L
        private const val YOUTUBE_CONTENT_SCAN_SPACING_MS = 120L
        private const val YOUTUBE_IDLE_RESCAN_MS = 450L
        private const val YOUTUBE_CLICK_INTENT_MS = 1_500L
private const val YOUTUBE_SHORTS_TAB_CLICK_LATCH_MS = 2_000L
private const val YOUTUBE_ALLOWED_FLIP_SETTLE_MS = 200L
        private const val YOUTUBE_QUICK_IDENTITY_MAX_NODES = 80
        private const val OVERLAY_FOREGROUND_CHECK_MS = 250L
        private const val MAX_TREE_NODES = 240
        private const val PROFILE_ENSURE_MAX_TICKS = 100
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    // Instagram can block the accessibility service's main looper for several
    // seconds while it rebuilds a notification/comment tree.  The second Back
    // for a verified notification comment viewer must not wait behind that
    // scan, so keep a tiny independent queue for this one-shot action.
    private val instagramFastExitThread = HandlerThread("InstagramFastExit").apply { start() }
    private val instagramFastExitHandler = Handler(instagramFastExitThread.looper)
    private var instagramFastExitDeadlineAt = 0L
    private var instagramFastExitGeneration = 0L
    private val instagramFastNotificationBack: Runnable = Runnable {
        if (!instagramExitInProgress ||
            !instagramExitFromNotifications ||
            !instagramNotificationReelExitIssued ||
            instagramFastExitGeneration != instagramNotificationExitGeneration) return@Runnable
        // The main-looper verifier may already have issued the one permitted
        // fallback click. Never race it with another click from this worker;
        // a second accepted action can leave Instagram's notification stack.
        if (instagramNotificationReelSecondBackIssued) return@Runnable
        if (!ProtectionPreferences.isEnabled(this, ProtectedApp.INSTAGRAM)) return@Runnable
        val now = SystemClock.uptimeMillis()
        if (instagramFastExitDeadlineAt == 0L || now >= instagramFastExitDeadlineAt) {
            return@Runnable
        }
        val activeRoot = runCatching { rootInActiveWindow }.getOrNull()
        if (activeRoot == null || activeRoot.packageName?.toString() != INSTAGRAM_PACKAGE) {
            if (now < instagramFastExitDeadlineAt) {
                requeueFastNotificationBack()
            } else {
                // No foreground proof means this retry is stale. Never send a
                // blind Back that could land in a different notification item.
                mainHandler.post { cancelInstagramExit() }
            }
            return@Runnable
        }
        // Prefer the freshly published Instagram application window over the
        // active root, which may still be a stale notification/overlay root
        // during the transition animation.
        val root = findInstagramNotificationViewerRoot(activeRoot)?.first
        if (root == null) {
            if (hasFastNotificationsSurface(activeRoot)) {
                // Only cancel after probing every Instagram window. The
                // notification list can remain the active root while the
                // blocked viewer lives in a separate application window.
                mainHandler.post { cancelInstagramExit() }
            } else if (now < instagramFastExitDeadlineAt) {
                requeueFastNotificationBack()
            } else {
                mainHandler.post { cancelInstagramExit() }
            }
            return@Runnable
        }
        val commentStillVisible = hasFastCommentSheetInRoot(root)
        val reelStillVisible = hasFastReelsViewer(root, directLaunchPending = true)
        if (commentStillVisible && now < instagramFastExitDeadlineAt) {
            requeueFastNotificationBack()
            return@Runnable
        }
        if (!commentStillVisible && !reelStillVisible) {
            if (now < instagramFastExitDeadlineAt) requeueFastNotificationBack()
            else mainHandler.post { cancelInstagramExit() }
            return@Runnable
        }
        val clickedViewerBack = performViewerBackFromRoot(root)
        val accepted = clickedViewerBack
        if (accepted) {
            instagramNotificationBackCount++
            instagramNotificationReelSecondBackIssued = true
            Log.d(TAG, "IG_NOTIFICATION_REEL_FAST_RETRY_BACK " +
                "count=$instagramNotificationBackCount source=worker " +
                "viewerBack=$clickedViewerBack")
            // An accepted accessibility action is not proof of navigation.
            // Keep the bounded worker alive until the Notifications surface is
            // actually observed, or until its short deadline expires.
            // One fresh-window targeted click is enough. Do not click the same
            // top-left control again while Instagram is publishing a stale
            // viewer root; a second click can leave Notifications too.
            mainHandler.post { queueInstagramScan(0L) }
        } else if (now < instagramFastExitDeadlineAt) {
            // The Reel root may still be settling. Retry only the targeted
            // Instagram control; never fall back to a global Back that could
            // leave Instagram entirely.
            requeueFastNotificationBack()
        }
    }
    private var lastTikTokExitTime = 0L
    private var lastTikTokTopTabCheckMs = 0L
    private val youtubeShortsSession = YouTubeShortsSession()
    private val youtubeBlockedAttempt = BlockedAttemptCounter()
    private val instagramBlockedAttempt = BlockedAttemptCounter()
    private val instagramProtectedNavigation = InstagramProtectedNavigationLatch(
        holdMs = INSTAGRAM_PROTECTED_NAVIGATION_HOLD_MS)
    private val youtubeCurtainState = YouTubeCurtainState()
    private var youtubeObservedScreen = YouTubeCurtainScreen.UNKNOWN
    private var youtubeLastVerifiedScreen = YouTubeCurtainScreen.UNKNOWN
    private var youtubeAllowedFlipSince = 0L
    private var lastYouTubeCurtainStateSignature = ""
    private var lastYouTubeSignalsSignature = ""
    private var youtubeNotificationChildOpen = false
    private var youtubeTabGestureDestination: YouTubeNavigationDestination? = null
    private var youtubeTabGestureGeneration: Long? = null
    private var youtubeLastDispatchedTab = YouTubeNavigationDestination.UNKNOWN
    private var youtubeLastDispatchedTabAt = 0L
    private var youtubeStrictShortsExitAt = 0L
    private var youtubeLastTrace = ""
    private var youtubeOverlayForegroundCheckScheduled = false
    private var youtubeOverlayForegroundCheckAt = 0L
    private var youtubeAllowedReturnSurface: YouTubeProtectedSurface? = null
    private var youtubeAllowedReturnBounds: Rect? = null
    private var youtubeLastStableDestination = YouTubeNavigationDestination.UNKNOWN
    private var youtubeSurfaceScanScheduled = false
    private var youtubeSurfaceScanAt = 0L
    private var lastPackage = ""
    private var instagramSessionStartedAt = 0L
    private var lastInstagramScreen = InstagramScreen.UNKNOWN
    private val instagramStateMachine = InstagramProtectionStateMachine()
    private val instagramBalancedProfilePost = InstagramBalancedProfilePostSession()
    private var instagramBalancedProfileReelIntentWindowId = -1
    private var instagramBalancedProfileReelIntentUntil = 0L
    private var instagramBalancedCollectionReelIntentWindowId = -1
    private var instagramBalancedCollectionReelIntentUntil = 0L
    private var instagramBalancedReelOrigin = InstagramScreen.UNKNOWN
    private var instagramCollectionFeedOrigin: InstagramScreen? = null
    private var lastCollectionReturnProbeAt = 0L
    private var precoverDrawPendingUntil = 0L

    /**
     * A precover's window draws on this service's main thread. A full scan
     * started right after it occupied that thread for ~650 ms on device and
     * the curtain reached the screen only afterwards. Scans wait for the
     * curtain's first frame (bounded), then verify the destination.
     */
    /**
     * Balanced Saved opened from Explore/Home returns to that feed on Back.
     * Four exact lookups prove the feed is back; the full classifier needs
     * ~30 and took 0.5-0.8 s while Instagram animated the return.
     */
    private fun precoverCollectionReturn(knownRoot: AccessibilityNodeInfo? = null): Boolean {
        val origin = instagramCollectionFeedOrigin ?: return false
        if (lastInstagramScreen != InstagramScreen.SAVED_COLLECTION ||
            instagramOverlay.visibleCurtain() != null) return false
        val now = SystemClock.uptimeMillis()
        if (knownRoot == null && now - lastCollectionReturnProbeAt < 60L) return false
        lastCollectionReturnProbeAt = now
        val feedRoot = knownRoot ?: rootInActiveWindow ?: return false
        if (feedRoot.packageName?.toString() != INSTAGRAM_PACKAGE ||
            !matchesRetainedInstagramFeed(feedRoot, origin)) return false
        clearInstagramResumeProtection()
        currentInstagramWindowId = feedRoot.windowId
        val window = instagramWindowBounds(feedRoot)
        val bounds = if (origin == InstagramScreen.EXPLORE) {
            calculateExploreBounds(feedRoot, window)
        } else calculateHomeFeedBounds(feedRoot, window)
        clampCurtainBottomToNavigation(feedRoot, bounds)
        if (!showInstagramCurtain(bounds, origin)) return false
        instagramCollectionFeedOrigin = null
        if (ProtectionFlightRecorder.ENABLED) ProtectionFlightRecorder.record(
            this, "instagram.collection.back", "precover=$origin")
        scanAfterPrecoverDraw()
        return true
    }

    private fun scanAfterPrecoverDraw() {
        precoverDrawPendingUntil = SystemClock.uptimeMillis() + PRECOVER_DRAW_HOLD_MS
        instagramOverlay.afterCurtainDraw {
            precoverDrawPendingUntil = 0L
            queueInstagramScan(0L)
        }
        queueInstagramScan(PRECOVER_DRAW_HOLD_MS)
    }
    private var instagramBalancedNotificationPostWatching = false
    private val instagramReelsSession = InstagramReelsSession()
    private val instagramStoryExitReadiness = InstagramStoryExitReadiness()
    private val instagramReelsPagerMotion = InstagramReelsPagerMotion()
    private var instagramReelsMotionGeneration = -1L
    private var instagramReelsVerifiedAt = Long.MAX_VALUE
    private val instagramNotificationReturn = InstagramNotificationReturnState()
    private val instagramProfileReturn = InstagramProfileReturnState()
    private val instagramNotificationMotion = InstagramNotificationMotionGate()
    private val instagramNotificationIngress = InstagramNotificationIngress()
    private var overlayForegroundCheckScheduled = false
    private var instagramExitInProgress = false
    private var instagramExitFromDirectConversation = false
    private var instagramExitFromNotifications = false
    private var instagramNotificationBackCount = 0
    private var instagramNotificationExitHoldUntil = 0L
    private var instagramNotificationReelExitIssued = false
    private var instagramNotificationReelSecondBackIssued = false
    private var instagramNotificationCommentLayerObserved = false
    private var instagramNotificationCommentNeedsSecondBack = false
    private var instagramNotificationCommentWaitStartedAt = 0L
    private var instagramNotificationLastEventExitAt = 0L
    private var instagramNotificationEventLocked = false
    private var instagramNotificationExitGeneration = 0L
    private var instagramNotificationGestureReplayUntil = 0L
    private var instagramExitTreeQuietUntil = 0L
    private var instagramDetailExitChecksRemaining = 0
    private val instagramViewerExitRetry: Runnable = Runnable {
        if (!instagramExitInProgress) return@Runnable
        if (!ProtectionPreferences.isEnabled(this, ProtectedApp.INSTAGRAM)) {
            cancelInstagramExit()
            return@Runnable
        }
        // Notification-origin viewers have one owner for navigation: the
        // fresh-window worker above. The main-looper retry may only trigger a
        // scan, never a second Back, otherwise delayed accessibility trees can
        // race the worker and escape the notification list.
        if (instagramExitFromNotifications) {
            queueInstagramScan(0L)
            return@Runnable
        }

        // When the notification opened a Reel with a comment sheet, the first
        // Back is known to dismiss that sheet. Do not wait for a fresh
        // accessibility root (which can block for several seconds while
        // Instagram animates); send the second Back from the verified state
        // transition itself. Direct notification Reels do not take this path.
        if (instagramExitFromNotifications &&
            instagramNotificationCommentLayerObserved &&
            instagramNotificationReelExitIssued &&
            !instagramNotificationReelSecondBackIssued &&
            instagramStateMachine.currentScreen == InstagramScreen.REELS) {
            val accepted = rootInActiveWindow
                ?.takeIf { it.packageName?.toString() == INSTAGRAM_PACKAGE }
                ?.let(::performViewerBackFromRoot) == true
            if (accepted) {
                instagramNotificationBackCount++
                instagramNotificationReelSecondBackIssued = true
                Log.d(TAG, "IG_NOTIFICATION_REEL_FAST_RETRY_BACK " +
                    "count=$instagramNotificationBackCount")
            }
            scheduleFastNotificationSecondBack(initialDelayMs = 120L)
            return@Runnable
        }
        val root = rootInActiveWindow
        if (root == null || root.packageName?.toString() != INSTAGRAM_PACKAGE) {
            cancelInstagramExit()
            return@Runnable
        }

        if (instagramExitFromNotifications && instagramNotificationCommentNeedsSecondBack) {
            // A liked-comment notification opens a comment sheet over the
            // blocked Reel. The first Back only dismisses that sheet; once it
            // is gone, send exactly one more Back to close the Reel itself.
            val commentSheetStillVisible = hasFastCommentSheetInRoot(root)
            val reelStillVisible = hasFastReelsViewer(root, directLaunchPending = true)
            val postStillVisible = hasFastSinglePostViewer(root)
            val commentWaitElapsed = instagramNotificationCommentWaitStartedAt
                .takeIf { it > 0L }
                ?.let { SystemClock.uptimeMillis() - it } ?: 0L
            if (hasFastNotificationsSurface(root)) {
                instagramNotificationCommentNeedsSecondBack = false
                cancelInstagramExit()
            } else if (!commentSheetStillVisible ||
                commentWaitElapsed >= INSTAGRAM_NOTIFICATION_COMMENT_GRACE_MS) {
                instagramNotificationCommentNeedsSecondBack = false
                if (reelStillVisible) {
                    instagramNotificationReelExitIssued = false
                    instagramNotificationReelSecondBackIssued = true
                }
                Log.d(TAG, "IG_NOTIFICATION_COMMENT_SECOND_BACK " +
                    "sheetVisible=$commentSheetStillVisible waitMs=$commentWaitElapsed")
                exitBlockedViewer(
                    root,
                    instagramWindowBounds(root),
                    if (reelStillVisible || postStillVisible) {
                        if (reelStillVisible) InstagramScreen.REELS
                        else InstagramScreen.COMMENTS_OR_DETAIL
                    } else InstagramScreen.COMMENTS_OR_DETAIL,
                    useGlobalBackOnly = false
                )
            } else {
                Log.d(TAG, "IG_NOTIFICATION_COMMENT_WAIT " +
                    "sheetVisible=true waitMs=$commentWaitElapsed")
                scheduleInstagramViewerExitRetry(120L)
            }
            return@Runnable
        }
        if (instagramExitFromNotifications && instagramNotificationReelExitIssued) {
            if (instagramNotificationReelSecondBackIssued) {
                // The independent fast path owns retries, but an accepted
                // action is not proof that Instagram navigated. Let its
                // bounded worker verify the Notifications surface.
                val viewerStillVisible = findInstagramNotificationViewerRoot(root) != null
                if (!viewerStillVisible && hasFastNotificationsSurface(root)) {
                    cancelInstagramExit()
                } else if (SystemClock.uptimeMillis() < instagramNotificationExitHoldUntil) {
                    scheduleInstagramViewerExitRetry(220L)
                } else {
                    cancelInstagramExit()
                }
                return@Runnable
            }
            // The notification list can remain the active root while the
            // opened Reel/comment viewer is exposed through another Instagram
            // accessibility window. Resolve the viewer window first; treating
            // the active Notifications root as proof of return here would
            // cancel the exit while the video is still on screen.
            val notificationViewerRoot = findInstagramNotificationViewerRoot(root)?.first
            if (notificationViewerRoot == null && hasFastNotificationsSurface(root)) {
                if (SystemClock.uptimeMillis() < instagramNotificationExitHoldUntil) {
                    scheduleInstagramViewerExitRetry(120L)
                } else {
                    cancelInstagramExit()
                }
                return@Runnable
            }
            val viewerRoot = notificationViewerRoot ?: root
            // The first Back can dismiss only the comment layer while the Reel
            // remains visible. Send one bounded second Back only when the
            // current foreground tree still proves that Reel is on screen.
            if (hasFastNotificationsSurface(viewerRoot) && notificationViewerRoot == null) {
                val attemptElapsed = instagramNotificationAttemptStartedAt
                    .takeIf { it > 0L }
                    ?.let { SystemClock.uptimeMillis() - it }
                Log.d(TAG, "IG_NOTIFICATION_RETURN elapsedMs=$attemptElapsed " +
                    "backCount=$instagramNotificationBackCount")
                cancelInstagramExit()
                observeInstagramScreen(InstagramScreen.NOTIFICATIONS, root.windowId)
                refreshInstagramNotificationBackGuard(root, instagramWindowBounds(root))
                refreshInstagramNotificationEntryGuards()
                queueInstagramScan(0L)
                return@Runnable
            }
            if (!hasFastReelsViewer(viewerRoot, directLaunchPending = true) &&
                !hasFastCommentSheetInRoot(viewerRoot) &&
                instagramStateMachine.currentScreen != InstagramScreen.REELS) {
                // Instagram can briefly publish an incomplete accessibility
                // tree while the comment layer is settling. Keep the bounded
                // notification exit alive when the state machine still proves
                // that the Reel viewer is the active surface; cancel only once
                // the viewer is actually gone or Notifications is visible.
                cancelInstagramExit()
                return@Runnable
            }
            if (!instagramNotificationReelSecondBackIssued) {
                instagramNotificationReelSecondBackIssued = true
            }
            if (instagramNotificationBackCount < INSTAGRAM_NOTIFICATION_MAX_BACKS) {
                val accepted = performViewerBackFromRoot(viewerRoot)
                if (accepted) {
                    instagramNotificationBackCount++
                    instagramExitTreeQuietUntil = SystemClock.uptimeMillis() +
                        INSTAGRAM_EXIT_TREE_QUIET_MS
                }
                val attemptElapsed = instagramNotificationAttemptStartedAt
                    .takeIf { it > 0L }
                    ?.let { SystemClock.uptimeMillis() - it }
                Log.d(TAG, "IG_NOTIFICATION_REEL_RETRY_BACK count=$instagramNotificationBackCount " +
                    "accepted=$accepted elapsedMs=$attemptElapsed")
            } else {
                cancelInstagramExit()
                return@Runnable
            }
            scheduleInstagramViewerExitRetry(220L)
            return@Runnable
        }
        if (instagramExitFromNotifications &&
            instagramNotificationBackCount < INSTAGRAM_NOTIFICATION_MAX_BACKS) {
            if (instagramStateMachine.currentScreen != InstagramScreen.NOTIFICATIONS &&
                !hasFastNotificationsSurface(root)) {
                val accepted = performViewerBackFromRoot(root)
                if (accepted) {
                    instagramNotificationBackCount++
                    instagramExitTreeQuietUntil = SystemClock.uptimeMillis() +
                        INSTAGRAM_EXIT_TREE_QUIET_MS
                    Log.d(TAG, "IG_NOTIFICATION_FAST_BACK count=$instagramNotificationBackCount")
                }
                scheduleInstagramViewerExitRetry(120L)
                return@Runnable
            }
            // After the first Back, Instagram can keep the underlying Reel in
            // a second accessibility window while the notification list stays
            // focused. Use the same all-window probe as the initial launch so
            // the second Back does not wait for focus to move back.
            findInstagramNotificationViewerRoot(root)?.let { (viewerRoot, viewerScreen) ->
                exitBlockedViewer(
                    viewerRoot,
                    instagramWindowBounds(viewerRoot),
                    viewerScreen,
                    useGlobalBackOnly = false)
                return@Runnable
            }
        }

        if (instagramStateMachine.currentScreen == InstagramScreen.COMMENTS_OR_DETAIL &&
            instagramDetailExitChecksRemaining > 0) {
            instagramDetailExitChecksRemaining--
            // A reply opens keyboard -> comments -> post layers. A successful
            // Back may remove only one. Verify the next current layer even when
            // Instagram publishes no further accessibility event.
            if (hasFastSinglePostViewer(root)) {
                exitBlockedViewer(root, instagramWindowBounds(root), InstagramScreen.COMMENTS_OR_DETAIL)
            } else if (hasFastCommentSheet(root)) {
                exitBlockedViewer(root, instagramWindowBounds(root), InstagramScreen.COMMENTS_OR_DETAIL,
                    useGlobalBackOnly = true)
            }
            if (instagramExitInProgress && instagramDetailExitChecksRemaining > 0) {
                scheduleInstagramViewerExitRetry()
            }
            return@Runnable
        }

        // Instagram can accept GLOBAL_ACTION_BACK without acting on it when the
        // main pager has just settled from DM/Home onto Reels. Retry once, but
        // only while current foreground structure still proves a Reel viewer.
        if (hasFastStoryViewer(root) && instagramStoriesBlocked()) {
            exitBlockedViewer(root, instagramWindowBounds(root), InstagramScreen.STORY, useGlobalBackOnly = true)
        } else if (hasFastMainReelsTabDestination(root) ||
            hasFastReelsViewer(root) || hasFastReelPreview(root) ||
            (instagramBalancedReelOrigin == InstagramScreen.SAVED_COLLECTION &&
                hasFastSavedCollectionReelList(root))) {
            Log.d(TAG, "IG_VIEWER_EXIT_RETRY stillVisible=true")
            exitBlockedViewer(root, instagramWindowBounds(root), InstagramScreen.REELS)
        } else if (instagramExitFromNotifications &&
            instagramNotificationBackCount < INSTAGRAM_NOTIFICATION_MAX_BACKS) {
            // Notification comments may reveal the underlying Reel only after
            // the first Back settles. Keep checking briefly so the second Back
            // is sent as soon as that layer appears, without a long dwell.
            scheduleInstagramViewerExitRetry(120L)
        } else {
            cancelInstagramExit()
        }
    }
    private var profileAllowedDepartureUntil = 0L
    private var directDetailsLaunchPendingUntil = 0L
    private var directDetailsFallbackBackAt = 0L
    private var directReelLaunchPendingUntil = 0L
    private var directReelIntentDebounceUntil = 0L
    private var instagramReelsTabTapUntil = 0L
    private var youtubeShortsTabClickUntil = 0L
    private var reelsSlipCoverUntil = 0L
    private var reelsSlipCoverExit = false
    private var lastReelsTabGuardBounds: Rect? = null
    private var reelsTabGuardHoldUntil = 0L
    private var lastReelsTabGuardLogState: String? = null
    private var directConversationGuardHoldUntil = 0L
    private var directDetailsGuardHoldUntil = 0L
    private val instagramResumeGeometryGate = InstagramResumeGeometryGate()
    private var instagramResumePending = false
    private var currentInstagramWindowId = -1
    private var instagramNotificationShadeWindowId = -1
    // A notification row removes its list markers before Instagram publishes
    // the opened post/Reel. Keep that short hand-off native so the old Home
    // curtain cannot flash over it.
    private var instagramNotificationContentLaunchUntil = 0L
    private var instagramNotificationAttemptStartedAt = 0L
    private var instagramNotificationViewerDetectedAt = 0L
    private var lastVerifiedInstagramCurtain: Pair<InstagramScreen, Rect>? = null
    private var directDetailsInteractionBounds: Rect? = null
    private var lastInstagramScanTime = 0L
    private var lastInboxProbeLogAt = 0L
    private var instagramFeedSnapshotYields = 0
    private var instagramAlternateFeedActive = false
    private var lastInstagramUserInputAt = Long.MIN_VALUE
    private val instagramOverlay by lazy {
        InstagramFeedOverlayController(
            this,
            ::onInstagramOverlayTouched,
            onProfileSwipeUp = {
                // Strict owns the whole profile body. Consuming a swipe must
                // not synthesize Back or let the moving grid escape the fixed
                // curtain; Balanced has no profile curtain at all.
                if (lastInstagramScreen == InstagramScreen.PROFILE &&
                    instagramProfilePostsBlocked()) queueInstagramScan(0L)
            },
            onProtectedNavigation = ::onInstagramProtectedNavigation,
            onEntryGuardVerticalSwipe = ::onInstagramEntryGuardVerticalSwipe,
            onEntryGuardGesture = ::onInstagramEntryGuardGesture,
            onStoryEntrySwipe = ::onInstagramStoryEntrySwipe,
            onProfileSwipeDown = { _, _ -> expandStrictProfileHeader() },
            entryGuardReplaysLongPress = {
                lastInstagramScreen == InstagramScreen.DIRECT_MESSAGES &&
                    InstagramDmInteractionPolicy.blocksSharedMedia(
                        ProtectionPreferences.getMode(this, ProtectedApp.INSTAGRAM))
            }
        )
    }
    private val youtubeOverlay by lazy {
        YouTubeProtectionOverlayController(
            service = this,
            onShortsBlocked = ::onYouTubeShortsGuardBlocked,
            onProtectedNavigation = ::onYouTubeProtectedNavigation,
            onAllowedBackNavigation = ::onYouTubeAllowedBackNavigation,
            onAllowedYouNavigation = ::onYouTubeAllowedYouNavigation,
            onAllowedBackNavigationDown = ::onYouTubeAllowedBackNavigationDown,
            onAllowedLaunchNavigation = ::onYouTubeAllowedToolbarNavigation,
            onCurtainVisibilityChanged = ::measureYouTubeCurtainVisibility
        )
    }
    private fun measureYouTubeCurtainVisibility(visible: Boolean) {
        val interactive = getSystemService(android.os.PowerManager::class.java)?.isInteractive == true
        YouTubeProtectionMetrics.onVisibilityChanged(this, visible && interactive)
    }
    private fun onYouTubeShortsGuardBlocked() {
        // The guard consumed one completed native-button tap; no viewer exit
        // follows. Each distinct user attempt counts as one intervention.
        recordIntervention(YOUTUBE_PACKAGE, System.currentTimeMillis())
    }
    private fun recordYouTubeViewerIntervention(accepted: Boolean) {
        if (youtubeBlockedAttempt.acceptedIntervention(accepted)) {
            recordIntervention(YOUTUBE_PACKAGE, System.currentTimeMillis())
        }
    }
    private val youtubeSurfaceScan = Runnable {
        youtubeSurfaceScanScheduled = false
        youtubeSurfaceScanAt = 0L
        runYouTubeScan("event")
    }
    private var youtubeLastScanFinishedAt = 0L
    private var youtubeLastClickAt = 0L
    private var youtubeLastSelectedDestination = YouTubeNavigationDestination.UNKNOWN

    private fun runYouTubeScan(source: String) {
        val started = SystemClock.uptimeMillis()
        try {
            currentYouTubeRoot()?.let(::refreshYouTubeSurfaceProtection)
        } finally {
            clearNodeLookupCache()
            youtubeLastScanFinishedAt = SystemClock.uptimeMillis()
            val elapsed = youtubeLastScanFinishedAt - started
            if (ProtectionFlightRecorder.ENABLED && elapsed >= 40L) {
                ProtectionFlightRecorder.record(this, "youtube.scan",
                    "elapsedMs=$elapsed source=$source screen=$youtubeObservedScreen")
            }
        }
    }
    private fun onYouTubeAllowedBackNavigationDown() {
        val surface = youtubeAllowedReturnSurface ?: return
        if (youtubeCurtainState.pendingDestination == surface.curtainScreen()) return
        youtubeCurtainState.request(surface.curtainScreen(), SystemClock.uptimeMillis())
        youtubeOverlay.showCurtain(
            youtubeAllowedReturnBounds, surface, isEnglish(), preserveBounds = true
        )
        traceYouTubeProtection("back-down")
        scheduleYouTubeOverlayForegroundCheck()
    }

    private fun onYouTubeAllowedToolbarNavigation(target: YouTubeCurtainScreen) {
        val root = currentYouTubeRoot() ?: return
        if (focusedNonOverlayPackage() != YOUTUBE_PACKAGE) return
        val window = Rect().also(root::getBoundsInScreen)
        val toolbar = collectYouTubeToolbar(root, window)
        val launch = toolbar.launchNodes[target] ?: return
        youtubeAllowedReturnSurface = youtubeCurtainState.currentSurface
        youtubeCurtainState.request(target, SystemClock.uptimeMillis())
        toolbar.bounds?.let { bounds ->
            if (youtubeAllowedReturnSurface != null) {
                youtubeOverlay.updateAllowedBackGuard(
                    Rect(window.left, bounds.top, window.left + dp(56), bounds.bottom), isEnglish()
                )
            }
        }
        youtubeOverlay.releaseAllowedLaunchGuard(target)
        launch.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        traceYouTubeProtection("allowed-launch")
        queueYouTubeSurfaceScan(0L)
        scheduleYouTubeOverlayForegroundCheck()
    }

    private fun onYouTubeAllowedBackNavigation() {
        onYouTubeAllowedBackNavigationDown()
        youtubeOverlay.releaseAllowedBackGuard()
        val token = youtubeCurtainState.generation
        youtubeOverlay.afterCurtainDraw {
            if (youtubeCurtainState.isCurrentGeneration(token)) dispatchYouTubeAllowedBack(token)
        }
    }

    private fun dispatchYouTubeAllowedBack(token: Long) {
        val root = currentYouTubeRoot() ?: return
        val accepted = findYouTubeBackNavigationNode(root)
            ?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
        if (!accepted) performGlobalAction(GLOBAL_ACTION_BACK)
        mainHandler.postDelayed({
            if (youtubeCurtainState.isCurrentGeneration(token) &&
                youtubeCurtainState.pendingDestination in setOf(
                    YouTubeCurtainScreen.HOME, YouTubeCurtainScreen.SUBSCRIPTIONS
                )
            ) {
                // A Back control clicked during entry animation can accept ACTION_CLICK
                // without navigating. Retry only a freshly verified, stationary Notifications
                // page; never an outgoing frame, Search/IME, or a later navigation generation.
                currentYouTubeRoot()?.let { live ->
                    if (collectYouTubeCurtainSignals(live).screen == YouTubeCurtainScreen.NOTIFICATIONS) {
                        performGlobalAction(GLOBAL_ACTION_BACK)
                        queueYouTubeSurfaceScan(0L)
                    }
                }
            }
        }, 450L)
        queueYouTubeSurfaceScan(0L)
        scheduleYouTubeOverlayForegroundCheck()
    }

    private fun YouTubeProtectedSurface.curtainScreen(): YouTubeCurtainScreen =
        when (this) {
            YouTubeProtectedSurface.HOME -> YouTubeCurtainScreen.HOME
            YouTubeProtectedSurface.SUBSCRIPTIONS -> YouTubeCurtainScreen.SUBSCRIPTIONS
        }

    private fun onYouTubeProtectedNavigation(surface: YouTubeProtectedSurface) {
        navigateYouTubeTab(
            if (surface == YouTubeProtectedSurface.HOME) YouTubeNavigationDestination.HOME
            else YouTubeNavigationDestination.SUBSCRIPTIONS
        )
    }

    private fun onYouTubeAllowedYouNavigation() {
        navigateYouTubeTab(YouTubeNavigationDestination.YOU)
    }

    private fun navigateYouTubeTab(destination: YouTubeNavigationDestination) {
        if (!ProtectionPreferences.isEnabled(this, ProtectedApp.YOUTUBE)) return
        if (currentYouTubeRoot() == null) return
        val now = SystemClock.uptimeMillis()
        val token = youtubeCurtainState.request(destination.curtainScreen(), now)
        youtubeAllowedReturnSurface = null
        youtubeTabGestureDestination = null
        youtubeTabGestureGeneration = null
        val surface = youtubeCurtainState.currentSurface
        val curtainAlreadyVisible = youtubeOverlay.isCurtainVisible
        youtubeOverlay.showCurtain(null, surface, isEnglish(), preserveBounds = true)
        traceYouTubeProtection("tab-armed")
        // addView/state publication is not a rendered opaque frame. Do not let YouTube
        // start its feed animation until that frame has actually passed pre-draw.
        if (!curtainAlreadyVisible && destination in setOf(
                YouTubeNavigationDestination.HOME, YouTubeNavigationDestination.SUBSCRIPTIONS
            )) {
            youtubeOverlay.afterCurtainDraw {
                if (youtubeCurtainState.isCurrentGeneration(token)) dispatchYouTubeTabNavigation(destination, token)
            }
        } else dispatchYouTubeTabNavigation(destination, token)
    }

    private fun dispatchYouTubeTabNavigation(destination: YouTubeNavigationDestination, token: Long) {
        val root = currentYouTubeRoot() ?: return
        val targetNode = findYouTubeNavigationNode(root, destination)
        youtubeLastDispatchedTab = destination
        youtubeLastDispatchedTabAt = SystemClock.uptimeMillis()
        // Accessibility actions operate on the real tab without removing its touch guard.
        performYouTubeNavigationClick(targetNode)
        traceYouTubeProtection("tab-request")
        queueYouTubeSurfaceScan(0L)
        scheduleYouTubeOverlayForegroundCheck()
        mainHandler.postDelayed(fallback@ {
            if (!youtubeCurtainState.isCurrentGeneration(token)) return@fallback
            val pendingDestination = youtubeCurtainState.pendingDestination
            if (pendingDestination != destination.curtainScreen() &&
                !(pendingDestination == null && destination == YouTubeNavigationDestination.YOU)
            ) return@fallback
            val fresh = currentYouTubeRoot() ?: return@fallback
            // The selected tab survives behind search/results. Only skip the physical
            // fallback when the visible page is also consistent with tab navigation.
            val signals = collectYouTubeCurtainSignals(fresh)
            if (!YouTubeSurfacePolicy.shouldRetryTabNavigation(
                    destination, signals.selectedDestination, signals.screen
                )) return@fallback
            val freshNode = findYouTubeNavigationNode(fresh, destination) ?: return@fallback
            val bounds = Rect().also(freshNode::getBoundsInScreen)
            if (bounds.width() <= 0 || bounds.height() <= 0) return@fallback
            youtubeTabGestureDestination = destination
            youtubeTabGestureGeneration = token
            when (destination) {
                YouTubeNavigationDestination.HOME ->
                    youtubeOverlay.releaseProtectedNavigationGuard(YouTubeProtectedSurface.HOME)
                YouTubeNavigationDestination.SUBSCRIPTIONS ->
                    youtubeOverlay.releaseProtectedNavigationGuard(YouTubeProtectedSurface.SUBSCRIPTIONS)
                YouTubeNavigationDestination.YOU -> youtubeOverlay.releaseYouNavigationGuard()
                else -> return@fallback
            }
            // Only retry a still-pending current request, using freshly resolved geometry.
            dispatchScreenTap(bounds.centerX().toFloat(), bounds.centerY().toFloat()) {
                if (!youtubeCurtainState.isCurrentGeneration(token)) return@dispatchScreenTap
                youtubeTabGestureDestination = null
                youtubeTabGestureGeneration = null
                queueYouTubeSurfaceScan(0L)
            }
        }, 160L)
    }

    private var gestureDispatchInProgress = false

    private fun dispatchScreenTap(
        x: Float,
        y: Float,
        durationMs: Long = 35L,
        onResult: ((Boolean) -> Unit)? = null
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || gestureDispatchInProgress) {
            onResult?.invoke(false)
            return false
        }
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder().addStroke(
            GestureDescription.StrokeDescription(path, 0L, durationMs.coerceAtLeast(1L))
        ).build()
        gestureDispatchInProgress = true
        val queued = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                gestureDispatchInProgress = false
                onResult?.invoke(true)
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                gestureDispatchInProgress = false
                onResult?.invoke(false)
            }
        }, mainHandler)
        if (!queued) {
            gestureDispatchInProgress = false
            onResult?.invoke(false)
        }
        return queued
    }

    private fun dispatchScreenSwipe(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long,
        onResult: ((Boolean) -> Unit)? = null
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || gestureDispatchInProgress) {
            onResult?.invoke(false)
            return false
        }
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val gesture = GestureDescription.Builder().addStroke(
            // Instagram's message list treats very short synthetic strokes as
            // fling/page commands. Keep a quick finger drag smooth enough to
            // track the intended movement instead of skipping message rows.
            GestureDescription.StrokeDescription(path, 0L, durationMs.coerceIn(260L, 1_000L))
        ).build()
        gestureDispatchInProgress = true
        val queued = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                gestureDispatchInProgress = false
                onResult?.invoke(true)
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                gestureDispatchInProgress = false
                onResult?.invoke(false)
            }
        }, mainHandler)
        if (!queued) {
            gestureDispatchInProgress = false
            onResult?.invoke(false)
        }
        return queued
    }

    private fun performYouTubeNavigationClick(node: AccessibilityNodeInfo?): Boolean {
        var current = node
        repeat(4) {
            val candidate = current ?: return false
            if (candidate.isClickable &&
                candidate.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            ) return true
            current = candidate.parent
        }
        return false
    }

    private fun traceYouTubeProtection(reason: String) {
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        val trace = "screen=$youtubeObservedScreen target=${youtubeCurtainState.pendingDestination} " +
            "curtain=${youtubeOverlay.currentCurtainSurface} generation=${youtubeCurtainState.generation}"
        if (trace == youtubeLastTrace) return
        youtubeLastTrace = trace
        Log.d("YTProtection", "${SystemClock.uptimeMillis()} $reason $trace")
    }

    private val youtubeOverlayForegroundCheck = object : Runnable {
        override fun run() {
            youtubeOverlayForegroundCheckScheduled = false
            youtubeOverlayForegroundCheckAt = 0L
            // Numeric window visibility alone does not prove an illuminated display.
            // The existing foreground loop also pauses/resumes measurement at screen off/on.
            measureYouTubeCurtainVisibility(youtubeOverlay.isCurtainVisible)
            val foreground = focusedNonOverlayPackage()
            if (foreground != null && foreground != YOUTUBE_PACKAGE) {
                // Accessibility overlays are above the shade on Samsung. Suspend their visual
                // windows, not the navigation state/geometry, and restore the existing windows
                // as soon as YouTube regains focus. Never curtain the system notification panel.
                if (foreground == "com.android.systemui" && isYouTubeNotificationShade()) {
                    youtubeOverlay.setSystemUiSuspended(true)
                    scheduleYouTubeOverlayForegroundCheck()
                    return
                }
                resetYouTubeProtection()
                if (foreground == "com.android.systemui") {
                    scheduleYouTubeOverlayForegroundCheck()
                }
                return
            }
            if (!ProtectionPreferences.isEnabled(this@AntiScrollService, ProtectedApp.YOUTUBE)) {
                resetYouTubeProtection()
                return
            }
            youtubeOverlay.setSystemUiSuspended(false)
            // Reclassify even when only tab guards remain; a missing final event must not
            // leave a returned Home feed uncovered. The cheap focus probe above runs every
            // tick; the full classification only when no event-driven scan ran recently.
            // Uncovered while a feed tab is selected (search, notifications or a
            // watch page over Home/Subscriptions) is where a silent return to the
            // feed can happen: keep the original 200 ms cadence there.
            val feedReturnPossible = !youtubeOverlay.isCurtainVisible &&
                youtubeLastSelectedDestination in setOf(
                    YouTubeNavigationDestination.HOME, YouTubeNavigationDestination.SUBSCRIPTIONS)
            val idleRescanMs = if (feedReturnPossible) 200L else YOUTUBE_IDLE_RESCAN_MS
            if (youtubeCurtainState.isPending ||
                SystemClock.uptimeMillis() - youtubeLastScanFinishedAt >= idleRescanMs) {
                runYouTubeScan("foreground")
            }
            if (youtubeOverlay.hasVisibleWindows || youtubeCurtainState.isPending) {
                scheduleYouTubeOverlayForegroundCheck()
            }
        }
    }
    private val overlayForegroundCheck = object : Runnable {
        override fun run() {
            overlayForegroundCheckScheduled = false
            if (!instagramOverlay.hasVisibleWindows) return

            val activePkg = rootInActiveWindow?.packageName?.toString()
            if (activePkg != null && activePkg != INSTAGRAM_PACKAGE) {
                if (retainInstagramCurtainUnderNotificationShade(activePkg, rootInActiveWindow)) return
                // Window IPC above can span the shade's entire closing frame.
                // Never tear down from the pre-probe SystemUI snapshot when
                // the application has already returned during that IPC.
                val returnedRoot = rootInActiveWindow
                if (InstagramNotificationShadePolicy.returnedDuringProbe(
                        instagramOverlay.isNotificationShadeSuspended,
                        returnedRoot?.packageName?.toString())) {
                    returnedRoot?.let(::resumeInstagramNotificationShade)
                    scheduleOverlayForegroundCheck()
                    return
                }
                suspendInstagramProtection()
                trackPackageTransition("")
                return
            }
            if (!ProtectionPreferences.isEnabled(this@AntiScrollService, ProtectedApp.INSTAGRAM)) {
                hideInstagramProtection()
                return
            }

            rootInActiveWindow?.let(::resumeInstagramNotificationShade)

            scheduleOverlayForegroundCheck()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instagramStructuralNodesRequested = null
        if (!AccessibilityConsent.isAccepted(this)) {
            Log.w(TAG, "Accessibility service disabled: in-app disclosure has not been accepted")
            disableSelf()
            return
        }
        InstagramProtectionMetrics.discardInterruptedSession(this)
        YouTubeProtectionMetrics.discardInterruptedSession(this)
        AccessibilityStreakPolicy.onServiceConnected(this)
        serviceShutdownNotified = false
        NotificationHelper.cancelShieldNotification(this, 1002)
        NotificationHelper.cancelShieldNotification(this, 1003)

        val isEn = isEnglish()
        NotificationHelper.showShieldStatusNotification(
            this,
            if (isEn) "Protection active" else "Koruma etkin",
            if (isEn) {
                "AwayDoomscrollin' is protecting the feeds you selected in Instagram, TikTok and YouTube."
            } else {
                "AwayDoomscrollin' Instagram, TikTok ve YouTube'da seçtiğiniz akışları koruyor."
            },
            1001
        )
        Log.d(TAG, "Accessibility protection connected")
        if (ProtectionFlightRecorder.ENABLED) {
            @Suppress("DEPRECATION")
            val versionName = runCatching {
                packageManager.getPackageInfo(packageName, 0).versionName
            }.getOrNull().orEmpty().ifBlank { "unknown" }
            ProtectionFlightRecorder.startSession(this, versionName)
            ProtectionFlightRecorder.record(this, "service.connected",
                "root=${rootInActiveWindow?.packageName ?: "none"}")
        }

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
            val root = rootInActiveWindow ?: return@postDelayed
            when (root.packageName?.toString()) {
                INSTAGRAM_PACKAGE -> {
                    updateInstagramStructuralNodeAccess(true)
                    queueInstagramScan(0L)
                }
                YOUTUBE_PACKAGE -> queueYouTubeSurfaceScan(0L)
            }
        }, 120L)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Lookups are only valid for the root instances fetched while handling
        // this one event; never carry their snapshot nodes into later work.
        val started = SystemClock.uptimeMillis()
        try { dispatchAccessibilityEvent(event) } finally {
            clearNodeLookupCache()
            val elapsed = SystemClock.uptimeMillis() - started
            if (ProtectionFlightRecorder.ENABLED && elapsed >= 24L && event != null) {
                ProtectionFlightRecorder.record(this, "a11y.slow",
                    "type=${event.eventType} pkg=${event.packageName} elapsedMs=$elapsed")
            }
        }
    }

    private fun dispatchAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !AccessibilityConsent.isAccepted(this)) return
        maybeRecordHeartbeat()

        val reportedPackage = event.packageName?.toString()
        if (ProtectionFlightRecorder.ENABLED && reportedPackage in setOf(
                INSTAGRAM_PACKAGE, YOUTUBE_PACKAGE, TIKTOK_PACKAGE, "com.android.systemui")) {
            val traceNow = SystemClock.uptimeMillis()
            val highValueEvent = event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
                traceNow - lastFlightContentEventAt >= 80L
            if (highValueEvent) {
                if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
                    lastFlightContentEventAt = traceNow
                }
                val packageKey = when (reportedPackage) {
                    INSTAGRAM_PACKAGE -> "instagram"
                    YOUTUBE_PACKAGE -> "youtube"
                    TIKTOK_PACKAGE -> "tiktok"
                    else -> "systemui"
                }
                ProtectionFlightRecorder.record(this, "a11y.event",
                    "pkg=$packageKey type=${event.eventType} window=${event.windowId} " +
                        "ageMs=${(SystemClock.uptimeMillis() - event.eventTime).coerceAtLeast(0L)} " +
                        "lastPkg=${when (lastPackage) {
                            INSTAGRAM_PACKAGE -> "instagram"
                            YOUTUBE_PACKAGE -> "youtube"
                            TIKTOK_PACKAGE -> "tiktok"
                            else -> "other"
                        }} ig=$lastInstagramScreen yt=$youtubeObservedScreen" +
                        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
                            " class=${event.className}"
                        else "")
            }
        }
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0 &&
            reportedPackage == INSTAGRAM_PACKAGE && event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED &&
            InstagramNotificationShadePolicy.isProtectedFeedOrigin(lastInstagramScreen)) {
            Log.d(TAG, "IG_FEED_CLICK_DELIVERY window=${event.windowId} " +
                "eventAt=${event.eventTime} receivedAt=${SystemClock.uptimeMillis()}")
        }
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0 &&
            reportedPackage == INSTAGRAM_PACKAGE && instagramOverlay.visibleProfileBounds() != null &&
            event.eventType in setOf(AccessibilityEvent.TYPE_VIEW_CLICKED,
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, AccessibilityEvent.TYPE_WINDOWS_CHANGED)) {
            // No node/source lookup here: measure delivery before foreground IPC.
            Log.d(TAG, "IG_PROFILE_RETURN_EVENT type=${event.eventType} window=${event.windowId} " +
                "eventAt=${event.eventTime} receivedAt=${SystemClock.uptimeMillis()}")
        }
        // The header collapse of a profile fling publishes only content
        // changes (no scroll event) and a still profile publishes none, so
        // these run the anchor probe before any coalescing or scan below.
        if (reportedPackage == INSTAGRAM_PACKAGE &&
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            lastInstagramScreen == InstagramScreen.PROFILE) {
            kickProfileGeometryWatch(urgent = true)
        }
        // A native account-row tap can beat the search proxy during a cold
        // return or mode change. Arm the existing profile handoff before other
        // applications' window queries delay the first profile frame.
        if (reportedPackage == INSTAGRAM_PACKAGE &&
            event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED &&
            armInstagramNativeAccountEntry(event)) return
        // TYPE_WINDOWS_CHANGED for our own accessibility overlays can have the
        // Instagram root active. Never reinterpret that event as Instagram.
        if (reportedPackage == this.packageName) return
        // A mounted curtain was re-verified within one identical-window content
        // event of another: queueing another full scan for video progress ticks,
        // story timers and like-state changes costs the main thread 8+ remote
        // queries per event with no new decision. Clicks, state and window
        // events always retain normal routing.
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            SystemClock.uptimeMillis() - setCurtainVerifiedAtMs < 64L &&
            event.windowId == setCurtainVerifiedWindowId &&
            instagramOverlay.visibleCurtain() != null) return
        // Surface-left precover: search results and notifications are allowed
        // screens whose leaving transition exposes a protected feed. Their
        // markers disappear at the very start of the animation, well before
        // classification resolves, so the destination curtain is pre-mounted
        // from that first structural event (system Back gestures included).
        if (reportedPackage == INSTAGRAM_PACKAGE &&
            event.eventType in setOf(
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                AccessibilityEvent.TYPE_WINDOWS_CHANGED) &&
            !instagramProfileIngress.pending && !instagramProfileIngress.awaitingCurtainDraw &&
            instagramOverlay.visibleCurtain() == null) {
            when (lastInstagramScreen) {
                InstagramScreen.SEARCH_RESULTS -> {
                    val searchRoot = rootInActiveWindow
                    // The editor check comes first: it is visible for every
                    // event while the user stays on search, so one lookup ends
                    // the probe instead of six (~150 ms per event measured).
                    if (searchRoot != null &&
                        searchRoot.packageName?.toString() == INSTAGRAM_PACKAGE &&
                        !igVisible(searchRoot, "action_bar_search_edit_text") &&
                        !hasFastDirectSafeReturn(searchRoot) &&
                        !hasVisibleProfileHeaderMarkers(searchRoot) &&
                        !igVisible(searchRoot, "row_search_user_container") &&
                        !igVisible(searchRoot, "search_row") && !igVisible(searchRoot, "echo_text")) {
                        clearInstagramResumeProtection()
                        currentInstagramWindowId = searchRoot.windowId
                        showInstagramCurtain(
                            calculateExploreBounds(searchRoot, instagramWindowBounds(searchRoot)),
                            InstagramScreen.EXPLORE)
                        if (ProtectionFlightRecorder.ENABLED) ProtectionFlightRecorder.record(
                            this, "instagram.search.back", "precover=editor-gone")
                        scanAfterPrecoverDraw()
                    }
                }
                InstagramScreen.SAVED_COLLECTION -> {
                    // Balanced allows Saved, but Back returns to the feed whose
                    // bookmark opened it; cover that feed on its first frames.
                    precoverCollectionReturn()
                }
                InstagramScreen.NOTIFICATIONS -> {
                    // Notifications are an allowed native surface; opening a
                    // row can publish a post/Reel in another Instagram window,
                    // so nothing is pre-covered from the row tap. Leaving with
                    // Back is different: on the first event after it, the Home
                    // header is already visible in the active root while the
                    // notification list and its Back control are gone (on the
                    // notification page neither header is visible). The scan
                    // recognised Home ~550 ms later, long enough to open a
                    // Story from the uncovered tray in Strict.
                    val probeNow = SystemClock.uptimeMillis()
                    if (!instagramExitInProgress && probeNow >= instagramNotificationContentLaunchUntil) {
                        val homeRoot = rootInActiveWindow
                        if (homeRoot != null && homeRoot.packageName?.toString() == INSTAGRAM_PACKAGE &&
                            igVisible(homeRoot, "main_feed_action_bar") &&
                            !igVisible(homeRoot, "newsfeed_you") &&
                            !igVisible(homeRoot, "action_bar_button_back")) {
                            clearInstagramResumeProtection()
                            currentInstagramWindowId = homeRoot.windowId
                            val bounds = calculateHomeFeedBounds(homeRoot, instagramWindowBounds(homeRoot))
                            clampCurtainBottomToNavigation(homeRoot, bounds)
                            showInstagramCurtain(bounds, InstagramScreen.HOME_FEED)
                            if (ProtectionFlightRecorder.ENABLED) ProtectionFlightRecorder.record(
                                this, "instagram.notifications.back", "precover=home-header")
                            queueInstagramScan(0L)
                        }
                    }
                }
                else -> Unit
            }
        }
        if (instagramProfileIngress.coalesceStructuralEvent(
                instagramEvent = reportedPackage == INSTAGRAM_PACKAGE,
                sameTrackedApp = lastPackage == INSTAGRAM_PACKAGE,
                structuralEvent = event.eventType in setOf(
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                    AccessibilityEvent.TYPE_WINDOWS_CHANGED))) {
            // The account-entry owner already verified the foreground package.
            // Every scan still checks the active root/enabled setting; real
            // SystemUI/other-app events and clicks retain normal routing.
            // Re-querying all focused windows for each animation event can
            // occupy the main looper before the curtain's first draw callback.
            queueInstagramScan(if (instagramProfileIngress.awaitingCurtainDraw) 48L else 16L)
            return
        }
        val shadeReturnRoot = if (InstagramNotificationShadePolicy.useCurrentInstagramRoot(
                reportedSystemUi = reportedPackage == "com.android.systemui",
                hasInstagramSession = instagramOverlay.isNotificationShadeSuspended ||
                    lastPackage == INSTAGRAM_PACKAGE)) rootInActiveWindow else null
        val packageName = if ((event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED ||
                shadeReturnRoot != null) &&
            (shadeReturnRoot ?: rootInActiveWindow)?.packageName?.toString() == INSTAGRAM_PACKAGE) {
            INSTAGRAM_PACKAGE
        } else reportedPackage ?: return
        if (packageName == this.packageName) return
        if (retainInstagramCurtainUnderNotificationShade(packageName)) return
        if (packageName == INSTAGRAM_PACKAGE && instagramOverlay.isNotificationShadeSuspended) {
            // The resume helper is a no-op unless the shade suspended the
            // curtain; do not pay a remote root query for every event.
            rootInActiveWindow?.let(::resumeInstagramNotificationShade)
        }
        updateInstagramStructuralNodeAccess(packageName == INSTAGRAM_PACKAGE)
        val returningToInstagram = packageName == INSTAGRAM_PACKAGE && lastPackage != INSTAGRAM_PACKAGE
        val returningToYouTube = packageName == YOUTUBE_PACKAGE && lastPackage != YOUTUBE_PACKAGE
        // Suspend once when Instagram is left, not for every YouTube/TikTok
        // event: each call also queued a delayed recovery probe with a remote
        // root query, so a stream of events stacked dozens of them on the
        // main looper and delayed every other app's protection.
        if (packageName != INSTAGRAM_PACKAGE && packageName != this.packageName &&
            (lastPackage == INSTAGRAM_PACKAGE || instagramOverlay.hasVisibleWindows)) {
            suspendInstagramProtection()
        }
        // The focused-window probe is two remote calls. It only matters while
        // YouTube protection still has state to reset or the event did not
        // come from a tracked app; Instagram/TikTok events paid it every time.
        val youtubeNeedsForegroundProbe = packageName != YOUTUBE_PACKAGE &&
            (lastPackage == YOUTUBE_PACKAGE || youtubeOverlay.hasVisibleWindows ||
                youtubeOverlay.isSystemUiSuspended ||
                (packageName != INSTAGRAM_PACKAGE && packageName != TIKTOK_PACKAGE))
        val activeRootPackage = if (youtubeNeedsForegroundProbe) {
            focusedNonOverlayPackage()
                ?: rootInActiveWindow?.packageName?.toString()
        } else null
        if (packageName != YOUTUBE_PACKAGE && packageName != this.packageName &&
            activeRootPackage != YOUTUBE_PACKAGE
        ) {
            if (activeRootPackage == "com.android.systemui" && isYouTubeNotificationShade()) {
                youtubeOverlay.setSystemUiSuspended(true)
                scheduleYouTubeOverlayForegroundCheck()
            } else {
                resetYouTubeProtection()
            }
        }
        trackPackageTransition(packageName)
        if (returningToYouTube && ProtectionPreferences.isEnabled(this, ProtectedApp.YOUTUBE)) {
            scheduleYouTubeOverlayForegroundCheck()
        }
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
            else -> {
                if (activeRootPackage == YOUTUBE_PACKAGE) {
                    handleYouTube(event)
                }
            }
        }
    }

    private val scanSchedule = InstagramScanSchedule()
    private var lastHeartbeatMs = 0L
    private var lastFlightContentEventAt = 0L
    private var lastFlightScanAt = 0L
    private var lastFlightScanSignature = ""

    /**
     * One scan asks the same view id up to a dozen times across the fast
     * surface probe, geometry calculation and layer checks. Every distinct
     * query is a separate remote traversal, so results are memoized per root
     * instance for the duration of one callback chain. The key holds the root
     * reference itself: a fresh rootInActiveWindow instance is fetched for
     * every scan, so cross-scan staleness is impossible without manual clears.
     */
    /**
     * AccessibilityNodeInfo.equals() compares only the window and source node
     * ids, so every fresh rootInActiveWindow instance of the same window is
     * "equal" to the previous one. Keying the cache by that equality silently
     * reused snapshot nodes (stale visibility and bounds) across scans, event
     * handlers and foreground checks. The key therefore uses reference
     * identity of the root instance that one callback chain fetched.
     */
    private class NodeLookupKey(val root: AccessibilityNodeInfo, val id: String) {
        override fun equals(other: Any?): Boolean =
            other is NodeLookupKey && other.root === root && other.id == id
        override fun hashCode(): Int = System.identityHashCode(root) * 31 + id.hashCode()
    }
    private val nodeLookupCache = HashMap<NodeLookupKey, List<AccessibilityNodeInfo>>(64)

    private fun cachedNodeLookup(root: AccessibilityNodeInfo, id: String): List<AccessibilityNodeInfo> {
        val key = NodeLookupKey(root, id)
        nodeLookupCache[key]?.let { return it }
        val started = SystemClock.uptimeMillis()
        val result = runCatching { root.findAccessibilityNodeInfosByViewId(id) }
            .getOrNull().orEmpty()
        if (ProtectionFlightRecorder.ENABLED) {
            val elapsed = SystemClock.uptimeMillis() - started
            if (elapsed >= 12L) ProtectionFlightRecorder.record(this, "lookup.slow",
                "id=${id.substringAfter(":id/")} elapsedMs=$elapsed n=${result.size}")
        }
        if (nodeLookupCache.size >= 96) nodeLookupCache.clear()
        nodeLookupCache[key] = result
        return result
    }

    private fun clearNodeLookupCache() {
        if (nodeLookupCache.isNotEmpty()) nodeLookupCache.clear()
    }

    private fun igVisible(root: AccessibilityNodeInfo, id: String): Boolean =
        cachedNodeLookup(root, "$INSTAGRAM_PACKAGE:id/$id").any { it.isVisibleToUser }

    private fun igSelected(root: AccessibilityNodeInfo, id: String): Boolean =
        cachedNodeLookup(root, "$INSTAGRAM_PACKAGE:id/$id")
            .any { it.isVisibleToUser && isSelectedRecursive(it, 2) }

    private fun maybeRecordHeartbeat(now: Long = System.currentTimeMillis()) {
        if (now - lastHeartbeatMs >= 60_000L) {
            lastHeartbeatMs = now
            AccessibilityStreakPolicy.recordHeartbeat(this, now)
        }
    }
    private var queuedClick: AccessibilityEvent? = null
    private var directNavigationUntil = 0L
    private var directNavigationSourceWindowId = -1

    /**
     * Re-cover safety net for the fast DM release: if the messaging intent
     * never produced a verified inbox inside the recovery window, put the last
     * verified feed rectangle back on screen so a mistap cannot leave a
     * protected feed exposed while Instagram finishes its tree swap.
     */
    private val instagramDirectRecoverCover = Runnable {
        if (directNavigationSourceWindowId == -1) return@Runnable
        val root = rootInActiveWindow
        if (root == null || root.packageName?.toString() != INSTAGRAM_PACKAGE) return@Runnable
        if (SystemClock.uptimeMillis() >= directNavigationUntil) return@Runnable
        val retained = lastVerifiedInstagramCurtain
            ?.takeIf { (screen, _) ->
                InstagramSurfacePolicy.needsCurtain(screen) &&
                    instagramOverlay.visibleCurtain() == null
            }
        val homeVerified = lastInstagramScreen == InstagramScreen.HOME_FEED ||
            lastInstagramScreen == InstagramScreen.EXPLORE ||
            lastInstagramScreen == InstagramScreen.UNKNOWN
        if (retained != null && homeVerified) {
            if (ProtectionFlightRecorder.ENABLED) ProtectionFlightRecorder.record(
                this@AntiScrollService, "instagram.recover.cover",
                "screen=${retained.first} directWindowId=$directNavigationSourceWindowId")
            showInstagramCurtain(Rect(retained.second), retained.first)
        }
    }
    private var strictProfilePrecoverAtMs = 0L
    private var setCurtainVerifiedAtMs = 0L
    private var setCurtainVerifiedWindowId = -1
    private val instagramScan = Runnable {
        scanSchedule.clear()
        val precoverRemaining = precoverDrawPendingUntil - SystemClock.uptimeMillis()
        if (precoverRemaining > 0L) {
            queueInstagramScan(precoverRemaining)
            return@Runnable
        }
        val storyLaunchRemaining = instagramStoryLaunchUntil - SystemClock.uptimeMillis()
        if (storyLaunchRemaining > 0L) {
            // A Strict Story launch is starting: Instagram's UI thread answers
            // no tree query for 1-3 s and a scan would block the main looper,
            // delaying the very window event that triggers the exit.
            queueInstagramScan(minOf(120L, storyLaunchRemaining + 16L))
            return@Runnable
        }
        val click = queuedClick
        queuedClick = null
        val started = SystemClock.uptimeMillis()
        try { scanInstagram(click) } finally {
            @Suppress("DEPRECATION")
            click?.recycle()
            clearNodeLookupCache()
            val elapsed = SystemClock.uptimeMillis() - started
            if (ProtectionFlightRecorder.ENABLED) {
                val curtain = instagramOverlay.visibleCurtain()
                val signature = "$lastInstagramScreen/$currentInstagramWindowId/" +
                    "${curtain?.first ?: "none"}/${curtain?.second ?: "none"}"
                val traceNow = SystemClock.uptimeMillis()
                if (elapsed >= 24L || signature != lastFlightScanSignature ||
                    traceNow - lastFlightScanAt >= 500L) {
                    lastFlightScanAt = traceNow
                    lastFlightScanSignature = signature
                    ProtectionFlightRecorder.record(this, "instagram.scan",
                        "elapsedMs=$elapsed state=$lastInstagramScreen window=$currentInstagramWindowId " +
                            "curtain=${curtain?.first ?: "none"} bounds=${curtain?.second ?: "none"}")
                }
            }
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
            if (!instagramProfilePostsBlocked()) {
                stopProfileCurtainEnsure()
                releaseAllowedProfileSurface()
                return
            }
            val root = rootInActiveWindow
            if (root == null || root.packageName?.toString() != INSTAGRAM_PACKAGE ||
                !ProtectionPreferences.isEnabled(this@AntiScrollService, ProtectedApp.INSTAGRAM)
            ) {
                stopProfileCurtainEnsure()
                return
            }
            if (profileGeometryWatchActive && profileWatchedFullCover &&
                instagramProfilePostsBlocked()) {
                // The strict-profile geometry watch holds the whole collapsible
                // body during a collapse. Re-mounting the row-based curtain
                // here would cut its top and flash; defer to the watch.
                profileCurtainEnsureAttempts++
                if (profileCurtainEnsureAttempts > 60) stopProfileCurtainEnsure()
                mainHandler.postDelayed(this, 96L)
                return
            }
            val windowBounds = instagramWindowBounds(root)
            val now = SystemClock.uptimeMillis()
            if (now < profileAllowedDepartureUntil) {
                // A verified departure is owned by the transition scanner. Do not
                // recreate the outgoing grid while that scanner is releasing it.
                mainHandler.postDelayed(this, profileAllowedDepartureUntil - now)
                return
            }
            if (hasVisibleProfileNativeSheet(root) || hasVisibleAboutAccountSurface(root)) {
                armInstagramProfileReturn(root, now)
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
        applyStrictProfileCurtain(root, windowBounds, bounds)
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

    /**
     * Strict-profile geometry watch. Event delivery from Instagram cannot keep
     * up with a collapsing header (scroll events often carry no delta at all),
     * so this pro-active loop owns the curtain while it is mounted: it measures
     * the grid top at a fixed cadence, covers the whole collapsible body for as
     * long as any motion lasts, and releases to the exact grid rectangle once
     * the geometry repeats stably. A tap aimed through the moving header lands
     * on the curtain instead of a sliver, and no stale hold can pin the cover
     * over the whole page after the scroll ends.
     */
    private var profileGeometryWatchActive = false
    private var profileWatchedCoverTop: Int? = null
    private var profileWatchedStableSamples = 0
    private var profileWatchedLastChangeAt = 0L
    private var profileWatchedFullCover = false
    private var profileWatchedFullCoverStartedAt = 0L
    /**
     * Last travelled direction of the row: +1 = content expanded downward
     * (header opening back), -1 = content collapsed upward, 0 unknown. An
     * anchor loss happens in both directions, but only a collapse can move
     * grid content above the mounted edge; an expansion moves content away
     * from it, so the anchor loss there keeps the last verified cover
     * instead of flashing the whole body.
     */
    private var profileWatchedLastDirection = 0
    private var profileWatchTick = 0

    /**
     * Strict-profile geometry watch.
     *
     * Event delivery from Instagram cannot follow the collapsing header (its
     * scroll events often carry no delta at all), so while the curtain is
     * mounted this loop polls the Follow/Message row bottom at a fixed cadence
     * and follows every position change without flashing: the row belongs to
     * the scrolling block, so covering from its current bottom keeps the grid
     * under the cover at ALL poses. The only dangerous case is the row (and
     * its anchors) vanishing from the tree entirely while content is still
     * travelling: that arms a bounded full-body hold until the anchor
     * publishes again or the hold cap expires. Within the jitter band stability
     * accumulates and the exact geometry remounts once.
     */
    private val profileGeometryWatch = object : Runnable {
        override fun run() {
            if (!profileGeometryWatchActive) return
            profileWatchLastTickAt = SystemClock.uptimeMillis()
            val departureRemaining = maxOf(profileAllowedDepartureUntil, profileWatchQuietUntil) -
                SystemClock.uptimeMillis()
            if (departureRemaining > 0L) {
                // A verified allowed departure (Story ring, Edit Profile, menu
                // sheet...) is in flight. Do not query the animating tree: one
                // such tick measured 1.6 s and delayed the Story exit with it.
                profileWatchedCoverTop = Int.MIN_VALUE
                profileWatchedFullCover = false
                profileWatchedStableSamples = 0
                profileWatchIdleTicks = 0
                mainHandler.postDelayed(this, departureRemaining + 16L)
                return
            }
            val root = rootInActiveWindow
            if (root == null || root.packageName?.toString() != INSTAGRAM_PACKAGE ||
                !ProtectionPreferences.isEnabled(this@AntiScrollService, ProtectedApp.INSTAGRAM) ||
                !instagramProfilePostsBlocked()) {
                // Leaving Instagram or losing the mode stops the loop for good.
                profileGeometryWatchActive = false
                return
            }
            // Transitional classifications (UNKNOWN right after fragment swaps)
            // must NOT kill the guard: the header collapse happens exactly in
            // those frames. Silence decisions while unknown, keep ticking.
            val screenIsProfile = lastInstagramScreen == InstagramScreen.PROFILE
            if (!screenIsProfile &&
                lastInstagramScreen != InstagramScreen.UNKNOWN &&
                instagramOverlay.visibleProfileBounds() == null) {
                // A verified different surface owns the screen now.
                profileGeometryWatchActive = false
                return
            }
            if (instagramProfileCurtainMustYield(root)) {
                // An allowed profile layer (avatar preview, Edit Profile,
                // highlights picker) owns the screen: retire any mounted
                // curtain at once and re-anchor for the moment it closes.
                if (instagramOverlay.visibleProfileBounds() != null) {
                    instagramOverlay.hide()
                    instagramOverlay.updateEntryGuards(emptyList(), isEnglish())
                    if (ProtectionFlightRecorder.ENABLED) ProtectionFlightRecorder.record(
                        this@AntiScrollService, "profile.watch", "decision=layer-yield")
                }
                profileWatchedCoverTop = Int.MIN_VALUE
                profileWatchedFullCover = false
                profileWatchedStableSamples = 0
                mainHandler.postDelayed(this, nextProfileWatchDelay())
                return
            }
            val window = instagramWindowBounds(root)
            val coverTop = instagramStrictProfileRowBottom(root, window)
            val now = SystemClock.uptimeMillis()
            if (now < profileAllowedDepartureUntil) {
                // A verified allowed departure (Edit Profile, menu sheet, About
                // layer...) is in flight: keep the curtain away and re-check
                // after the quiet window instead of flashing it back on.
                profileWatchedCoverTop = Int.MIN_VALUE
                profileWatchedFullCover = false
                profileWatchedStableSamples = 0
                mainHandler.postDelayed(this, nextProfileWatchDelay())
                return
            }
            val floor = if (profileScrollFloor > window.top) profileScrollFloor else
                calculateProfileScrollFloor(root, window)
            val bottom = stableMainNavigationTop(root, window, InstagramScreen.PROFILE)
                .takeIf { it > window.centerY() } ?: window.bottom
            fun mountAt(top: Int?) {
                profileWindowId = root.windowId
                val resolved = top?.coerceAtMost(bottom - 1) ?: floor
                showInstagramCurtain(Rect(window.left, resolved, window.right, bottom),
                    InstagramScreen.PROFILE)
            }
            if (profileWatchedFullCover &&
                now - profileWatchedFullCoverStartedAt > PROFILE_FULL_COVER_MAX_HOLD_MS) {
                // The protective hold outlived any realistic scroll gesture:
                // treat the ongoing instability as animation noise and release
                // to the last measured row position.
                profileWatchedFullCover = false
                mountAt(profileWatchedCoverTop)
                if (ProtectionFlightRecorder.ENABLED) ProtectionFlightRecorder.record(
                    this@AntiScrollService, "profile.watch", "decision=cap-release top=$profileWatchedCoverTop")
                profileWatchedStableSamples = 0
            }
            when {
                coverTop == null -> {
                    if (!hasVisibleProfileHeaderMarkers(root)) {
                        // The profile shell left the tree: a full-screen layer
                        // (hamburger menu, account switcher, other page) owns
                        // the screen. Yield: never mount over it, retire any
                        // mounted curtain, and re-anchor for the return.
                        if (instagramOverlay.visibleProfileBounds() != null) {
                            instagramOverlay.hide()
                            instagramOverlay.updateEntryGuards(emptyList(), isEnglish())
                            if (ProtectionFlightRecorder.ENABLED) ProtectionFlightRecorder.record(
                                this@AntiScrollService, "profile.watch",
                                "decision=shell-absent-yield")
                        }
                        profileWatchedCoverTop = Int.MIN_VALUE
                        profileWatchedFullCover = false
                        profileWatchedStableSamples = 0
                        mainHandler.postDelayed(this, nextProfileWatchDelay())
                        return
                    }
                    // Row unmeasured (rebuild or exotic layout) while the shell
                    // is present.
                    if (!profileWatchedFullCover) {
                        val mounted = instagramOverlay.visibleProfileBounds()
                        if (mounted == null || mounted.top > floor + PROFILE_COVER_JITTER_PX) {
                            val collapseRisk = profileWatchedLastDirection <= 0
                            if (collapseRisk && mounted != null) {
                                // A collapse (or unknown) direction with the
                                // anchor gone: content is climbing above the
                                // mounted edge. Hold the whole body until the
                                // anchor publishes again (bounded by the cap).
                                if (ProtectionFlightRecorder.ENABLED) ProtectionFlightRecorder.record(
                                    this@AntiScrollService, "profile.watch",
                                    "decision=anchor-lost-hold mounted=${mounted.top} dir=$profileWatchedLastDirection")
                                profileWatchedFullCover = true
                                profileWatchedFullCoverStartedAt = now
                                profileScrollCover.reset()
                                mountAt(null)
                            } else if (profileWatchedLastDirection > 0 && profileWatchedCoverTop != null) {
                                // Expansion moved content away: keep the last
                                // verified cover position, no flash.
                                if (ProtectionFlightRecorder.ENABLED) ProtectionFlightRecorder.record(
                                    this@AntiScrollService, "profile.watch",
                                    "decision=anchor-lost-keep top=$profileWatchedCoverTop dir=$profileWatchedLastDirection")
                                mountAt(profileWatchedCoverTop)
                            } else if (mounted == null) {
                                // Nothing mounted and unknown direction.
                                mountAt(null)
                            }
                        }
                    }
                }
                profileWatchedCoverTop == null || profileWatchedCoverTop == Int.MIN_VALUE -> {
                    // First observation after arming: anchor only.
                    if (ProtectionFlightRecorder.ENABLED) ProtectionFlightRecorder.record(
                        this@AntiScrollService, "profile.watch", "decision=anchor top=$coverTop")
                    profileWatchedCoverTop = coverTop
                    profileWatchedLastChangeAt = now
                    // A dropped curtain must not wait for the next move.
                    if (instagramOverlay.visibleProfileBounds() == null) mountAt(coverTop)
                }
                kotlin.math.abs(coverTop - profileWatchedCoverTop!!) <= PROFILE_COVER_JITTER_PX -> {
                    profileWatchedStableSamples++
                    val settled = if (profileWatchedFullCover) {
                        // Release the protective hold as soon as the row stops
                        // moving: stable samples plus a short quiet window. The
                        // row and the grid travel together, so a stopped finger
                        // means the exact pose is safe to mount again.
                        profileWatchedStableSamples >= 2 &&
                            now - profileWatchedLastChangeAt >= PROFILE_FULL_COVER_SETTLE_HOLD_MS
                    } else {
                        profileWatchedStableSamples >= 3 &&
                            now - profileWatchedLastChangeAt >= 240L
                    }
                    if (settled) {
                        profileWatchedLastChangeAt = now
                        val mounted = instagramOverlay.visibleProfileBounds()
                        if (profileWatchedFullCover) {
                            if (ProtectionFlightRecorder.ENABLED) ProtectionFlightRecorder.record(
                                this@AntiScrollService, "profile.watch", "decision=release top=$coverTop samples=$profileWatchedStableSamples")
                            profileWatchedFullCover = false
                            mountAt(coverTop)
                        } else if (mounted == null ||
                            mounted.top != coverTop.coerceIn(floor, bottom - 1)) {
                            if (ProtectionFlightRecorder.ENABLED) ProtectionFlightRecorder.record(
                                this@AntiScrollService, "profile.watch", "decision=drift-fix top=$coverTop mounted=${mounted?.top}")
                            mountAt(coverTop)
                        }
                    }
                }
                coverTop > profileWatchedCoverTop!! -> {
                    if (ProtectionFlightRecorder.ENABLED) ProtectionFlightRecorder.record(
                        this@AntiScrollService, "profile.watch", "decision=follow-down top=$coverTop prev=$profileWatchedCoverTop")
                    profileWatchedFullCover = false
                    mountAt(coverTop)
                    profileWatchedCoverTop = coverTop
                    profileWatchedStableSamples = 0
                    profileWatchedLastChangeAt = now
                    profileWatchedLastDirection = 1
                }
                else -> {
                    val previous = profileWatchedCoverTop
                    if (ProtectionFlightRecorder.ENABLED) ProtectionFlightRecorder.record(
                        this@AntiScrollService, "profile.watch", "decision=cover-up top=$coverTop prev=$previous")
                    profileWatchedCoverTop = coverTop
                    profileWatchedStableSamples = 0
                    profileWatchedLastChangeAt = now
                    when {
                        profileWatchedFullCover -> {
                            // Protective hold already running: nothing to change.
                        }
                        previous != null && previous != Int.MIN_VALUE &&
                            coverTop > previous - PROFILE_COVER_UP_STEP_PX -> {
                            // Small upward drift: follow the row without a flash.
                            mountAt(coverTop)
                        }
                        else -> {
                            // Real collapse: content climbs several pixels above
                            // the mounted edge in one frame, so hold the whole
                            // collapsible body until it settles.
                            profileWatchedFullCover = true
                            profileWatchedFullCoverStartedAt = now
                            profileScrollCover.reset()
                            mountAt(null)
                        }
                    }
                }
            }
            mainHandler.postDelayed(this, nextProfileWatchDelay())
            if (ProtectionFlightRecorder.ENABLED) {
                profileWatchTick++
                if (profileWatchTick % 8 == 0) ProtectionFlightRecorder.record(
                    this@AntiScrollService, "profile.watch.tick",
                    "screen=$lastInstagramScreen top=$coverTop watched=$profileWatchedCoverTop " +
                        "active=${profileGeometryWatchActive} full=$profileWatchedFullCover")
            }
        }
    }

    private var profileWatchIdleTicks = 0
    private var profileWatchLastTickAt = 0L
    private var profileWatchLastSignature = ""

    /**
     * Every tick costs several remote lookups (100-185 ms measured). While the
     * header and grid are moving the watch keeps its 48 ms cadence; once the
     * pose has repeated it backs off to at most 480 ms. Instagram scroll and
     * click events wake it immediately through [kickProfileGeometryWatch].
     */
    private fun nextProfileWatchDelay(): Long {
        val signature = "$profileWatchedCoverTop|$profileWatchedFullCover|" +
            "${instagramOverlay.visibleProfileBounds()}"
        if (signature == profileWatchLastSignature) profileWatchIdleTicks++ else {
            profileWatchIdleTicks = 0
            profileWatchLastSignature = signature
        }
        return if (profileWatchIdleTicks < 4) 48L
        else minOf(480L, 48L shl minOf(profileWatchIdleTicks - 3, 4))
    }

    private fun kickProfileGeometryWatch(urgent: Boolean = false) {
        if (!profileGeometryWatchActive) return
        val now = SystemClock.uptimeMillis()
        if (urgent) {
            // A header collapse is in progress: the anchor probe must run
            // before the general scan (which first spends ~100-200 ms on
            // viewer probes), or the first grid row shows above the curtain.
            if (now - profileWatchLastTickAt < 120L) return
            profileWatchIdleTicks = 0
            mainHandler.removeCallbacks(profileGeometryWatch)
            mainHandler.postAtFrontOfQueue(profileGeometryWatch)
            return
        }
        if (profileWatchIdleTicks < 4) return
        profileWatchIdleTicks = 0
        mainHandler.removeCallbacks(profileGeometryWatch)
        mainHandler.post(profileGeometryWatch)
    }

    private fun armProfileGeometryWatch() {
        if (profileGeometryWatchActive) return
        profileWatchIdleTicks = 0
        profileWatchLastSignature = ""
        profileGeometryWatchActive = true
        profileWatchedCoverTop = Int.MIN_VALUE
        profileWatchedStableSamples = 0
        profileWatchedFullCover = false
        mainHandler.postDelayed(profileGeometryWatch, 48L)
    }

    private fun stopProfileCurtainEnsure() {
        profileCurtainEnsureActive = false
        profileCurtainEnsureAttempts = 0
        profileCurtainEnsureStableStreak = 0
    }

    private val exitGate = InstagramExitGate()
    private val instagramEntryTargets = mutableListOf<Pair<Rect, Boolean>>()
    private val lastDirectConversationGuards = mutableListOf<Rect>()
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
            val now = SystemClock.uptimeMillis()
            val homeNavigationPending = instagramProtectedNavigation.isPending(
                InstagramTab.HOME, now) && instagramOverlay.visibleHomeBounds() != null
            if (!refreshed && (homeNavigationPending ||
                    (scrollCoverTop != null && now < scrollCoverUntil))) {
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
    private var debugProfileScrollActionWindowId = -1
    private var profileScrollFloor = 0
    private var profileCurtainEnsureActive = false
    private var profileCurtainEnsureAttempts = 0
    private var profileCurtainEnsureStableStreak = 0
    private var lastProfileCurtainFingerprint: String? = null
    private val profileScrollCover = InstagramProfileScrollCover()
    private val profileScrollExit = InstagramProfileScrollExit()
    private val profileScrollPositions = mutableMapOf<String, Int>()
    private var exploreTransitionCoverUntil = 0L
    private var exploreTransitionCoverTop = 0
    private var exploreTransitionCoverWindowId = -1
    private val exploreChromeRestore = object : Runnable {
        override fun run() {
            val remaining = exploreTransitionCoverUntil - SystemClock.uptimeMillis()
            if (remaining > 0L) {
                mainHandler.postDelayed(this, remaining)
                return
            }
            if (!ProtectionPreferences.isEnabled(this@AntiScrollService, ProtectedApp.INSTAGRAM)) return
            val root = rootInActiveWindow ?: return
            if (root.packageName?.toString() != INSTAGRAM_PACKAGE) return
            val retained = instagramOverlay.visibleCurtain() ?: return
            val selectedSearch = root.findAccessibilityNodeInfosByViewId("$INSTAGRAM_PACKAGE:id/search_tab")
                .any { it.isVisibleToUser && it.isSelected }
            if (!InstagramNavigationGeometry.canRestoreSearchChrome(retained.first,
                    root.windowId == exploreTransitionCoverWindowId, selectedSearch)) return
            val header = attachedExploreHeaderBounds(root, instagramWindowBounds(root)) ?: return
            // Only release the temporary header strip; keep the existing content
            // rectangle mounted. Do not wait for a full accessibility-tree scan.
            val bounds = Rect(retained.second).apply { top = header.bottom }
            if (bounds.top >= bounds.bottom) return
            exploreTransitionCoverUntil = 0L
            if (instagramOverlay.show(bounds, InstagramScreen.EXPLORE, isEnglish())) {
                lastVerifiedInstagramCurtain = InstagramScreen.EXPLORE to Rect(bounds)
            }
            queueInstagramScan(0L)
        }
    }

    private fun handleInstagram(event: AccessibilityEvent) {
        if (!ProtectionPreferences.isEnabled(this, ProtectedApp.INSTAGRAM)) {
            hideInstagramProtection()
            return
        }
        val eventNow = SystemClock.uptimeMillis()
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            profileLayerInvalidatedAt = eventNow
        }
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED ||
            event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            kickProfileGeometryWatch()
        }
        if (handleStrictInstagramStoryLaunch(event, eventNow)) return
        if (eventNow < instagramStoryLaunchUntil) {
            // No remote queries while the Story viewer starts (see above).
            queueInstagramScan(120L)
            return
        }
        refreshEarlyInstagramProfileReturn(event, eventNow)
        if (instagramProtectedNavigation.isAwaitingDispatch(eventNow)) {
            // Window/content events from the source tab can otherwise perform
            // several remote tree queries before the pre-draw callback gets a
            // main-thread turn. The destination is already covered; coalesce
            // those stale events and scan the resulting tab after dispatch.
            queueInstagramScan(32L)
            return
        }
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            armDirectWindowTransition(event, eventNow)
        }
        if (ProtectionPreferences.getMode(this, ProtectedApp.INSTAGRAM) == ProtectionMode.STRICT &&
            lastInstagramScreen == InstagramScreen.NOTIFICATIONS &&
            event.eventType in setOf(
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                AccessibilityEvent.TYPE_WINDOWS_CHANGED) &&
            eventNow - event.eventTime in 0L..700L &&
            // A viewer only opens after a notification row tap, which arms this
            // hold. Without it the ancestor walk below cost 25-40 ms for every
            // content event of the notification list.
            eventNow < instagramNotificationExitHoldUntil &&
            isFreshNotificationViewerEvent(event)) {
            dispatchNotificationViewerBackFromEvent(event, eventNow)
            return
        }
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            val freshClick = eventNow - event.eventTime in 0L..1_500L
            if (freshClick && lastInstagramScreen == InstagramScreen.NOTIFICATIONS &&
                isInstagramNotificationContentIntent(event.source)) {
                instagramNotificationBackCount = 0
                // Preserve notification origin through the first state-machine
                // transition. observeInstagramScreen() may change
                // NOTIFICATIONS -> REELS before exitBlockedViewer() runs.
                instagramNotificationExitHoldUntil = eventNow + 10_000L
                instagramNotificationContentLaunchUntil = eventNow + 1_500L
                instagramBalancedNotificationPostWatching = false
                instagramNotificationReelExitIssued = false
                instagramNotificationReelSecondBackIssued = false
                instagramNotificationCommentNeedsSecondBack = false
                instagramOverlay.hide()
                clearInstagramResumeProtection()
                queueInstagramScan(0L)
                Log.d(TAG, "IG_NOTIFICATION_CONTENT_INTENT pending=true")
            }
            if (freshClick && lastInstagramScreen == InstagramScreen.NOTIFICATIONS &&
                !isBackNavigationIntent(event.source)) {
                // Compose notification rows do not always expose their row id
                // through the click event. Keep a short origin latch for any
                // non-header notification tap so the first viewer scan still
                // knows this was notification-originated.
                instagramNotificationExitHoldUntil = eventNow + 10_000L
                instagramNotificationAttemptStartedAt = eventNow
                instagramNotificationViewerDetectedAt = 0L
                instagramNotificationReelExitIssued = false
                instagramNotificationReelSecondBackIssued = false
                instagramNotificationCommentNeedsSecondBack = false
                Log.d(TAG, "IG_NOTIFICATION_TAP ageMs=${eventNow - event.eventTime}")
            }
            if (freshClick && instagramProfileIngress.pending &&
                compactId(event.source?.viewIdResourceName) == "action_bar_button_back") {
                instagramProfileIngress.reset()
                instagramOverlay.updateProfileIngressGuard(null)
            }
            val openingInboxThread = freshClick && armDirectConversationEntryGuard(event, eventNow)
            if (freshClick && lastInstagramScreen == InstagramScreen.PROFILE &&
                instagramBalancedProfilePost.arm(
                    ProtectionPreferences.getMode(this, ProtectedApp.INSTAGRAM),
                    verifiedProfile = true,
                    profileThumbnail = isProfileThumbnailIntent(event.source),
                    now = eventNow)) {
                Log.d(TAG, "IG_BALANCED_PROFILE_POST_INTENT")
            }
            if (freshClick && lastInstagramScreen == InstagramScreen.SAVED_COLLECTION &&
                instagramBalancedProfilePost.arm(
                    ProtectionPreferences.getMode(this, ProtectedApp.INSTAGRAM),
                    verifiedProfile = false,
                    profileThumbnail = isCollectionThumbnailIntent(event.source),
                    now = eventNow,
                    verifiedCollection = true)) {
                Log.d(TAG, "IG_BALANCED_COLLECTION_POST_INTENT")
            }
            if (freshClick && lastInstagramScreen == InstagramScreen.PROFILE &&
                isProfileReelThumbnailIntent(event.source)) {
                instagramBalancedProfileReelIntentWindowId = event.windowId
                instagramBalancedProfileReelIntentUntil = eventNow + 1_500L
                Log.d(TAG, "IG_BALANCED_PROFILE_REEL_INTENT window=${event.windowId}")
            }
            if (freshClick && lastInstagramScreen == InstagramScreen.SAVED_COLLECTION &&
                isCollectionReelThumbnailIntent(event.source)) {
                instagramBalancedCollectionReelIntentWindowId = event.windowId
                instagramBalancedCollectionReelIntentUntil = eventNow + 1_500L
                Log.d(TAG, "IG_BALANCED_COLLECTION_REEL_INTENT window=${event.windowId}")
            }
            val profileCurtainVisible = instagramOverlay.visibleProfileBounds() != null
            val profileDeparture = if (freshClick &&
                (profileCurtainVisible || lastInstagramScreen == InstagramScreen.PROFILE)) {
                allowedProfileDeparture(event.source)
            } else null
            if (profileDeparture != null) {
                // Click delivery can lag behind the profile mount: the search-row
                // click which opened it is not a departure from it. Only a verified
                // allowed destination can release the grid; unknown clicks retain it.
                // Instagram keeps the profile pager mounted behind Highlights
                // and native action sheets. Release the grid curtain before the
                // destination draws, then let structural checks own the state.
                profileAllowedDepartureUntil = eventNow + if (profileDeparture) {
                    1_200L
                } else {
                    PROFILE_ALLOWED_DEPARTURE_QUIET_MS
                }
                instagramProfileReturn.arm(eventNow)
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
                    val bounds = calculateExploreBounds(root, instagramWindowBounds(root))
                    clampCurtainBottomToNavigation(root, bounds)
                    showInstagramCurtain(bounds, InstagramScreen.EXPLORE)
                    Log.d(TAG,
                        "IG_EXPLORE_INTENT_CURTAIN eventAgeMs=${eventNow - event.eventTime}")
                }
            }
            if (freshClick && lastInstagramScreen == InstagramScreen.SEARCH_RESULTS &&
                isBackNavigationIntent(event.source) && !isInputMethodVisible()) {
                // Back from search results returns to Explore. The click is
                // often reported ~0.5 s late and the destination scan can take
                // a further ~0.7 s; pre-mount the destination curtain so the
                // returning grid is never exposed while classification runs.
                val root = rootInActiveWindow
                if (root?.packageName?.toString() == INSTAGRAM_PACKAGE &&
                    instagramOverlay.visibleCurtain() == null) {
                    clearInstagramResumeProtection()
                    currentInstagramWindowId = root.windowId
                    showInstagramCurtain(
                        calculateExploreBounds(root, instagramWindowBounds(root)),
                        InstagramScreen.EXPLORE)
                    Log.d(TAG,
                        "IG_SEARCH_BACK_PRECOVER eventAgeMs=${eventNow - event.eventTime}")
                    queueInstagramScan(0L)
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
            if (freshClick && isMainTabClick(event.source)) {
                // Bottom-tab switch: the tab bar is static across main tabs, so
                // the blocked Reels tab gets its guard immediately - including
                // the first second of a Profile/DM/Explore transition where
                // the settled-path updates have not run yet.
                if (isReelsTabClick(event.source)) {
                    // A tap on the blocked Reels TAB must never take the
                    // Balanced single-viewer allowance: the transitional feed
                    // frame can miss the tab-bar marker, so the origin is
                    // latched and every feed entry exits immediately.
                    instagramReelsTabTapUntil = eventNow + 3_000L
                    // The click event only exists when the tap reached
                    // Instagram at all (the guard consumed the others). The
                    // feed renders within a few hundred ms of it: arm the
                    // opaque cover now so even its first frame never shows.
                    armReelsSlipCover()
                }
                rootInActiveWindow?.let { tabRoot ->
                    if (tabRoot.packageName?.toString() == INSTAGRAM_PACKAGE) {
                        refreshMainReelsTabGuard(tabRoot)
                    }
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
                var isBack = false
                repeat(8) {
                    val node = candidate ?: return@repeat
                    val id = compactId(node.viewIdResourceName)
                    if (id.contains("back", ignoreCase = true)) isBack = true
                    candidate = node.parent
                }
                if (!isBack) {
                    exitGate.userClick(event.eventTime)
                    lastInstagramUserInputAt = event.eventTime
                    val currentRoot = rootInActiveWindow
                    if (instagramExitInProgress &&
                        currentRoot?.packageName?.toString() == INSTAGRAM_PACKAGE &&
                        hasFastNotificationsSurface(currentRoot)) {
                        // A second notification tap can arrive after the first
                        // viewer exit while Instagram still reports the old
                        // Reel encounter. Treat it as a new attempt instead
                        // of letting the stale retry state swallow the click.
                        cancelInstagramExit()
                        instagramNotificationBackCount = 0
                        instagramNotificationExitHoldUntil = eventNow + 10_000L
                        instagramNotificationContentLaunchUntil = eventNow + 1_500L
                        instagramNotificationReelExitIssued = false
                        instagramNotificationReelSecondBackIssued = false
                        instagramNotificationCommentNeedsSecondBack = false
                        Log.d(TAG, "IG_NOTIFICATION_NEW_ATTEMPT reset=true")
                    }
                }
            }
            @Suppress("DEPRECATION")
            queuedClick?.recycle()
            @Suppress("DEPRECATION")
            val copy = AccessibilityEvent.obtain(event)
            queuedClick = copy
            if (InstagramInboxPolicy.canReleaseFeedForDirectIntent(
                    instagramOverlay.visibleCurtain()?.first, freshClick)) {
                var source = event.source
                repeat(5) {
                    val node = source ?: return@repeat
                    if (InstagramInboxPolicy.isDirectNavigation(compactId(node.viewIdResourceName),
                            node.contentDescription?.toString().orEmpty())) {
                        // Intent is not a destination: lift the outgoing feed
                        // curtain at once (messaging is a verified-allowed
                        // surface, and the user asked for a fast release), but
                        // re-cover from the last verified rectangle if no inbox
                        // verification lands inside the short recovery window.
                        directNavigationSourceWindowId = event.windowId
                        directNavigationUntil = SystemClock.uptimeMillis() +
                            DIRECT_NAVIGATION_GRACE_MS
                        // Keep the mounted entry guards through the transition:
                        // the bottom Reels tab must stay covered while the DM
                        // surface loads, otherwise a fast follow-up tap enters
                        // Reels natively.
                        instagramOverlay.hide()
                        mainHandler.postDelayed(instagramDirectRecoverCover,
                            DIRECT_RECOVER_COVER_MS)
                        Log.d(TAG, "IG_DIRECT_INTENT_PENDING eventAgeMs=${SystemClock.uptimeMillis() - event.eventTime}")
                        source = null
                    } else source = node.parent
                }
            }
            if (freshClick && isNotificationsIntent(event.source)) {
                // ACTION_CLICK from our launch proxy is an acknowledgement,
                // not permission to tear down the already mounted bridge.
                // Keep it until the destination row windows have drawn.
                if (ProtectionPreferences.getMode(this, ProtectedApp.INSTAGRAM) != ProtectionMode.STRICT) {
                    clearInstagramNotificationIngress()
                    instagramOverlay.updateEntryGuards(emptyList(), isEnglish())
                }
                clearInstagramResumeProtection()
                instagramOverlay.hide()
                mainHandler.removeCallbacks(homeScrollSettled)
                scrollCoverTop = null
                scrollCoverUntil = 0L
                directNavigationUntil = 0L
                directNavigationSourceWindowId = -1
                Log.d(TAG, "IG_ALLOWED_NOTIFICATION_INTENT_RELEASE")
            }
        }
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            if (handleBalancedInstagramReelScroll(event)) return
            if (lastInstagramScreen == InstagramScreen.NOTIFICATIONS) {
                val root = rootInActiveWindow
                if (root?.packageName?.toString() == INSTAGRAM_PACKAGE && hasFastNotificationsSurface(root)) {
                    if (InstagramNotificationEntryPolicy.blocksCommentLikes(
                            ProtectionPreferences.getMode(this, ProtectedApp.INSTAGRAM))) {
                        instagramNotificationMotion.arm(SystemClock.uptimeMillis())
                    }
                    refreshInstagramNotificationEntryGuards()
                    queueInstagramScan(16L)
                    return
                }
            }
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
                        dx, dy, effectiveDy, profileSource) ||
                    (profileSource && (dx != 0 || dy != 0 || positionDelta != 0))) {
                    // The strict-profile geometry watch owns the curtain while
                    // it is mounted: it polls the row faster than events can
                    // deliver deltas, so this branch only keeps the scanner
                    // warm and lets the watch decide coverage. No visual work
                    // here means no double owner, hence no flash churn.
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
            if (retainInstagramCurtainUnderNotificationShade(root.packageName?.toString())) return
            suspendInstagramProtection()
            // This path is reached when a delayed Instagram event is handled
            // after Recents/SystemUI has already become the active root. Mark
            // the foreground departure just like overlayForegroundCheck does,
            // otherwise the next Instagram event is not recognized as resume.
            trackPackageTransition("")
            return
        }
        resumeInstagramNotificationShade(root)
        if (precoverCollectionReturn(root)) return
        val notificationTransitionActive = lastInstagramScreen == InstagramScreen.NOTIFICATIONS ||
            instagramExitFromNotifications || instagramNotificationReturn.isPending(scanNow) ||
            instagramNotificationIngress.active(scanNow) || scanNow < instagramNotificationExitHoldUntil
        if (notificationTransitionActive &&
            (!instagramNotificationIngress.active(scanNow) ||
                lastInstagramScreen != InstagramScreen.HOME_FEED) &&
            igVisible(root, "main_feed_action_bar") &&
            selectedInstagramMainTab(root) == InstagramTab.HOME &&
            !hasFastActionBarBack(root) && !igVisible(root, "bottom_sheet_container") &&
            !hasFastNotificationsSurface(root)) {
            // A verified destination owns its protection, not the timers from
            // the previous notification visit. Do not cancel an ingress still
            // waiting to leave its Home source tree before Notifications opens.
            clearInstagramNotificationIngress()
            instagramNotificationReturn.reset()
            instagramNotificationContentLaunchUntil = 0L
            instagramNotificationExitHoldUntil = 0L
            cancelInstagramExit()
            val window = instagramWindowBounds(root)
            if (refreshFastMainProtectedSurface(root, window)) {
                refreshProtectedNavigationGuards(root, window)
                return
            }
        }
        if (instagramNotificationReturn.isPending(scanNow)) {
            // Back was already dispatched by the verified Notifications
            // header. Transitional feed/comment nodes are not a new viewer
            // launch and must never trigger an additional Back.
            queueInstagramScan(32L)
            return
        }
        if (instagramNotificationIngress.active(scanNow)) {
            if (ProtectionPreferences.getMode(this, ProtectedApp.INSTAGRAM) != ProtectionMode.STRICT) {
                clearInstagramNotificationIngress()
            } else {
                // Source-tree scans and native click acknowledgements can run
                // between mounting the input bridge and drawing destination
                // guards. They must not release or reclassify that transition.
                if (!instagramNotificationIngress.awaitingDispatch && hasFastNotificationsSurface(root)) {
                    clearInstagramResumeProtection()
                    instagramOverlay.hide()
                    cancelInstagramExit()
                    observeInstagramScreen(InstagramScreen.NOTIFICATIONS, root.windowId)
                    refreshInstagramNotificationBackGuard(root, instagramWindowBounds(root))
                    refreshInstagramNotificationEntryGuards()
                }
                queueInstagramScan(32L)
                return
            }
        }
        // While a notification-origin viewer is being closed, never fall
        // through to the broad Instagram tree walk. That walk routinely takes
        // 300–500 ms (and occasionally longer), which is directly visible as
        // the delayed return. The targeted window probe is the only authority
        // until Notifications is confirmed again.
        if (instagramExitFromNotifications ||
            (lastInstagramScreen == InstagramScreen.NOTIFICATIONS &&
                scanNow < instagramNotificationExitHoldUntil)) {
            val notificationViewer = findInstagramNotificationViewerRoot(root)
            if (notificationViewer != null) {
                if (instagramExitInProgress) {
                    queueInstagramScan(48L)
                    return
                }
                val (viewerRoot, viewerScreen) = notificationViewer
                if (ProtectionPreferences.getMode(this, ProtectedApp.INSTAGRAM) ==
                    ProtectionMode.BALANCED &&
                    allowBalancedInstagramNotificationViewer(viewerRoot, viewerScreen)) return
                exitBlockedViewer(
                    viewerRoot,
                    instagramWindowBounds(viewerRoot),
                    viewerScreen,
                    useGlobalBackOnly = viewerScreen == InstagramScreen.REELS)
                return
            }
            if (instagramExitInProgress && hasFastNotificationsSurface(root)) {
                cancelInstagramExit()
                observeInstagramScreen(InstagramScreen.NOTIFICATIONS, root.windowId)
                refreshInstagramNotificationBackGuard(root, instagramWindowBounds(root))
                refreshInstagramNotificationEntryGuards()
                return
            }
        }
        probeInstagramSearchForProfile(root, scanNow)
        if (instagramProtectedNavigation.shouldHoldBeforeDispatch(scanNow, root.windowId)) {
            // A DM event which was already queued before the user's protected-tab
            // tap must not classify the still-selected inbox and remove the new
            // destination curtain before its first frame. Yield to pre-draw; the
            // draw callback performs the real native tab action exactly once.
            if (instagramOverlay.visibleCurtain() != null) {
                queueInstagramScan(32L)
                return
            }
            instagramProtectedNavigation.clear()
        }
        val initialWindowBounds = instagramWindowBounds(root)
        currentInstagramWindowId = root.windowId
        // Refresh the persistent blocked-Reels-tab guard before ANY early
        // return below: the fast protected-surface paths skip the scan tail,
        // which previously left the Explore/Home tab guard stale while the
        // user could spam the tab during a search-return transition.
        refreshMainReelsTabGuard(root)
        if (InstagramNotificationShadePolicy.isProtectedFeedOrigin(lastInstagramScreen) &&
            instagramOverlay.visibleCurtain() != null &&
            !isDirectConversationEntryGuardArmed() &&
            releaseVerifiedInstagramInbox(root, requireSelectedMainTab = true)) return
        if (InstagramDmInteractionPolicy.prioritizesChangedViewer(
                ProtectionPreferences.getMode(this, ProtectedApp.INSTAGRAM),
                verifiedConversation = lastInstagramScreen == InstagramScreen.DIRECT_MESSAGES &&
                    directConversationSurfaceWindowId >= 0,
                changedWindow = root.windowId != directConversationSurfaceWindowId) &&
            hasFastReelsViewer(root, directLaunchPending = scanNow < directReelLaunchPendingUntil)) {
            // A current, verified Reel in a new chat-owned window needs no
            // profile/navigation/inbox discovery. Stories still fail this exact
            // Reel predicate and keep their separate transition grace.
            exitBlockedViewer(root, initialWindowBounds, InstagramScreen.REELS)
            return
        }
        if (releaseProfileCurtainToAccountSearch(root, initialWindowBounds)) return
        // A full-screen Story owns a new foreground root. Do not spend the
        // first frame probing the old profile, inbox, Reel player, or snapshot.
        if (hasFastStoryViewer(root)) {
            stopProfileCurtainEnsure()
            instagramProfileIngress.reset()
            instagramOverlay.updateProfileIngressGuard(null)
            instagramOverlay.updateAccountSearchGuard(null, ::onInstagramAccountSearchTap, {})
            instagramOverlay.updateStoryEntryGuards(emptyList())
            instagramOverlay.updateNavigationGuards(emptyMap())
            if (instagramStoriesBlocked()) {
                val readinessNow = SystemClock.uptimeMillis()
                // The modal window event already proved the viewer window is up.
                val modalAnnounced = root.windowId == instagramModalViewerWindowId &&
                    readinessNow - instagramModalViewerSeenAt in 0L..3_000L
                val wait = if (modalAnnounced) 0L else instagramStoryExitReadiness.delayBeforeBack(
                    root.windowId, readinessNow)
                if (wait > 0L) {
                    clearInstagramResumeProtection()
                    instagramOverlay.hide()
                    instagramOverlay.updateEntryGuards(emptyList(), isEnglish())
                    queueInstagramScan(minOf(wait, 80L))
                    return
                }
                exitBlockedViewer(root, initialWindowBounds, InstagramScreen.STORY, useGlobalBackOnly = true)
            } else {
                releaseAllowedProfileSurface()
                observeInstagramScreen(InstagramScreen.STORY, root.windowId)
            }
            return
        }
        // A mounted main feed which is still the verified destination needs no
        // absent-profile, navigation or general-tree discovery. In particular,
        // let the looper deliver Direct clicks while Instagram's selected tab
        // still describes Explore. Never release the curtain in this shortcut.
        if (InstagramNotificationShadePolicy.isProtectedFeedOrigin(lastInstagramScreen) &&
            instagramOverlay.visibleCurtain() != null &&
            root.windowId == instagramStateMachine.currentWindowId &&
            !hasFastMainReelsTabDestination(root) &&
            refreshFastMainProtectedSurface(root, initialWindowBounds)) {
            refreshProtectedNavigationGuards(root, initialWindowBounds)
            if (SystemClock.uptimeMillis() - lastInstagramUserInputAt in
                    0L..INSTAGRAM_USER_LAUNCH_GRACE_MS) queueInstagramScan(48L)
            return
        }
        if (refreshEnteringInstagramProfile(root, initialWindowBounds)) return
        if (refreshSettledInstagramProfile(root, initialWindowBounds, scanNow)) return
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
        val instagramMode = ProtectionPreferences.getMode(this, ProtectedApp.INSTAGRAM)
        if (!InstagramBalancedReelIngressPolicy.keepsVerifiedSession(
                instagramMode, instagramReelsSession.watching, instagramReelsSession.exiting) &&
            hasFastMainReelsTabDestination(root)) {
            instagramReelsSession.observe(instagramMode,
                viewerVisible = true, feedEntry = true)
            exitBlockedViewer(root, initialWindowBounds, InstagramScreen.REELS)
            return
        }
        if (lastInstagramScreen == InstagramScreen.DIRECT_MESSAGES) {
            // DM is intentionally left untouched.  Do not pre-arm a full
            // conversation window: the only enforcement point is the viewer
            // detector below, which sends Back after Instagram actually opens
            // a Reel.
            clearDirectConversationEntryGuard()
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
        // Notifications are an allowed native surface. During a row launch,
        // do not fall through to collectInstagramSnapshot(): that full tree
        // walk can monopolize the accessibility looper for several seconds,
        // which is exactly the delay the user sees before the Reel is closed.
        // Resolve only the small, viewer-specific markers until the surface
        // settles back to Notifications.
        if (lastInstagramScreen == InstagramScreen.NOTIFICATIONS) {
            if (hasFastNotificationsSurface(root)) {
                // Instagram can keep the Notifications tree as the active
                // root while the Reel/comment viewer is published in a
                // separate accessibility window. Only acknowledge return
                // after every Instagram window has been probed.
                val separateViewer = findInstagramNotificationViewerRoot(root)
                if (separateViewer == null) {
                    instagramNotificationEventLocked = false
                    instagramNotificationLastEventExitAt = 0L
                    cancelInstagramExit()
                    instagramOverlay.hide()
                    refreshInstagramNotificationBackGuard(root, initialWindowBounds)
                    refreshInstagramNotificationEntryGuards()
                    return
                }
                val (viewerRoot, viewerScreen) = separateViewer
                if (ProtectionPreferences.getMode(this, ProtectedApp.INSTAGRAM) ==
                    ProtectionMode.BALANCED &&
                    allowBalancedInstagramNotificationViewer(viewerRoot, viewerScreen)) {
                    return
                }
                instagramNotificationContentLaunchUntil = 0L
                instagramNotificationExitHoldUntil = SystemClock.uptimeMillis() + 10_000L
                if (viewerScreen == InstagramScreen.REELS) {
                    instagramNotificationReelExitIssued = true
                }
                exitBlockedViewer(
                    viewerRoot,
                    instagramWindowBounds(viewerRoot),
                    viewerScreen,
                    useGlobalBackOnly = viewerScreen == InstagramScreen.REELS)
                return
            }
            val notificationReel = hasFastReelsViewer(root, directLaunchPending = true)
            val notificationComment = hasFastCommentSheetInRoot(root)
            if (notificationReel || notificationComment) {
                if (ProtectionPreferences.getMode(this, ProtectedApp.INSTAGRAM) ==
                    ProtectionMode.BALANCED &&
                    allowBalancedInstagramNotificationViewer(
                        root,
                        if (notificationReel) InstagramScreen.REELS else InstagramScreen.COMMENTS_OR_DETAIL)) {
                    return
                }
                instagramNotificationContentLaunchUntil = 0L
                instagramNotificationExitHoldUntil = SystemClock.uptimeMillis() + 10_000L
                if (notificationReel) instagramNotificationReelExitIssued = true
                exitBlockedViewer(
                    root,
                    initialWindowBounds,
                    if (notificationReel) InstagramScreen.REELS else InstagramScreen.COMMENTS_OR_DETAIL,
                    useGlobalBackOnly = notificationReel)
                return
            }
            if (scanNow < instagramNotificationContentLaunchUntil || instagramExitInProgress) {
                queueInstagramScan(32L)
                return
            }
        }
        // Check the player before walking the full conversation tree. A stable
        // Home curtain prevents Instagram's autonomous embedded-video rebuild
        // from being mistaken for a user-opened Reel.
        // Search all Instagram windows only during the actual notification
        // click hand-off or while this viewer encounter is still exiting.
        // After the list is verified again, Instagram can retain the old Reel
        // window in AccessibilityWindows for several seconds; treating that
        // stale tree as a new click causes a delayed second/third Back.
        if (lastInstagramScreen == InstagramScreen.NOTIFICATIONS &&
            (scanNow < instagramNotificationContentLaunchUntil || instagramExitInProgress)) {
            findInstagramNotificationViewerRoot(root)?.let { (viewerRoot, viewerScreen) ->
                if (ProtectionPreferences.getMode(this, ProtectedApp.INSTAGRAM) ==
                    ProtectionMode.BALANCED &&
                    scanNow < instagramNotificationContentLaunchUntil &&
                    allowBalancedInstagramNotificationViewer(viewerRoot, viewerScreen)) {
                    return
                }
                instagramNotificationContentLaunchUntil = 0L
                instagramNotificationExitHoldUntil =
                    // Keep the origin latch long enough to survive a user's
                    // manual close of the comment sheet before the post layer
                    // is published again.
                    SystemClock.uptimeMillis() + 10_000L
                if (viewerScreen == InstagramScreen.REELS) {
                    instagramNotificationReelExitIssued = true
                }
                exitBlockedViewer(
                    viewerRoot,
                    instagramWindowBounds(viewerRoot),
                    viewerScreen,
                    useGlobalBackOnly = viewerScreen == InstagramScreen.REELS)
                return
            }
        }
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
        if (hasFastSinglePostViewer(root)) {
            if (ProtectionPreferences.getMode(this, ProtectedApp.INSTAGRAM) ==
                ProtectionMode.BALANCED && instagramBalancedNotificationPostWatching) {
                hideInstagramContentOverlay()
                cancelInstagramExit()
                observeInstagramScreen(InstagramScreen.COMMENTS_OR_DETAIL, root.windowId)
                return
            }
            if (allowBalancedInstagramProfilePost(root)) return
            exitBlockedViewer(root, windowBounds, InstagramScreen.COMMENTS_OR_DETAIL)
            return
        }
        // A notification can open a comment sheet over a post. If the user
        // closes that sheet manually, Instagram may keep the underlying post
        // visible after the short launch window has expired. Keep the
        // notification-origin block alive for that hand-off and re-check the
        // broad post markers here; otherwise the post becomes an accidental
        // allowed surface and can be watched indefinitely.
        if (ProtectionPreferences.getMode(this, ProtectedApp.INSTAGRAM) ==
            ProtectionMode.STRICT &&
            scanNow < instagramNotificationExitHoldUntil &&
            lastInstagramScreen != InstagramScreen.NOTIFICATIONS) {
            val notificationViewer = findInstagramNotificationViewerRoot(root)
                ?: if (hasFastPostViewer(root) || hasFastCommentSheet(root)) {
                    root to InstagramScreen.COMMENTS_OR_DETAIL
                } else null
            notificationViewer?.let { (viewerRoot, viewerScreen) ->
                if (viewerScreen == InstagramScreen.REELS) {
                    instagramNotificationReelExitIssued = true
                }
                exitBlockedViewer(
                    viewerRoot,
                    instagramWindowBounds(viewerRoot),
                    viewerScreen,
                    useGlobalBackOnly = viewerScreen == InstagramScreen.REELS)
                return
            }
        }
        // A notification row is not an allowed source for a post, Reel or
        // comment viewer. During the short marker-to-viewer hand-off, suppress
        // feed classification and wait for the real viewer so one Back closes
        // it without mounting a flashing Home curtain.
        if (lastInstagramScreen == InstagramScreen.NOTIFICATIONS &&
            scanNow < instagramNotificationContentLaunchUntil) {
            val notificationPostViewer = hasFastPostViewer(root)
            val notificationCommentSheet = hasFastCommentSheet(root)
            if (notificationPostViewer || notificationCommentSheet) {
                if (ProtectionPreferences.getMode(this, ProtectedApp.INSTAGRAM) ==
                    ProtectionMode.BALANCED &&
                    allowBalancedInstagramNotificationViewer(
                        root, InstagramScreen.COMMENTS_OR_DETAIL)) return
                instagramNotificationContentLaunchUntil = 0L
                exitBlockedViewer(root, windowBounds, InstagramScreen.COMMENTS_OR_DETAIL)
            } else {
                instagramOverlay.hide()
                // Instagram can publish the notification row click before the
                // Reel/comment window exists. Poll briefly instead of waiting
                // for a second accessibility event; this short loop has no
                // overlay and is bounded by the launch grace above.
                queueInstagramScan(24L)
            }
            return
        }
        if (refreshBalancedInstagramCollection(root)) return
        if (hasFastNotificationsSurface(root)) {
            if (instagramNotificationReturn.isPending(scanNow)) {
                queueInstagramScan(32L)
                return
            }
            clearDirectConversationEntryGuard()
            if (lastInstagramScreen != InstagramScreen.NOTIFICATIONS) clearDirectDetailsScrollGuard()
            clearInstagramResumeProtection()
            instagramOverlay.hide()
            cancelInstagramExit()
            observeInstagramScreen(InstagramScreen.NOTIFICATIONS, root.windowId)
            refreshInstagramNotificationBackGuard(root, windowBounds)
            refreshInstagramNotificationEntryGuards()
            return
        }
        if (hasFastStoryViewer(root)) {
            clearDirectConversationEntryGuard()
            profileAllowedDepartureUntil = 0L
            if (instagramStoriesBlocked()) {
                exitBlockedViewer(root, windowBounds, InstagramScreen.STORY, useGlobalBackOnly = true)
                return
            }
            exitGate.reset()
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
            if (!instagramProfilePostsBlocked()) {
                releaseBalancedInstagramProfile(root, windowBounds)
                return
            }
            if (hasVisibleProfileNativeSheet(root) || hasVisibleAboutAccountSurface(root)) {
                profileAllowedDepartureUntil = 0L
                armInstagramProfileReturn(root, scanNow)
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
            if (applyStrictProfileCurtainResult(root, windowBounds, earlyProfileBounds)) {
                clearInstagramProfileReturn()
            }
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
        // The fast checks above took several hundred ms while Instagram animated
        // a return from Saved; the feed may be back by now. Re-check with a
        // fresh root before the full tree walk (another ~0.5-1 s).
        if (instagramCollectionFeedOrigin != null &&
            precoverCollectionReturn(rootInActiveWindow ?: root)) return
        stageStarted = SystemClock.uptimeMillis()
        val yieldFeedSnapshot = earlyProfileBounds == null &&
            InstagramNotificationShadePolicy.isProtectedFeedOrigin(lastInstagramScreen) &&
            instagramOverlay.visibleCurtain() != null && instagramFeedSnapshotYields < 2
        val signals = collectInstagramSnapshot(root,
            event?.takeIf { it.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED &&
                it.windowId == root.windowId &&
                SystemClock.uptimeMillis() - it.eventTime in 0L..1_500L }?.source,
            protectedProfileGrid = earlyProfileBounds != null && hasVisibleProfileHeaderMarkers(root),
            yieldProtectedFeed = yieldFeedSnapshot)
        logSlowInstagramStage("snapshot", stageStarted)
        if (signals == null) {
            // Never classify an incomplete tree or remove the existing curtain.
            // Return to the event loop so pending Direct clicks and a fresh root
            // can be handled instead of walking the old transition for a second.
            instagramFeedSnapshotYields++
            Log.d(TAG, "IG_FEED_SNAPSHOT_YIELD count=$instagramFeedSnapshotYields")
            queueInstagramScan(24L)
            return
        }
        instagramFeedSnapshotYields = 0
        val retainedCurtain = instagramOverlay.visibleCurtain()
        val profileSideSurface = InstagramProfileSurfacePolicy.hasAllowedLayer(
            signals.ids, signals.titles)
        if (retainedCurtain != null &&
            retainedCurtain.first in setOf(InstagramScreen.EXPLORE, InstagramScreen.HOME_FEED) &&
            (profileSideSurface ||
                InstagramProfileSurfacePolicy.isSettingsSurfaceTitle(signals.titles))) {
            // Saved/Likes/Reposts can return to the profile menu. That menu is
            // not a feed, so an older Explore/Home curtain must not survive on
            // top of its settings rows while the classifier is UNKNOWN.
            Log.d(TAG, "IG_SETTINGS_RELEASE_STALE_CURTAIN source=${retainedCurtain.first}")
            hideInstagramContentOverlay()
            instagramEntryTargets.clear()
            return
        }
        val profileContext = lastInstagramScreen == InstagramScreen.PROFILE || signals.ids.any { id ->
            id in setOf("profile_header_container", "profile_viewpager", "profile_tabs_container")
        }
        // Menu collection screens are Bloks-driven and expose only an action-bar title.
        // Balanced treats them as a user-curated library; Strict keeps the existing
        // one-shot Back behavior. The title check is intentionally independent of
        // profileContext because Instagram can briefly report UNKNOWN during entry.
        if (InstagramProfileSurfacePolicy.isUserCollectionTitle(signals.titles)) {
            if (refreshBalancedInstagramCollection(root, signals.titles)) return
            val collectionOrigin = lastInstagramScreen
            // Saved/Likes/Reposts opened from the profile menu return to that
            // allowed menu, so an older Explore/Home curtain must not survive
            // there. Opened from Explore's or Home's own bookmark button, Back
            // lands on that same feed: removing its curtain first exposed the
            // grid until the next scan re-covered it (0.7-2 s on device).
            val keepProtectedOrigin =
                InstagramProfileSurfacePolicy.keepsFeedCoverOnCollectionExit(
                    collectionOrigin, retainedCurtain?.first)
            Log.d(TAG, "IG_BLOCKED_COLLECTION_EXIT origin=$collectionOrigin " +
                "keepCover=$keepProtectedOrigin")
            exitBlockedViewer(root, windowBounds, InstagramScreen.COMMENTS_OR_DETAIL,
                preserveObservedOrigin = true,
                keepOriginCover = keepProtectedOrigin)
            return
        }
        if (profileContext && InstagramProfileSurfacePolicy.hasAllowedLayer(signals.ids, signals.titles)) {
            profileAllowedDepartureUntil = 0L
            armInstagramProfileReturn(root, scanNow)
            // Native Back takes a few frames to close the allowed layer. A
            // precovered curtain mounted directly before Back may be dropped by
            // this verified-if-stale layer branch while the tree is still
            // transitional, reopening the protected profile before the next
            // allows-classified scan; hold it through that interval.
            if (scanNow - strictProfilePrecoverAtMs >= 700L ||
                instagramOverlay.visibleProfileBounds() == null) {
                releaseAllowedProfileSurface()
            }
            return
        }
        // Source ancestry describes the old screen. Only the current tree classifies
        // the destination; a click may arm a shield, but cannot trigger BACK itself.
        val classifiedScreen = InstagramProtectionEngine.classify(
            signals.copy(sourceIds = emptySet(), sourceLabels = emptySet(), sourceTab = null))
        val standardHeaderAttached = attachedInstagramBounds(
            root, windowBounds, "main_feed_action_bar", requireWide = true) != null
        val standardStoriesAttached = findAttachedHomeStoriesBounds(root, windowBounds) != null
        val returnedToStandardHome = InstagramHomeGeometry.returnedToStandardFeed(
            alternateFeedActive = instagramAlternateFeedActive,
            // Instagram leaves the alternate feed's old Back node attached for
            // a few frames after returning Home. Its header is outside our
            // curtain, so the current visible snapshot is authoritative here;
            // an attached-but-hidden Back must not strand alternate geometry.
            backVisible = signals.hasBackNavigation,
            mainHeaderAttached = standardHeaderAttached,
            storiesAttached = standardStoriesAttached
        )
        // Saved's All Posts surface looks like Home once its action-bar title
        // scrolls away, even though the selected Reel and its audio player are
        // still mounted in the list. Keep it classified as the verified Reel
        // until the list is actually exited; otherwise a Home curtain can be
        // shown over a still-playing video without dispatching Back.
        val savedCollectionReelSurface = hasFastSavedCollectionReelList(root) &&
            (lastInstagramScreen in setOf(
                InstagramScreen.SAVED_COLLECTION, InstagramScreen.REELS) ||
                (instagramBalancedReelOrigin == InstagramScreen.SAVED_COLLECTION &&
                    instagramReelsSession.watching))
        val screen = when {
            savedCollectionReelSurface -> InstagramScreen.REELS
            returnedToStandardHome -> InstagramScreen.HOME_FEED
            classifiedScreen == InstagramScreen.REELS && keepVerifiedHome -> {
                Log.d(TAG, "IG_HOME_PLAYER_MUTATION_IGNORED window=${root.windowId}")
                InstagramScreen.HOME_FEED
            }
            else -> classifiedScreen
        }
        val alternateFeedDetected = screen == InstagramScreen.HOME_FEED &&
            InstagramHomeGeometry.isAlternateFeed(
                signals.ids, signals.titles, signals.hasBackNavigation)
        if (returnedToStandardHome) {
            instagramAlternateFeedActive = false
            Log.d(TAG, "IG_ALTERNATE_FEED_RETURN standard-home-ready")
        } else if (alternateFeedDetected) {
            instagramAlternateFeedActive = true
        } else if (screen != InstagramScreen.UNKNOWN &&
            (screen != InstagramScreen.HOME_FEED ||
                ("main_feed_action_bar" in signals.ids && !signals.hasBackNavigation))) {
            instagramAlternateFeedActive = false
        }
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
        val mediaEntriesBlocked = screen == InstagramScreen.DIRECT_MESSAGES &&
            InstagramDmInteractionPolicy.blocksSharedMedia(
                ProtectionPreferences.getMode(this, ProtectedApp.INSTAGRAM))
        val canGuardEntries = screen != InstagramScreen.REELS && screen != InstagramScreen.STORY &&
            screen != InstagramScreen.CREATION && screen != InstagramScreen.UNKNOWN
        instagramOverlay.updateEntryGuards(if (canGuardEntries) {
            instagramEntryTargets.filter {
                (it.second || mediaEntriesBlocked) &&
                    (InstagramDmInteractionPolicy.mayGuardEntry(screen, it.second) ||
                        mediaEntriesBlocked)
            }.map { it.first }.distinct()
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
                    applyStrictProfileCurtain(root, windowBounds, bounds)
                    if (profileScrollCover.isHolding) queueInstagramScan(48L)
                } else if (coverStrictProfileBeforeGrid(root, windowBounds)) {
                    // Instagram publishes header markers seconds before the grid
                    // pager measures. In Strict that interval leaves every
                    // thumbnail and caption exposed; mount a conservative
                    // full-width cover now and let the ensure loop refine it
                    // once real geometry lands.
                    queueInstagramScan(48L)
                } else {
                    queueInstagramScan(80L)
                }
            }
                else -> Unit
            }
            InstagramProtectionAction.ALLOW -> {
                // Keyword search opens on an algorithmic "For you" grid of
                // Reels and posts: the Explore feed under a search field.
                // Account search (the Accounts tab, typed suggestions) stays
                // open; only that grid is covered, as Explore is.
                val keywordGrid = if (screen == InstagramScreen.SEARCH_RESULTS) {
                    keywordResultsGridBounds(root, windowBounds)
                } else null
                if (keywordGrid == null || !showInstagramCurtain(keywordGrid, InstagramScreen.EXPLORE)) {
                    hideInstagramContentOverlay()
                }
                if (screen == InstagramScreen.PROFILE) {
                    // The Balanced profile is an allowed surface but the Reels
                    // tab stays blocked: re-assert its guard on every verified
                    // frame so the entry window after a tab tap is covered.
                    refreshMainReelsTabGuard(root)
                }
            }
            else -> Unit
        }
        // Touch-first return from search results: while that screen is the
        // verified surface, guard its Back control so the Explore curtain is
        // drawn before Instagram runs its own back navigation. Any other
        // destination retires the guard.
        if (screen == InstagramScreen.SEARCH_RESULTS &&
            stateDecision.action == InstagramProtectionAction.ALLOW) {
            val back = findInstagramTopLeftBack(root, windowBounds)
            instagramOverlay.updateSearchReturnGuard(
                back?.let { Rect().also(it::getBoundsInScreen) }, ::onSearchReturnBack)
        } else {
            instagramOverlay.updateSearchReturnGuard(null) {}
        }
        // Keep the persistent Reels-tab guard fresh on every verified frame:
        // present on the main tabs, retired in viewers and sheets.
        refreshMainReelsTabGuard(root)
        if (instagramOverlay.hasVisibleWindows) scheduleOverlayForegroundCheck()
    }

    /**
     * Transition covers can be computed while the bottom bar is momentarily
     * unmeasurable; the fallback inset then lands inside the navigation bar.
     * Clamp the curtain's bottom edge to the visible tab bar top (or the last
     * verified curtain bottom) so a precover never covers the bottom menu.
     */
    private fun keywordResultsGridBounds(root: AccessibilityNodeInfo, window: Rect): Rect? {
        val tabs = findVisibleNodeBounds(root, "$INSTAGRAM_PACKAGE:id/search_tab_bar_layout")
            ?: return null
        // The keyword page always shows its query field directly above the
        // tabs. Other surfaces misread as search results (the Story editor
        // was) must never receive this curtain.
        val field = findVisibleNodeBounds(root, "$INSTAGRAM_PACKAGE:id/action_bar_search_edit_text")
            ?: return null
        if (tabs.top < field.bottom || tabs.top - field.bottom > dp(48)) return null
        val gridVisible =
            findVisibleNodeBounds(root, "$INSTAGRAM_PACKAGE:id/grid_card_layout_container") != null ||
                findVisibleNodeBounds(root, "$INSTAGRAM_PACKAGE:id/media_previews_rv") != null
        if (!gridVisible) return null
        val navigationTop = findVisibleNodeBounds(root, "$INSTAGRAM_PACKAGE:id/tab_bar")?.top
        val bottom = navigationTop?.takeIf { it > tabs.bottom } ?: window.bottom
        return Rect(window.left, tabs.bottom, window.right, bottom).takeIf { it.height() > dp(80) }
    }

    private fun clampCurtainBottomToNavigation(root: AccessibilityNodeInfo, bounds: Rect) {
        val tabTop = findVisibleNodeBounds(root, "$INSTAGRAM_PACKAGE:id/tab_bar")?.top
        // The retained curtain may have been measured during the task animation
        // or on another surface. Using it as a permanent ceiling leaves a strip
        // of the feed visible even after the real navigation bar has settled.
        // Missing chrome is already handled by stableMainNavigationTop().
        bounds.bottom = InstagramBottomNavigationGeometry.alignToVisibleNavigation(
            minimumTop = bounds.top + dp(80),
            windowBottom = instagramWindowBounds(root).bottom,
            currentBottom = bounds.bottom,
            visibleNavigationTop = tabTop
        )
    }

    /**
     * Back control of the search results was tapped. Draw the Explore curtain
     * first, then let Instagram run its native Back (arrow click, with a
     * global fallback). The keyboard-open case performs the native Back only:
     * there the first Back closes the IME, not the search surface.
     */
    private fun onSearchReturnBack() {
        if (!ProtectionPreferences.isEnabled(this, ProtectedApp.INSTAGRAM) ||
            lastInstagramScreen != InstagramScreen.SEARCH_RESULTS) return
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != INSTAGRAM_PACKAGE) return
        if (isInputMethodVisible()) {
            performGlobalAction(GLOBAL_ACTION_BACK)
            queueInstagramScan(0L)
            return
        }
        clearInstagramResumeProtection()
        currentInstagramWindowId = root.windowId
        val bounds = calculateExploreBounds(root, instagramWindowBounds(root))
        clampCurtainBottomToNavigation(root, bounds)
        val mounted = showInstagramCurtain(bounds, InstagramScreen.EXPLORE)
        if (!mounted) {
            performGlobalAction(GLOBAL_ACTION_BACK)
            queueInstagramScan(0L)
            return
        }
        // Touch-first: the Explore surface returns with the tab bar already
        // blocked, so the immediate tab spam after touching Back cannot slip.
        premountReelsTabGuard()
        if (ProtectionFlightRecorder.ENABLED) ProtectionFlightRecorder.record(
            this, "instagram.search.back", "precover=mounted")
        instagramOverlay.afterCurtainDraw {
            val current = rootInActiveWindow ?: return@afterCurtainDraw
            if (current.packageName?.toString() != INSTAGRAM_PACKAGE) return@afterCurtainDraw
            val accepted = findInstagramTopLeftBack(current, instagramWindowBounds(current))
                ?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
            if (!accepted) performGlobalAction(GLOBAL_ACTION_BACK)
            queueInstagramScan(0L)
        }
    }

    private fun hasVisibleProfileHeaderMarkers(root: AccessibilityNodeInfo): Boolean =
        listOf("profile_header_container", "profile_tabs_container", "profile_action_bar").any { id ->
            igVisible(root, id)
        }

    // The picker screen is cheap to identify: one id lookup plus a title match, so the
    // release decision never depends on the bounded snapshot walk reaching the title.
    private fun isHighlightPickerForeground(root: AccessibilityNodeInfo): Boolean =
        root.findAccessibilityNodeInfosByViewId("$INSTAGRAM_PACKAGE:id/action_bar_title").any { node ->
            val title = listOfNotNull(node.text?.toString(), node.contentDescription?.toString())
                .joinToString(" ")
            InstagramProfileSurfacePolicy.isHighlightPicker(setOf(title))
        }

    private var expandedPicCheckedAt = 0L
    private var expandedPicVisible = false

    /**
     * Instagram renders the avatar long-press overlay in its own application
     * window, so the focused root alone cannot prove it. Every Instagram
     * window is checked, throttled so the watcher cadence stays cheap.
     */
    private fun instagramExpandedProfilePictureVisible(): Boolean {
        val now = SystemClock.uptimeMillis()
        if (profileLayerCacheValid(expandedPicCheckedAt, now, expandedPicVisible)) return expandedPicVisible
        expandedPicCheckedAt = now
        fun rootHasOverlay(root: AccessibilityNodeInfo?): Boolean =
            root != null && root.packageName?.toString() == INSTAGRAM_PACKAGE &&
                runCatching {
                    root.findAccessibilityNodeInfosByViewId(
                        "$INSTAGRAM_PACKAGE:id/expanded_profile_pic")
                }.getOrNull().orEmpty().any { it.isVisibleToUser }
        // Only the active window: the expanded avatar takes focus when shown.
        // Probing every application window queried Story/Reel viewer windows
        // whose UI thread was busy; each query waited for the 5 s interaction
        // timeout and froze protection for up to 37 s on a real device.
        expandedPicVisible = rootHasOverlay(rootInActiveWindow)
        return expandedPicVisible
    }

    private var profileYieldCheckedAt = 0L
    private var profileYieldVisible = false
    private var profileLayerInvalidatedAt = 0L

    /**
     * Profile-side layers (expanded avatar, share card, Edit Profile, sheets,
     * About) only appear after a tap or a window change. Their ~30 exact-ID
     * probes ran every 120 ms regardless and were the bulk of a 100-700 ms
     * geometry-watch tick. A "no layer" result measured at least one second
     * after the last structural event stays valid (refreshed every two
     * seconds anyway). A "layer present" result keeps the 120 ms throttle: a
     * layer can close by a swipe without any click or window event, and a
     * stale true would keep the Strict grid uncovered for up to two seconds.
     */
    private fun profileLayerCacheValid(checkedAt: Long, now: Long, cachedVisible: Boolean): Boolean =
        now - checkedAt < 120L ||
            (!cachedVisible && checkedAt - profileLayerInvalidatedAt >= 1_000L &&
                now - checkedAt < 2_000L)

    /**
     * Allowed profile layers that own the screen without changing the tracked
     * profile screen: Edit Profile and the highlights picker. The scan path
     * releases the curtain for them, so the geometry watch must yield too;
     * otherwise it re-covers the layer within one tick and the curtain
     * flickers and finally sticks over the allowed screen.
     */
    private fun instagramProfileCurtainMustYield(root: AccessibilityNodeInfo?): Boolean {
        if (instagramExpandedProfilePictureVisible()) return true
        val now = SystemClock.uptimeMillis()
        if (profileLayerCacheValid(profileYieldCheckedAt, now, profileYieldVisible)) return profileYieldVisible
        profileYieldCheckedAt = now
        val titleRoot = root ?: rootInActiveWindow
        if (titleRoot == null || titleRoot.packageName?.toString() != INSTAGRAM_PACKAGE) {
            profileYieldVisible = false
            return false
        }
        // The notifications/activity surface is an allowed screen: while it is
        // foreground the profile curtain must stay away (its rows can carry
        // post previews whose taps the blocked-viewer exit owns).
        if (hasFastNotificationsSurface(titleRoot)) {
            profileYieldVisible = true
            return true
        }
        // Detailed classification: every allowed profile-side layer (share
        // card, edit profile, native sheets, menu, About) is recognised by its
        // exact visible ids and action-bar titles so the user's intentional
        // surfaces are never curtained.
        val visibleMarkers = InstagramProfileSurfacePolicy.allowedSurfaceMarkerIds
            .filterTo(mutableSetOf()) { igVisible(titleRoot, it) }
        val titles = cachedNodeLookup(titleRoot, "$INSTAGRAM_PACKAGE:id/action_bar_title")
            .filter { it.isVisibleToUser }
            .mapNotNull { node -> node.text?.toString() ?: node.contentDescription?.toString() }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
        profileYieldVisible = InstagramProfileSurfacePolicy.isAllowedProfileSurface(
            visibleMarkers, titles)
        return profileYieldVisible
    }

    /**
     * Strict exposes exactly two native bands: the posts/followers/following
     * numbers and the Follow/Message row. Highlights, name, avatar, bio and the
     * media grid stay covered. Plans over the canonical app-bar-floor body so
     * the result is independent of which geometry owner produced the input.
     */
    /**
     * The bottom Reels tab is blocked in both modes, but the settled-surface
     * fast paths return before the general guard update. Refresh its dedicated
     * guard (from the current tree; the tab bar is static across the main
     * tabs) so a tap spam cannot enter Reels natively, including the first
     * second of a screen transition. Passing the visible clips_tab bounds
     * keeps a persistent window that unrelated guard updates never clear;
     * when the tab is not visible the guard retires - except during a
     * premounted transition window ([premountReelsTabGuard]), where the last
     * verified rectangle stays mounted until the new surface publishes its
     * own bar.
     */
    private fun refreshMainReelsTabGuard(root: AccessibilityNodeInfo, force: Boolean = false) {
        if (force) reelsTabGuardHoldUntil = 0L
        val window = instagramWindowBounds(root)
        val found = root.findAccessibilityNodeInfosByViewId("$INSTAGRAM_PACKAGE:id/clips_tab")
            .firstNotNullOfOrNull { node ->
                val bounds = Rect().also(node::getBoundsInScreen)
                bounds.takeIf {
                    node.isVisibleToUser && it.intersect(window) &&
                        it.height() in 1 until window.height() / 2
                }
            }
            ?.let { Rect(it).apply { inset(-dp(4), 0) } }
        val guard: Rect?
        val state: String
        if (found != null) {
            reelsTabGuardHoldUntil = 0L
            lastReelsTabGuardBounds = found
            guard = found
            state = "found"
        } else if (!force && SystemClock.uptimeMillis() < reelsTabGuardHoldUntil) {
            // Transition window (search return, notification return): the new
            // surface has not published its bottom bar yet; keep the last
            // verified rectangle so an immediate tab spam cannot slip through.
            guard = lastReelsTabGuardBounds
            state = "hold"
        } else {
            // Retire now, but keep the last verified rectangle: the next
            // transition can premount it before the bar is published again.
            guard = null
            state = "retired"
        }
        if (ProtectionFlightRecorder.ENABLED) {
            val key = "$state:${guard?.toShortString()}"
            if (key != lastReelsTabGuardLogState) {
                lastReelsTabGuardLogState = key
                ProtectionFlightRecorder.record(this, "instagram.reels.guard",
                    "state=$state bounds=${guard?.toShortString() ?: "none"} " +
                        "last=$lastInstagramScreen")
            }
        }
        instagramOverlay.updateReelsTabGuard(guard)
    }

    /**
     * Covers the transition into a main surface: the incoming tree can be
     * visible for a few frames before its bottom bar node exists, and a tab
     * spam in that window used to reach Instagram natively. The last verified
     * rectangle is mounted immediately and kept until the surface publishes
     * its own bar (bounded by the hold).
     */
    private fun premountReelsTabGuard() {
        val bounds = lastReelsTabGuardBounds ?: return
        reelsTabGuardHoldUntil = SystemClock.uptimeMillis() + 2_000L
        instagramOverlay.updateReelsTabGuard(bounds)
    }

    /**
     * Arms the opaque cover for a Reels feed slip. Called the moment the tap
     * provably reached Instagram (its clips_tab click event) and again when a
     * feed exit is classified. It is retired only after the exit is verified
     * (or after a bounded window when no feed ever appeared).
     */
    private fun armReelsSlipCover() {
        instagramOverlay.showReelsSlipCover(currentDisplayBounds())
        reelsSlipCoverExit = false
        reelsSlipCoverUntil = SystemClock.uptimeMillis() + 1_200L
        if (ProtectionFlightRecorder.ENABLED) ProtectionFlightRecorder.record(
            this, "instagram.reels.slip", "cover=armed")
    }

    private fun applyStrictProfileCurtain(root: AccessibilityNodeInfo, window: Rect, bounds: Rect) {
        armProfileGeometryWatch()
        refreshMainReelsTabGuard(root)
        if (!instagramProfilePostsBlocked()) {
            instagramOverlay.updateStrictProfilePieceGuards(emptyList())
            showInstagramCurtain(bounds, InstagramScreen.PROFILE)
            return
        }
        // Allowed profile layers (avatar preview, Edit Profile, highlights
        // picker) own the screen: the curtain yields so they stay fully
        // visible and interactive, and returns as soon as they close.
        if (instagramProfileCurtainMustYield(root)) {
            instagramOverlay.updateStrictProfilePieceGuards(emptyList())
            instagramOverlay.hide()
            return
        }
        // The profile photo, the numbers row and the Follow/Message buttons
        // stay native. Everything below them (grid) and the highlight tray
        // (which are rendered above the row in current Instagram layouts) are
        // covered, so "Öne çıkaranlar" can neither be seen nor entered.
        val rowBottom = instagramStrictProfileRowBottom(root, window)
        val floor = if (profileScrollFloor > window.top) profileScrollFloor else
            calculateProfileScrollFloor(root, window)
        if (rowBottom == null) {
            if (!hasVisibleProfileHeaderMarkers(root)) {
                // The profile shell is not in the tree: a full-screen layer
                // (hamburger menu, account switcher, other page) owns the
                // screen. Never mount a curtain over it.
                instagramOverlay.updateStrictProfilePieceGuards(emptyList())
                instagramOverlay.hide()
                return
            }
            // The row and its anchors are not measurable yet (fragments
            // building): never expose highlights or the grid while the header
            // rebuilds.
            instagramOverlay.updateStrictProfilePieceGuards(emptyList())
            showInstagramCurtain(bounds, InstagramScreen.PROFILE)
            return
        }
        val bottom = stableMainNavigationTop(root, window, InstagramScreen.PROFILE)
            .takeIf { it > window.centerY() } ?: window.bottom
        val tray = findVisibleNodeBounds(root, "$INSTAGRAM_PACKAGE:id/highlights_tray")
            ?.takeIf { it.intersect(window) && it.height() in 1..dp(220) }
        val rowTop = rowBottom.coerceIn(floor, bottom - 1)
        if (tray != null && tray.top < rowTop) {
            // Highlights sit above the Follow/Message row: cover the tray with
            // its own opaque band and continue the curtain below the row, so
            // both "highlights covered" and "buttons native" hold at once.
            val trayBand = Rect(window.left, tray.top.coerceIn(floor, bottom - 1),
                window.right, tray.bottom.coerceIn(floor + 1, bottom))
            if (!trayBand.isEmpty) {
                instagramOverlay.updateStrictProfilePieceGuards(listOf(trayBand))
            } else {
                instagramOverlay.updateStrictProfilePieceGuards(emptyList())
            }
            showInstagramCurtain(Rect(window.left, rowTop, window.right, bottom),
                InstagramScreen.PROFILE)
            return
        }
        // No tray above the row (or the tray renders below it): a single
        // curtain from the row's bottom covers the tray and the grid together.
        instagramOverlay.updateStrictProfilePieceGuards(emptyList())
        showInstagramCurtain(Rect(window.left, rowTop, window.right, bottom),
            InstagramScreen.PROFILE)
    }

    /**
     * Bottom edge of the Follow/Message row anchors as the settled curtain
     * top; null while the row is unmeasured (full-cover period). The tab
     * container is the stable anchor in collapsed poses where the row id
     * leaves the tree.
     */
    private fun instagramStrictProfileRowBottom(
        root: AccessibilityNodeInfo,
        window: Rect
    ): Int? {
        var bottom: Int? = null
        for (id in listOf(
            "profile_header_actions_top_row", "row_profile_header_button_chaining"
        )) {
            findVisibleNodeBounds(root, "$INSTAGRAM_PACKAGE:id/$id")?.let { found ->
                if (found.intersect(window) && found.height() in 1..dp(96)) {
                    bottom = minOf(bottom ?: found.bottom, found.bottom)
                }
            }
        }
        if (bottom != null) return bottom
        findVisibleNodeBounds(root, "$INSTAGRAM_PACKAGE:id/profile_tabs_container")?.let { tabs ->
            if (tabs.intersect(window) && tabs.height() in 1..dp(150)) {
                bottom = tabs.top
            }
        }
        return bottom
    }

    /**
     * Strict must not expose the profile grid while Instagram has not yet
     * measured the pager. When the destination is already classified as
     * PROFILE and the header shell is visible, mount a conservative
     * application-space cover immediately and let the ensure loop refine it to
     * exact grid geometry. The wait-for-grid retries never cover the screen;
     * they only keep the curtain from lifting.
     */
    private fun coverStrictProfileBeforeGrid(root: AccessibilityNodeInfo, window: Rect): Boolean {
        if (!instagramProfilePostsBlocked()) return false
        if (!hasVisibleProfileHeaderMarkers(root)) return false
        if (hasVisibleProfileNativeSheet(root) || hasVisibleAboutAccountSurface(root)) return false
        if (hasFastStoryViewer(root) || hasFastMainReelsTabDestination(root) ||
            hasFastReelsViewer(root) || hasFastSinglePostViewer(root) ||
            hasFastNotificationsSurface(root)) return false
        profileWindowId = root.windowId
        val bounds = conservativeInstagramResumeBounds(root, window, InstagramScreen.PROFILE) ?: return false
        applyStrictProfileCurtain(root, window, bounds)
        armProfileCurtainEnsure()
        return instagramOverlay.visibleProfileBounds() != null
    }

    private var instagramSearchProfileProbeAt = 0L

    /**
     * Instagram reports the account-row click only 450-600 ms after the finger,
     * so the click path cannot precover the destination. While Search is the
     * verified surface, poll the profile header shell at a coarse cadence: if
     * the new fragment has already started building, the curtain goes up before
     * its grid publishes. Click events keep their normal routing; this only
     * reads three cached ids at most twice per second.
     */
    private fun probeInstagramSearchForProfile(root: AccessibilityNodeInfo, now: Long): Boolean {
        if (lastInstagramScreen != InstagramScreen.SEARCH_RESULTS ||
            instagramProfileIngress.pending || instagramProfileIngress.awaitingCurtainDraw) return false
        if (!instagramProfilePostsBlocked()) return false
        if (now - instagramSearchProfileProbeAt < 150L) return false
        instagramSearchProfileProbeAt = now
        val detected = igVisible(root, "row_profile_header_button_chaining") ||
            igVisible(root, "profile_header_follow_button")
        if (!detected) return false
        return coverStrictProfileBeforeGrid(root, instagramWindowBounds(root))
    }

    private fun profileGridBounds(root: AccessibilityNodeInfo, window: Rect): Rect? {
        val pager = cachedNodeLookup(root, "$INSTAGRAM_PACKAGE:id/profile_viewpager")
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
        fun visible(id: String): Boolean = cachedNodeLookup(root,
            "$INSTAGRAM_PACKAGE:id/$id").any { it.isVisibleToUser }
        fun selected(id: String): Boolean = cachedNodeLookup(root,
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

        val mainReelsGuard = cachedNodeLookup(root, "$INSTAGRAM_PACKAGE:id/clips_tab")
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
        if (screen == InstagramScreen.HOME_FEED && instagramAlternateFeedActive) {
            // This shortcut is reached only when the selected Home tab and its
            // visible main header are both present and no visible Back exists.
            // Clear Following/Favorites geometry before measuring Home; the
            // shortcut returns before the general snapshot can clear it.
            instagramAlternateFeedActive = false
        }
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

    private fun hasFastReelsViewer(
        root: AccessibilityNodeInfo,
        directLaunchPending: Boolean = false
    ): Boolean {
        if (!igVisible(root, "root_clips_layout") && !igVisible(root, "clips_viewer") &&
            !igVisible(root, "clips_viewer_view_pager") && !igVisible(root, "clips_view_pager")) return false
        val controlsVisible = igVisible(root, "clips_ufi") || igVisible(root, "clips_viewer_action_bar")
        val deliberateProfileOrigin = lastInstagramScreen in setOf(
            InstagramScreen.PROFILE, InstagramScreen.SAVED_COLLECTION) ||
            SystemClock.uptimeMillis() < instagramBalancedProfileReelIntentUntil ||
            SystemClock.uptimeMillis() < instagramBalancedCollectionReelIntentUntil
        if (!controlsVisible && !directLaunchPending && !deliberateProfileOrigin) return false
        // Instagram briefly clears the selected state of feed_tab while an
        // embedded feed video replaces its player tree. The Home action bar is
        // the more stable host signal. Requiring the selected tab here made a
        // normal feed video look like a full-screen Reel at the end of playback
        // and caused an erroneous GLOBAL_ACTION_BACK.
        val homeHostVisible = igVisible(root, "main_feed_action_bar")
        return InstagramEntryPolicy.isFastViewer(
            playerVisible = true,
            controlsVisible = controlsVisible,
            storyVisible = hasFastStoryViewer(root),
            homeHostVisible = homeHostVisible,
            backVisible = hasFastActionBarBack(root),
            directLaunchPending = directLaunchPending
        )
    }

    private fun isFreshNotificationViewerEvent(event: AccessibilityEvent): Boolean {
        val viewerIds = setOf(
            "root_clips_layout", "clips_viewer", "clips_viewer_view_pager",
            "clips_view_pager", "video_container", "clips_ufi",
            "clips_viewer_action_bar", "bottom_sheet_container",
            "layout_comment_thread_edittext_multiline", "comment_thread"
        )
        var node = event.source
        repeat(10) {
            val current = node ?: return false
            if (compactId(current.viewIdResourceName) in viewerIds) return true
            node = current.parent
        }
        return false
    }

    private fun dispatchNotificationViewerBackFromEvent(
        event: AccessibilityEvent,
        now: Long
    ) {
        // A single Instagram notification launch can publish several delayed
        // Reel/comment window events over a few seconds. Treat them as one
        // attempt; otherwise a late event starts a second Back sequence on
        // the next video.
        if (instagramNotificationEventLocked) return
        if (now - instagramNotificationLastEventExitAt < 5_000L) return
        if (instagramExitInProgress) return
        instagramNotificationLastEventExitAt = now
        instagramNotificationEventLocked = true
        instagramNotificationExitGeneration++
        var node = event.source
        var commentLayer = false
        repeat(10) {
            val current = node ?: return@repeat
            val id = compactId(current.viewIdResourceName)
            if (id in setOf(
                    "bottom_sheet_container", "layout_comment_thread_edittext_multiline",
                    "comment_composer_parent_updated", "comment_thread")) {
                commentLayer = true
            }
            node = current.parent
        }
        instagramNotificationContentLaunchUntil = 0L
        instagramNotificationExitHoldUntil = now + 10_000L
        instagramNotificationReelExitIssued = true
        // A first GLOBAL_ACTION_BACK can be accepted while Instagram's
        // player animation is still settling, even when no comment sheet is
        // visible. Keep one guarded fallback for every event-proven viewer;
        // the worker cancels as soon as Notifications is the active root.
        instagramNotificationReelSecondBackIssued = false
        instagramNotificationCommentLayerObserved = commentLayer
        instagramNotificationViewerDetectedAt = now
        instagramExitInProgress = true
        instagramExitFromNotifications = true
        instagramExitFromDirectConversation = false
        instagramExitTreeQuietUntil = now + INSTAGRAM_EXIT_TREE_QUIET_MS
        val clickedViewerBack = performViewerBackFromEvent(event)
        val accepted = clickedViewerBack
        instagramNotificationBackCount = if (accepted) 1 else 0
        Log.d(TAG, "IG_NOTIFICATION_EVENT_REEL_EXIT commentLayer=$commentLayer " +
            "viewerBack=$clickedViewerBack eventAgeMs=${now - event.eventTime}")
        // Always schedule one fresh-window verification. ACTION_CLICK may
        // return true while Instagram is still animating and has not changed
        // the viewer state; the worker performs at most one more targeted
        // click and never uses global Back.
        scheduleFastNotificationSecondBack(initialDelayMs = if (commentLayer) 90L else 300L)
        queueInstagramScan(INSTAGRAM_EXIT_TREE_QUIET_MS)
    }

    private fun performViewerBackFromEvent(event: AccessibilityEvent): Boolean {
        rootInActiveWindow?.takeIf {
            it.packageName?.toString() == INSTAGRAM_PACKAGE &&
                !hasFastNotificationsSurface(it) &&
                (hasFastReelsViewer(it, directLaunchPending = true) ||
                    hasFastCommentSheetInRoot(it) || hasFastSinglePostViewer(it))
        }?.let {
            if (performViewerBackFromRoot(it)) return true
        }
        var node = event.source
        repeat(12) {
            val current = node ?: return false
            val back = runCatching {
                current.findAccessibilityNodeInfosByViewId(
                    "$INSTAGRAM_PACKAGE:id/action_bar_button_back")
                    .firstOrNull { candidate ->
                        candidate.isVisibleToUser && candidate.isClickable
                    }
            }.getOrNull()
            if (back?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) return true
            node = current.parent
        }
        return false
    }

    private fun performViewerBackFromRoot(root: AccessibilityNodeInfo): Boolean {
        val byId = runCatching {
            root.findAccessibilityNodeInfosByViewId(
                "$INSTAGRAM_PACKAGE:id/action_bar_button_back")
                .firstOrNull { candidate ->
                    candidate.isVisibleToUser && candidate.isClickable
                }
        }.getOrNull()
        val back = byId ?: findInstagramTopLeftBack(root, instagramWindowBounds(root))
        if (back?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) return true
        val bounds = back?.let { Rect().also(it::getBoundsInScreen) }
            ?.takeIf { !it.isEmpty }
            ?: return false
        val path = Path().apply {
            moveTo(bounds.exactCenterX(), bounds.exactCenterY())
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 60L))
            .build()
        return runCatching { dispatchGesture(gesture, null, null) }.getOrDefault(false)
    }

    /**
     * Instagram's profile Reel viewer uses a semantic ImageView labelled
     * "Back" on some builds instead of the action_bar_button_back id. Treat
     * both forms as the same navigation affordance; otherwise a valid
     * Profile -> Reel launch is mistaken for the blocked Reels tab.
     */
    private fun hasFastActionBarBack(root: AccessibilityNodeInfo): Boolean =
        igVisible(root, "action_bar_button_back") ||
            findInstagramTopLeftBack(root, instagramWindowBounds(root)) != null

    private fun hasFastSavedCollectionReelList(root: AccessibilityNodeInfo): Boolean =
        igVisible(root, "layout_listview_parent_container") &&
            igVisible(root, "video_container") &&
            !igVisible(root, "clips_viewer_view_pager") &&
            !igVisible(root, "clips_view_pager")

    private fun hasFastMainReelsTabDestination(root: AccessibilityNodeInfo): Boolean {
        val reelsSelected = igSelected(root, "clips_tab")
        if (!reelsSelected) return false
        val anotherMainTabSelected = listOf(
            "feed_tab", "direct_tab", "search_tab", "profile_tab"
        ).any { igSelected(root, it) }
        return InstagramEntryPolicy.isMainReelsTabDestination(
            reelsSelected = true,
            anotherMainTabSelected = anotherMainTabSelected,
            hasBackNavigation = igVisible(root, "action_bar_button_back")
        )
    }

    private fun hasFastStoryViewer(root: AccessibilityNodeInfo): Boolean =
        InstagramStoryIdentity.isViewer { id ->
            cachedNodeLookup(root, "$INSTAGRAM_PACKAGE:id/$id")
                .any { it.isVisibleToUser }
        }

    private var instagramStoryLaunchUntil = 0L
    private var instagramStoryLaunchEventDeadline = 0L
    private var profileWatchQuietUntil = 0L
    private var instagramModalViewerWindowId = -1
    private var instagramModalViewerSeenAt = 0L

    /**
     * Strict Story exit without remote queries. The tapped avatar's label says
     * it opens a Story, and the viewer arrives as a modal-activity window
     * event. While that window starts, Instagram's UI thread answers no
     * accessibility query for 1-2 s, so the tree-based classifier saw the Story
     * only after it had played for that long. Back is sent on the window event.
     */
    private fun handleStrictInstagramStoryLaunch(event: AccessibilityEvent, now: Long): Boolean {
        if (!instagramStoriesBlocked()) return false
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                val label = (event.text.joinToString(" ") + " " +
                    (event.contentDescription ?: "")).trim()
                instagramStoryLaunchUntil = if (now - event.eventTime in 0L..1_500L &&
                    InstagramStoryIdentity.isStoryLaunchLabel(label)) {
                    now + INSTAGRAM_STORY_LAUNCH_WINDOW_MS
                } else 0L
                instagramStoryLaunchEventDeadline = if (instagramStoryLaunchUntil != 0L) {
                    event.eventTime + INSTAGRAM_STORY_LAUNCH_WINDOW_MS
                } else 0L
                // Keep the profile geometry watch off the starting viewer window.
                if (instagramStoryLaunchUntil != 0L) profileWatchQuietUntil = instagramStoryLaunchUntil
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                if (event.className?.toString()?.endsWith("TransparentModalActivity") == true) {
                    // Story and Reel viewers open in this window. Avatars in
                    // the Direct inbox send no click event (no label), so the
                    // classifier verifies the window now; its arrival also
                    // proves the transition is far enough for Back to land.
                    instagramModalViewerWindowId = event.windowId
                    instagramModalViewerSeenAt = now
                    armDirectModalCover(event.windowId, now)
                    queueInstagramScan(0L)
                }
                // Compare event timestamps: a late-delivered window event still
                // belongs to the tap that produced it.
                if (event.eventTime >= instagramStoryLaunchEventDeadline) return false
                if (!InstagramStoryIdentity.isStoryViewerWindowClass(event.className)) {
                    // Something else opened (expired Story -> profile, a chat):
                    // stop deferring scans so that surface is classified now.
                    instagramStoryLaunchUntil = 0L
                    instagramStoryLaunchEventDeadline = 0L
                    profileWatchQuietUntil = 0L
                    return false
                }
                instagramStoryLaunchUntil = 0L
                instagramStoryLaunchEventDeadline = 0L
                profileWatchQuietUntil = now + 400L
                val accepted = performGlobalAction(GLOBAL_ACTION_BACK)
                if (instagramBlockedAttempt.acceptedIntervention(accepted)) {
                    recordIntervention(INSTAGRAM_PACKAGE, System.currentTimeMillis())
                }
                Log.d(TAG, "IG_STORY_FAST_EXIT accepted=$accepted window=${event.windowId}")
                if (ProtectionFlightRecorder.ENABLED) ProtectionFlightRecorder.record(
                    this, "instagram.story.fast-exit", "accepted=$accepted window=${event.windowId}")
                // The regular classifier still verifies the return (and exits a
                // Story that survived Back) once the modal window settles.
                queueInstagramScan(250L)
                return true
            }
        }
        return false
    }

    private var directModalCoverWindowId = -1
    private var directModalCoverUntil = 0L
    private var directModalCoverExiting = false
    private val directModalCoverTimeout = Runnable { releaseDirectModalCover("timeout") }

    /**
     * A shared Reel in a Strict chat sends no click event, so there is no label
     * to act on before its viewer appears. The modal window event arrives with
     * the first viewer frames, ~160 ms before the classifier can query the
     * frozen viewer tree. Cover the display at once (no remote query); the
     * classifier then either exits the viewer or proves an allowed surface.
     */
    private fun armDirectModalCover(windowId: Int, now: Long) {
        if (!instagramStoriesBlocked() ||
            lastInstagramScreen != InstagramScreen.DIRECT_MESSAGES) return
        instagramOverlay.showReelsSlipCover(currentDisplayBounds())
        directModalCoverWindowId = windowId
        directModalCoverUntil = now + DIRECT_MODAL_COVER_MAX_MS
        directModalCoverExiting = false
        mainHandler.removeCallbacks(directModalCoverTimeout)
        mainHandler.postDelayed(directModalCoverTimeout, DIRECT_MODAL_COVER_MAX_MS)
        if (ProtectionFlightRecorder.ENABLED) ProtectionFlightRecorder.record(
            this, "instagram.dm.modal-cover", "armed window=$windowId")
    }

    private fun observeDirectModalCover(screen: InstagramScreen, windowId: Int) {
        if (directModalCoverWindowId == -1) return
        when {
            screen == InstagramScreen.REELS || screen == InstagramScreen.STORY ||
                screen == InstagramScreen.COMMENTS_OR_DETAIL -> directModalCoverExiting = true
            screen == InstagramScreen.UNKNOWN -> Unit
            // The modal itself is an allowed surface (profile, camera...).
            windowId == directModalCoverWindowId -> releaseDirectModalCover("allowed")
            // Back from the blocked viewer landed on the chat again.
            directModalCoverExiting -> releaseDirectModalCover("returned")
            // A stale chat frame right after the modal event proves nothing.
            SystemClock.uptimeMillis() >= directModalCoverUntil -> releaseDirectModalCover("expired")
        }
    }

    private fun releaseDirectModalCover(reason: String) {
        if (directModalCoverWindowId == -1) return
        directModalCoverWindowId = -1
        directModalCoverUntil = 0L
        directModalCoverExiting = false
        mainHandler.removeCallbacks(directModalCoverTimeout)
        instagramOverlay.hideReelsSlipCover()
        if (ProtectionFlightRecorder.ENABLED) ProtectionFlightRecorder.record(
            this, "instagram.dm.modal-cover", "released reason=$reason")
    }

    private fun instagramStoriesBlocked(): Boolean =
        InstagramProfileInteractionPolicy.exitsStoryViewer(
            ProtectionPreferences.getMode(this, ProtectedApp.INSTAGRAM))

    private fun instagramProfilePostsBlocked(): Boolean =
        InstagramProfileInteractionPolicy.blocksProfilePosts(
            ProtectionPreferences.getMode(this, ProtectedApp.INSTAGRAM))

    private fun allowBalancedInstagramProfilePost(root: AccessibilityNodeInfo): Boolean {
        val mode = ProtectionPreferences.getMode(this, ProtectedApp.INSTAGRAM)
        val now = SystemClock.uptimeMillis()
        var confirmed = instagramBalancedProfilePost.confirm(mode, now)
        // Some Instagram builds replace the grid without publishing a click
        // event for the thumbnail. A verified Profile -> static Post transition
        // is still an intentional profile selection; no other source receives
        // this fallback because its last confirmed screen is not PROFILE.
        if (!confirmed && InstagramProfileInteractionPolicy.allowsProfilePostFallback(
                mode, lastInstagramScreen)) {
            instagramBalancedProfilePost.arm(
                mode, verifiedProfile = true, profileThumbnail = true, now = now)
            confirmed = instagramBalancedProfilePost.confirm(mode, now)
            if (confirmed) Log.d(TAG, "IG_BALANCED_PROFILE_POST_PROFILE_FALLBACK")
        }
        if (!confirmed) return false
        stopProfileCurtainEnsure()
        clearDirectConversationEntryGuard()
        clearDirectDetailsScrollGuard()
        instagramOverlay.updateEntryGuards(emptyList(), isEnglish())
        hideInstagramContentOverlay()
        cancelInstagramExit()
        observeInstagramScreen(InstagramScreen.COMMENTS_OR_DETAIL, root.windowId)
        Log.d(TAG, "IG_BALANCED_PROFILE_POST_ALLOWED")
        return true
    }

    private fun allowBalancedInstagramNotificationViewer(
        root: AccessibilityNodeInfo,
        screen: InstagramScreen
    ): Boolean {
        if (ProtectionPreferences.getMode(this, ProtectedApp.INSTAGRAM) !=
            ProtectionMode.BALANCED) return false
        if (screen != InstagramScreen.REELS && screen != InstagramScreen.COMMENTS_OR_DETAIL) {
            return false
        }
        if (screen == InstagramScreen.REELS) {
            if (instagramReelsSession.exiting) return false
            if (instagramReelsSession.observe(ProtectionMode.BALANCED, viewerVisible = true) ==
                InstagramReelsDecision.EXIT) return false
            instagramBalancedReelOrigin = InstagramScreen.NOTIFICATIONS
        } else {
            instagramBalancedNotificationPostWatching = true
        }
        // The notification entry grants exactly this one destination. It does
        // not grant a feed, pager continuation, or any later notification row.
        instagramNotificationContentLaunchUntil = 0L
        instagramNotificationExitHoldUntil = 0L
        clearDirectConversationEntryGuard()
        clearDirectDetailsScrollGuard()
        instagramOverlay.updateEntryGuards(emptyList(), isEnglish())
        hideInstagramContentOverlay()
        cancelInstagramExit()
        observeInstagramScreen(screen, root.windowId)
        Log.d(TAG, "IG_BALANCED_NOTIFICATION_ITEM_ALLOWED screen=$screen")
        return true
    }

    private fun allowBalancedInstagramReel(root: AccessibilityNodeInfo): Boolean {
        val mode = ProtectionPreferences.getMode(this, ProtectedApp.INSTAGRAM)
        // A latched next-video exit needs no additional native tree queries.
        if (instagramReelsSession.exiting) return false
        // Instagram publishes the selected main-Reels chrome after the first
        // frame even for a Reel deliberately opened from DM or a profile. Once
        // that first frame has positive ingress identity, retain the session;
        // its pager-motion gate remains responsible for exiting on video two.
        if (InstagramBalancedReelIngressPolicy.keepsVerifiedSession(
                mode, instagramReelsSession.watching, instagramReelsSession.exiting)) return true
        if (!instagramReelsSession.watching &&
            InstagramNotificationShadePolicy.isProtectedFeedOrigin(lastInstagramScreen)) {
            instagramReelsSession.observe(mode,
                viewerVisible = true, feedEntry = true)
            return false
        }
        if (instagramStoriesBlocked() || hasFastMainReelsTabDestination(root)) return false
        val now = SystemClock.uptimeMillis()
        val originWindowId = if (lastInstagramScreen == InstagramScreen.DIRECT_MESSAGES) {
            directConversationSurfaceWindowId.takeIf { it >= 0 }
                ?: instagramStateMachine.currentWindowId
        } else {
            instagramStateMachine.currentWindowId
        }
        val ingress = InstagramBalancedReelIngressPolicy.resolve(
            mode = mode,
            previousScreen = lastInstagramScreen,
            originWindowId = originWindowId,
            viewerWindowId = root.windowId,
            profileReelIntentWindowId = instagramBalancedProfileReelIntentWindowId,
            profileReelIntentPending = now < instagramBalancedProfileReelIntentUntil,
            collectionReelIntentWindowId = instagramBalancedCollectionReelIntentWindowId,
            collectionReelIntentPending = now < instagramBalancedCollectionReelIntentUntil,
            reelsTabTapLatched = now < instagramReelsTabTapUntil,
            mainTabBarVisible = igVisible(root, "tab_bar"),
            actionBarBackVisible = hasFastActionBarBack(root),
            directIntentPending = now < directReelLaunchPendingUntil
        )
        if (ingress == InstagramBalancedReelIngress.NONE) return false
        val wasWatching = instagramReelsSession.watching
        if (instagramReelsSession.observe(mode, viewerVisible = true) ==
            InstagramReelsDecision.EXIT) return false
        if (!wasWatching) instagramReelsVerifiedAt = now
        instagramBalancedReelOrigin = lastInstagramScreen
        clearDirectConversationEntryGuard()
        clearDirectDetailsScrollGuard()
        lastDirectConversationGuards.clear()
        instagramEntryTargets.clear()
        instagramOverlay.updateEntryGuards(emptyList(), isEnglish())
        hideInstagramContentOverlay()
        cancelInstagramExit()
        directReelLaunchPendingUntil = 0L
        instagramBalancedProfileReelIntentWindowId = -1
        instagramBalancedProfileReelIntentUntil = 0L
        instagramBalancedCollectionReelIntentWindowId = -1
        instagramBalancedCollectionReelIntentUntil = 0L
        Log.d(TAG, "IG_BALANCED_REEL_ALLOWED ingress=$ingress")
        observeInstagramScreen(InstagramScreen.REELS, root.windowId)
        return true
    }

    private fun handleBalancedInstagramReelScroll(event: AccessibilityEvent): Boolean {
        if (instagramStoriesBlocked() || !instagramReelsSession.watching) return false
        val started = SystemClock.uptimeMillis()
        val root = rootInActiveWindow ?: return false
        if (root.packageName?.toString() != INSTAGRAM_PACKAGE ||
            (lastInstagramScreen != InstagramScreen.REELS &&
                !(instagramBalancedReelOrigin == InstagramScreen.SAVED_COLLECTION &&
                    instagramReelsSession.watching && hasFastSavedCollectionReelList(root))) ||
            !InstagramReelsIdentity.isCurrentSessionEvent(instagramStateMachine.currentWindowId,
                event.windowId, root.windowId, event.eventTime, instagramReelsVerifiedAt, started)) return false
        val ids = linkedSetOf<String>()
        var node = event.source
        var depth = 0
        while (node != null && depth++ < 12) {
            val current = node
            val id = compactId(current.viewIdResourceName)
            ids.add(id)
            // Exact pager or comment ancestry is sufficient; walking up to the
            // window root adds remote calls but no stronger identity evidence.
            if (id in InstagramReelsIdentity.pagerIds || id in InstagramReelsIdentity.safeSheetIds ||
                id == "bottom_sheet_container" || id == "layout_container_bottom_sheet") break
            node = current.parent
        }
        val savedCollectionListScroll = instagramBalancedReelOrigin ==
            InstagramScreen.SAVED_COLLECTION &&
            (InstagramReelsIdentity.isSavedCollectionListPath(ids) ||
                hasFastSavedCollectionReelList(root))
        if (!savedCollectionListScroll && !InstagramReelsIdentity.isVideoPagerPath(ids)) return false
        if (instagramReelsMotionGeneration != instagramReelsSession.generation) {
            instagramReelsPagerMotion.reset()
            instagramReelsMotionGeneration = instagramReelsSession.generation
        }
        val dx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) event.scrollDeltaX else 0
        val dy = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) event.scrollDeltaY else 0
        val movement = if (savedCollectionListScroll) {
            // Saved's feed-style viewer reports a normal RecyclerView scroll,
            // so it has no clips pager identity. Any vertical movement here is
            // the attempted advance to another saved item and must close the
            // single-Reel session instead.
            true
        } else {
            instagramReelsPagerMotion.observe(dx, dy, event.fromIndex, event.scrollY)
        }
        if (!movement && !instagramReelsSession.exiting) return true
        // Retain fresh comment-layer checks even on the low-latency path. A
        // closing sheet may move its background player without a video swipe.
        val safeSheetOpen = InstagramReelsIdentity.safeSheetIds.any { id ->
            root.findAccessibilityNodeInfosByViewId("$INSTAGRAM_PACKAGE:id/$id").any { it.isVisibleToUser }
        }
        val decision = instagramReelsSession.observe(ProtectionMode.BALANCED, viewerVisible = true,
            safeSheetOpen = safeSheetOpen, pagerScroll = true, verticalMovement = movement)
        if (decision == InstagramReelsDecision.EXIT) {
            Log.d(TAG, "IG_SINGLE_REEL_NEXT_VIDEO_EXIT generation=${instagramReelsSession.generation} " +
                "eventAgeMs=${started - event.eventTime} decisionMs=${SystemClock.uptimeMillis() - started}")
            // The verified pager belongs to the already-confirmed foreground
            // Reel. Issue Back directly instead of querying an absent control.
            exitBlockedViewer(root, instagramWindowBounds(root), InstagramScreen.REELS, useGlobalBackOnly = true)
        }
        return true
    }

    private fun hasFastNotificationsSurface(root: AccessibilityNodeInfo): Boolean =
        listOf(
            "activity_feed", "activity_feed_root", "newsfeed_you",
            "newsfeed_recycler", "notifications_recycler",
            "follow_requests_header"
        ).any { id -> igVisible(root, id) }

    private fun hasFastSinglePostViewer(root: AccessibilityNodeInfo): Boolean {
        val titles = cachedNodeLookup(root, "$INSTAGRAM_PACKAGE:id/action_bar_title")
            .filter { it.isVisibleToUser }.mapNotNull { it.text?.toString() }.toSet()
        if (titles.none(InstagramPostIdentity::isPostTitle)) return false
        if (!igVisible(root, "action_bar_button_back") || igVisible(root, "main_feed_action_bar")) return false
        if (!igVisible(root, "row_feed_profile_header")) return false
        return igVisible(root, "row_feed_photo_imageview") || igVisible(root, "media_group") ||
            igVisible(root, "row_feed_view_group_buttons")
    }

    private fun hasFastCommentSheet(root: AccessibilityNodeInfo): Boolean {
        if (hasFastCommentSheetInRoot(root)) return true
        return windows.any { window ->
            runCatching { window.root?.let(::hasFastCommentSheetInRoot) == true }
                .getOrDefault(false)
        }
    }

    private fun hasFastCommentSheetInRoot(candidate: AccessibilityNodeInfo): Boolean {
        // Current Instagram builds expose the notification comment sheet with
        // stable composer IDs while the underlying video remains a generic
        // main_list_view. Prefer these IDs over text matching: it avoids a
        // full tree walk and makes the second Back event immediate.
        val hasCommentComposer =
            igVisible(candidate, "layout_comment_thread_edittext_multiline") ||
                igVisible(candidate, "comment_composer_parent_updated") ||
                igVisible(candidate, "comment_composer_animated_image_picker_button")
        if (igVisible(candidate, "bottom_sheet_container") && hasCommentComposer) return true
        if (listOf(
                "comments_recycler_view", "comments_recycler", "comment_thread",
                "layout_comment_thread", "comment_list"
            ).any { id -> igVisible(candidate, id) }) return true
        val labels = runCatching {
            cachedNodeLookup(candidate, "$INSTAGRAM_PACKAGE:id/row_feed_comment_textview_layout")
                .mapNotNull { it.text?.toString() }
        }.getOrDefault(emptyList())
        if (labels.any { text ->
                text.contains("replying to", ignoreCase = true) ||
                    text.contains("yanıtlanıyor", ignoreCase = true) ||
                    text.contains("add a reply", ignoreCase = true) ||
                    text.contains("yanıt ekle", ignoreCase = true)
            }) return true
        return listOf("Replying to", "Add a reply", "Yanıtlanıyor", "Yanıt ekle")
            .any { label ->
                runCatching {
                    candidate.findAccessibilityNodeInfosByText(label)
                        .any { it.isVisibleToUser }
                }.getOrDefault(false)
            }
    }

    private fun findInstagramNotificationViewerRoot(
        fallback: AccessibilityNodeInfo
    ): Pair<AccessibilityNodeInfo, InstagramScreen>? {
        val candidates = buildList {
            // Prefer the currently focused/active application window. The
            // Notifications tree can remain in AccessibilityWindows after a
            // Reel viewer has been opened; choosing it first creates a false
            // "returned" result and makes later retries race the new viewer.
            windows.filter { window ->
                window.type == AccessibilityWindowInfo.TYPE_APPLICATION &&
                    (window.isFocused || window.isActive)
            }.forEach { window ->
                runCatching { window.root }.getOrNull()?.let { root ->
                    if (root.packageName?.toString() == INSTAGRAM_PACKAGE) add(root)
                }
            }
            windows.filter { window ->
                window.type == AccessibilityWindowInfo.TYPE_APPLICATION &&
                    !window.isFocused && !window.isActive
            }.forEach { window ->
                runCatching { window.root }.getOrNull()?.let { root ->
                    if (root.packageName?.toString() == INSTAGRAM_PACKAGE) add(root)
                }
            }
            add(fallback)
        }.distinctBy { it.windowId }
        return candidates.firstNotNullOfOrNull { candidate ->
            when {
                hasFastReelsViewer(candidate, directLaunchPending = true) ->
                    candidate to InstagramScreen.REELS
                hasFastPostViewer(candidate) || hasFastCommentSheetInRoot(candidate) ->
                    candidate to InstagramScreen.COMMENTS_OR_DETAIL
                else -> null
            }
        }
    }

    private fun hasFastReelPreview(root: AccessibilityNodeInfo): Boolean {
        val previewChrome = igVisible(root, "feed_preview_keep_watching_backdrop") ||
            igVisible(root, "feed_preview_keep_watching_button") ||
            igVisible(root, "feed_preview_bottom_cta_container")
        return previewChrome && igVisible(root, "video_container") &&
            igVisible(root, "action_bar_button_back")
    }

    private fun hasVisibleDirectDetailsGrid(root: AccessibilityNodeInfo): Boolean =
        igVisible(root, "thread_details_pager") && igVisible(root, "tab_layout") &&
            igVisible(root, "shared_media_list")

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
        val inbox = igVisible(root, "inbox_refreshable_thread_list_recyclerview") &&
            (igVisible(root, "direct_inbox_action_bar") || igVisible(root, "direct_inbox_search_bar") ||
                igVisible(root, "search_row"))
        val conversation = igVisible(root, "direct_thread_header") && igVisible(root, "message_list")
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
        val window = instagramWindowBounds(root)
        val fullPhotoViewer = listOf(
            "direct_media_viewer", "direct_visual_media_viewer", "media_viewer",
            "media_viewer_container", "photo_viewer", "image_viewer",
            "zoomable_view_container", "fullscreen_media_container"
        ).any { id ->
            cachedNodeLookup(root, "$INSTAGRAM_PACKAGE:id/$id")
                .any { node ->
                    if (!node.isVisibleToUser) false else {
                        val bounds = Rect().also(node::getBoundsInScreen)
                        bounds.intersect(window) && bounds.width() >= window.width() * 3 / 4 &&
                            bounds.height() >= window.height() / 2
                    }
                }
        }
        if (fullPhotoViewer) return true
        // Home contains row_feed_* and carousel IDs too. Treating them as a
        // detail viewer removes the feed curtain and tries a nonexistent Back.
        val hasHomeHeader = igVisible(root, "main_feed_action_bar")
        if (hasHomeHeader) return false
        val postChrome = listOf(
            "media_view_pager", "carousel_media_group", "post_detail", "feed_detail",
            "single_media_view", "row_feed_profile_header", "row_feed_button_comment",
            "row_feed_comment_textview_layout"
        ).any { igVisible(root, it) }
        if (!postChrome) return false
        val hasBack = igVisible(root, "action_bar_button_back")
        val detailsTabsStillForeground = igVisible(root, "tab_layout")
        return InstagramPostIdentity.isGenericViewer(
            fullPhotoViewer = fullPhotoViewer,
            hasHomeHeader = hasHomeHeader,
            postChrome = postChrome,
            hasBack = hasBack,
            detailsTabsVisible = detailsTabsStillForeground
        )
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
        return ids.any { id -> igVisible(root, id) }
    }

    private fun hasVisibleAboutAccountSurface(root: AccessibilityNodeInfo): Boolean {
        val explicitIds = listOf(
            "about_this_account_container", "account_transparency_container",
            "account_transparency_root", "about_account_fragment"
        )
        if (explicitIds.any { id -> igVisible(root, id) }) return true
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

    private fun isProfileThumbnailIntent(source: AccessibilityNodeInfo?): Boolean {
        var current = source
        repeat(8) {
            val node = current ?: return false
            if (compactId(node.viewIdResourceName) in InstagramEntryPolicy.profileThumbnailIds) {
                return true
            }
            current = node.parent
        }
        return false
    }

    private fun isProfileReelThumbnailIntent(source: AccessibilityNodeInfo?): Boolean {
        var current = source
        repeat(10) {
            val node = current ?: return false
            if (compactId(node.viewIdResourceName) == "preview_clip_thumbnail") return true
            current = node.parent
        }
        return false
    }

    private fun isCollectionThumbnailIntent(source: AccessibilityNodeInfo?): Boolean {
        var current = source
        repeat(10) {
            val node = current ?: return false
            val id = compactId(node.viewIdResourceName)
            if (id in InstagramEntryPolicy.profileThumbnailIds ||
                id.contains("media_grid") || id.contains("grid_media") ||
                id.contains("thumbnail") || id.contains("clips_grid")) return true
            current = node.parent
        }
        return false
    }

    private fun isCollectionReelThumbnailIntent(source: AccessibilityNodeInfo?): Boolean {
        var current = source
        repeat(10) {
            val node = current ?: return false
            val id = compactId(node.viewIdResourceName)
            val label = listOf(node.text, node.contentDescription)
                .joinToString(" ") { it?.toString().orEmpty() }
                .lowercase(Locale.ROOT)
            if (id.contains("clip") || id.contains("reel") ||
                label.contains("reel") || label.contains("reels") ||
                label.contains("klip")) return true
            current = node.parent
        }
        return false
    }

    private fun instagramCollectionTitles(root: AccessibilityNodeInfo): Set<String> =
        listOf("action_bar_title", "profile_action_bar_title")
            .flatMap { id ->
                root.findAccessibilityNodeInfosByViewId("$INSTAGRAM_PACKAGE:id/$id")
                    .filter { it.isVisibleToUser }
                    .mapNotNull { it.text?.toString()?.trim() }
            }.toSet()

    private fun refreshBalancedInstagramCollection(
        root: AccessibilityNodeInfo,
        observedTitles: Set<String> = instagramCollectionTitles(root)
    ): Boolean {
        if (ProtectionPreferences.getMode(this, ProtectedApp.INSTAGRAM) != ProtectionMode.BALANCED ||
            !InstagramProfileSurfacePolicy.isUserCollectionTitle(observedTitles)) return false
        clearDirectConversationEntryGuard()
        clearDirectDetailsScrollGuard()
        clearInstagramResumeProtection()
        profileAllowedDepartureUntil = 0L
        profileScrollPositions.clear()
        profileScrollCover.reset()
        profileWindowId = -1
        instagramEntryTargets.clear()
        instagramOverlay.updateEntryGuards(emptyList(), isEnglish())
        instagramOverlay.updateNavigationGuards(emptyMap())
        instagramOverlay.hide()
        cancelInstagramExit()
        observeInstagramScreen(InstagramScreen.SAVED_COLLECTION, root.windowId)
        Log.d(TAG, "IG_BALANCED_COLLECTION_ALLOWED titles=${observedTitles.joinToString("|")}")
        return true
    }

    private fun releaseAllowedProfileSurface() {
        clearDirectDetailsScrollGuard()
        clearInstagramResumeProtection()
        instagramOverlay.updateProfileChromeGuard(null)
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

    private fun armInstagramProfileReturn(root: AccessibilityNodeInfo?, now: Long) {
        if (!instagramProfilePostsBlocked() || root?.packageName?.toString() != INSTAGRAM_PACKAGE) {
            clearInstagramProfileReturn()
            return
        }
        instagramProfileReturn.observeAllowedLayer(now)
        val window = instagramWindowBounds(root)
        val backBounds = findInstagramTopLeftBack(root, window)?.let {
            Rect().also(it::getBoundsInScreen)
        }
        instagramOverlay.updateProfileReturnGuard(backBounds, ::onInstagramProfileReturnBack)
        if (backBounds != null) scheduleOverlayForegroundCheck()
    }

    private fun refreshEarlyInstagramProfileReturn(event: AccessibilityEvent, now: Long) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED) return
        if (!instagramProfilePostsBlocked() || !instagramProfileReturn.isArmed(now)) {
            clearInstagramProfileReturn()
            return
        }
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != INSTAGRAM_PACKAGE) return
        if (hasVisibleProfileNativeSheet(root) || hasVisibleAboutAccountSurface(root)) {
            armInstagramProfileReturn(root, now)
            return
        }
        if (!instagramProfileReturn.canPrecoverReturn(now) ||
            (!hasVisibleProfileHeaderMarkers(root) &&
                profileGridBounds(root, instagramWindowBounds(root)) == null)) return

        if (precoverStrictProfileReturn(root)) {
            clearInstagramProfileReturn()
            armProfileCurtainEnsure()
            queueInstagramScan(0L)
            Log.d(TAG, "IG_PROFILE_RETURN_PRECOVER source=structure")
        }
    }

    private fun onInstagramProfileReturnBack() {
        if (!ProtectionPreferences.isEnabled(this, ProtectedApp.INSTAGRAM) ||
            !instagramProfilePostsBlocked()) return
        val now = SystemClock.uptimeMillis()
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != INSTAGRAM_PACKAGE ||
            (!hasVisibleProfileNativeSheet(root) && !hasVisibleAboutAccountSurface(root))) return
        val token = instagramProfileReturn.begin(now) ?: return
        val windowId = root.windowId
        instagramOverlay.updateProfileReturnGuard(null)
        if (!precoverStrictProfileReturn(root)) {
            instagramProfileReturn.clear()
            val accepted = findInstagramTopLeftBack(root, instagramWindowBounds(root))
                ?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
            if (!accepted) performGlobalAction(GLOBAL_ACTION_BACK)
            queueInstagramScan(0L)
            return
        }
        instagramOverlay.afterCurtainDraw {
            if (!instagramProfileReturn.accepts(token, SystemClock.uptimeMillis()) ||
                !ProtectionPreferences.isEnabled(this, ProtectedApp.INSTAGRAM)) {
                return@afterCurtainDraw
            }
            val current = rootInActiveWindow ?: return@afterCurtainDraw
            if (current.packageName?.toString() != INSTAGRAM_PACKAGE ||
                current.windowId != windowId) return@afterCurtainDraw
            val accepted = findInstagramTopLeftBack(current, instagramWindowBounds(current))
                ?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
            if (!accepted) performGlobalAction(GLOBAL_ACTION_BACK)
            Log.d(TAG, "IG_PROFILE_RETURN_PRECOVER source=back accepted=$accepted")
            queueInstagramScan(0L)
        }
        queueInstagramScan(32L)
    }

    private fun precoverStrictProfileReturn(root: AccessibilityNodeInfo): Boolean {
        val window = instagramWindowBounds(root)
        val retained = lastVerifiedInstagramCurtain
            ?.takeIf { it.first == InstagramScreen.PROFILE }
            ?.second
            ?.let(::Rect)
        val bounds = retained ?: conservativeInstagramResumeBounds(
            root, window, InstagramScreen.PROFILE) ?: return false
        profileAllowedDepartureUntil = 0L
        clearInstagramResumeProtection()
        currentInstagramWindowId = root.windowId
        return applyStrictProfileCurtainResult(root, window, bounds).also { shown ->
            if (shown) {
                strictProfilePrecoverAtMs = SystemClock.uptimeMillis()
                scheduleOverlayForegroundCheck()
            }
        }
    }

    /** Split-aware variant of the profile curtain mount with a Boolean result. */
    private fun applyStrictProfileCurtainResult(
        root: AccessibilityNodeInfo,
        window: Rect,
        bounds: Rect
    ): Boolean {
        applyStrictProfileCurtain(root, window, bounds)
        return instagramOverlay.visibleProfileBounds() != null
    }

    private fun clearInstagramProfileReturn() {
        instagramProfileReturn.clear()
        instagramOverlay.updateProfileReturnGuard(null)
    }

    private fun findInstagramTopLeftBack(
        root: AccessibilityNodeInfo,
        window: Rect
    ): AccessibilityNodeInfo? {
        val pending = ArrayDeque<AccessibilityNodeInfo>()
        pending.add(root)
        var visited = 0
        // A Back control sits in the top-left third of the upper half. Subtrees
        // that are invisible or never touch that region cannot contain it;
        // expanding them spent one remote call per child on every scan.
        val region = Rect(window.left, window.top, window.left + window.width() / 3, window.centerY())
        while (pending.isNotEmpty() && visited++ < 140) {
            val node = pending.removeFirst()
            val bounds = Rect().also(node::getBoundsInScreen)
            if (node !== root && (!node.isVisibleToUser || !Rect.intersects(bounds, region))) continue
            val id = compactId(node.viewIdResourceName)
            val label = listOfNotNull(node.text?.toString(), node.contentDescription?.toString())
                .joinToString(" ").trim().lowercase(Locale.US)
            val semanticBack = id == "action_bar_button_back" ||
                label == "back" || label == "geri"
            if (semanticBack && node.isVisibleToUser && node.isClickable && !bounds.isEmpty &&
                bounds.top >= window.top && bounds.bottom < window.centerY() &&
                bounds.left >= window.left &&
                bounds.right <= window.left + window.width() / 3) return node
            for (index in 0 until minOf(node.childCount, 24)) {
                node.getChild(index)?.let(pending::addLast)
            }
        }
        return null
    }

    private fun refreshStrictProfileChromeGuard() {
        // The profile top bar (Back, Threads, username/account switch, menu)
        // stays fully native: these are intentional, useful controls, and the
        // layers they open (profile menu, About, account switcher) are now
        // recognised allowed surfaces that the curtain yields to and re-covers
        // after. No guard window is mounted over the bar any more.
        instagramOverlay.updateProfileChromeGuard(null)
    }

    private fun releaseVerifiedInstagramInbox(root: AccessibilityNodeInfo,
        requireSelectedMainTab: Boolean = false): Boolean {
        val probeStarted = SystemClock.uptimeMillis()
        fun reject(reason: String): Boolean {
            val now = SystemClock.uptimeMillis()
            if (requireSelectedMainTab && now - lastInboxProbeLogAt >= 250L &&
                applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
                lastInboxProbeLogAt = now
                Log.d(TAG, "IG_INBOX_EARLY_REJECT reason=$reason window=${root.windowId} " +
                    "probeMs=${now - probeStarted}")
            }
            return false
        }
        fun visible(id: String): Boolean = igVisible(root, id)
        if (requireSelectedMainTab) {
            // This path precedes viewer probes: require current native inbox
            // navigation and reject mounted inboxes underneath any back stack.
            if (!igSelected(root, "direct_tab")) return reject("direct-not-selected")
            if (igSelected(root, "clips_tab")) return reject("reels-selected")
            if (visible("action_bar_button_back")) return reject("back-present")
        }
        val listBounds = findVisibleNodeBounds(root,
            "$INSTAGRAM_PACKAGE:id/inbox_refreshable_thread_list_recyclerview") ?: return reject("list-missing")
        val display = currentDisplayBounds()
        if (!InstagramInboxPolicy.isStableInboxLayout(
                listBounds.toInstagramGeometrySample(),
                display.toInstagramGeometrySample())) return reject("list-transient")
        val header = visible("direct_inbox_action_bar") || visible("direct_inbox_search_bar") ||
            visible("search_row")
        if (!header) return reject("header-missing")
        // Even a partially opened viewer must not take this inbox-only bypass.
        // Fast Reel and Story checks run before this path. Keep only the two
        // layers which can otherwise leave a full-width inbox mounted behind
        // an opened media/details surface.
        val blockedTopLayerId = listOf(
            "root_clips_layout", "clips_viewer", "clips_viewer_view_pager",
            "clips_view_pager", "thread_details_pager", "story_viewer_container",
            "reel_item_toolbar_container", "reel_viewer_root", "reel_viewer_content_layout"
        ).firstOrNull(::visible)
        if (!InstagramInboxPolicy.isInbox(true, header, blockedTopLayerId != null)) {
            return reject("layer-$blockedTopLayerId")
        }
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
        cachedNodeLookup(root, "$INSTAGRAM_PACKAGE:id/clips_tab")
            .filter { it.isVisibleToUser }.forEach { node ->
                val bounds = Rect().also(node::getBoundsInScreen)
                if (bounds.intersect(window) && bounds.height() in 1 until window.height() / 2) {
                    instagramEntryTargets.add(bounds to true)
                }
            }
        lastDirectConversationGuards.clear()
        instagramOverlay.updateEntryGuards(instagramEntryTargets.map { it.first }, isEnglish())
        clearNonMessagingTouchGuards()
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
        if (listOf("thread_details_pager", "reel_item_toolbar_container",
                "root_clips_layout", "clips_viewer_view_pager", "clips_view_pager",
                "reel_viewer_root", "story_viewer_container").any {
                findVisibleNodeBounds(root, "$INSTAGRAM_PACKAGE:id/$it") != null }) return false
        val viewport = Rect(list)
        viewport.top = maxOf(viewport.top, header.bottom)
        findVisibleNodeBounds(root, "$INSTAGRAM_PACKAGE:id/message_composer_bar")?.let {
            viewport.bottom = minOf(viewport.bottom, it.top)
        }
        if (viewport.isEmpty) return false
        val dmMode = ProtectionPreferences.getMode(this, ProtectedApp.INSTAGRAM)
        if (!InstagramDmInteractionPolicy.interceptsChatTouches(dmMode) &&
            !InstagramDmInteractionPolicy.blocksSharedMedia(dmMode)) {
            clearDirectConversationEntryGuard()
            clearDirectDetailsScrollGuard()
            lastDirectConversationGuards.clear()
            directConversationGuardHoldUntil = 0L
            instagramEntryTargets.clear()
            instagramOverlay.updateEntryGuards(emptyList(), isEnglish())
            instagramOverlay.updateNavigationGuards(emptyMap())
            clearNonMessagingTouchGuards()
            hideInstagramContentOverlay()
            cancelInstagramExit()
            verifiedInstagramInboxWindowId = -1
            verifiedInstagramInboxListBounds = null
            directConversationSurfaceWindowId = root.windowId
            observeInstagramScreen(InstagramScreen.DIRECT_MESSAGES, root.windowId)
            exitGate.observe(InstagramScreen.DIRECT_MESSAGES, SystemClock.uptimeMillis(),
                verifiedConversationReturn = true)
            return true
        }
        // The verified conversation remains completely native.  In particular,
        // do not mount the provisional full-viewport entry guard here.
        clearDirectConversationEntryGuard()
        verifiedInstagramInboxWindowId = -1
        verifiedInstagramInboxListBounds = null
        // Everything in the conversation stays native except, in Strict, the
        // shared Reel cards themselves. A tapped card sends no click event and
        // Instagram stops publishing accessibility events for 1.5-2 s while
        // its Reel viewer starts and already plays, so a viewer-side exit can
        // never be earlier than that. The card guard consumes the tap; drags
        // and long presses (react/reply menu) are replayed underneath it.
        val now = SystemClock.uptimeMillis()
        lastDirectConversationGuards.clear()
        if (InstagramDmInteractionPolicy.blocksSharedMedia(dmMode)) {
            lastDirectConversationGuards.addAll(directReelCardGuards(root, viewport))
        }
        // The bottom Reels tab stays guarded in the conversation too: the tab
        // bar can remain visible above the composer, and an unguarded tab lets
        // a fast tap enter Reels natively.
        val reelsTabGuards = mutableListOf<Rect>()
        val conversationWindow = instagramWindowBounds(root)
        root.findAccessibilityNodeInfosByViewId("$INSTAGRAM_PACKAGE:id/clips_tab")
            .filter { it.isVisibleToUser }.forEach { node ->
                val bounds = Rect().also(node::getBoundsInScreen)
                if (bounds.intersect(conversationWindow) &&
                    bounds.height() in 1 until conversationWindow.height() / 2) {
                    reelsTabGuards.add(bounds)
                }
            }
        val renderedGuards = (lastDirectConversationGuards + reelsTabGuards).distinct()
        clearDirectDetailsScrollGuard()
        clearInstagramResumeProtection()
        instagramOverlay.updateEntryGuards(
            renderedGuards,
            isEnglish(),
            forceRelayout = false)
        // Keep the persistent Reels-tab guard consistent with this surface:
        // find it when the tab bar is visible, force-retire it when it is
        // not - the conversation composer shares the bottom strip, so a
        // transition hold must never linger here.
        refreshMainReelsTabGuard(root, force = true)
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
        val remaining = directReelLaunchPendingUntil - now
        if (remaining > 0L) {
            // A card click that escaped its region gets a short fast-player
            // polling burst. This avoids waiting for Instagram's next delayed
            // content event before issuing the viewer Back action.
            queueInstagramScan(if (remaining > 1_150L) 16L else minOf(48L, remaining))
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
        if (!InstagramDmInteractionPolicy.interceptsChatTouches(
                ProtectionPreferences.getMode(this, ProtectedApp.INSTAGRAM))) return false
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
        directConversationEntryGuardUntil = now + DIRECT_CONVERSATION_ENTRY_GUARD_TIMEOUT_MS
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
        if (!InstagramDmInteractionPolicy.interceptsChatTouches(
                ProtectionPreferences.getMode(this, ProtectedApp.INSTAGRAM))) return false
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
        directConversationEntryGuardUntil = now + DIRECT_CONVERSATION_SURFACE_GUARD_TIMEOUT_MS
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
        if (!InstagramDmInteractionPolicy.interceptsChatTouches(
                ProtectionPreferences.getMode(this, ProtectedApp.INSTAGRAM))) return false
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

    private fun isDirectConversationEntryGuardArmed(): Boolean =
        directConversationEntryGuardUntil > SystemClock.uptimeMillis()

    private fun clearDirectConversationEntryGuard() {
        mainHandler.removeCallbacks(directConversationEntryGuardTimeout)
        mainHandler.removeCallbacks(directConversationEntryGuardRelease)
        directConversationEntryGuardArmedAt = 0L
        directConversationEntryGuardUntil = 0L
        instagramOverlay.hideConversationEntryGuard()
    }

    /** A verified messaging surface cannot inherit another screen's touch windows. */
    private fun clearNonMessagingTouchGuards() {
        instagramProfileIngress.reset()
        instagramOverlay.updateProfileIngressGuard(null)
        instagramOverlay.updateAccountSearchGuard(null, ::onInstagramAccountSearchTap, {})
        instagramOverlay.updateStoryEntryGuards(emptyList())
        clearInstagramNotificationIngress()
        instagramOverlay.updateNotificationLaunchGuard(null)
        instagramOverlay.updateNotificationBackGuard(null)
    }

    /**
     * Visible shared-Reel cards (reel_share_item_view; portrait XMA in current
     * layouts), clipped to the message viewport so the guard never reaches the
     * header or the composer. Two exact lookups, no tree walk.
     */
    private fun directReelCardGuards(root: AccessibilityNodeInfo, viewport: Rect): List<Rect> =
        // The long-press message menu (react, reply, pin, delete) is drawn over
        // the cards; while it is open no card guard may cover its rows.
        if (igVisible(root, "message_actions_container")) emptyList()
        else listOf("reel_share_item_view", "message_content_portrait_xma_container")
            .flatMap { id -> cachedNodeLookup(root, "$INSTAGRAM_PACKAGE:id/$id") }
            .filter { it.isVisibleToUser }
            .mapNotNull { node ->
                Rect().also(node::getBoundsInScreen).takeIf {
                    it.intersect(viewport) && it.width() >= dp(48) && it.height() >= dp(48)
                }
            }
            .distinctBy { "${it.left}:${it.top}:${it.right}:${it.bottom}" }

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

    /*
    private fun directSharedMediaCardBounds(
        root: AccessibilityNodeInfo,
        viewport: Rect
    ): List<Rect> {
        val results = mutableListOf<Rect>()
        // Older Instagram layouts expose shared photos/Reels through the
        // stable media_container id even when the outer message row is not
        // clickable in the accessibility tree.
        val mediaNodes = root.findAccessibilityNodeInfosByViewId(
            "$INSTAGRAM_PACKAGE:id/media_container"
        ).take(32)
        val messageLists = root.findAccessibilityNodeInfosByViewId(
            "$INSTAGRAM_PACKAGE:id/message_list"
        )
        mediaNodes.forEach { node ->
            val bounds = Rect().also(node::getBoundsInScreen)
            if (bounds.intersect(viewport) && bounds.width() >= dp(100) &&
                bounds.height() >= dp(100)) results.add(bounds)
        }
        val pending = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        val traversalRoot = messageLists.firstOrNull() ?: root
        pending.add(traversalRoot to 0)
        var visited = 0
        while (pending.isNotEmpty() && visited++ < 700) {
            val (node, depth) = pending.removeFirst()
            val rawBounds = Rect().also(node::getBoundsInScreen)
            val isMediaSizedRow = rawBounds.width() >= viewport.width() * 2 / 3 &&
                rawBounds.height() >= dp(260) &&
                rawBounds.height() <= viewport.height() * 3 / 4
            if (isMediaSizedRow) {
                val bounds = Rect(rawBounds)
                if (!bounds.intersect(viewport)) {
                    if (depth < 8) {
                        for (index in 0 until minOf(node.childCount, 24)) {
                            node.getChild(index)?.let { pending.add(it to depth + 1) }
                        }
                    }
                    continue
                }
                var best: Rect? = null
                val descendants = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
                descendants.add(node to 0)
                var descendantCount = 0
                while (descendants.isNotEmpty() && descendantCount++ < 180) {
                    val (child, childDepth) = descendants.removeFirst()
                    val childBounds = Rect().also(child::getBoundsInScreen)
                    val betterThanBest = best?.let {
                        childBounds.width() * childBounds.height() >
                            it.width() * it.height()
                    } ?: true
                    if (childDepth > 0 &&
                        childBounds.intersect(viewport) &&
                        childBounds.width() >= dp(120) && childBounds.height() >= dp(120) &&
                        childBounds.width() <= viewport.width() * 9 / 10 &&
                        betterThanBest) {
                        best = childBounds
                    }
                    if (childDepth < 4) {
                        for (index in 0 until minOf(child.childCount, 24)) {
                            child.getChild(index)?.let { descendants.add(it to childDepth + 1) }
                        }
                    }
                }
                val card = best ?: bounds.takeIf { it.width() <= viewport.width() * 9 / 10 }
                if (card != null && !card.isEmpty && card.intersect(viewport)) {
                    // In Strict mode every point on a shared-media card must
                    // be blocked.  The overlay replays vertical drags through
                    // AccessibilityService after deciding they are movement,
                    // so the full card can be protected without reopening the
                    // Reel viewer (or allowing a one-frame black flash).
                    results.add(card)
                }
            }
            if (depth < 8) {
                for (index in 0 until minOf(node.childCount, 24)) {
                    node.getChild(index)?.let { pending.add(it to depth + 1) }
                }
            }
        }
        return results.distinct()
    }
    */

    private fun exitBlockedViewer(
        root: AccessibilityNodeInfo,
        windowBounds: Rect,
        screen: InstagramScreen,
        useGlobalBackOnly: Boolean = false,
        preserveObservedOrigin: Boolean = false,
        keepOriginCover: Boolean = false
    ) {
        if (screen == InstagramScreen.REELS && allowBalancedInstagramReel(root)) return
        val wasDirectConversation = lastInstagramScreen == InstagramScreen.DIRECT_MESSAGES
        val wasNotificationSurface = lastInstagramScreen == InstagramScreen.NOTIFICATIONS
        val now = SystemClock.uptimeMillis()
        val notificationOrigin = wasNotificationSurface ||
            instagramExitFromNotifications ||
            now < instagramNotificationContentLaunchUntil ||
            now < instagramNotificationExitHoldUntil
        val fromDirectConversation = wasDirectConversation ||
            (instagramExitInProgress && instagramExitFromDirectConversation)
        val fromNotificationSurface = notificationOrigin ||
            (instagramExitInProgress && instagramExitFromNotifications)
        if (fromNotificationSurface && screen == InstagramScreen.REELS &&
            instagramExitInProgress && instagramNotificationReelExitIssued &&
            instagramNotificationBackCount > 0) {
            if (instagramFastExitDeadlineAt == 0L) {
                scheduleFastNotificationSecondBack()
            }
            return
        }
        if (notificationOrigin && screen == InstagramScreen.REELS) {
            // The fast Reel path can win the race before the notification
            // viewer-root helper publishes its markers. Latch the origin here
            // as well, so the first exit is still the only exit.
            if (!instagramNotificationReelExitIssued) {
                instagramNotificationReelExitIssued = true
                instagramNotificationReelSecondBackIssued = false
                instagramNotificationCommentLayerObserved = hasFastCommentSheetInRoot(root)
            }
            instagramNotificationExitHoldUntil = now + 10_000L
            instagramNotificationViewerDetectedAt = now
            val attemptElapsed = instagramNotificationAttemptStartedAt
                .takeIf { it > 0L }
                ?.let { now - it }
            Log.d(TAG, "IG_NOTIFICATION_VIEWER_DETECTED elapsedMs=$attemptElapsed")
        }
        if (notificationOrigin && screen == InstagramScreen.COMMENTS_OR_DETAIL &&
            !instagramExitInProgress) {
            // Notification comment entries have two layers: comments first,
            // then the underlying Reel. Keep that second Back scoped to this
            // origin so direct notification Reels remain one-shot.
            instagramNotificationCommentNeedsSecondBack = true
            instagramNotificationCommentWaitStartedAt = now
        }
        if (instagramNotificationBackCount >= INSTAGRAM_NOTIFICATION_MAX_BACKS &&
            SystemClock.uptimeMillis() < instagramNotificationExitHoldUntil) return
        // A tab-slip entry must not reveal the feed: the feed never carries an
        // action-bar Back control, so this frames-exact signal separates it
        // from a verified single viewer. Cover first, then run the one-shot
        // exit; the cover is retired when the next surface is verified.
        if (screen == InstagramScreen.REELS && !fromDirectConversation &&
            !fromNotificationSurface &&
            (ProtectionPreferences.getMode(this, ProtectedApp.INSTAGRAM) == ProtectionMode.STRICT ||
                instagramReelsSession.exiting || !hasFastActionBarBack(root))) {
            // A verified single-Reel pager can still publish its old footer
            // while Back is transitioning to Home. Cover the complete display
            // before dispatching Back so the footer cannot leak through the
            // smaller Home-feed curtain during that hand-off.
            armReelsSlipCover()
            reelsSlipCoverExit = true
        }
        clearDirectConversationEntryGuard()
        // A DM conversation has no touch overlay.  Preserve no card regions
        // while the viewer is being closed; otherwise the old transparent
        // window survives the Back transition and becomes the visible black
        // flash the user sees over the native conversation.
        val preservedConversationGuards = emptyList<Rect>()
        if (!preserveObservedOrigin) observeInstagramScreen(screen, root.windowId)
        directDetailsLaunchPendingUntil = 0L
        directDetailsFallbackBackAt = 0L
        directReelLaunchPendingUntil = 0L
        clearDirectDetailsScrollGuard()
        clearInstagramResumeProtection()
        exitGate.observe(screen, now)
        // No full-screen exit curtain. Keep the one-shot exit, not a black window.
        // Profile-menu collections also return to an allowed menu, so a curtain
        // retained from an older feed must be removed before Back is dispatched.
        mainHandler.removeCallbacks(homeScrollSettled)
        scrollCoverTop = null
        scrollCoverUntil = 0L
        if (!keepOriginCover) instagramOverlay.hide()
        // Never keep a DM card window mounted while the viewer closes.  The
        // conversation remains fully native; the next scan only checks for a
        // newly mounted viewer and exits it again if needed.
        instagramOverlay.updateEntryGuards(preservedConversationGuards, isEnglish())
        val newViewerEncounter = !instagramExitInProgress
        if (newViewerEncounter) {
            if (fromNotificationSurface) instagramNotificationExitGeneration++
            instagramBlockedAttempt.reset()
            instagramExitInProgress = true
            instagramExitFromDirectConversation = fromDirectConversation
            instagramExitFromNotifications = notificationOrigin
            Log.d(TAG, "IG_VIEWER screen=$screen detected scanMs=${now - lastInstagramScanTime}")
            instagramDetailExitChecksRemaining = if (screen == InstagramScreen.COMMENTS_OR_DETAIL) 3 else 0
            if (screen == InstagramScreen.REELS || screen == InstagramScreen.STORY ||
                screen == InstagramScreen.COMMENTS_OR_DETAIL) {
                mainHandler.removeCallbacks(instagramViewerExitRetry)
                mainHandler.postDelayed(
                    instagramViewerExitRetry,
                    if (notificationOrigin) 120L else INSTAGRAM_VIEWER_EXIT_RETRY_MS)
            }
        }
        if (exitGate.requestBack(now, if (fromNotificationSurface) 120L else
            InstagramExitGate.VIEWER_FAILSAFE_REARM_MS)) {
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
            val accepted = if (fromNotificationSurface) {
                // Notification viewers are exited only through an Instagram
                // control or a tap on that control. A global Back can escape
                // the app when Instagram accepts the command late.
                clickedBack || performViewerBackFromRoot(root)
            } else {
                clickedBack || performGlobalAction(GLOBAL_ACTION_BACK)
            }
            if (accepted) instagramExitTreeQuietUntil = now + INSTAGRAM_EXIT_TREE_QUIET_MS
            if (accepted && fromNotificationSurface) {
                instagramNotificationBackCount++
                // The comment sheet is already proven in the same accessibility
                // tree.  Its first Back only dismisses the sheet; dispatch the
                // Reel-closing Back from an independent queue so a slow tree
                // scan cannot turn a 120 ms retry into a 5–6 second delay.
                if (screen == InstagramScreen.REELS &&
                    !instagramNotificationReelSecondBackIssued) {
                    scheduleFastNotificationSecondBack()
                }
            }
            val attemptElapsed = instagramNotificationAttemptStartedAt
                .takeIf { it > 0L && fromNotificationSurface }
                ?.let { now - it }
            Log.d(TAG, "IG_VIEWER_EXIT screen=$screen singleAction viewerBack=$clickedBack " +
                "accepted=$accepted notificationElapsedMs=$attemptElapsed")
            // Failsafe exit retries belong to the same viewer encounter and
            // must not inflate the user-facing or API block count.
            if (instagramBlockedAttempt.acceptedIntervention(accepted)) {
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
        source: AccessibilityNodeInfo?,
        protectedProfileGrid: Boolean = false,
        yieldProtectedFeed: Boolean = false
    ): InstagramUiSignals? {
        val snapshotStarted = SystemClock.uptimeMillis()
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
            if (yieldProtectedFeed && InstagramSurfacePolicy.yieldFeedSnapshot(
                    SystemClock.uptimeMillis() - snapshotStarted, instagramFeedSnapshotYields)) {
                instagramEntryTargets.clear()
                return null
            }
            val node = pending.removeFirst()
            visited++
            var shouldPruneSubtree = false
            // Android visibility propagates: a subtree under an invisible node
            // can never expose a visible descendant. Expanding those nodes
            // spends every child on binder IPC only to discard the result.
            val nodeVisible = node.isVisibleToUser

            if (nodeVisible) {
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
                // The empty Favorites screen exposes its identity on the
                // illustration rather than a feed row. Keep only this exact,
                // bounded semantic node; never collect arbitrary post text.
                if (id == "igds_empty_state_image" && desc.isNotEmpty()) {
                    titles.add(desc)
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
                if (InstagramSurfacePolicy.skipInboxRows(id) ||
                    InstagramSurfacePolicy.skipProtectedProfileGridChildren(id,
                        measuredProfileGrid = protectedProfileGrid,
                        profileHeaderVisible = protectedProfileGrid) ||
                    id.startsWith("row_feed_") ||
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

            if (!shouldPruneSubtree && nodeVisible) {
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
        if (!ProtectionPreferences.isEnabled(this, ProtectedApp.YOUTUBE)) {
            resetYouTubeProtection()
            youtubeShortsSession.clear()
            return
        }
        val now = SystemClock.uptimeMillis()
        val isClickEvent = event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED
        val isScrollEvent = event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED
        // Content and window-state events only schedule classification. Fetching
        // the root or the event source here costs a remote call per event that
        // waits for YouTube's UI thread; during a transition those calls queued
        // the following events for hundreds of milliseconds.
        if (!isClickEvent) {
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                // Bursts of 3-6 per transition coalesce into one scan behind
                // the events already queued on the main looper.
                queueYouTubeSurfaceScan(0L)
            } else queueYouTubeContentScan()
            scheduleYouTubeOverlayForegroundCheck()
            if (!isScrollEvent ||
                ProtectionPreferences.getMode(this, ProtectedApp.YOUTUBE) == ProtectionMode.STRICT
            ) return
            // Feed, results and comment scrolls far outnumber Shorts swipes, and
            // walking each source's ancestors cost up to 160 ms. Only a scroll
            // inside a known or freshly classified Shorts viewer can end a session.
            if (youtubeShortsSession.state == YouTubeShortsSessionState.OUTSIDE &&
                youtubeObservedScreen != YouTubeCurtainScreen.SHORTS &&
                youtubeLastVerifiedScreen != YouTubeCurtainScreen.SHORTS
            ) return
        }
        val source = event.source
        if (isClickEvent) {
            youtubeLastClickAt = now
            val root = currentYouTubeRoot() ?: return
            // Both modes share exactly the same feed/navigation curtain. Only Shorts
            // viewing differs: strict rejects entry, balanced runs the session below.
            val destination = youtubeNavigationIntent(source)
            val selected = collectYouTubeNavigationSignals(root).selectedDestination
            val dispatchFeedback = destination == youtubeLastDispatchedTab &&
                now - youtubeLastDispatchedTabAt < 700L
            if (destination != YouTubeNavigationDestination.UNKNOWN &&
                destination == selected && !dispatchFeedback && !youtubeCurtainState.isPending
            ) {
                youtubeCurtainState.request(destination.curtainScreen(), now)
                youtubeAllowedReturnSurface = null
            } else if (!youtubeCurtainState.isPending &&
                // Only a launch from a protected feed records where Back must
                // return. The search page reports a click on its own search
                // field; treating that as a new launch replaced HOME with the
                // search page's null surface and dropped the Back guard.
                youtubeCurtainState.currentSurface != null &&
                isYouTubeExplicitlyAllowedNavigationSource(source)) {
                youtubeAllowedReturnSurface = youtubeCurtainState.currentSurface
                youtubeCurtainState.request(youtubeAllowedLaunchScreen(source), now)
            }
            // A click must classify against the tree that produced it.
            refreshYouTubeSurfaceProtection(root)
            scheduleYouTubeOverlayForegroundCheck()
            if (ProtectionPreferences.getMode(this, ProtectedApp.YOUTUBE) == ProtectionMode.STRICT) return
        }
        val launchRequested = isClickEvent && isYouTubeShortsLaunchSource(source)
        // One ancestor walk serves both questions; each step can be a remote call.
        val sourcePath = sourcePathIds(source, YOUTUBE_SOURCE_PATH_DEPTH)
        val safeBottomSheetOpen = sourcePath.any { id ->
            listOf("comment", "reply", "bottom_sheet", "results").any(id::contains)
        }
        // The reel source proves the Shorts context, but not user intent: YouTube emits a
        // zero-delta reel_recycler scroll while merely opening a Short and closing a sheet.
        val sourceIsShortsPager = sourcePath.any { id ->
            listOf("reel_recycler", "reel_watch", "reel_player", "shorts_player", "shorts_video")
                .any(id::contains)
        }
        val feedTabEntry = isClickEvent && YouTubeShortsPolicy.shouldBlockEntry(
            ProtectionMode.BALANCED,
            youtubeNavigationIntent(source) == YouTubeNavigationDestination.SHORTS
        )
        if (feedTabEntry) {
            // A real tab tap. The tab guard normally consumes the touch before
            // YouTube sees it; this latch covers the frames where one slips
            // through so later scans can distinguish it from a Short opened
            // via a link, search result or notification.
            youtubeShortsTabClickUntil = now + YOUTUBE_SHORTS_TAB_CLICK_LATCH_MS
        }
        val decision = youtubeShortsSession.observe(YouTubeShortsObservation(
            nowMs = now,
            shortsStructureVisible = false,
            launchRequested = launchRequested,
            definitelyOutsideShorts = false,
            safeBottomSheetOpen = safeBottomSheetOpen,
            isScrollEvent = isScrollEvent,
            hasVerticalMovement = isScrollEvent && isVerticalScroll(event),
            sourceIsShortsPager = sourceIsShortsPager,
            feedTabEntry = feedTabEntry
        ))
        queueYouTubeContentScan()
        // Entering and watching the first Short stays allowed. Only a real vertical movement
        // outside a safe bottom sheet can move the session to EXITING.
        if (decision != YouTubeShortsDecision.EXIT) return
        performYouTubeShortsExit()
    }

    private fun performYouTubeShortsExit() {
        val token = youtubeShortsSession.generation
        recordYouTubeViewerIntervention(performGlobalAction(GLOBAL_ACTION_BACK))

        mainHandler.postDelayed({
            // The user may already have returned to results and opened another Short.
            // An old attempt must neither close that viewer nor clear its new session.
            if (!youtubeShortsSession.isExitPending(token, SystemClock.uptimeMillis())) {
                return@postDelayed
            }
            if (!ProtectionPreferences.isEnabled(this, ProtectedApp.YOUTUBE) ||
                ProtectionPreferences.getMode(this, ProtectedApp.YOUTUBE) != ProtectionMode.BALANCED
            ) {
                youtubeShortsSession.finishExit(token)
                return@postDelayed
            }
            val root = rootInActiveWindow
            if (root?.packageName?.toString() != YOUTUBE_PACKAGE) {
                youtubeShortsSession.finishExit(token)
                return@postDelayed
            }

            val signals = collectYouTubeSignals(root)
            if (youtubeShortsSession.isExitPending(token, SystemClock.uptimeMillis()) &&
                signals.shortsVisible && !signals.safeBottomSheetOpen
            ) {
                recordYouTubeViewerIntervention(performGlobalAction(GLOBAL_ACTION_BACK))
            }
            // Always acknowledge completion, even if the returned results page lacks the
            // expected browse IDs. EXITING must never become an unbounded allow-all state.
            youtubeShortsSession.finishExit(token)
        }, YouTubeShortsPolicy.EXIT_RETRY_DELAY_MS)
    }

    private data class YouTubeUiSignals(
        val shortsVisible: Boolean,
        val safeBottomSheetOpen: Boolean,
        val definiteBrowseSurface: Boolean,
        val protectedSurface: YouTubeProtectedSurface?,
        val feedViewportBounds: Rect?,
        val shortsTabBounds: Rect?,
        val homeTabBounds: Rect?,
        val subscriptionsTabBounds: Rect?,
        val youTabBounds: Rect?,
        val allowedBackBounds: Rect?,
        val selectedDestination: YouTubeNavigationDestination,
        val searchSurfaceVisible: Boolean,
        val allowedSecondarySurfaceVisible: Boolean,
        val screen: YouTubeCurtainScreen = YouTubeCurtainScreen.UNKNOWN,
        val windowBounds: Rect? = null,
        val allowedLaunchBounds: Map<YouTubeCurtainScreen, Rect> = emptyMap(),
        val miniplayerBounds: Rect? = null
    )

    private fun collectYouTubeSignals(root: AccessibilityNodeInfo): YouTubeUiSignals {
        val navigation = collectYouTubeNavigationSignals(root)
        val pending = ArrayDeque<AccessibilityNodeInfo>()
        pending.add(root)
        var visited = 0
        var shortsVisible = listOf(
            "shorts_player",
            "reel_recycler",
            "shorts_video",
            "reel_watch_sequence",
            "reel_watch_fragment_root"
        ).any { id ->
            root.findAccessibilityNodeInfosByViewId("$YOUTUBE_PACKAGE:id/$id")
                .orEmpty()
                .isNotEmpty()
        }
        var bottomSheetHandleVisible = findVisibleNodeBounds(
            root,
            "$YOUTUBE_PACKAGE:id/arrow_drag_handle"
        ) != null
        var sheetCloseControlVisible = findVisibleNodeBounds(
            root,
            "$YOUTUBE_PACKAGE:id/close_button"
        ) != null
        val homeToolbarVisible = hasQuickYouTubeHomeToolbar(root)
        var definiteBrowseSurface = homeToolbarVisible
        var searchSurfaceVisible = false
        var allowedSecondarySurfaceVisible = false
        var allowedBackCandidate: Rect? = null
        var selectedDestination = navigation.selectedDestination
        var bottomBarTop: Int? = navigation.bottomBarTop
        var shortsTabCandidate: Rect? = navigation.shortsTabBounds
        val rootBounds = Rect().also(root::getBoundsInScreen)
        val toolbarBounds = findVisibleNodeBounds(root, "$YOUTUBE_PACKAGE:id/toolbar")
        val toolbarContainerBounds = findVisibleNodeBounds(root, "$YOUTUBE_PACKAGE:id/toolbar_container")

        while (pending.isNotEmpty() && visited < MAX_TREE_NODES) {
            val node = pending.removeFirst()
            visited++
            var pruneSubtree = false

            val id = compactId(node.viewIdResourceName)
            // Structural reel ids may temporarily report not-visible while the pager settles.
            // They still belong to the active window and are stronger evidence than visibility.
            if (id.contains("shorts_player") ||
                id.contains("reel_recycler") ||
                id.contains("shorts_video") ||
                id.contains("reel_watch_sequence") ||
                id.contains("reel_watch_fragment_root")
            ) {
                shortsVisible = true
            }

            if (node.isVisibleToUser) {
                val bounds = Rect().also(node::getBoundsInScreen)
                val isSearchSurfaceId = YouTubeUiIdentity.isSearchSurfaceId(id)
                val isSearchSurfaceNode = isSearchSurfaceId &&
                    YouTubeUiIdentity.hasMeaningfulHorizontalPresence(
                        left = bounds.left,
                        right = bounds.right,
                        windowLeft = rootBounds.left,
                        windowRight = rootBounds.right
                    )
                if (id.contains("youtube_logo") || isSearchSurfaceNode) {
                    definiteBrowseSurface = true
                }
                if (isSearchSurfaceNode) searchSurfaceVisible = true
                if ((YouTubeUiIdentity.isAllowedSecondarySurfaceId(id) &&
                        YouTubeUiIdentity.isHorizontallyOnScreen(
                            left = bounds.left,
                            right = bounds.right,
                            windowLeft = rootBounds.left,
                            windowRight = rootBounds.right
                        )) ||
                    YouTubeUiIdentity.isAllowedSecondaryToolbarTitle(
                        rawText = node.text?.toString(),
                        isClickable = node.isClickable,
                        top = bounds.top,
                        windowHeight = rootBounds.height(),
                        left = bounds.left,
                        right = bounds.right,
                        windowLeft = rootBounds.left,
                        windowRight = rootBounds.right
                    )
                ) {
                    allowedSecondarySurfaceVisible = true
                }
                if (node.isClickable &&
                    YouTubeUiIdentity.isBackNavigationLabel(
                        node.contentDescription?.toString(),
                        node.text?.toString()
                    ) &&
                    bounds.width() > 0 && bounds.height() > 0 &&
                    bounds.top < rootBounds.top + rootBounds.height() / 4
                ) {
                    allowedBackCandidate = Rect(bounds)
                }
                if (id.contains("arrow_drag_handle")) bottomSheetHandleVisible = true
                if (id.contains("close_button")) sheetCloseControlVisible = true
                if (id.contains("bottom_bar_container") && bounds.height() > 0) {
                    bottomBarTop = bottomBarTop?.coerceAtMost(bounds.top) ?: bounds.top
                }


                val description = node.contentDescription?.toString()
                val destination = YouTubeSurfacePolicy.navigationDestination(description)
                if (node.isClickable && node.isSelected &&
                    destination != YouTubeNavigationDestination.UNKNOWN
                ) {
                    selectedDestination = destination
                }
                if (node.isClickable && destination == YouTubeNavigationDestination.SHORTS &&
                    bounds.width() > 0 && bounds.height() > 0 &&
                    (shortsTabCandidate?.let { bounds.top > it.top } != false)
                ) {
                    shortsTabCandidate = Rect(bounds)
                }

                if (node.isSelected &&
                    node.contentDescription?.toString()?.trim()?.equals("Shorts", ignoreCase = true) == true
                ) {
                    shortsVisible = true
                }
            }

            // The feed/results subtree contains hundreds of remote nodes and none of their
            // descendants are needed to classify Home, Subscriptions, Search or the bottom
            // navigation. Traversing it for every content event was measured at up to two
            // seconds on a real Samsung device. Its own bounds were already consumed above;
            // Shorts/comment intent is handled from exact IDs and the originating event path.
            if (id == "results" || id == "bottom_bar_container") {
                pruneSubtree = true
            }

            if (!pruneSubtree) {
                for (index in 0 until node.childCount) {
                    node.getChild(index)?.let(pending::addLast)
                }
            }
        }

        if (hasQuickYouTubeNotificationsSurface(root)) {
            allowedSecondarySurfaceVisible = true
        }
        if (hasQuickYouTubeSearchSurface(root)) {
            searchSurfaceVisible = true
        }
        if (homeToolbarVisible || selectedDestination == YouTubeNavigationDestination.YOU) {
            // YouTube leaves the outgoing Notifications/Search toolbar in the same tree while
            // Home is already on screen. Its live logo wins over those stale allowed IDs.
            // On the You tab, toolbar buttons like menu_search are part of the account header,
            // never a secondary overlay surface that should return to feed.
            searchSurfaceVisible = false
            allowedSecondarySurfaceVisible = false
            // Embedded Shorts shelf, ads, or preview videos on the Home feed must never treat
            // the Home feed as the fullscreen Shorts player.
            shortsVisible = false
        }
        val protectedSurface = YouTubeSurfacePolicy.strictProtectedSurface(
            destination = selectedDestination,
            searchSurfaceVisible = searchSurfaceVisible,
            shortsVisible = shortsVisible,
            allowedSecondarySurfaceVisible = allowedSecondarySurfaceVisible
        )
        val feedViewportBounds = YouTubeFeedGeometry.calculateFeedViewport(
            rootBounds = rootBounds,
            statusBarInset = statusBarInset(),
            toolbarBottom = toolbarBounds?.bottom,
            toolbarContainerBottom = toolbarContainerBounds?.bottom,
            bottomBarTop = bottomBarTop,
            dp56 = dp(56),
            dp48 = dp(48),
            dp16 = dp(16)
        )
        val shortsTabBounds = shortsTabCandidate?.takeIf { tab ->
            // YouTube may retain a hidden reel tree briefly after leaving Shorts. The live
            // navigation selection is the authoritative signal for whether this entry guard
            // belongs over the visible bottom bar.
            selectedDestination != YouTubeNavigationDestination.SHORTS &&
                bottomBarTop?.let { tab.centerY() >= it } == true
        }

        return YouTubeUiSignals(
            shortsVisible = shortsVisible,
            safeBottomSheetOpen = bottomSheetHandleVisible && sheetCloseControlVisible,
            definiteBrowseSurface = definiteBrowseSurface && !shortsVisible,
            protectedSurface = protectedSurface,
            feedViewportBounds = feedViewportBounds,
            shortsTabBounds = shortsTabBounds,
            homeTabBounds = navigation.homeTabBounds,
            subscriptionsTabBounds = navigation.subscriptionsTabBounds,
            youTabBounds = navigation.youTabBounds,
            allowedBackBounds = allowedBackCandidate,
            selectedDestination = selectedDestination,
            searchSurfaceVisible = searchSurfaceVisible,
            allowedSecondarySurfaceVisible = allowedSecondarySurfaceVisible
        )
    }

    private data class YouTubeNavigationSignals(
        val selectedDestination: YouTubeNavigationDestination,
        val bottomBarTop: Int?,
        val shortsTabBounds: Rect?,
        val homeTabBounds: Rect?,
        val subscriptionsTabBounds: Rect?,
        val youTabBounds: Rect?
    )

    private fun collectYouTubeNavigationSignals(
        root: AccessibilityNodeInfo
    ): YouTubeNavigationSignals {
        val bars = cachedNodeLookup(root, "$YOUTUBE_PACKAGE:id/bottom_bar_container")
            .filter { it.isVisibleToUser }.ifEmpty {
                cachedNodeLookup(root, "$YOUTUBE_PACKAGE:id/pivot_bar")
                    .filter { it.isVisibleToUser }
            }
        var selectedDestination = YouTubeNavigationDestination.UNKNOWN
        var bottomBarTop: Int? = null
        var shortsTabBounds: Rect? = null
        var homeTabBounds: Rect? = null
        var subscriptionsTabBounds: Rect? = null
        var youTabBounds: Rect? = null

        for (bar in bars) {
            val barBounds = Rect().also(bar::getBoundsInScreen)
            if (barBounds.width() <= 0 || barBounds.height() <= 0) continue
            bottomBarTop = bottomBarTop?.coerceAtMost(barBounds.top) ?: barBounds.top

            val pending = ArrayDeque<AccessibilityNodeInfo>()
            pending.add(bar)
            var visited = 0
            while (pending.isNotEmpty() && visited < 60) {
                val node = pending.removeFirst()
                visited++
                if (node.isVisibleToUser) {
                    val destination = YouTubeSurfacePolicy.navigationDestination(
                        node.contentDescription?.toString()
                    )
                    if (node.isClickable && node.isSelected &&
                        destination != YouTubeNavigationDestination.UNKNOWN
                    ) {
                        selectedDestination = destination
                    }
                    if (node.isClickable && destination == YouTubeNavigationDestination.SHORTS) {
                        val bounds = Rect().also(node::getBoundsInScreen)
                        if (bounds.width() > 0 && bounds.height() > 0) {
                            shortsTabBounds = Rect(bounds)
                        }
                    }
                    if (node.isClickable &&
                        destination == YouTubeNavigationDestination.HOME
                    ) {
                        val bounds = Rect().also(node::getBoundsInScreen)
                        if (bounds.width() > 0 && bounds.height() > 0) {
                            homeTabBounds = Rect(bounds)
                        }
                    }
                    if (node.isClickable &&
                        destination == YouTubeNavigationDestination.SUBSCRIPTIONS
                    ) {
                        val bounds = Rect().also(node::getBoundsInScreen)
                        if (bounds.width() > 0 && bounds.height() > 0) {
                            subscriptionsTabBounds = Rect(bounds)
                        }
                    }
                    if (node.isClickable &&
                        destination == YouTubeNavigationDestination.YOU
                    ) {
                        val bounds = Rect().also(node::getBoundsInScreen)
                        if (bounds.width() > 0 && bounds.height() > 0) {
                            youTabBounds = Rect(bounds)
                        }
                    }
                    // An invisible node cannot own a visible descendant.
                    for (index in 0 until node.childCount) {
                        node.getChild(index)?.let(pending::addLast)
                    }
                }
            }
        }

        return YouTubeNavigationSignals(
            selectedDestination = selectedDestination,
            bottomBarTop = bottomBarTop,
            shortsTabBounds = shortsTabBounds,
            homeTabBounds = homeTabBounds,
            subscriptionsTabBounds = subscriptionsTabBounds,
            youTabBounds = youTabBounds
        )
    }

    private fun findYouTubeNavigationNode(
        root: AccessibilityNodeInfo,
        destination: YouTubeNavigationDestination
    ): AccessibilityNodeInfo? {
        val bars = cachedNodeLookup(root, "$YOUTUBE_PACKAGE:id/bottom_bar_container")
            .filter { it.isVisibleToUser }.ifEmpty {
                cachedNodeLookup(root, "$YOUTUBE_PACKAGE:id/pivot_bar")
                    .filter { it.isVisibleToUser }
            }
        for (bar in bars) {
            val barBounds = Rect().also(bar::getBoundsInScreen)
            val pending = ArrayDeque<AccessibilityNodeInfo>()
            pending.add(bar)
            var visited = 0
            while (pending.isNotEmpty() && visited < 60) {
                val node = pending.removeFirst()
                visited++
                val bounds = Rect().also(node::getBoundsInScreen)
                if (node.isVisibleToUser && node.isClickable && !bounds.isEmpty &&
                    barBounds.contains(bounds.centerX(), bounds.centerY()) &&
                    YouTubeSurfacePolicy.navigationDestination(
                        node.contentDescription?.toString()
                    ) == destination
                ) {
                    return node
                }
                for (index in 0 until node.childCount) {
                    node.getChild(index)?.let(pending::addLast)
                }
            }
        }
        val rootBounds = Rect().also(root::getBoundsInScreen)
        val bottomRegionTop = rootBounds.bottom - dp(100)
        val pending = ArrayDeque<AccessibilityNodeInfo>()
        pending.add(root)
        var visited = 0
        while (pending.isNotEmpty() && visited < 100) {
            val node = pending.removeFirst()
            visited++
            val bounds = Rect().also(node::getBoundsInScreen)
            if (node.isVisibleToUser && node.isClickable && !bounds.isEmpty &&
                rootBounds.contains(bounds.centerX(), bounds.centerY()) &&
                bounds.top >= bottomRegionTop &&
                YouTubeSurfacePolicy.navigationDestination(
                    node.contentDescription?.toString()
                ) == destination
            ) {
                return node
            }
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(pending::addLast)
            }
        }
        return null
    }

    private data class YouTubeToolbarSnapshot(
        val bounds: Rect?,
        val homeLogo: Boolean,
        val search: Boolean,
        val notifications: Boolean,
        val allowedTitle: Boolean,
        val backNode: AccessibilityNodeInfo?,
        val loadingBackPresent: Boolean,
        val launchNodes: Map<YouTubeCurtainScreen, AccessibilityNodeInfo>
    )

    private fun collectYouTubeToolbar(root: AccessibilityNodeInfo, window: Rect): YouTubeToolbarSnapshot {
        val toolbar = cachedNodeLookup(root, "$YOUTUBE_PACKAGE:id/toolbar")
            .firstOrNull { it.isVisibleToUser }
        val toolbarBounds = toolbar?.let { Rect().also(it::getBoundsInScreen) }
        var home = false
        var search = false
        var notifications = false
        var allowedTitle = false
        var back: AccessibilityNodeInfo? = null
        var loadingBackPresent = false
        val launchNodes = mutableMapOf<YouTubeCurtainScreen, AccessibilityNodeInfo>()
        val pending = ArrayDeque<AccessibilityNodeInfo>()
        toolbar?.let(pending::addLast)
        var visited = 0
        while (pending.isNotEmpty() && visited++ < 40) {
            val node = pending.removeFirst()
            if (node.isClickable && YouTubeUiIdentity.isBackNavigationLabel(
                    node.contentDescription?.toString(), node.text?.toString()
                )) loadingBackPresent = true
            if (node.isVisibleToUser) {
                val bounds = Rect().also(node::getBoundsInScreen)
                val id = compactId(node.viewIdResourceName)
                if (node.isClickable && id == "menu_item_view") {
                    val label = node.contentDescription?.toString().orEmpty().lowercase(Locale.ROOT)
                    when {
                        label.startsWith("notifications") || label.startsWith("bildirim") ->
                            launchNodes[YouTubeCurtainScreen.NOTIFICATIONS] = node
                        label == "search" || label == "ara" ->
                            launchNodes[YouTubeCurtainScreen.SEARCH] = node
                    }
                }
                if (id == "youtube_logo" &&
                    YouTubeUiIdentity.isHomeToolbarLogo(
                        bounds.left, bounds.right, window.left, window.right, dp(72)
                    )
                ) {
                    home = true
                }
                if (YouTubeUiIdentity.isSearchSurfaceId(id) &&
                    YouTubeUiIdentity.isSettledSearchSurface(
                        id, bounds.left, bounds.right, window.left, window.right, dp(8)
                    )
                ) search = true
                if (YouTubeUiIdentity.isSettledSecondaryToolbarTitle(
                        node.text?.toString(), node.isClickable, bounds.top - window.top,
                        window.height(), bounds.left, bounds.right, window.left, window.right, dp(8)
                    )
                ) {
                    val title = node.text?.toString().orEmpty().lowercase(Locale.ROOT)
                    if (title.startsWith("notification") || title.startsWith("bildirim")) {
                        notifications = true
                    } else allowedTitle = true
                } else if (ProtectionFlightRecorder.ENABLED) {
                    // Diagnostic: a title that exists but fails the settled test
                    // delays the notifications/search classification.
                    val raw = node.text?.toString()
                    val lower = raw.orEmpty().trim().lowercase(Locale.ROOT)
                    if (lower.startsWith("notifications") || lower.startsWith("bildirim")) {
                        ProtectionFlightRecorder.record(this, "youtube.notif.candidate",
                            "text=${raw.orEmpty().take(20)} clickable=${node.isClickable} " +
                                "top=${bounds.top - window.top} left=${bounds.left} " +
                                "right=${bounds.right} winTop=${window.top} winH=${window.height()}")
                    }
                }
                if (node.isClickable && YouTubeUiIdentity.isSettledLeadingBackControl(
                        node.contentDescription?.toString(), node.text?.toString(),
                        bounds.left, bounds.right, window.left, window.right, dp(8)
                    )
                ) back = node
                // An invisible toolbar node cannot own a visible descendant.
                for (index in 0 until node.childCount) node.getChild(index)?.let(pending::addLast)
            }
        }
        return YouTubeToolbarSnapshot(toolbarBounds, home, search, notifications, allowedTitle, back,
            loadingBackPresent, launchNodes)
    }

    private fun collectYouTubeCurtainSignals(root: AccessibilityNodeInfo): YouTubeUiSignals {
        val navigation = collectYouTubeNavigationSignals(root)
        val window = Rect().also(root::getBoundsInScreen)
        val toolbarSnapshot = collectYouTubeToolbar(root, window)
        val homeToolbar = toolbarSnapshot.homeLogo
        val search = toolbarSnapshot.search
        val notifications = toolbarSnapshot.notifications
        // Every exact-ID lookup is a full remote traversal that waits for
        // YouTube's UI thread, which is busiest exactly during transitions.
        // Evaluate them lazily in classifier order: a settled Home toolbar or
        // a settled secondary page never needs the player/profile/camera
        // probes, and those used to queue 15+ IPCs per scan.
        val shorts = !homeToolbar && listOf(
            "reel_recycler", "reel_watch_fragment_root", "shorts_player"
        ).any { id ->
            findVisibleNodeBounds(root, "$YOUTUBE_PACKAGE:id/$id")?.let {
                YouTubeUiIdentity.hasMeaningfulHorizontalPresence(
                    it.left, it.right, window.left, window.right
                ) && it.height() >= window.height() / 2
            } == true
        }
        val needsSecondaryProbe = !homeToolbar && !shorts && !search && !notifications
        val profileVisible = needsSecondaryProbe &&
            navigation.selectedDestination == YouTubeNavigationDestination.YOU &&
            findVisibleNodeBounds(root, "$YOUTUBE_PACKAGE:id/collapsing_header_container")?.let {
                YouTubeUiIdentity.isConfirmedYouSurface(
                    navigation.selectedDestination, false,
                    it.left, it.top, it.right, it.bottom,
                    window.left, window.top, window.right, window.bottom
                )
            } == true
        val otherAllowed = needsSecondaryProbe && !profileVisible && (
            toolbarSnapshot.allowedTitle ||
                // Channel and playlist pages share a collapsing header under a
                // toolbar titled with the channel or playlist name, which no
                // title list can know. Home and Subscriptions have no such
                // header. A channel's tab strip (Home/Videos/Shorts/...) stays
                // pinned if the header ever scrolls out of view.
                listOf("collapsing_header_container", "tabs_bar_view").any { id ->
                    findVisibleNodeBounds(root, "$YOUTUBE_PACKAGE:id/$id")?.let {
                        YouTubeUiIdentity.hasMeaningfulHorizontalPresence(
                            it.left, it.right, window.left, window.right)
                    } == true
                } ||
                // watch_player alone also exists for the miniplayer; only then
                // are the two confirming lookups worth their remote cost.
                (findVisibleNodeBounds(root, "$YOUTUBE_PACKAGE:id/watch_player") != null &&
                    YouTubeUiIdentity.isConfirmedWatchSurface(
                        watchPlayerVisible = true,
                        watchPanelVisible = findVisibleNodeBounds(
                            root, "$YOUTUBE_PACKAGE:id/watch_panel") != null,
                        inlinePlayerVisible = findVisibleNodeBounds(
                            root, "$YOUTUBE_PACKAGE:id/inline_player_container") != null
                    )) ||
                // The Shorts creation flow (camera, mode switcher, post-record
                // editor) is an allowed surface. Its window replaces the feed,
                // so the curtain must lift instead of being retained for an
                // unclassified tree (the + button opened the camera under it).
                // Those full-screen pages hide the bottom navigation; while the
                // bar is visible the five lookups cannot match and are skipped.
                (navigation.bottomBarTop == null && listOf(
                    "reel_camera_layout", "camera_preview", "record_button_view",
                    "creation_modes_container", "editor_tool_button_icon"
                ).any { findVisibleNodeBounds(root, "$YOUTUBE_PACKAGE:id/$it") != null })
            )
        // A minimized long video stays usable above a covered feed: the user
        // must be able to expand, pause or close what they chose to watch.
        val miniplayer = if (homeToolbar) {
            YouTubeFeedGeometry.miniplayerCutout(
                controls = findVisibleNodeBounds(root, "$YOUTUBE_PACKAGE:id/floaty_bar_controls_view"),
                window = window
            )
        } else null
        val screen = YouTubeScreenClassifier.classify(
            navigation.selectedDestination, homeToolbar, profileVisible,
            search, notifications, otherAllowed, shorts
        )
        if (ProtectionFlightRecorder.ENABLED) {
            val signature = "$screen|$search|$notifications|$otherAllowed|$homeToolbar|" +
                "${navigation.selectedDestination}"
            if (signature != lastYouTubeSignalsSignature) {
                lastYouTubeSignalsSignature = signature
                ProtectionFlightRecorder.record(this, "youtube.signals",
                    "screen=$screen search=$search notif=$notifications other=$otherAllowed " +
                        "home=$homeToolbar selected=${navigation.selectedDestination} " +
                        "shorts=$shorts")
            }
        }
        val toolbar = toolbarSnapshot.bounds
        val viewport = YouTubeFeedGeometry.calculateFeedViewport(
            window, statusBarInset(), toolbar?.bottom, null,
            navigation.bottomBarTop, dp(56), dp(48), dp(16)
        )
        val backBounds = if (screen == YouTubeCurtainScreen.SEARCH ||
            screen == YouTubeCurtainScreen.NOTIFICATIONS ||
            (screen == YouTubeCurtainScreen.UNKNOWN && !homeToolbar)
        ) toolbarSnapshot.backNode?.let {
            Rect().also(it::getBoundsInScreen)
        } ?: toolbar?.takeIf {
            screen == YouTubeCurtainScreen.UNKNOWN && !homeToolbar && toolbarSnapshot.loadingBackPresent
        }?.let { Rect(window.left, it.top, window.left + dp(56), it.bottom) }
            // The search-suggestions page (before a query is submitted) draws
            // its Back arrow outside the "toolbar" view; without this its
            // touch-first return guard was never mounted.
            ?: if (screen == YouTubeCurtainScreen.SEARCH) {
                findYouTubeBackNavigationNode(root)?.let { Rect().also(it::getBoundsInScreen) }
                    ?.takeIf { it.left <= window.left + dp(8) && it.width() <= dp(96) }
            } else null
        else null
        return YouTubeUiSignals(
            shortsVisible = shorts,
            safeBottomSheetOpen = shorts &&
                findVisibleNodeBounds(root, "$YOUTUBE_PACKAGE:id/arrow_drag_handle") != null &&
                findVisibleNodeBounds(root, "$YOUTUBE_PACKAGE:id/close_button") != null,
            definiteBrowseSurface = !shorts && screen != YouTubeCurtainScreen.UNKNOWN,
            protectedSurface = when (screen) {
                YouTubeCurtainScreen.HOME -> YouTubeProtectedSurface.HOME
                YouTubeCurtainScreen.SUBSCRIPTIONS -> YouTubeProtectedSurface.SUBSCRIPTIONS
                else -> null
            },
            feedViewportBounds = viewport,
            shortsTabBounds = navigation.shortsTabBounds,
            homeTabBounds = navigation.homeTabBounds,
            subscriptionsTabBounds = navigation.subscriptionsTabBounds,
            youTabBounds = navigation.youTabBounds,
            allowedBackBounds = backBounds,
            allowedLaunchBounds = toolbarSnapshot.launchNodes.mapValues { (_, node) ->
                Rect().also(node::getBoundsInScreen)
            }.filterValues { bounds ->
                // A top-docked miniplayer covers these toolbar icons; a guard
                // left above it turned a tap on the player's Close button
                // into opening Search.
                miniplayer == null || !Rect.intersects(bounds, miniplayer)
            }.takeIf { screen in setOf(YouTubeCurtainScreen.HOME, YouTubeCurtainScreen.SUBSCRIPTIONS) }
                ?: emptyMap(),
            selectedDestination = navigation.selectedDestination,
            searchSurfaceVisible = search,
            allowedSecondarySurfaceVisible = notifications || otherAllowed,
            screen = screen,
            windowBounds = window,
            miniplayerBounds = miniplayer
        )
    }

    private fun refreshYouTubeSurfaceProtection(root: AccessibilityNodeInfo) {
        if (youtubeOverlay.isSystemUiSuspended) {
            if (focusedNonOverlayPackage() != YOUTUBE_PACKAGE) return
            youtubeOverlay.setSystemUiSuspended(false)
        }
        if (root.packageName?.toString() != YOUTUBE_PACKAGE ||
            !ProtectionPreferences.isEnabled(this, ProtectedApp.YOUTUBE)
        ) {
            resetYouTubeProtection()
            return
        }
        val collectStarted = SystemClock.uptimeMillis()
        val signals = collectYouTubeCurtainSignals(root)
        val applyStarted = SystemClock.uptimeMillis()
        applyYouTubeSurfaceProtection(signals)
        if (ProtectionFlightRecorder.ENABLED) {
            val done = SystemClock.uptimeMillis()
            if (done - collectStarted >= 40L) ProtectionFlightRecorder.record(this, "youtube.scan.split",
                "collectMs=${applyStarted - collectStarted} applyMs=${done - applyStarted}")
        }
        if (ProtectionPreferences.getMode(this, ProtectedApp.YOUTUBE) == ProtectionMode.STRICT) return
        val decision = youtubeShortsSession.observe(
            YouTubeShortsObservation(
                nowMs = SystemClock.uptimeMillis(),
                shortsStructureVisible = signals.shortsVisible,
                launchRequested = false,
                definitelyOutsideShorts = signals.definiteBrowseSurface,
                safeBottomSheetOpen = signals.safeBottomSheetOpen,
                isScrollEvent = false,
                hasVerticalMovement = false,
                sourceIsShortsPager = false,
                feedTabEntry = YouTubeShortsPolicy.shouldBlockEntry(
                    ProtectionMode.BALANCED,
                    fromShortsTab = SystemClock.uptimeMillis() < youtubeShortsTabClickUntil
                )
            )
        )
        if (decision == YouTubeShortsDecision.EXIT) {
            performYouTubeShortsExit()
        }
        if (youtubeOverlay.hasVisibleWindows) scheduleYouTubeOverlayForegroundCheck()
    }

    private fun applyYouTubeSurfaceProtection(signals: YouTubeUiSignals) {
        val now = SystemClock.uptimeMillis()
        // Empty/oscillating trees or an exit timeout must not create a new
        // count. Only a verified return outside Shorts rearms the encounter.
        if (signals.definiteBrowseSurface && !signals.shortsVisible && !signals.safeBottomSheetOpen) {
            youtubeBlockedAttempt.reset()
        }
        signals.windowBounds?.let(youtubeOverlay::updateWindowBounds)
        if (signals.selectedDestination != YouTubeNavigationDestination.UNKNOWN) {
            youtubeLastSelectedDestination = signals.selectedDestination
        }
        val previousScreen = youtubeObservedScreen
        youtubeObservedScreen = signals.screen
        if (signals.screen == YouTubeCurtainScreen.OTHER_ALLOWED &&
            youtubeLastVerifiedScreen == YouTubeCurtainScreen.NOTIFICATIONS
        ) youtubeNotificationChildOpen = true
        if (signals.screen !in setOf(YouTubeCurtainScreen.UNKNOWN, YouTubeCurtainScreen.OTHER_ALLOWED)) {
            youtubeNotificationChildOpen = false
        }
        val returningWithinAllowedPages = signals.screen == YouTubeCurtainScreen.UNKNOWN &&
            youtubeNotificationChildOpen &&
            YouTubeSurfacePolicy.preserveAllowedSecondaryDuringLoading(
                youtubeLastVerifiedScreen, signals.allowedBackBounds != null
            )
        if (signals.screen == YouTubeCurtainScreen.UNKNOWN &&
            signals.allowedBackBounds != null && youtubeAllowedReturnSurface == null &&
            !returningWithinAllowedPages
        ) {
            // The Back arrow can be usable before the secondary page title has settled.
            // Arm its return guard during loading too, not only after Notifications is named.
            youtubeAllowedReturnSurface = when (youtubeLastStableDestination) {
                YouTubeNavigationDestination.HOME -> YouTubeProtectedSurface.HOME
                YouTubeNavigationDestination.SUBSCRIPTIONS -> YouTubeProtectedSurface.SUBSCRIPTIONS
                else -> null
            }
        }
        // Conservative coverage while a Home/Subscriptions tab is selected is
        // only safe when the feed itself was the last verified surface. If the
        // last verified surface was an allowed one (a watch page, search,
        // notifications, the You tab), an UNKNOWN frame is a loading step of
        // that allowed page - covering it would drop the curtain onto a video
        // the user is watching. Tab-initiated feed returns are guarded by the
        // protection latches instead.
        val lastVerifiedAllowedSurface =
            youtubeLastVerifiedScreen == YouTubeCurtainScreen.OTHER_ALLOWED ||
                youtubeLastVerifiedScreen == YouTubeCurtainScreen.SEARCH ||
                youtubeLastVerifiedScreen == YouTubeCurtainScreen.NOTIFICATIONS ||
                youtubeLastVerifiedScreen == YouTubeCurtainScreen.YOU
        // Leaving Search/Notifications with system Back produces no click and no
        // touch on the back guard, and the feed slides in for 300-700 ms before
        // its toolbar settles. Opening anything from those pages always starts
        // with a click, so an unclassified frame without a recent click is the
        // feed coming back. A false guess lasts one frame: the next verified
        // secondary page lifts the curtain without a settle delay.
        val clicklessSecondaryReturn =
            (youtubeLastVerifiedScreen == YouTubeCurtainScreen.SEARCH ||
                youtubeLastVerifiedScreen == YouTubeCurtainScreen.NOTIFICATIONS) &&
                !youtubeNotificationChildOpen &&
                now - youtubeLastClickAt >= YOUTUBE_CLICK_INTENT_MS
        if (!youtubeCurtainState.isPending && signals.screen == YouTubeCurtainScreen.UNKNOWN &&
            !returningWithinAllowedPages &&
            (!lastVerifiedAllowedSurface || clicklessSecondaryReturn) &&
            signals.selectedDestination in setOf(
                YouTubeNavigationDestination.HOME, YouTubeNavigationDestination.SUBSCRIPTIONS
            )
        ) {
            // The selected tab also survives behind Search/Notifications while their toolbar
            // loads. Cover UNKNOWN conservatively, but do not invent a navigation request:
            // the next verified secondary page must release coverage without a timeout.
            youtubeCurtainState.observe(signals.selectedDestination.curtainScreen(), now)
        }
        if (signals.screen == YouTubeCurtainScreen.SHORTS && youtubeCurtainState.isPending) {
            youtubeCurtainState.clear()
        }
        val surface = youtubeCurtainState.observe(signals.screen, now, signals.selectedDestination)
        if (ProtectionFlightRecorder.ENABLED) {
            val signature = "${youtubeCurtainState.pendingDestination}|" +
                "${youtubeCurtainState.currentSurface}|${youtubeCurtainState.isPending}|" +
                "${signals.allowedBackBounds}|$youtubeAllowedReturnSurface"
            if (signature != lastYouTubeCurtainStateSignature) {
                lastYouTubeCurtainStateSignature = signature
                ProtectionFlightRecorder.record(this, "youtube.curtain.state",
                    "pending=${youtubeCurtainState.pendingDestination} " +
                        "surface=${youtubeCurtainState.currentSurface} screen=${signals.screen} " +
                        "selected=${signals.selectedDestination} " +
                        "outgoing=${youtubeCurtainState.lastRequestOutgoingScreen} " +
                        "back=${signals.allowedBackBounds} returnTo=$youtubeAllowedReturnSurface")
            }
        }
        if (signals.screen != YouTubeCurtainScreen.UNKNOWN) youtubeLastVerifiedScreen = signals.screen
        when (signals.screen) {
            YouTubeCurtainScreen.HOME, YouTubeCurtainScreen.SUBSCRIPTIONS -> {
                youtubeLastStableDestination = signals.selectedDestination
                youtubeAllowedReturnBounds = signals.feedViewportBounds?.let(::Rect)
            }
            YouTubeCurtainScreen.YOU -> {
                youtubeLastStableDestination = YouTubeNavigationDestination.YOU
                youtubeAllowedReturnSurface = null
            }
            YouTubeCurtainScreen.SEARCH, YouTubeCurtainScreen.NOTIFICATIONS -> {
                if (previousScreen != signals.screen && youtubeAllowedReturnSurface == null) {
                    youtubeAllowedReturnSurface = when (youtubeLastStableDestination) {
                        YouTubeNavigationDestination.HOME -> YouTubeProtectedSurface.HOME
                        YouTubeNavigationDestination.SUBSCRIPTIONS -> YouTubeProtectedSurface.SUBSCRIPTIONS
                        else -> null
                    }
                }
            }
            else -> Unit
        }
        youtubeOverlay.rememberCurtainViewport(
            signals.feedViewportBounds,
            overwriteExisting = !youtubeCurtainState.isPending
        )
        // A Shorts entry is covered in strict mode and when a real tab tap just
        // slipped through the guard; an intentionally opened link/search Short
        // stays uncovered in balanced mode and runs the single-video session.
        val shortsTabEntry = now < youtubeShortsTabClickUntil
        val blockedShorts = signals.screen == YouTubeCurtainScreen.SHORTS &&
            YouTubeShortsPolicy.shouldBlockEntry(
                ProtectionPreferences.getMode(this, ProtectedApp.YOUTUBE),
                fromShortsTab = shortsTabEntry
            )
        val effectiveSurface = if (blockedShorts) YouTubeProtectedSurface.HOME else surface
        // Anti-flap: while the active window flips between a leaving allowed
        // page (camera, watch) and the feed, the curtain must not be removed
        // and re-added per frame. Hiding waits until the allowed surface has
        // held for a short settle window; showing is immediate. Verified
        // secondary pages (Notifications, Search) are definitive: their curtain
        // lifts on the first verified frame so the return never feels late.
        val definitiveSecondary = signals.screen == YouTubeCurtainScreen.SEARCH ||
            signals.screen == YouTubeCurtainScreen.NOTIFICATIONS
        val showSurface = if (effectiveSurface == null) {
            if (definitiveSecondary) {
                youtubeAllowedFlipSince = 0L
                null
            } else {
                if (youtubeAllowedFlipSince == 0L) youtubeAllowedFlipSince = now
                if (now - youtubeAllowedFlipSince < YOUTUBE_ALLOWED_FLIP_SETTLE_MS) {
                    youtubeOverlay.currentCurtainSurface
                } else null
            }
        } else {
            youtubeAllowedFlipSince = 0L
            effectiveSurface
        }
        if (showSurface != null && effectiveSurface == null) {
            // Hiding waits for the settle window, but no further event is
            // guaranteed once the allowed page is still. Re-check right after.
            queueYouTubeSurfaceScan(
                (YOUTUBE_ALLOWED_FLIP_SETTLE_MS - (now - youtubeAllowedFlipSince)).coerceAtLeast(0L) + 8L)
        }
        // Transitional frames never prove the player went away; only a verified
        // feed frame may restore or remove the minimized-player hole.
        if (signals.screen == YouTubeCurtainScreen.HOME ||
            signals.screen == YouTubeCurtainScreen.SUBSCRIPTIONS) {
            youtubeOverlay.updateMiniplayerCutout(signals.miniplayerBounds)
        } else if (blockedShorts) {
            youtubeOverlay.updateMiniplayerCutout(null)
        }
        youtubeOverlay.showCurtain(
            signals.feedViewportBounds, showSurface, isEnglish(),
            preserveBounds = youtubeCurtainState.isPending
        )
        val inFlight = youtubeTabGestureDestination.takeIf {
            youtubeTabGestureGeneration == youtubeCurtainState.generation
        }
        youtubeOverlay.updateYouNavigationGuard(
            signals.youTabBounds,
            active = youtubeOverlay.isCurtainVisible && inFlight != YouTubeNavigationDestination.YOU,
            isEnglish = isEnglish(),
            preserveBounds = youtubeCurtainState.isPending
        )
        youtubeOverlay.updateShortsGuard(signals.shortsTabBounds, isEnglish())
        youtubeOverlay.updateAllowedLaunchGuards(signals.allowedLaunchBounds, isEnglish())
        youtubeOverlay.updateProtectedNavigationGuards(
            signals.homeTabBounds, signals.subscriptionsTabBounds, isEnglish(),
            active = true,
            preserveBounds = youtubeCurtainState.isPending,
            suspendedSurface = when (inFlight) {
                YouTubeNavigationDestination.HOME -> YouTubeProtectedSurface.HOME
                YouTubeNavigationDestination.SUBSCRIPTIONS -> YouTubeProtectedSurface.SUBSCRIPTIONS
                else -> null
            }
        )
        if (youtubeCurtainState.pendingDestination !in
            setOf(YouTubeCurtainScreen.HOME, YouTubeCurtainScreen.SUBSCRIPTIONS)
        ) {
            // ACTION_DOWN pre-arms the feed curtain. Keep the touch window alive until
            // ACTION_UP delivers Back; its callback releases it after consuming the gesture.
            val waitingForSecondary = youtubeCurtainState.pendingDestination in
                setOf(YouTubeCurtainScreen.SEARCH, YouTubeCurtainScreen.NOTIFICATIONS)
            if (!waitingForSecondary || signals.allowedBackBounds != null || youtubeAllowedReturnSurface == null) {
                youtubeOverlay.updateAllowedBackGuard(
                    signals.allowedBackBounds.takeIf {
                        youtubeAllowedReturnSurface != null && !returningWithinAllowedPages
                    }, isEnglish()
                )
            }
        }
        traceYouTubeProtection("observed")
        if (blockedShorts && now - youtubeStrictShortsExitAt >= 600L) {
            youtubeStrictShortsExitAt = now
            recordYouTubeViewerIntervention(performGlobalAction(GLOBAL_ACTION_BACK))
            queueYouTubeSurfaceScan(96L)
        }
        scheduleYouTubeOverlayForegroundCheck()
    }

    private fun resetYouTubeProtection() {
        youtubeBlockedAttempt.reset()
        clearYouTubePendingProtectedNavigation()
        cancelYouTubeSurfaceScan()
        mainHandler.removeCallbacks(youtubeOverlayForegroundCheck)
        youtubeOverlayForegroundCheckScheduled = false
        youtubeOverlayForegroundCheckAt = 0L
        youtubeObservedScreen = YouTubeCurtainScreen.UNKNOWN
        youtubeLastVerifiedScreen = YouTubeCurtainScreen.UNKNOWN
        youtubeAllowedFlipSince = 0L
        youtubeNotificationChildOpen = false
        youtubeLastStableDestination = YouTubeNavigationDestination.UNKNOWN
        youtubeLastSelectedDestination = YouTubeNavigationDestination.UNKNOWN
        youtubeAllowedReturnSurface = null
        youtubeAllowedReturnBounds = null
        youtubeTabGestureDestination = null
        youtubeTabGestureGeneration = null
        youtubeOverlay.hideAll()
        traceYouTubeProtection("reset")
    }

    private fun clearYouTubePendingProtectedNavigation() {
        youtubeCurtainState.clear()
    }

    private fun currentYouTubeRoot(): AccessibilityNodeInfo? {
        val activeRoot = rootInActiveWindow
        val activePackage = activeRoot?.packageName?.toString()
        if (activePackage == YOUTUBE_PACKAGE) return activeRoot
        val visibleWindows = windows
        // An active soft keyboard (IME) or our own accessibility overlay can temporarily make its
        // window the active root while YouTube remains the visible foreground application underneath.
        val foremostAppPackage = visibleWindows.asSequence()
            .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            .mapNotNull { it.root?.packageName?.toString() }
            .firstOrNull()
        if (foremostAppPackage == YOUTUBE_PACKAGE) {
            return visibleWindows.asSequence()
                .mapNotNull { it.root }
                .firstOrNull { it.packageName?.toString() == YOUTUBE_PACKAGE }
        }
        return null
    }

    private fun isInputMethodVisible(): Boolean =
        windows.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }

    private fun isYouTubeNotificationShade(): Boolean {
        if (lastPackage != YOUTUBE_PACKAGE && !youtubeOverlay.hasVisibleWindows) return false
        val systemRoot = windows.asSequence().mapNotNull { it.root }
            .firstOrNull { it.packageName?.toString() == "com.android.systemui" } ?: return false
        return listOf("notification_panel", "notification_stack_scroller", "quick_settings_container")
            .any { id -> systemRoot.findAccessibilityNodeInfosByViewId("com.android.systemui:id/$id")
                .orEmpty().any { it.isVisibleToUser } }
    }

    private fun retainInstagramCurtainUnderNotificationShade(activePackage: String?,
        knownActiveRoot: AccessibilityNodeInfo? = null): Boolean {
        if (activePackage != "com.android.systemui") return false
        // Resolved only after the cheap package check: a default argument of
        // rootInActiveWindow was evaluated at every call site, which meant a
        // blocking remote root query for every event of every protected app.
        val activeRoot = knownActiveRoot ?: rootInActiveWindow
        if (activeRoot?.packageName?.toString() == "com.android.systemui" &&
            InstagramNotificationShadePolicy.canReuseShadeWindow(
                instagramOverlay.isNotificationShadeSuspended, activeRoot.windowId,
                instagramNotificationShadeWindowId) &&
            ProtectionPreferences.isEnabled(this, ProtectedApp.INSTAGRAM)) {
            scheduleInstagramShadeCheck()
            return true
        }
        if (focusedNonOverlayPackage() != "com.android.systemui" &&
            rootInActiveWindow?.packageName?.toString() != "com.android.systemui") return false
        val foremostApp = windows.asSequence()
            .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            .mapNotNull { it.root?.packageName?.toString() }.firstOrNull()
        val systemRoot = windows.asSequence().mapNotNull { it.root }
            .firstOrNull { it.packageName?.toString() == "com.android.systemui" }
        val shadeVisible = systemRoot != null && listOf(
            "notification_panel", "notification_stack_scroller", "quick_settings_container"
        ).any { id -> systemRoot.findAccessibilityNodeInfosByViewId("com.android.systemui:id/$id")
            .orEmpty().any { it.isVisibleToUser } }
        if (!InstagramNotificationShadePolicy.canRetain(
                enabled = ProtectionPreferences.isEnabled(this, ProtectedApp.INSTAGRAM),
                screen = lastInstagramScreen,
                curtainAttached = instagramOverlay.hasVisibleWindows,
                instagramUnderneath = InstagramNotificationShadePolicy.hasInstagramContext(
                    foremostApp, previouslyInstagram = lastPackage == INSTAGRAM_PACKAGE),
                shadeVisible = shadeVisible)) return false
        if (!instagramOverlay.isNotificationShadeSuspended) {
            instagramNotificationShadeWindowId = systemRoot?.windowId ?: -1
            instagramOverlay.suspendForNotificationShade()
            Log.d(TAG, "IG_SHADE_RETAIN screen=$lastInstagramScreen")
        }
        // Poll only during the shade session; do not wait for a new app event
        // or perform the general Instagram tree scan on each SystemUI update.
        scheduleInstagramShadeCheck()
        return true
    }

    private fun scheduleInstagramShadeCheck() {
        mainHandler.removeCallbacks(overlayForegroundCheck)
        overlayForegroundCheckScheduled = true
        mainHandler.postDelayed(overlayForegroundCheck, 32L)
    }

    private fun matchesRetainedInstagramFeed(root: AccessibilityNodeInfo, screen: InstagramScreen): Boolean {
        if (igVisible(root, "action_bar_button_back")) return false
        return when (screen) {
            InstagramScreen.HOME_FEED -> igVisible(root, "main_feed_action_bar") && igSelected(root, "feed_tab")
            InstagramScreen.EXPLORE -> igVisible(root, "explore_action_bar") && igSelected(root, "search_tab") &&
                igVisible(root, "grid_card_layout_container")
            else -> false
        }
    }

    private fun resumeInstagramNotificationShade(root: AccessibilityNodeInfo) {
        if (!instagramOverlay.isNotificationShadeSuspended ||
            root.packageName?.toString() != INSTAGRAM_PACKAGE) return
        val started = SystemClock.uptimeMillis()
        val display = currentDisplayBounds()
        val retained = lastVerifiedInstagramCurtain?.takeIf { (screen, bounds) ->
            screen == lastInstagramScreen && root.windowId == currentInstagramWindowId &&
                bounds.width() == display.width() && display.contains(bounds)
        }
        if (ProtectionPreferences.isEnabled(this, ProtectedApp.INSTAGRAM) && retained != null &&
            matchesRetainedInstagramFeed(root, retained.first)) {
            clearInstagramResumeProtection()
            if (instagramOverlay.show(retained.second, retained.first, isEnglish())) {
                Log.d(TAG, "IG_SHADE_FAST_RETURN screen=${retained.first} " +
                    "elapsedMs=${SystemClock.uptimeMillis() - started}")
                queueInstagramScan(0L)
                return
            }
        }
        if (!ProtectionPreferences.isEnabled(this, ProtectedApp.INSTAGRAM) ||
            !refreshFastMainProtectedSurface(root, instagramWindowBounds(root))) {
            // A notification can open a chat/search/player, even in the same
            // activity window. Never restore merely from the old screen/window.
            instagramOverlay.hideAll()
        }
        Log.d(TAG, "IG_SHADE_RETURN screen=$lastInstagramScreen")
        queueInstagramScan(0L)
    }

    private fun focusedNonOverlayPackage(): String? {
        val visibleWindows = windows
        // Only our accessibility overlays are skipped. AwayDoomscrollin's own
        // activity is a real foreground app: treating it as an overlay kept the
        // YouTube tab guards mounted above it, where they swallowed taps on the
        // app's own bottom bar and counted them as blocked Shorts attempts.
        fun ownOverlay(window: AccessibilityWindowInfo) =
            window.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY
        for (window in visibleWindows) {
            if (!window.isFocused) continue
            if (window.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD || ownOverlay(window)) continue
            val foregroundPackage = window.root?.packageName?.toString() ?: continue
            return foregroundPackage
        }
        for (window in visibleWindows) {
            if (!window.isActive) continue
            if (window.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD || ownOverlay(window)) continue
            val foregroundPackage = window.root?.packageName?.toString() ?: continue
            return foregroundPackage
        }
        return null
    }

    private fun queueYouTubeSurfaceScan(delayMs: Long = YOUTUBE_SURFACE_SCAN_DELAY_MS) {
        val now = SystemClock.uptimeMillis()
        val requestedAt = now + delayMs
        if (youtubeSurfaceScanScheduled && youtubeSurfaceScanAt <= requestedAt) return
        if (youtubeSurfaceScanScheduled) mainHandler.removeCallbacks(youtubeSurfaceScan)
        youtubeSurfaceScanScheduled = true
        youtubeSurfaceScanAt = requestedAt
        mainHandler.postDelayed(youtubeSurfaceScan, delayMs)
    }

    /**
     * Content-change events stream continuously while a video, preview or
     * timer is playing. Each one used to request a scan 24 ms later, which
     * kept the main looper permanently busy with remote traversals and made
     * the decisive window-state events wait behind them. Structural events
     * still scan immediately; content events are rate limited.
     */
    private fun queueYouTubeContentScan() {
        val now = SystemClock.uptimeMillis()
        val earliest = youtubeLastScanFinishedAt + YOUTUBE_CONTENT_SCAN_SPACING_MS
        queueYouTubeSurfaceScan(maxOf(YOUTUBE_SURFACE_SCAN_DELAY_MS, earliest - now))
    }

    private fun cancelYouTubeSurfaceScan() {
        mainHandler.removeCallbacks(youtubeSurfaceScan)
        youtubeSurfaceScanScheduled = false
        youtubeSurfaceScanAt = 0L
    }

    private fun scheduleYouTubeOverlayForegroundCheck() {
        // The loop is a safety net for a missing final event, not the primary
        // detector. On a still allowed page (watch page, You, search) a full
        // classification every 200 ms only competed with real events.
        val delay = if (youtubeCurtainState.isPending || youtubeOverlay.isSystemUiSuspended) 32L
        else 250L
        val requestedAt = SystemClock.uptimeMillis() + delay
        if (youtubeOverlayForegroundCheckScheduled &&
            youtubeOverlayForegroundCheckAt <= requestedAt
        ) return
        if (youtubeOverlayForegroundCheckScheduled) {
            mainHandler.removeCallbacks(youtubeOverlayForegroundCheck)
        }
        youtubeOverlayForegroundCheckScheduled = true
        youtubeOverlayForegroundCheckAt = requestedAt
        mainHandler.postDelayed(youtubeOverlayForegroundCheck, delay)
    }

    private fun hasQuickYouTubeHomeToolbar(root: AccessibilityNodeInfo): Boolean {
        val logo = findVisibleNodeBounds(root, "$YOUTUBE_PACKAGE:id/youtube_logo")
            ?: return false
        val window = Rect().also(root::getBoundsInScreen)
        return YouTubeUiIdentity.isHomeToolbarLogo(
            logo.left, logo.right, window.left, window.right, dp(16)
        )
    }

    private fun hasQuickYouTubeSearchSurface(root: AccessibilityNodeInfo): Boolean {
        val rootBounds = Rect().also(root::getBoundsInScreen)
        val tolerance = dp(16)
        val searchFieldMaxLeft = rootBounds.left + (rootBounds.width() / 6) + (tolerance / 2)
        return YouTubeUiIdentity.exactAllowedSurfaceIds.asSequence()
            .filter(YouTubeUiIdentity::isSearchSurfaceId)
            .any { compactId ->
                root.findAccessibilityNodeInfosByViewId("$YOUTUBE_PACKAGE:id/$compactId")
                    .orEmpty()
                    .any nodeLoop@ { node ->
                        if (!node.isVisibleToUser) return@nodeLoop false
                        val bounds = Rect().also(node::getBoundsInScreen)
                        val maxLeft = if (compactId == "search_layout") rootBounds.left + tolerance else searchFieldMaxLeft
                        if (bounds.left > maxLeft || bounds.right > rootBounds.right + tolerance) {
                            return@nodeLoop false
                        }
                        YouTubeUiIdentity.isSettledSearchSurface(
                            compactId,
                            bounds.left,
                            bounds.right,
                            rootBounds.left,
                            rootBounds.right,
                            tolerance
                        )
                    }
            }
    }

    private fun hasQuickYouTubeNotificationsSurface(root: AccessibilityNodeInfo): Boolean {
        if (hasQuickYouTubeHomeToolbar(root)) {
            return false
        }
        // Note: the notifications page itself publishes a filter_bar (the
        // All / Comments / Mentions chips). Rejecting the page whenever that
        // bar exists made this quick path permanently false on real
        // notifications screens and delayed the curtain lift.
        val rootBounds = Rect().also(root::getBoundsInScreen)
        val pending = ArrayDeque<AccessibilityNodeInfo>()
        pending.add(root)
        var visited = 0
        while (pending.isNotEmpty() && visited < YOUTUBE_QUICK_IDENTITY_MAX_NODES) {
            val node = pending.removeFirst()
            visited++
            if (node.isVisibleToUser) {
                val bounds = Rect().also(node::getBoundsInScreen)
                if (YouTubeUiIdentity.isSettledSecondaryToolbarTitle(
                        rawText = node.text?.toString(),
                        isClickable = node.isClickable,
                        top = bounds.top - rootBounds.top,
                        windowHeight = rootBounds.height(),
                        left = bounds.left,
                        right = bounds.right,
                        windowLeft = rootBounds.left,
                        windowRight = rootBounds.right,
                        tolerancePx = dp(16)
                    )
                ) return true
            }
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(pending::addLast)
            }
        }
        return false
    }

    private fun findYouTubeBackNavigationNode(
        root: AccessibilityNodeInfo
    ): AccessibilityNodeInfo? {
        val rootBounds = Rect().also(root::getBoundsInScreen)
        val topLimit = rootBounds.top + rootBounds.height() / 4
        val pending = ArrayDeque<AccessibilityNodeInfo>()
        pending.add(root)
        var visited = 0
        while (pending.isNotEmpty() && visited < YOUTUBE_QUICK_IDENTITY_MAX_NODES) {
            val node = pending.removeFirst()
            visited++
            if (node.isVisibleToUser && node.isClickable &&
                YouTubeUiIdentity.isBackNavigationLabel(
                    node.contentDescription?.toString(),
                    node.text?.toString()
                )
            ) {
                val bounds = Rect().also(node::getBoundsInScreen)
                if (bounds.width() > 0 && bounds.height() > 0 &&
                    bounds.top < topLimit &&
                    YouTubeUiIdentity.isHorizontallyOnScreen(
                        bounds.left,
                        bounds.right,
                        rootBounds.left,
                        rootBounds.right
                    )
                ) {
                    return node
                }
            }
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(pending::addLast)
            }
        }
        return null
    }

    private fun youtubeNavigationIntent(
        source: AccessibilityNodeInfo?
    ): YouTubeNavigationDestination {
        var current = source
        var candidate = YouTubeNavigationDestination.UNKNOWN
        var depth = 0
        while (current != null && depth < YOUTUBE_SOURCE_PATH_DEPTH) {
            if (candidate == YouTubeNavigationDestination.UNKNOWN) {
                candidate = YouTubeSurfacePolicy.navigationDestination(
                    current.contentDescription?.toString()
                )
            }
            val id = compactId(current.viewIdResourceName)
            if (id == "bottom_bar_container" || id == "pivot_bar") return candidate
            current = current.parent
            depth++
        }
        return YouTubeNavigationDestination.UNKNOWN
    }

    private fun isYouTubeExplicitlyAllowedNavigationSource(
        source: AccessibilityNodeInfo?
    ): Boolean {
        var current = source
        var depth = 0
        while (current != null && depth++ < 8) {
            val id = compactId(current.viewIdResourceName)
            val label = current.contentDescription?.toString() ?: current.text?.toString()
            if (YouTubeUiIdentity.isExplicitlyAllowedLaunch(id, label)) return true
            current = current.parent
        }
        return false
    }

    private fun youtubeAllowedLaunchScreen(source: AccessibilityNodeInfo?): YouTubeCurtainScreen {
        var current = source
        var depth = 0
        while (current != null && depth++ < 8) {
            val label = (current.contentDescription?.toString() ?: current.text?.toString())
                .orEmpty().lowercase(Locale.ROOT)
            if (label.startsWith("notification") || label.startsWith("bildirim")) {
                return YouTubeCurtainScreen.NOTIFICATIONS
            }
            current = current.parent
        }
        return YouTubeCurtainScreen.SEARCH
    }

    private fun isYouTubeShortsLaunchSource(source: AccessibilityNodeInfo?): Boolean {
        var current = source
        var depth = 0
        while (current != null && depth < 8) {
            val id = compactId(current.viewIdResourceName)
            val text = current.text?.toString()?.trim().orEmpty()
            val description = current.contentDescription?.toString()?.trim().orEmpty()
            if (id.contains("short") || id.contains("reel") ||
                text.equals("Shorts", ignoreCase = true) ||
                description.equals("Shorts", ignoreCase = true) ||
                description.contains("play Short", ignoreCase = true) ||
                description.contains("Short oynat", ignoreCase = true) ||
                description.contains("Short'u oynat", ignoreCase = true)
            ) {
                return true
            }
            current = current.parent
            depth++
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

    private fun sourcePathIds(source: AccessibilityNodeInfo?, maxDepth: Int): List<String> {
        val ids = ArrayList<String>(maxDepth)
        var current = source
        while (current != null && ids.size < maxDepth) {
            ids += compactId(current.viewIdResourceName)
            current = current.parent
        }
        return ids
    }

    private fun sourcePathContains(
        source: AccessibilityNodeInfo?,
        vararg fragments: String,
        maxDepth: Int = 8
    ): Boolean {
        var current = source
        var depth = 0
        while (current != null && depth < maxDepth) {
            val id = compactId(current.viewIdResourceName)
            if (fragments.any(id::contains)) return true
            current = current.parent
            depth++
        }
        return false
    }

    private fun isInstagramNotificationContentIntent(source: AccessibilityNodeInfo?): Boolean =
        sourcePathContains(
            source,
            "activity_feed_newsfeed_story_row",
            "activity_feed_story_row",
            "notification_row"
        ) && !sourcePathContains(source, "action_bar_button_back", "navigation_back")

    private fun isMainTabClick(source: AccessibilityNodeInfo?): Boolean {
        var current = source
        repeat(6) {
            val node = current ?: return false
            val id = compactId(node.viewIdResourceName)
            if (id in setOf(
                    "feed_tab", "clips_tab", "direct_tab", "search_tab", "profile_tab"
                )) return true
            current = node.parent
        }
        return false
    }

    private fun isReelsTabClick(source: AccessibilityNodeInfo?): Boolean {
        var current = source
        repeat(6) {
            val node = current ?: return false
            if (compactId(node.viewIdResourceName) == "clips_tab") return true
            current = node.parent
        }
        return false
    }

    private fun isBackNavigationIntent(source: AccessibilityNodeInfo?): Boolean {
        var current = source
        repeat(6) {
            val node = current ?: return false
            val id = compactId(node.viewIdResourceName)
            val label = listOf(node.text, node.contentDescription)
                .joinToString(" ") { it?.toString().orEmpty() }
                .trim().lowercase(Locale.US)
            if (id == "action_bar_button_back" || id.contains("action_bar_back") ||
                id == "back_button" || id.contains("navigation_back") ||
                label == "back" || label == "geri" ||
                label == "navigate up" || label == "yukarı git" ||
                label.startsWith("back ") || label.startsWith("geri ")) return true
            current = node.parent
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

    private fun releaseProfileCurtainToAccountSearch(root: AccessibilityNodeInfo, window: Rect): Boolean {
        if (instagramOverlay.visibleProfileBounds() == null) return false
        val probeStarted = SystemClock.uptimeMillis()
        val trace = applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
        fun visible(id: String): Boolean = igVisible(root, id)
        val editor = visible("action_bar_search_edit_text")
        val row = editor && visible("row_search_user_container")
        if (!editor || !row) {
            if (trace) Log.d(TAG, "IG_PROFILE_RETURN_PROBE startedAt=$probeStarted " +
                "editor=$editor row=$row result=wait_search elapsedMs=${SystemClock.uptimeMillis() - probeStarted}")
            return false
        }
        // Outgoing search nodes can coexist with a sliding profile. Never treat
        // them as a completed departure while profile content is still mounted.
        val profileVisible = visible("profile_viewpager") || hasVisibleProfileHeaderMarkers(root)
        if (trace) Log.d(TAG, "IG_PROFILE_RETURN_PROBE startedAt=$probeStarted " +
            "editor=true row=true profile=$profileVisible elapsedMs=${SystemClock.uptimeMillis() - probeStarted}")
        if (!InstagramSurfacePolicy.canReleaseProfileToAccountSearch(
                profileCurtainVisible = true, searchEditorVisible = true, accountRowVisible = true,
                profileSurfaceVisible = profileVisible)) return false
        instagramProfileIngress.reset()
        instagramOverlay.updateProfileIngressGuard(null)
        instagramOverlay.updateStoryEntryGuards(emptyList())
        releaseAllowedProfileSurface()
        observeInstagramScreen(InstagramScreen.SEARCH_RESULTS, root.windowId)
        refreshInstagramAccountSearchGuard(root, window)
        Log.d(TAG, "IG_PROFILE_SEARCH_RETURN released=true")
        queueInstagramScan(48L)
        return true
    }

    private fun refreshSettledInstagramProfile(root: AccessibilityNodeInfo, window: Rect, now: Long): Boolean {
        if (profileGeometryWatchActive && profileWatchedFullCover &&
            instagramProfilePostsBlocked()) {
            // During a protective watch hold, curtain geometry belongs to the
            // watch; a settled re-mount here would flash the row-based cover.
            return false
        }
        if (!instagramProfilePostsBlocked() && hasVisibleProfileHeaderMarkers(root) &&
            profileGridBounds(root, window) != null) {
            releaseBalancedInstagramProfile(root, window)
            return true
        }
        if (lastInstagramScreen != InstagramScreen.PROFILE || instagramProfileIngress.pending ||
            instagramResumePending || now < profileAllowedDepartureUntil ||
            instagramOverlay.visibleProfileBounds() == null) return false
        if (!hasVisibleProfileHeaderMarkers(root)) return false
        val bounds = profileGridBounds(root, window) ?: return false
        val mounted = instagramOverlay.visibleProfileBounds()
        if (mounted != null && root.windowId == profileWindowId &&
            bounds.top < mounted.top - PROFILE_COVER_JITTER_PX) {
            // The grid climbed above the curtain edge (a header fling). Raise
            // the edge first: the viewer probes below cost 100-200 ms, during
            // which the first grid row was visible. Exact geometry follows.
            showInstagramCurtain(Rect(mounted.left, maxOf(bounds.top, window.top), mounted.right,
                mounted.bottom), InstagramScreen.PROFILE)
        }
        // A retained profile can sit behind a viewer or native sheet. These
        // exact foreground checks remain authoritative, not the previous state.
        if (hasFastStoryViewer(root) || hasFastMainReelsTabDestination(root) ||
            hasVisibleProfileNativeSheet(root) || hasVisibleAboutAccountSurface(root) ||
            hasFastReelsViewer(root, directLaunchPending = now < directReelLaunchPendingUntil) ||
            hasFastReelPreview(root) || hasFastSinglePostViewer(root) ||
            hasFastNotificationsSurface(root)) return false
        val titles = listOf("action_bar_title", "profile_action_bar_title").flatMap { id ->
            root.findAccessibilityNodeInfosByViewId("$INSTAGRAM_PACKAGE:id/$id")
                .filter { it.isVisibleToUser }
                .mapNotNull { it.text?.toString()?.trim()?.lowercase(Locale.US) }
        }.toSet()
        if (InstagramProfileSurfacePolicy.hasAllowedLayer(emptySet(), titles) ||
            InstagramProfileSurfacePolicy.isBlockedCollectionTitle(titles)) return false
        profileWindowId = root.windowId
        profileScrollFloor = calculateProfileScrollFloor(root, window)
        bounds.top = profileScrollCover.measure(bounds.top, profileScrollFloor, now)
        applyStrictProfileCurtain(root, window, bounds)
        observeInstagramScreen(InstagramScreen.PROFILE, root.windowId)
        refreshProtectedNavigationGuards(root, window)
        cancelInstagramExit()
        val fingerprint = bounds.flattenToString()
        if (fingerprint != lastProfileCurtainFingerprint) armProfileCurtainEnsure()
        lastProfileCurtainFingerprint = fingerprint
        if (profileScrollCover.isHolding) queueInstagramScan(48L)
        scheduleOverlayForegroundCheck()
        // Do not walk message/media subtrees or the general snapshot after a
        // fully measured profile. Leave the looper available for native Back.
        return true
    }

    private fun refreshEnteringInstagramProfile(root: AccessibilityNodeInfo, window: Rect): Boolean {
        // Only the explicitly armed search -> profile transition uses this path.
        // Normal sheets, DM, balanced viewers and settled profile scrolling retain
        // their existing classification and geometry owners.
        if (!instagramProfileIngress.pending) return false
        if (!instagramProfilePostsBlocked()) {
            val bounds = profileGridBounds(root, window)
            if (bounds != null && hasVisibleProfileHeaderMarkers(root)) {
                releaseBalancedInstagramProfile(root, window)
                return true
            }
        }
        if (instagramProfileIngress.awaitingCurtainDraw) {
            queueInstagramScan(48L)
            return true
        }
        val bounds = profileGridBounds(root, window)
        if (bounds == null) {
            if (instagramProfileIngress.waitForInitialGrid(SystemClock.uptimeMillis())) {
                // Strict: cover the profile shell as soon as header markers
                // publish, ahead of the grid pager appearing. Covering via the
                // conservative mount keeps thumbnails hidden while the pager
                // measures; the ensure loop refines to exact geometry.
                coverStrictProfileBeforeGrid(root, window)
                // The source search tree is being replaced. A full scan of it
                // can occupy the looper just as the destination grid appears.
                queueInstagramScan(32L)
                return true
            }
            return false
        }
        if (!hasVisibleProfileHeaderMarkers(root) || hasVisibleProfileNativeSheet(root) ||
            hasVisibleAboutAccountSurface(root) || isHighlightPickerForeground(root) ||
            SystemClock.uptimeMillis() < profileAllowedDepartureUntil) return false
        if (instagramProfileIngress.requestCurtainDraw()) {
            val token = instagramProfileIngress.generation
            instagramOverlay.updateAccountSearchGuard(null, ::onInstagramAccountSearchTap, {})
            applyStrictProfileCurtain(root, window, bounds)
            if (instagramOverlay.visibleProfileBounds() != null) {
                observeInstagramScreen(InstagramScreen.PROFILE, root.windowId)
                instagramOverlay.afterCurtainDraw {
                    if (!instagramProfileIngress.isCurrent(token)) return@afterCurtainDraw
                    instagramProfileIngress.confirmCurtainDraw(token)
                    Log.d(TAG, "IG_PROFILE_FIRST_CURTAIN drawn=true")
                    queueInstagramScan(0L)
                }
                mainHandler.postDelayed({
                    // A cancelled draw callback must not strand the scanner.
                    // This resumes verification only; it never uncovers input.
                    if (instagramProfileIngress.isCurrent(token) &&
                        instagramProfileIngress.awaitingCurtainDraw) {
                        instagramProfileIngress.confirmCurtainDraw(token)
                        queueInstagramScan(0L)
                    }
                }, 250L)
                // Yield for grid drawing. Profile header touches stay native;
                // foreground Story detection owns Strict viewer exits.
                queueInstagramScan(48L)
                return true
            }
            instagramProfileIngress.confirmCurtainDraw(token)
            return false
        }
        profileWindowId = root.windowId
        profileScrollFloor = calculateProfileScrollFloor(root, window)
        bounds.top = profileScrollCover.measure(bounds.top, profileScrollFloor, SystemClock.uptimeMillis())
        applyStrictProfileCurtain(root, window, bounds)
        observeInstagramScreen(InstagramScreen.PROFILE, root.windowId)
        // Complete the grid's actual-draw handoff without blocking native
        // profile header gestures or probing unrelated message/media trees.
        refreshInstagramStoryEntryGuards(root, window)
        queueInstagramScan(48L)
        scheduleOverlayForegroundCheck()
        return true
    }

    private fun releaseBalancedInstagramProfile(root: AccessibilityNodeInfo, window: Rect) {
        stopProfileCurtainEnsure()
        instagramProfileIngress.reset()
        instagramOverlay.updateProfileIngressGuard(null)
        instagramOverlay.updateStoryEntryGuards(emptyList())
        profileAllowedDepartureUntil = 0L
        releaseAllowedProfileSurface()
        observeInstagramScreen(InstagramScreen.PROFILE, root.windowId)
        refreshMainReelsTabGuard(root)
        refreshProtectedNavigationGuards(root, window)
        cancelInstagramExit()
        Log.d(TAG, "IG_BALANCED_PROFILE_ALLOWED")
    }

    private fun refreshProtectedNavigationGuards(
        root: AccessibilityNodeInfo,
        window: Rect
    ) {
        refreshInstagramAccountSearchGuard(root, window)
        refreshInstagramStoryEntryGuards(root, window)
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
        val notificationBounds = if (ProtectionPreferences.getMode(this, ProtectedApp.INSTAGRAM) == ProtectionMode.STRICT &&
            root.findAccessibilityNodeInfosByViewId("$INSTAGRAM_PACKAGE:id/main_feed_action_bar").isNotEmpty()) {
            root.findAccessibilityNodeInfosByViewId("$INSTAGRAM_PACKAGE:id/notification")
                .firstNotNullOfOrNull { node ->
                    Rect().also(node::getBoundsInScreen).takeIf {
                        node.isClickable && !it.isEmpty && it.intersect(window) && it.bottom < window.centerY()
                    }
                }
        } else null
        if (notificationBounds != null) lastInstagramNotificationLaunchBounds = Rect(notificationBounds)
        if (notificationBounds != null || !instagramNotificationReturn.isPending(SystemClock.uptimeMillis())) {
            instagramOverlay.updateNotificationLaunchGuard(notificationBounds, ::onInstagramNotificationLaunch)
        }
    }

    private var lastInstagramNotificationLaunchBounds: Rect? = null
    private val instagramProfileIngress = InstagramProfileIngress()

    private fun armInstagramNativeAccountEntry(event: AccessibilityEvent): Boolean {
        if (!ProtectionPreferences.isEnabled(this, ProtectedApp.INSTAGRAM) ||
            !instagramStoriesBlocked() || instagramProfileIngress.pending ||
            SystemClock.uptimeMillis() - event.eventTime !in 0L..1_500L) return false
        val source = event.source ?: return false
        if (!InstagramAccountEntryIdentity.isPossibleSearchAccountSource(
                compactId(source.viewIdResourceName))) return false
        val ids = linkedSetOf<String>()
        var node: AccessibilityNodeInfo? = source
        var depth = 0
        while (node != null && depth++ < 6) {
            val current = node
            val id = compactId(current.viewIdResourceName)
            ids.add(id)
            if (id == "row_search_user_follow_button") return false
            if (InstagramAccountEntryIdentity.isProfileEntry(ids)) break
            node = current.parent
        }
        if (!InstagramAccountEntryIdentity.isProfileEntry(ids)) return false
        val root = rootInActiveWindow ?: return false
        if (root.packageName?.toString() != INSTAGRAM_PACKAGE) return false
        if (!InstagramProfileInteractionPolicy.interceptsProfileTouches(
                ProtectionPreferences.getMode(this, ProtectedApp.INSTAGRAM))) {
            // Observe the native row click for fast grid drawing, without
            // intercepting the source row or the destination profile body.
            instagramProfileIngress.begin(SystemClock.uptimeMillis())
            instagramOverlay.updateProfileIngressGuard(null)
            updateInstagramStructuralNodeAccess(true)
            resetYouTubeProtection()
            trackPackageTransition(INSTAGRAM_PACKAGE)
            exitGate.userClick(event.eventTime)
            lastInstagramUserInputAt = event.eventTime
            Log.d(TAG, "IG_PROFILE_SEARCH_ENTRY nativeTouch=true")
            queueInstagramScan(0L)
            return true
        }
        val window = instagramWindowBounds(root)
        val body = Rect(window).apply {
            top += dp(56)
            bottom = stableMainNavigationTop(root, window, InstagramScreen.PROFILE)
        }
        if (!instagramOverlay.updateProfileIngressGuard(body)) return false
        // Draw the profile curtain the moment the account-row click is known,
        // before Instagram replaces the fragment. Otherwise the outgoing search
        // surface fades into unmeasured thumbnails that stay exposed until the
        // post-classification scan. A misclassified tap retreats to the search
        // surface through releaseProfileCurtainToAccountSearch within one scan.
        conservativeInstagramResumeBounds(root, window, InstagramScreen.PROFILE)?.let {
            applyStrictProfileCurtainResult(root, window, it)
        }
        val token = instagramProfileIngress.begin(SystemClock.uptimeMillis())
        updateInstagramStructuralNodeAccess(true)
        resetYouTubeProtection()
        trackPackageTransition(INSTAGRAM_PACKAGE)
        exitGate.userClick(event.eventTime)
        lastInstagramUserInputAt = event.eventTime
        Log.d(TAG, "IG_PROFILE_SEARCH_ENTRY nativeArmed=true eventAgeMs=${SystemClock.uptimeMillis() - event.eventTime}")
        // Let the new input window get its first layout before remote profile
        // queries can occupy the main thread. The fallback only requests a
        // scan; it never releases the ingress guard on a timer.
        mainHandler.removeCallbacks(instagramScan)
        scanSchedule.clear()
        instagramOverlay.afterProfileIngressDraw {
            if (instagramProfileIngress.isCurrent(token)) queueInstagramScan(0L)
        }
        mainHandler.postDelayed({
            if (instagramProfileIngress.isCurrent(token)) queueInstagramScan(0L)
        }, 250L)
        scheduleOverlayForegroundCheck()
        return true
    }

    private fun refreshInstagramAccountSearchGuard(root: AccessibilityNodeInfo, window: Rect) {
        if (!InstagramProfileInteractionPolicy.interceptsProfileTouches(
                ProtectionPreferences.getMode(this, ProtectedApp.INSTAGRAM))) {
            instagramOverlay.updateAccountSearchGuard(null, ::onInstagramAccountSearchTap, {})
            instagramOverlay.updateProfileIngressGuard(null)
            return
        }
        val rows = root.findAccessibilityNodeInfosByViewId("$INSTAGRAM_PACKAGE:id/row_search_user_container")
            .filter { it.isVisibleToUser && it.isClickable }
        val list = if (instagramStoriesBlocked() && rows.isNotEmpty()) {
            root.findAccessibilityNodeInfosByViewId("$INSTAGRAM_PACKAGE:id/recycler_view")
                // A single-result account list is not scrollable, but its
                // clickable row still needs the same pre-navigation guard.
                .firstOrNull { node ->
                    val viewport = Rect().also(node::getBoundsInScreen)
                    node.isVisibleToUser && !viewport.isEmpty && rows.any { row ->
                        Rect().also(row::getBoundsInScreen).let(viewport::contains)
                    }
                }
        } else null
        val bounds = list?.let { Rect().also(it::getBoundsInScreen) }
            ?.takeIf { !it.isEmpty && it.intersect(window) }
            ?.apply {
                // The list's native rectangle can extend underneath both the
                // editor and IME. Only account-row territory needs a proxy.
                top = maxOf(top, rows.minOf { Rect().also(it::getBoundsInScreen).top })
                bottom = minOf(bottom, rows.maxOf { Rect().also(it::getBoundsInScreen).bottom },
                    stableMainNavigationTop(root, window, InstagramScreen.SEARCH_RESULTS))
            }
        instagramOverlay.updateAccountSearchGuard(bounds, ::onInstagramAccountSearchTap) { forward ->
            val current = rootInActiveWindow
            if (current?.packageName?.toString() == INSTAGRAM_PACKAGE &&
                current.findAccessibilityNodeInfosByViewId("$INSTAGRAM_PACKAGE:id/row_search_user_container")
                    .any { it.isVisibleToUser }) {
                current.findAccessibilityNodeInfosByViewId("$INSTAGRAM_PACKAGE:id/recycler_view")
                    .firstOrNull { it.isVisibleToUser && it.isScrollable }
                    ?.performAction(if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                        else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
                queueInstagramScan(0L)
            }
        }
        if (!instagramStoriesBlocked()) {
            instagramProfileIngress.reset()
            instagramOverlay.updateProfileIngressGuard(null)
        }
    }

    private fun onInstagramAccountSearchTap(x: Float, y: Float) {
        if (!InstagramProfileInteractionPolicy.interceptsProfileTouches(
                ProtectionPreferences.getMode(this, ProtectedApp.INSTAGRAM))) return
        if (!ProtectionPreferences.isEnabled(this, ProtectedApp.INSTAGRAM) ||
            !instagramStoriesBlocked() || instagramProfileIngress.pending) return
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != INSTAGRAM_PACKAGE) return
        fun contains(node: AccessibilityNodeInfo) = node.isVisibleToUser &&
            Rect().also(node::getBoundsInScreen).contains(x.toInt(), y.toInt())
        val row = root.findAccessibilityNodeInfosByViewId("$INSTAGRAM_PACKAGE:id/row_search_user_container")
            .firstOrNull { it.isClickable && contains(it) }
        if (row == null) {
            // Mixed search results keep their verified native click targets.
            val list = root.findAccessibilityNodeInfosByViewId("$INSTAGRAM_PACKAGE:id/recycler_view")
                .firstOrNull { contains(it) } ?: return
            var target: AccessibilityNodeInfo? = null
            val nodes = java.util.ArrayDeque<AccessibilityNodeInfo>()
            nodes.add(list)
            var visited = 0
            while (nodes.isNotEmpty() && visited++ < 200) {
                val node = nodes.removeFirst()
                if (!contains(node)) continue
                if (node.isClickable) target = node
                for (index in 0 until node.childCount) node.getChild(index)?.let(nodes::add)
            }
            target?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return
        }
        val follow = row.findAccessibilityNodeInfosByViewId("$INSTAGRAM_PACKAGE:id/row_search_user_follow_button")
            .firstOrNull { it.isClickable && contains(it) }
        if (follow != null) {
            follow.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return
        }
        val window = instagramWindowBounds(root)
        val body = Rect(window).apply {
            top += dp(56)
            bottom = stableMainNavigationTop(root, window, InstagramScreen.PROFILE)
        }
        val token = instagramProfileIngress.begin(SystemClock.uptimeMillis())
        if (!instagramOverlay.updateProfileIngressGuard(body)) {
            instagramProfileIngress.reset()
            return
        }
        instagramOverlay.afterProfileIngressDraw {
            if (!instagramProfileIngress.isCurrent(token)) return@afterProfileIngressDraw
            val current = rootInActiveWindow
            if (current?.packageName?.toString() != INSTAGRAM_PACKAGE ||
                current.windowId != root.windowId || !row.refresh() || !row.isVisibleToUser ||
                !row.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                instagramProfileIngress.reset()
                instagramOverlay.updateProfileIngressGuard(null)
            } else {
                Log.d(TAG, "IG_PROFILE_SEARCH_ENTRY armed=true")
                queueInstagramScan(0L)
            }
        }
    }

    private fun refreshInstagramStoryEntryGuards(root: AccessibilityNodeInfo, window: Rect) {
        if (!InstagramProfileInteractionPolicy.interceptsProfileTouches(
                ProtectionPreferences.getMode(this, ProtectedApp.INSTAGRAM))) {
            instagramOverlay.updateStoryEntryGuards(emptyList())
            instagramOverlay.updateProfileIngressGuard(null)
            if (instagramProfileIngress.pending) {
                val grid = profileGridBounds(root, window)
                if (grid != null && hasVisibleProfileHeaderMarkers(root)) {
                    val token = instagramProfileIngress.generation
                    instagramOverlay.afterCurtainDraw {
                        if (instagramProfileIngress.isCurrent(token)) instagramProfileIngress.reset()
                    }
                }
            }
            return
        }
        val targets = mutableListOf<Rect>()
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0 &&
            debugProfileScrollActionWindowId != root.windowId && hasVisibleProfileHeaderMarkers(root)) {
            debugProfileScrollActionWindowId = root.windowId
            for (id in listOf("coordinator_root_layout", "tab_appbar", "profile_header_container",
                    "profile_header_fixed_list", "profile_viewpager",
                    "swipeable_nav_view_pager_inner_recycler_view", "highlights_reel_tray_recycler_view")) {
                root.findAccessibilityNodeInfosByViewId("$INSTAGRAM_PACKAGE:id/$id")
                    .filter { it.isVisibleToUser }.forEach { node ->
                        Log.d(TAG, "IG_PROFILE_SCROLL_CAPABILITY id=$id scrollable=${node.isScrollable} " +
                            "actions=${node.actionList.map { it.id }}")
                    }
            }
        }
        if (instagramStoriesBlocked()) {
            val headers = root.findAccessibilityNodeInfosByViewId("$INSTAGRAM_PACKAGE:id/profile_header_container")
            for (header in headers) {
                if (!header.isVisibleToUser) continue
                // An ordinary profile photo must remain usable. Only a real
                // Story ring in the verified profile header identifies entry.
                val headerBounds = Rect().also(header::getBoundsInScreen)
                // Query the window root: decorative nodes may not be exposed
                // as searchable descendants of the semantic profile header.
                for (ring in root.findAccessibilityNodeInfosByViewId("$INSTAGRAM_PACKAGE:id/reel_ring")) {
                    if (!ring.isVisibleToUser) continue
                    val bounds = Rect().also(ring::getBoundsInScreen)
                    if (!headerBounds.contains(bounds)) continue
                    if (InstagramStoryEntryPolicy.isProfileStoryRing(
                            hasProfileHeader = true, hasStoryRing = true,
                            bounds = bounds.toInstagramGeometrySample(), window = window.toInstagramGeometrySample()) &&
                        bounds.intersect(window)) targets.add(bounds)
                }
                for (tray in root.findAccessibilityNodeInfosByViewId("$INSTAGRAM_PACKAGE:id/highlights_tray")) {
                    if (!tray.isVisibleToUser) continue
                    val bounds = Rect().also(tray::getBoundsInScreen)
                    if (headerBounds.contains(bounds) && root.findAccessibilityNodeInfosByViewId(
                            "$INSTAGRAM_PACKAGE:id/highlights_reel_tray_recycler_view").any {
                            bounds.contains(Rect().also(it::getBoundsInScreen))
                        } &&
                        !bounds.isEmpty && bounds.intersect(window)) targets.add(bounds)
                }
            }
        }
        instagramOverlay.updateStoryEntryGuards(targets.distinct())
        if (instagramProfileIngress.pending) {
            val avatar = root.findAccessibilityNodeInfosByViewId("$INSTAGRAM_PACKAGE:id/row_profile_header_imageview_frame_layout")
                .firstOrNull { it.isVisibleToUser }
            val grid = profileGridBounds(root, window)
            val fingerprint = if (avatar != null && grid != null && hasVisibleProfileHeaderMarkers(root)) {
                "${root.windowId}:${Rect().also(avatar::getBoundsInScreen)}:$grid:$targets"
            } else null
            if (instagramProfileIngress.ready(fingerprint)) {
                val token = instagramProfileIngress.generation
                applyStrictProfileCurtain(root, window, grid!!)
                instagramOverlay.afterCurtainDraw {
                    instagramOverlay.afterStoryEntryGuardsDraw(targets.distinct()) {
                        if (instagramProfileIngress.isCurrent(token)) {
                            instagramProfileIngress.reset()
                            instagramOverlay.updateProfileIngressGuard(null)
                            Log.d(TAG, "IG_PROFILE_SEARCH_ENTRY handoff=true")
                        }
                    }
                }
            }
            queueInstagramScan(48L)
        }
    }

    private var instagramStructuralNodesRequested: Boolean? = null

    private fun updateInstagramStructuralNodeAccess(instagramForeground: Boolean) {
        // getServiceInfo() is a synchronous binder call into the accessibility
        // manager, measured at 25-60 ms per event under load. It ran for every
        // event of every protected app; only a real change needs it.
        if (instagramStructuralNodesRequested == instagramForeground) return
        instagramStructuralNodesRequested = instagramForeground
        val info = serviceInfo ?: return
        val flag = android.accessibilityservice.AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        val wanted = if (instagramForeground) info.flags or flag else info.flags and flag.inv()
        if (wanted == info.flags) return
        info.flags = wanted
        serviceInfo = info
        Log.d(TAG, "IG_STRUCTURAL_NODES enabled=$instagramForeground")
    }

    private fun clearInstagramNotificationIngress() {
        instagramNotificationIngress.reset()
        notificationIngressToken = 0L
        instagramOverlay.updateNotificationIngressGuard(null)
    }

    private fun onInstagramNotificationLaunch() {
        if (!ProtectionPreferences.isEnabled(this, ProtectedApp.INSTAGRAM) ||
            ProtectionPreferences.getMode(this, ProtectedApp.INSTAGRAM) != ProtectionMode.STRICT) return
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != INSTAGRAM_PACKAGE) return
        if (root.findAccessibilityNodeInfosByViewId("$INSTAGRAM_PACKAGE:id/notification")
            .none { it.isClickable }) return
        val header = root.findAccessibilityNodeInfosByViewId("$INSTAGRAM_PACKAGE:id/main_feed_action_bar")
            .firstOrNull() ?: return
        val window = instagramWindowBounds(root)
        val headerBounds = Rect().also(header::getBoundsInScreen)
        if (headerBounds.bottom <= window.top || headerBounds.bottom >= window.bottom) return
        // A rapid re-entry can beat the Home scan. The verified native Home
        // button/header is sufficient to finish the preceding return here.
        if (instagramNotificationReturn.isPending(SystemClock.uptimeMillis())) {
            cancelInstagramExit()
            observeInstagramScreen(InstagramScreen.HOME_FEED, root.windowId)
        }
        // A previous Notifications visit may have been interrupted while its
        // rows were loading. Never reuse that generation: a stale callback
        // must not suppress the new visit's ingress guard.
        clearInstagramNotificationIngress()
        val token = instagramNotificationIngress.startNew()
        val windowId = root.windowId
        val covered = instagramOverlay.updateNotificationIngressGuard(
            Rect(window.left, headerBounds.bottom, window.right, window.bottom), ::onInstagramNotificationTap)
        if (!covered) {
            clearInstagramNotificationIngress()
            Log.w(TAG, "IG_NOTIFICATION_LAUNCH rejected=guard_unavailable")
            return
        }
        mainHandler.postDelayed({
            // A later generation must never be cleared by an old timeout.
            if (notificationIngressToken == token) {
                val current = rootInActiveWindow
                if (current?.packageName?.toString() != INSTAGRAM_PACKAGE ||
                    (!hasFastNotificationsSurface(current) &&
                        current.findAccessibilityNodeInfosByViewId("$INSTAGRAM_PACKAGE:id/main_feed_action_bar")
                            .any { it.isVisibleToUser })) clearInstagramNotificationIngress()
                // A slow/loading notification list stays guarded; its header
                // and Back remain outside the input window.
            }
        }, 2000L)
        notificationIngressToken = token
        instagramOverlay.afterNotificationIngressDraw {
            if (!instagramNotificationIngress.accepts(token, SystemClock.uptimeMillis())) return@afterNotificationIngressDraw
            val current = rootInActiveWindow
            if (!instagramNotificationIngress.markDispatched(token)) return@afterNotificationIngressDraw
            val accepted = current?.takeIf { it.packageName?.toString() == INSTAGRAM_PACKAGE && it.windowId == windowId }
                ?.findAccessibilityNodeInfosByViewId("$INSTAGRAM_PACKAGE:id/notification")
                ?.firstOrNull { it.isClickable }?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
            Log.d(TAG, "IG_NOTIFICATION_LAUNCH precovered=true accepted=$accepted")
            if (!accepted) clearInstagramNotificationIngress()
            queueInstagramScan(0L)
        }
    }

    private var notificationIngressToken = 0L

    private fun notificationRowChildren(row: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        val pending = ArrayDeque<AccessibilityNodeInfo>()
        for (index in 0 until minOf(row.childCount, 32)) row.getChild(index)?.let(pending::addLast)
        while (pending.isNotEmpty() && result.size < 96) {
            val node = pending.removeFirst()
            result.add(node)
            for (index in 0 until minOf(node.childCount, 16)) node.getChild(index)?.let(pending::addLast)
        }
        return result
    }

    private fun onInstagramNotificationTap(x: Float, y: Float) {
        if (!ProtectionPreferences.isEnabled(this, ProtectedApp.INSTAGRAM) ||
            ProtectionPreferences.getMode(this, ProtectedApp.INSTAGRAM) != ProtectionMode.STRICT) return
        // No row action is safe until the transition has handed input over to
        // the drawn row guards. In particular, never replay a loading tap on
        // a partially published row (which may later acquire a media child).
        if (instagramNotificationIngress.active(SystemClock.uptimeMillis())) {
            Log.d(TAG, "IG_NOTIFICATION_INPUT blocked=loading")
            return
        }
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != INSTAGRAM_PACKAGE || !hasFastNotificationsSurface(root) ||
            instagramNotificationReturn.isPending(SystemClock.uptimeMillis()) ||
            instagramNotificationMotion.isHolding) return
        val nodes = instagramNotificationListNodes(root)
        val row = nodes.firstOrNull {
            compactId(it.viewIdResourceName) == "activity_feed_newsfeed_story_row" &&
                Rect().also(it::getBoundsInScreen).contains(x.toInt(), y.toInt())
        } ?: return // Loading/unknown geometry is not permission to enter media.
        val children = notificationRowChildren(row)
        val rowBounds = Rect().also(row::getBoundsInScreen)
        val avatar = children.firstOrNull { compactId(it.viewIdResourceName) == "igds_profile_picture" }
            ?.let { Rect().also(it::getBoundsInScreen) }
        val profile = avatar?.let { picture -> children.firstOrNull { child ->
            val bounds = Rect().also(child::getBoundsInScreen)
            child.isClickable && !bounds.isEmpty && rowBounds.contains(bounds) &&
                bounds.right <= rowBounds.left + rowBounds.width() / 3 &&
                picture.contains(bounds.centerX(), bounds.centerY()) && bounds.contains(x.toInt(), y.toInt())
        } }
        val summaries = children.filter {
            val bounds = Rect().also(it::getBoundsInScreen)
            it.className?.toString() == "android.widget.TextView" && bounds.width() >= rowBounds.width() / 3
        }
            .mapNotNull { it.text?.toString()?.takeIf(String::isNotBlank) }
        val decision = InstagramNotificationEntryPolicy.routeTap(summaries, profile != null,
            hasMediaThumbnail = notificationRowMediaThumbnail(children, rowBounds) != null)
        when (decision) {
            InstagramNotificationTap.BLOCK -> {
                Log.d(TAG, "IG_NOTIFICATION_INPUT blocked=true")
                onInstagramOverlayTouched()
            }
            InstagramNotificationTap.WAIT -> {
                Log.d(TAG, "IG_NOTIFICATION_INPUT waiting=identity")
                queueInstagramScan(0L)
            }
            InstagramNotificationTap.PROFILE, InstagramNotificationTap.ALLOW -> {
                val target = if (decision == InstagramNotificationTap.PROFILE) profile else row
                val accepted = target?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
                Log.d(TAG, "IG_NOTIFICATION_INPUT allowed=$decision accepted=$accepted")
                queueInstagramScan(0L)
            }
        }
    }

    private fun onInstagramProtectedNavigation(tab: InstagramTab) {
        if (tab != InstagramTab.HOME && tab != InstagramTab.SEARCH) return
        if (!ProtectionPreferences.isEnabled(this, ProtectedApp.INSTAGRAM)) return
        val now = SystemClock.uptimeMillis()
        if (isRecentProxiedNavigation(tab, now)) return
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != INSTAGRAM_PACKAGE) return

        val armStarted = SystemClock.uptimeMillis()
        val covered = prearmProtectedNavigationCurtain(root, tab)
        val curtainReadyAt = SystemClock.uptimeMillis()
        val targetId = if (tab == InstagramTab.HOME) "feed_tab" else "search_tab"
        if (covered) {
            // Window attachment/layout is asynchronous. Preserve the mounted
            // curtain and wait for its destination rectangle to draw before
            // Instagram starts moving thumbnails underneath it.
            val sourceTab = selectedInstagramMainTab(root)
            Log.d(TAG, "IG_NAVIGATION_ARM tab=$tab curtainMs=${curtainReadyAt - armStarted} " +
                "sourceMs=${SystemClock.uptimeMillis() - curtainReadyAt} sourceTab=$sourceTab")
            val request = instagramProtectedNavigation.begin(
                tab = tab,
                sourceWindowId = root.windowId,
                sourceTab = sourceTab,
                nowMs = now)
            // A scan requested by the source surface may already be due ahead of
            // Choreographer. Let the destination curtain draw first; the retained
            // click event is consumed by the fresh scan queued after dispatch.
            mainHandler.removeCallbacks(instagramScan)
            scanSchedule.clear()
            instagramOverlay.afterCurtainDraw {
                dispatchInstagramProtectedNavigation(request, targetId, "draw")
            }
            // A WindowManager frame should normally win. This bounded fallback
            // preserves the tap if an OEM drops the pre-draw callback; it shares
            // the same generation token and therefore cannot dispatch twice.
            mainHandler.postDelayed({
                dispatchInstagramProtectedNavigation(request, targetId, "fallback")
            }, INSTAGRAM_PROTECTED_NAVIGATION_FALLBACK_MS)
        } else {
            // Preserve native navigation if WindowManager rejects the cover.
            val accepted = performInstagramTabAction(root, targetId)
            Log.d(TAG, "IG_NAVIGATION_PROXY tab=$tab precovered=false accepted=$accepted")
            queueInstagramScan(0L)
        }
    }

    private fun dispatchInstagramProtectedNavigation(
        request: InstagramProtectedNavigationRequest,
        targetId: String,
        source: String
    ) {
        if (!ProtectionPreferences.isEnabled(this, ProtectedApp.INSTAGRAM)) {
            instagramProtectedNavigation.clear(request)
            return
        }
        val current = rootInActiveWindow ?: return
        if (current.packageName?.toString() != INSTAGRAM_PACKAGE ||
            current.windowId != request.sourceWindowId) {
            instagramProtectedNavigation.clear(request)
            return
        }
        val now = SystemClock.uptimeMillis()
        val selectedTab = selectedInstagramMainTab(current)
        if (!instagramProtectedNavigation.acceptsDispatch(
                request, now, current.windowId, selectedTab)) return
        val accepted = selectedTab == request.tab || performInstagramTabAction(current, targetId)
        if (accepted) {
            instagramProtectedNavigation.markDispatched(request)
            if (request.tab == InstagramTab.HOME) {
                // Measuring Home before its native tab action is accepted walks
                // the still-selected DM tree and can occupy the main looper long
                // enough to swallow the user's first tap. The pre-armed rectangle
                // is already conservative; refine it only after navigation starts.
                mainHandler.removeCallbacks(homeScrollSettled)
                mainHandler.postDelayed(homeScrollSettled, 48L)
            } else if (request.tab == InstagramTab.SEARCH &&
                exploreTransitionCoverWindowId == current.windowId &&
                exploreTransitionCoverUntil > 0L) {
                exploreTransitionCoverUntil = now + 300L
                mainHandler.removeCallbacks(exploreChromeRestore)
                mainHandler.postDelayed(exploreChromeRestore, 300L)
            }
        } else {
            instagramProtectedNavigation.clear(request)
        }
        Log.d(TAG, "IG_NAVIGATION_PROXY tab=${request.tab} source=$source " +
            "precovered=true accepted=$accepted")
        queueInstagramScan(0L)
    }

    private fun refreshInstagramNotificationBackGuard(root: AccessibilityNodeInfo, window: Rect) {
        val bounds = if (instagramNotificationReturn.returnsHome) {
            root.findAccessibilityNodeInfosByViewId("$INSTAGRAM_PACKAGE:id/action_bar_button_back")
                .firstNotNullOfOrNull { node ->
                    Rect().also(node::getBoundsInScreen).takeIf {
                        node.isVisibleToUser && node.isClickable && !it.isEmpty &&
                            it.top >= window.top && it.bottom < window.centerY() &&
                            it.left >= window.left && it.right <= window.left + window.width() / 3
                    }
                }
        } else null
        instagramOverlay.updateNotificationBackGuard(bounds, ::onInstagramNotificationBack)
        if (bounds != null) scheduleOverlayForegroundCheck()
    }

    private fun instagramNotificationListNodes(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val host = root.findAccessibilityNodeInfosByViewId("$INSTAGRAM_PACKAGE:id/newsfeed_you")
            .firstOrNull() ?: return emptyList()
        val pending = ArrayDeque<AccessibilityNodeInfo>()
        pending.add(host)
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        while (pending.isNotEmpty() && nodes.size < 160) {
            val node = pending.removeFirst()
            // Off-screen subtrees hold no row the guards could cover.
            if (node !== host && !node.isVisibleToUser) continue
            nodes.add(node)
            for (index in 0 until minOf(node.childCount, 32)) node.getChild(index)?.let(pending::addLast)
        }
        return nodes
    }

    private fun refreshInstagramNotificationEntryGuards() {
        // The launch bridge is retired only after populated destination rows
        // have stable geometry AND their replacement input windows have drawn.
        val now = SystemClock.uptimeMillis()
        if (now < instagramNotificationGestureReplayUntil) {
            // Removal emits window events before the forwarded DOWN is sent.
            // Remounting from those events makes the overlay catch its own
            // synthetic swipe instead of delivering it to the native list.
            queueInstagramScan(instagramNotificationGestureReplayUntil - now)
            return
        }
        val root = rootInActiveWindow
        val mode = ProtectionPreferences.getMode(this, ProtectedApp.INSTAGRAM)
        if (mode != ProtectionMode.STRICT) {
            clearInstagramNotificationIngress()
            instagramOverlay.updateEntryGuards(emptyList(), isEnglish())
            return
        }
        if (root?.packageName?.toString() != INSTAGRAM_PACKAGE || !hasFastNotificationsSurface(root)) return
        val rows = instagramNotificationListNodes(root).filter {
            compactId(it.viewIdResourceName) == "activity_feed_newsfeed_story_row" && it.isVisibleToUser
        }
        val targets = instagramNotificationMediaGuardBounds(rows)
        instagramNotificationMotion.reset()
        instagramNotificationGestureReplayUntil = 0L
        instagramOverlay.updateEntryGuards(targets, isEnglish())
        instagramOverlay.hideTransientTouchGuard()
        val token = notificationIngressToken
        if (instagramNotificationIngress.accepts(token, SystemClock.uptimeMillis())) {
            val populated = rows.isNotEmpty() && rows.all { row ->
                notificationRowChildren(row).any { !it.text.isNullOrBlank() }
            }
            val identity = "${root.windowId}:" + rows.joinToString { Rect().also(it::getBoundsInScreen).toString() } +
                ":" + targets.joinToString()
            if (instagramNotificationIngress.prepareHandoff(token, identity, populated)) {
                instagramOverlay.afterEntryGuardsDraw(targets) {
                    if (instagramNotificationIngress.completeHandoff(token, identity)) {
                        notificationIngressToken = 0L
                        instagramOverlay.updateNotificationIngressGuard(null)
                        Log.d(TAG, "IG_NOTIFICATION_HANDOFF drawn=true rows=${rows.size} targets=${targets.size}")
                    }
                }
            }
            queueInstagramScan(32L)
        }
    }

    /** The clickable media thumbnail at the end of a notification row, if any. */
    private fun notificationRowMediaThumbnail(children: List<AccessibilityNodeInfo>, rowBounds: Rect): Rect? =
        children.asSequence()
            .map { child -> child to Rect().also(child::getBoundsInScreen) }
            .filter { (child, bounds) ->
                val id = compactId(child.viewIdResourceName)
                val label = child.text?.toString().orEmpty()
                child.isVisibleToUser && child.isClickable && !bounds.isEmpty &&
                    id !in setOf("igds_button", "ig_text") &&
                    label !in setOf("Message", "Follow", "Following", "Mesaj", "Takip Et", "Takiptesin") &&
                    rowBounds.contains(bounds.centerX(), bounds.centerY()) &&
                    bounds.right >= rowBounds.left + rowBounds.width() * 2 / 3
            }
            .maxByOrNull { (_, bounds) -> bounds.width().toLong() * bounds.height() }
            ?.second

    private fun instagramNotificationMediaGuardBounds(rows: List<AccessibilityNodeInfo>): List<Rect> {
        return rows.asSequence()
            .mapNotNull { row ->
                val rowBounds = Rect().also(row::getBoundsInScreen)
                if (rowBounds.isEmpty || !row.isVisibleToUser) return@mapNotNull null
                val children = notificationRowChildren(row)
                val hasBlockedMediaSummary = children.asSequence()
                    .filter { it.className?.toString() == "android.widget.TextView" }
                    .mapNotNull { it.text?.toString() }
                    .any(InstagramNotificationEntryPolicy::isBlockedMediaSummary)
                val candidate = notificationRowMediaThumbnail(children, rowBounds)
                // Instagram makes the whole notification row clickable, even
                // when the visible media thumbnail is the only affordance.
                // Guarding only the thumbnail therefore lets a tap on the
                // notification text launch the viewer. Once a row is known to
                // contain media (or a blocked comment/reply summary), consume
                // the row's tap region. The overlay replays vertical drags,
                // so the list itself remains scrollable.
                if (candidate == null && !hasBlockedMediaSummary) return@mapNotNull null
                rowBounds.takeIf { !it.isEmpty && it.width() >= dp(48) }
            }
            .distinctBy { "${it.left}:${it.top}:${it.right}:${it.bottom}" }
            .toList()
    }

    private fun onInstagramNotificationBack() {
        if (!ProtectionPreferences.isEnabled(this, ProtectedApp.INSTAGRAM)) return
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != INSTAGRAM_PACKAGE ||
            instagramStateMachine.currentScreen != InstagramScreen.NOTIFICATIONS ||
            !hasFastNotificationsSurface(root)) return
        val token = instagramNotificationReturn.begin(SystemClock.uptimeMillis()) ?: return
        // Restore the Home header proxy before the native Back animation. A
        // rapid re-entry must not wait for the next Home accessibility scan.
        instagramOverlay.updateNotificationLaunchGuard(lastInstagramNotificationLaunchBounds,
            ::onInstagramNotificationLaunch)
        val windowId = root.windowId
        if (!prearmProtectedNavigationCurtain(root, InstagramTab.HOME)) {
            instagramNotificationReturn.reset()
            instagramOverlay.updateNotificationBackGuard(null)
            // Preserve navigation if WindowManager cannot attach the curtain.
            val back = root.findAccessibilityNodeInfosByViewId("$INSTAGRAM_PACKAGE:id/action_bar_button_back")
                .firstOrNull { it.isVisibleToUser && it.isClickable }
            if (back?.performAction(AccessibilityNodeInfo.ACTION_CLICK) != true) {
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
            queueInstagramScan(0L)
            return
        }
        instagramOverlay.afterCurtainDraw {
            if (!instagramNotificationReturn.accepts(token, SystemClock.uptimeMillis()) ||
                !ProtectionPreferences.isEnabled(this, ProtectedApp.INSTAGRAM)) return@afterCurtainDraw
            val current = rootInActiveWindow ?: return@afterCurtainDraw
            if (current.packageName?.toString() != INSTAGRAM_PACKAGE || current.windowId != windowId ||
                instagramStateMachine.currentScreen != InstagramScreen.NOTIFICATIONS) return@afterCurtainDraw
            // The curtain may occlude the notification list and change its
            // accessibility visibility. Verify the uncovered native Back control
            // in the same confirmed window, not visibility of the covered list.
            val back = current.findAccessibilityNodeInfosByViewId("$INSTAGRAM_PACKAGE:id/action_bar_button_back")
                .firstOrNull { it.isVisibleToUser && it.isClickable }
                ?: return@afterCurtainDraw
            val accepted = back.performAction(AccessibilityNodeInfo.ACTION_CLICK) ||
                performGlobalAction(GLOBAL_ACTION_BACK)
            Log.d(TAG, "IG_NOTIFICATION_RETURN precovered=true accepted=$accepted")
            queueInstagramScan(0L)
        }
        // Also recover if the native Back action fails or the draw callback is cancelled.
        queueInstagramScan(32L)
    }

    private fun prearmProtectedNavigationCurtain(
        root: AccessibilityNodeInfo,
        tab: InstagramTab
    ): Boolean {
        val window = instagramWindowBounds(root)
        mainHandler.removeCallbacks(exploreChromeRestore)
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
        if (tab == InstagramTab.SEARCH && lastInstagramScreen == InstagramScreen.HOME_FEED) {
            // API 34+ overlays move instantly: precover the destination grid,
            // not its usable search chrome. Keep the bounded hold so a stale
            // Home measurement cannot reopen the top row before navigation commits.
            // Older Android retains the conservative animation-region cover.
            exploreTransitionCoverTop = InstagramNavigationGeometry.transitionTop(
                maxOf(window.top, statusBarInset()), bounds.top,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            exploreTransitionCoverWindowId = root.windowId
            exploreTransitionCoverUntil = SystemClock.uptimeMillis() + 300L
        } else {
            exploreTransitionCoverUntil = 0L
        }
        clearInstagramResumeProtection()
        stopProfileCurtainEnsure()
        currentInstagramWindowId = root.windowId
        // show() updates an existing window in place. Removing it here opens
        // a compositor frame between protected Home and Explore destinations.
        val screen = if (tab == InstagramTab.HOME) {
            InstagramScreen.HOME_FEED
        } else {
            InstagramScreen.EXPLORE
        }
        val shown = showInstagramCurtain(bounds, screen)
        if (shown && tab == InstagramTab.HOME) {
            // Tab navigation is not a feed fling. Keep the conservative cover
            // until native Home is verified, then immediately use its tray bounds;
            // do not impose scroll quiet-time/stable-sample holds on Stories.
            scrollCoverTop = null
            scrollCoverUntil = 0L
            settledHomeTop = null
            stableHomeSamples = 0
            mainHandler.removeCallbacks(homeScrollSettled)
        }
        return shown
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

    private fun selectedInstagramMainTab(root: AccessibilityNodeInfo): InstagramTab? = listOf(
        InstagramTab.HOME to "feed_tab",
        InstagramTab.REELS to "clips_tab",
        InstagramTab.DIRECT to "direct_tab",
        InstagramTab.SEARCH to "search_tab",
        InstagramTab.PROFILE to "profile_tab"
    ).firstOrNull { (_, id) ->
        root.findAccessibilityNodeInfosByViewId("$INSTAGRAM_PACKAGE:id/$id")
            .any { it.isVisibleToUser && isSelectedRecursive(it, 2) }
    }?.first

    private fun isRecentProxiedNavigation(tab: InstagramTab, now: Long): Boolean {
        return instagramProtectedNavigation.isDuplicate(tab, now)
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
        if (lastPackage == YOUTUBE_PACKAGE && packageName != YOUTUBE_PACKAGE) {
            youtubeShortsSession.clear()
        }
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
        val nodes = cachedNodeLookup(root, viewId)
        var maxRect: Rect? = null
        var maxArea = 0L
        for (node in nodes) {
            if (node.isVisibleToUser) {
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
        val selectedHome = root.findAccessibilityNodeInfosByViewId("$INSTAGRAM_PACKAGE:id/feed_tab")
            .any { it.isVisibleToUser && isSelectedRecursive(it, 2) }
        val homeNavigationPending = instagramProtectedNavigation.isPending(
            InstagramTab.HOME, SystemClock.uptimeMillis())
        if (!InstagramHomeGeometry.canMeasureDuringNavigation(homeNavigationPending, selectedHome) ||
            (header == null && !selectedHome)) return false
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
        if (instagramAlternateFeedActive) {
            val retained = instagramOverlay.visibleHomeBounds()
            val actionBarBottom = attachedInstagramBounds(
                root, displayBounds, "action_bar_container", requireWide = true)?.bottom
            val contentBottom = listOf(
                "layout_container_main_panel",
                "layout_container_main_wrapper",
                "layout_container_main"
            ).firstNotNullOfOrNull { id ->
                attachedInstagramBounds(root, displayBounds, id, requireWide = true)?.bottom
            }
            val top = InstagramHomeGeometry.alternateFeedTop(
                displayBounds.top, displayBounds.bottom, actionBarBottom, retained?.top)
            val bottom = InstagramHomeGeometry.alternateFeedBottom(
                top, displayBounds.bottom, contentBottom, retained?.bottom)
            return Rect(displayBounds.left, top.coerceAtMost(bottom - 1),
                displayBounds.right, bottom)
        }
        val stories = if (instagramStoriesBlocked()) null else {
            InstagramHomeGeometry.storiesTrayIds.firstNotNullOfOrNull { id ->
                findVisibleNodeBounds(root, "$INSTAGRAM_PACKAGE:id/$id")
            }
            // A conservative navigation curtain can temporarily occlude the
            // Stories tray and make isVisibleToUser false. HOME has already
            // been structurally verified before this method is called, so an
            // attached, sane tray rectangle is safe to use without uncovering
            // the feed for a measurement frame.
            ?: findAttachedHomeStoriesBounds(root, displayBounds)
        }
        val bounds = contentBounds(root, displayBounds, stories?.bottom, home = true)
        if (SystemClock.uptimeMillis() < scrollCoverUntil && instagramOverlay.visibleHomeBounds() != null) {
            scrollCoverTop?.let { bounds.top = minOf(bounds.top, it) }
        }
        clampCurtainBottomToNavigation(root, bounds)
        return bounds
    }

    private fun attachedInstagramBounds(
        root: AccessibilityNodeInfo,
        window: Rect,
        id: String,
        requireWide: Boolean = false
    ): Rect? = cachedNodeLookup(root, "$INSTAGRAM_PACKAGE:id/$id")
        .map { Rect().also(it::getBoundsInScreen) }
        .firstOrNull { bounds ->
            !bounds.isEmpty && bounds.left >= window.left && bounds.right <= window.right &&
                bounds.top >= window.top && bounds.bottom <= window.bottom &&
                (!requireWide || bounds.width() >= window.width() / 2)
        }

    private fun findAttachedHomeStoriesBounds(
        root: AccessibilityNodeInfo,
        window: Rect
    ): Rect? {
        val ids = InstagramHomeGeometry.storiesTrayIds
        return ids.firstNotNullOfOrNull { id ->
            cachedNodeLookup(root, "$INSTAGRAM_PACKAGE:id/$id")
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
        val header = cachedNodeLookup(root, "$INSTAGRAM_PACKAGE:id/main_feed_action_bar")
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
        val search = attachedExploreHeaderBounds(root, displayBounds)
            ?: findVisibleNodeBounds(root, "com.instagram.android:id/action_bar_search_edit_text")
        val bounds = contentBounds(root, displayBounds, search?.bottom, home = false)
        clampCurtainBottomToNavigation(root, bounds)
        return bounds
    }

    private fun attachedExploreHeaderBounds(root: AccessibilityNodeInfo, window: Rect): Rect? =
        cachedNodeLookup(root, "$INSTAGRAM_PACKAGE:id/explore_action_bar")
            .map { Rect().also(it::getBoundsInScreen) }
            .firstOrNull { bounds ->
                // Our temporary curtain can mark native chrome as occluded.
                // Require the exact header ID and sane, full-width window geometry.
                bounds.width() >= window.width() / 2 && bounds.height() in 1..dp(96) &&
                    bounds.left >= window.left && bounds.right <= window.right &&
                    bounds.top >= window.top && bounds.bottom < window.centerY()
            }

    private fun contentBounds(root: AccessibilityNodeInfo, window: Rect, anchor: Int?, home: Boolean): Rect {
        val header = if (home) verifiedHomeHeaderBounds(root, window) else null
        val screen = if (home) InstagramScreen.HOME_FEED else InstagramScreen.EXPLORE
        val bottom = stableMainNavigationTop(root, window, screen)
        // Only chrome/tray boundaries determine the mask. Never use a post/list's
        // moving bounds, and never reuse Explore coordinates for Home.
        val visibleAnchor = anchor?.takeIf { it > window.top && it < window.centerY() }
        val top = if (home) InstagramHomeGeometry.top(
            window.top, visibleAnchor, header?.bottom, blockStories = instagramStoriesBlocked())
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
            cachedNodeLookup(root, "$INSTAGRAM_PACKAGE:id/$id")
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
        val detectedTop = barTop ?: tabTop
        val profileContentBottom = if (screen == InstagramScreen.PROFILE && detectedTop == null) {
            findVisibleNodeBounds(root, "$INSTAGRAM_PACKAGE:id/layout_container_main")
                ?.bottom
                ?.takeIf { it > window.centerY() && it <= window.bottom }
        } else null

        return InstagramBottomNavigationGeometry.resolveTop(
            windowTop = window.top,
            windowBottom = window.bottom,
            retainedTop = retainedTop,
            detectedTop = detectedTop,
            fallbackInset = dp(48),
            contentBottom = profileContentBottom
        )
    }

    private fun onInstagramOverlayTouched() {
        if (!ProtectionPreferences.isEnabled(this, ProtectedApp.INSTAGRAM)) return
        // Only explicit blocked entry guards call this. Plain content-curtain
        // touches are not proof of an attempt to open a video or Story.
        recordIntervention(INSTAGRAM_PACKAGE, System.currentTimeMillis())
    }

    /**
     * A downward swipe on the Strict profile curtain. The curtain consumes the
     * whole collapsed body, so without this a collapsed profile could never
     * show its header (name, bio, Follow/Message) again. Expanding only moves
     * the grid down, away from the curtain edge, so it is always safe; the
     * collapsing direction stays blocked.
     */
    private fun expandStrictProfileHeader() {
        if (!ProtectionPreferences.isEnabled(this, ProtectedApp.INSTAGRAM) ||
            !instagramProfilePostsBlocked() || lastInstagramScreen != InstagramScreen.PROFILE) return
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != INSTAGRAM_PACKAGE) return
        val candidates = mutableListOf<AccessibilityNodeInfo>()
        for (id in listOf("tab_appbar", "profile_header_fixed_list", "coordinator_root_layout")) {
            candidates += root.findAccessibilityNodeInfosByViewId("$INSTAGRAM_PACKAGE:id/$id")
        }
        val target = candidates.firstOrNull { node ->
            node.isVisibleToUser && node.actionList.any {
                it.id == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            }
        }
        val accepted = target?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) == true
        Log.d(TAG, "IG_PROFILE_EXPAND target=${compactId(target?.viewIdResourceName)} accepted=$accepted")
        if (ProtectionFlightRecorder.ENABLED) ProtectionFlightRecorder.record(this, "instagram.profile.expand",
            "target=${compactId(target?.viewIdResourceName)} accepted=$accepted")
        kickProfileGeometryWatch()
        queueInstagramScan(0L)
    }

    private fun onInstagramStoryEntrySwipe(x: Float, y: Float, direction: InstagramStoryGuardSwipe) {
        if (!InstagramProfileInteractionPolicy.interceptsProfileTouches(
                ProtectionPreferences.getMode(this, ProtectedApp.INSTAGRAM))) return
        if (!ProtectionPreferences.isEnabled(this, ProtectedApp.INSTAGRAM) || !instagramStoriesBlocked()) return
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != INSTAGRAM_PACKAGE || !hasVisibleProfileHeaderMarkers(root)) return
        val horizontal = direction == InstagramStoryGuardSwipe.HIGHLIGHTS_FORWARD ||
            direction == InstagramStoryGuardSwipe.HIGHLIGHTS_BACKWARD
        val candidates = mutableListOf<AccessibilityNodeInfo>()
        val id = if (horizontal) "highlights_reel_tray_recycler_view" else "profile_header_fixed_list"
        candidates.addAll(root.findAccessibilityNodeInfosByViewId("$INSTAGRAM_PACKAGE:id/$id"))
        if (!horizontal) {
            // Only verified vertical ancestors, never the app's horizontal
            // main pager. ACTION_SCROLL_FORWARD on that pager changes tabs.
            for (anchorId in listOf("highlights_tray", "profile_header_container")) {
                var parent = root.findAccessibilityNodeInfosByViewId("$INSTAGRAM_PACKAGE:id/$anchorId")
                    .firstOrNull { it.isVisibleToUser }
                repeat(8) {
                    val node = parent ?: return@repeat
                    candidates.add(node)
                    parent = node.parent
                }
            }
        }
        val target = candidates
            .firstOrNull {
                it.isVisibleToUser && it.isScrollable && InstagramStoryEntryPolicy.isScrollTarget(
                    compactId(it.viewIdResourceName), it.className?.toString().orEmpty(), horizontal) &&
                    Rect().also(it::getBoundsInScreen).contains(x.toInt(), y.toInt())
            } ?: run {
                Log.d(TAG, "IG_STORY_ENTRY_SCROLL rejected=unverified_axis horizontal=$horizontal")
                queueInstagramScan(0L)
                return
            }
        val forward = direction == InstagramStoryGuardSwipe.PROFILE_FORWARD ||
            direction == InstagramStoryGuardSwipe.HIGHLIGHTS_FORWARD
        val accepted = target.performAction(if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
        Log.d(TAG, "IG_STORY_ENTRY_SCROLL direction=$direction accepted=$accepted")
        if (accepted) {
            // Keep both Story entry windows attached. Removing them to replay a
            // gesture would create a new fast-tap hole on the native avatar.
            val current = rootInActiveWindow
            if (!horizontal && current?.packageName?.toString() == INSTAGRAM_PACKAGE &&
                current.windowId == root.windowId && hasVisibleProfileHeaderMarkers(current)) {
                val window = instagramWindowBounds(current)
                profileGridBounds(current, window)?.let { bounds ->
                    bounds.top = profileScrollCover.measure(bounds.top,
                        calculateProfileScrollFloor(current, window), SystemClock.uptimeMillis())
                    applyStrictProfileCurtain(current, window, bounds)
                }
            }
            queueInstagramScan(0L)
        }
    }

    private fun onInstagramEntryGuardGesture(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long
    ) {
        if (lastInstagramScreen == InstagramScreen.NOTIFICATIONS) {
            // Notification row guards protect comment-like media taps in
            // STRICT mode, but must not turn a finger drag into a coarse
            // accessibility page jump. Temporarily remove only the guards,
            // replay the measured gesture on Instagram's native list, then
            // remount the row regions after the list settles.
            instagramOverlay.updateEntryGuards(emptyList(), isEnglish())
            instagramNotificationGestureReplayUntil =
                SystemClock.uptimeMillis() + durationMs.coerceIn(260L, 1_000L) + 200L
            mainHandler.postDelayed({
                val root = rootInActiveWindow
                if (root?.packageName?.toString() != INSTAGRAM_PACKAGE || !hasFastNotificationsSurface(root)) {
                    instagramNotificationGestureReplayUntil = 0L
                    queueInstagramScan(0L)
                    return@postDelayed
                }
                dispatchScreenSwipe(startX, startY, endX, endY, durationMs) { completed ->
                    instagramNotificationGestureReplayUntil = 0L
                    Log.d(TAG, "IG_NOTIFICATION_SWIPE completed=$completed")
                    refreshInstagramNotificationEntryGuards()
                    queueInstagramScan(16L)
                }
            }, 48L)
            mainHandler.postDelayed({
                scanSchedule.clear()
                queueInstagramScan(0L)
            }, durationMs.coerceIn(260L, 1_000L) + 200L)
            return
        }
        val dmMode = ProtectionPreferences.getMode(this, ProtectedApp.INSTAGRAM)
        if (lastInstagramScreen != InstagramScreen.DIRECT_MESSAGES ||
            !InstagramDmInteractionPolicy.blocksSharedMedia(dmMode)) {
            val direction = if (endY < startY) InstagramGuardScrollDirection.FORWARD
            else InstagramGuardScrollDirection.BACKWARD
            onInstagramEntryGuardVerticalSwipe(startX, startY, direction)
            return
        }
        // The exact Reel-card guard must not turn a finger drag into a coarse
        // ACTION_SCROLL page. Temporarily remove only this touch window, replay
        // the measured gesture underneath it, then let the next scan remount
        // the card guard at the new position.
        instagramOverlay.updateEntryGuards(emptyList(), isEnglish())
        mainHandler.postDelayed({
            dispatchScreenSwipe(startX, startY, endX, endY, durationMs) {
                queueInstagramScan(16L)
            }
        }, 48L)
        // A gesture can finish while Instagram is still replacing the
        // Compose message rows.  In that case the normal scan scheduler may
        // still hold the pre-gesture deadline and reject the callback above.
        // Force one bounded follow-up scan so the card guard is remounted at
        // the new scroll position instead of disappearing after the drag.
        mainHandler.postDelayed({
            scanSchedule.clear()
            queueInstagramScan(0L)
        }, 360L)
    }

    private fun onInstagramEntryGuardVerticalSwipe(
        startX: Float,
        startY: Float,
        direction: InstagramGuardScrollDirection
    ) {
        if (lastInstagramScreen == InstagramScreen.NOTIFICATIONS) {
            val root = rootInActiveWindow ?: return
            if (root.packageName?.toString() != INSTAGRAM_PACKAGE || !hasFastNotificationsSurface(root)) return
            val list = instagramNotificationListNodes(root).firstOrNull {
                compactId(it.viewIdResourceName) == "activity_feed_list" && it.isScrollable
            } ?: return
            val action = if (direction == InstagramGuardScrollDirection.FORWARD)
                AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            instagramNotificationMotion.arm(SystemClock.uptimeMillis())
            // The persistent notification input window already covers movement.
            val accepted = list.performAction(action)
            Log.d(TAG, "IG_NOTIFICATION_GUARD_SCROLL direction=$direction accepted=$accepted")
            if (accepted) {
                refreshInstagramNotificationEntryGuards()
                queueInstagramScan(16L)
            } else {
                instagramNotificationMotion.reset()
                instagramOverlay.hideTransientTouchGuard()
            }
            return
        }
        if (lastInstagramScreen != InstagramScreen.DIRECT_MESSAGES) return
        // Never replace native chat gestures with discrete accessibility pages.
        val dmMode = ProtectionPreferences.getMode(this, ProtectedApp.INSTAGRAM)
        if (!InstagramDmInteractionPolicy.interceptsChatTouches(dmMode) &&
            !InstagramDmInteractionPolicy.blocksSharedMedia(dmMode)) return
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
        instagramDetailExitChecksRemaining = 0
        val wasNotificationExit = instagramExitFromNotifications
        if (!instagramExitInProgress) {
            instagramExitFromDirectConversation = false
            instagramExitFromNotifications = false
            instagramNotificationCommentLayerObserved = false
            if (wasNotificationExit) {
                instagramNotificationEventLocked = false
                instagramNotificationLastEventExitAt = 0L
            }
            return
        }
        instagramExitInProgress = false
        instagramExitFromDirectConversation = false
        instagramExitFromNotifications = false
        instagramNotificationCommentLayerObserved = false
        if (wasNotificationExit) {
            instagramNotificationEventLocked = false
            instagramNotificationLastEventExitAt = 0L
        }
        instagramBlockedAttempt.reset()
        instagramExitTreeQuietUntil = 0L
        mainHandler.removeCallbacks(instagramViewerExitRetry)
        instagramFastExitHandler.removeCallbacks(instagramFastNotificationBack)
        instagramFastExitDeadlineAt = 0L
        instagramFastExitGeneration = 0L
        if (wasNotificationExit) instagramNotificationExitGeneration++
    }

    private fun scheduleFastNotificationSecondBack(initialDelayMs: Long = 90L) {
        instagramFastExitHandler.removeCallbacks(instagramFastNotificationBack)
        instagramFastExitGeneration = instagramNotificationExitGeneration
        // Let Instagram finish dismissing the comment sheet, but do not route
        // this through mainHandler: that queue is where the observed 4.57 s
        // delay originated. The worker checks the actual sheet state first.
        instagramFastExitDeadlineAt = SystemClock.uptimeMillis() + 1_800L
        instagramFastExitHandler.postDelayed(instagramFastNotificationBack, initialDelayMs)
    }

    private fun requeueFastNotificationBack() {
        instagramFastExitHandler.removeCallbacks(instagramFastNotificationBack)
        instagramFastExitHandler.postDelayed(instagramFastNotificationBack, 90L)
    }

    private fun scheduleInstagramViewerExitRetry(delayMs: Long = INSTAGRAM_VIEWER_EXIT_RETRY_MS) {
        mainHandler.removeCallbacks(instagramViewerExitRetry)
        mainHandler.postDelayed(instagramViewerExitRetry, delayMs)
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
        if (screen == InstagramScreen.SAVED_COLLECTION &&
            (previous == InstagramScreen.EXPLORE || previous == InstagramScreen.HOME_FEED)) {
            // Its bookmark button opened Saved; Back returns to this feed.
            instagramCollectionFeedOrigin = previous
        }
        if (previous == InstagramScreen.NOTIFICATIONS &&
            screen == InstagramScreen.REELS) {
            // Notification thumbnails do not reliably emit TYPE_VIEW_CLICKED.
            // The verified state transition is therefore the authoritative
            // start of the attempt and gives us an honest end-to-end timer.
            instagramNotificationAttemptStartedAt = SystemClock.uptimeMillis()
            if (ProtectionPreferences.getMode(this, ProtectedApp.INSTAGRAM) == ProtectionMode.STRICT) {
            // A second notification tap can arrive while the bounded exit from
            // the previous viewer is still pending. Treat this transition as
            // a fresh viewer encounter; otherwise the retry state is consumed
            // by the old attempt and the new Reel can remain open indefinitely.
            if (instagramExitInProgress) {
                cancelInstagramExit()
                Log.d(TAG, "IG_NOTIFICATION_NEW_VIEWER reset=true")
            }
            // Compose notification thumbnails may replace the surface without
            // publishing a click event. Latch the origin at the verified
            // state transition so exitBlockedViewer() cannot lose it.
            instagramNotificationExitHoldUntil = SystemClock.uptimeMillis() + 10_000L
            instagramNotificationBackCount = 0
            instagramNotificationReelExitIssued = false
            instagramNotificationReelSecondBackIssued = false
            instagramNotificationCommentNeedsSecondBack = false
            }
        }
        if (screen != previous) instagramFeedSnapshotYields = 0
        if (screen != InstagramScreen.STORY && screen != InstagramScreen.UNKNOWN) {
            instagramStoryExitReadiness.reset()
        }
        if (screen in setOf(InstagramScreen.HOME_FEED, InstagramScreen.EXPLORE,
                InstagramScreen.SEARCH_RESULTS, InstagramScreen.DIRECT_MESSAGES,
                InstagramScreen.DIRECT_SHARED_MEDIA, InstagramScreen.PROFILE,
                InstagramScreen.SAVED_COLLECTION, InstagramScreen.NOTIFICATIONS,
                InstagramScreen.CREATION, InstagramScreen.STORY)) {
            instagramReelsSession.observe(ProtectionPreferences.getMode(this, ProtectedApp.INSTAGRAM),
                viewerVisible = false, verifiedOutside = true)
        }
        if (instagramProfileIngress.pending && ((screen == InstagramScreen.SEARCH_RESULTS &&
                instagramProfileIngress.sawProfile) || (screen != InstagramScreen.PROFILE &&
                screen != InstagramScreen.SEARCH_RESULTS && screen != InstagramScreen.UNKNOWN))) {
            instagramProfileIngress.reset()
            instagramOverlay.updateProfileIngressGuard(null)
        }
        if (screen != InstagramScreen.NOTIFICATIONS && screen != InstagramScreen.UNKNOWN &&
            !instagramNotificationIngress.active(SystemClock.uptimeMillis())) {
            instagramOverlay.updateNotificationIngressGuard(null)
        }
        if (previous == InstagramScreen.NOTIFICATIONS && screen != InstagramScreen.NOTIFICATIONS &&
            screen != InstagramScreen.UNKNOWN) {
            instagramNotificationContentLaunchUntil = 0L
            instagramNotificationGestureReplayUntil = 0L
            clearInstagramNotificationIngress()
            instagramOverlay.updateEntryGuards(emptyList(), isEnglish())
            instagramOverlay.hideTransientTouchGuard()
            instagramNotificationMotion.reset()
        }
        instagramNotificationReturn.observe(screen, previous)
        if (screen != InstagramScreen.NOTIFICATIONS && screen != InstagramScreen.UNKNOWN) {
            instagramOverlay.updateNotificationBackGuard(null)
        }
        if (screen != InstagramScreen.PROFILE && screen != InstagramScreen.UNKNOWN) {
            clearInstagramProfileReturn()
        }
        if (screen != InstagramScreen.COMMENTS_OR_DETAIL && screen != InstagramScreen.UNKNOWN) {
            instagramBalancedNotificationPostWatching = false
        }
        // A tab-slip Reels cover must never outlive its exit: it is retired
        // once the return surface is verified (or when no feed followed the
        // arming within its bounded window).
        observeDirectModalCover(screen, windowId)
        if (screen == InstagramScreen.REELS) {
            if (reelsSlipCoverUntil != 0L) reelsSlipCoverExit = true
        } else if (screen != InstagramScreen.UNKNOWN) {
            val expired = reelsSlipCoverUntil != 0L &&
                SystemClock.uptimeMillis() >= reelsSlipCoverUntil
            if (reelsSlipCoverExit || expired) {
                reelsSlipCoverExit = false
                reelsSlipCoverUntil = 0L
                instagramOverlay.hideReelsSlipCover()
            }
        }
        instagramBalancedProfilePost.observeVerifiedSurface(screen, SystemClock.uptimeMillis())
        val decision = instagramStateMachine.observe(screen, windowId,
            allowChosenReel = !instagramStoriesBlocked() && instagramReelsSession.watching &&
                !instagramReelsSession.exiting,
            allowProfile = !instagramProfilePostsBlocked(),
            allowChosenProfilePost = instagramBalancedProfilePost.watching ||
                instagramBalancedNotificationPostWatching,
            allowUserCollection = ProtectionPreferences.getMode(
                this, ProtectedApp.INSTAGRAM) == ProtectionMode.BALANCED)
        if (decision.changed) {
            Log.d(TAG, "IG_V2_STATE $previous -> ${decision.screen} " +
                "window=$windowId revision=${decision.revision} action=${decision.action}")
            if (ProtectionFlightRecorder.ENABLED) {
                ProtectionFlightRecorder.record(this, "instagram.state",
                    "from=$previous to=${decision.screen} window=$windowId " +
                        "revision=${decision.revision} action=${decision.action}")
            }
        }
        if (screen != InstagramScreen.UNKNOWN) lastInstagramScreen = decision.screen
        if (decision.screen == InstagramScreen.NOTIFICATIONS && previous != InstagramScreen.NOTIFICATIONS) {
            instagramNotificationEventLocked = false
            instagramNotificationLastEventExitAt = 0L
        }
        // Gesture/native return from search results or notifications bypasses
        // the touch-first precovers: premount the blocked Reels-tab guard the
        // moment the destination main surface is classified, so the frames
        // before its own bottom bar is published stay covered.
        if ((previous == InstagramScreen.SEARCH_RESULTS || previous == InstagramScreen.NOTIFICATIONS) &&
            screen in setOf(InstagramScreen.HOME_FEED, InstagramScreen.EXPLORE,
                InstagramScreen.PROFILE, InstagramScreen.DIRECT_MESSAGES)) {
            premountReelsTabGuard()
        }
        return decision
    }

    private fun scheduleOverlayForegroundCheck() {
        if (overlayForegroundCheckScheduled) return
        overlayForegroundCheckScheduled = true
        mainHandler.postDelayed(overlayForegroundCheck,
            if (instagramOverlay.isNotificationShadeSuspended) 32L else OVERLAY_FOREGROUND_CHECK_MS)
    }

    private fun showInstagramCurtain(bounds: Rect?, screen: InstagramScreen): Boolean {
        if (bounds == null) return false
        if (screen == InstagramScreen.PROFILE) {
            if (ProtectionFlightRecorder.ENABLED) {
                val callers = Throwable().stackTrace.drop(1).take(3)
                    .joinToString(" <- ") { "${it.methodName}:${it.lineNumber}" }
                ProtectionFlightRecorder.record(this, "instagram.profile.mount",
                    "top=${bounds.top} caller=$callers")
            }
        }
        if (screen == InstagramScreen.PROFILE && instagramProfilePostsBlocked()) {
            val root = rootInActiveWindow
            if (root?.packageName?.toString() == INSTAGRAM_PACKAGE) {
                refreshStrictProfileChromeGuard()
                profileScrollFloor = calculateProfileScrollFloor(root, instagramWindowBounds(root))
                profileScrollCover.reset()
            }
        } else {
            instagramOverlay.updateProfileChromeGuard(null)
        }
        val now = SystemClock.uptimeMillis()
        val holdExploreTop = now < exploreTransitionCoverUntil &&
            currentInstagramWindowId == exploreTransitionCoverWindowId &&
            screen in setOf(InstagramScreen.EXPLORE, InstagramScreen.HOME_FEED)
        // Home can still be reported after Explore's precover is drawn but
        // before native navigation commits. That old geometry must not shrink
        // the precover back below Stories. The existing bounded transition
        // expires normally; settled Balanced Home restores its Stories area.
        bounds.top = InstagramNavigationGeometry.top(bounds.top, exploreTransitionCoverTop,
            screen, currentInstagramWindowId == exploreTransitionCoverWindowId, holdExploreTop)
        if (holdExploreTop) queueInstagramScan(minOf(48L, exploreTransitionCoverUntil - now))
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
            if (shown) {
                setCurtainVerifiedAtMs = now
                setCurtainVerifiedWindowId = currentInstagramWindowId
            }
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
        if (screen == InstagramScreen.PROFILE && !instagramProfilePostsBlocked()) {
            instagramResumePending = false
            releaseBalancedInstagramProfile(root, instagramWindowBounds(root))
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
        releaseDirectModalCover("reset")
        instagramCollectionFeedOrigin = null
        precoverDrawPendingUntil = 0L
        instagramReelsSession.clear()
        instagramBalancedReelOrigin = InstagramScreen.UNKNOWN
        instagramBalancedNotificationPostWatching = false
        instagramBalancedProfilePost.clear()
        instagramBalancedProfileReelIntentWindowId = -1
        instagramBalancedProfileReelIntentUntil = 0L
        instagramProfileIngress.reset()
        lastInstagramNotificationLaunchBounds = null
        clearInstagramNotificationIngress()
        instagramNotificationMotion.reset()
        instagramNotificationGestureReplayUntil = 0L
        instagramNotificationReturn.reset()
        clearInstagramProfileReturn()
        instagramProtectedNavigation.clear()
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
        instagramAlternateFeedActive = false
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
        if (instagramOverlay.isNotificationShadeSuspended) {
            Log.d(TAG, "IG_SHADE_FULL_SUSPEND root=${rootInActiveWindow?.packageName} " +
                "tracked=$lastPackage owner=${Throwable().stackTrace.drop(1).take(2).joinToString { it.methodName }}")
        }
        instagramStoryExitReadiness.reset()
        instagramOverlay.visibleCurtain()?.let { (screen, bounds) ->
            lastVerifiedInstagramCurtain = screen to Rect(bounds)
        }
        resetInstagramProtectionState()
        instagramStateMachine.leaveInstagram()
        // Samsung scales an attached accessibility window together with the
        // Recents task card. Remove the window entirely; resume recreates it
        // from the last verified application-space rectangle.
        instagramOverlay.hideAll()
        // The shade's closing frames can leave the tracked Instagram surface
        // foreground again without publishing any further event. One bounded
        // recovery appplies the last verified curtain from that rectangle; the
        // resume helpers only cover verified destinations.
        mainHandler.removeCallbacks(instagramShadeRecovery)
        mainHandler.postDelayed(instagramShadeRecovery, 350L)
    }

    private val instagramShadeRecovery = Runnable {
        val root = rootInActiveWindow ?: return@Runnable
        if (root.packageName?.toString() != INSTAGRAM_PACKAGE ||
            !ProtectionPreferences.isEnabled(this, ProtectedApp.INSTAGRAM) ||
            instagramOverlay.visibleCurtain() != null) return@Runnable
        instagramResumePending = true
        beginInstagramResumeProtection(root)
        scheduleOverlayForegroundCheck()
    }

    private fun hideInstagramProtection() {
        instagramStoryExitReadiness.reset()
        resetInstagramProtectionState()
        instagramStateMachine.reset()
        lastInstagramScreen = InstagramScreen.UNKNOWN
        lastVerifiedInstagramCurtain = null
        instagramOverlay.hideAll()
    }

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
        if (ProtectionFlightRecorder.ENABLED) {
            ProtectionFlightRecorder.record(this, "service.unbind")
        }
        hideInstagramProtection()
        youtubeOverlay.hideAll()
        AccessibilityStreakPolicy.onServiceDisconnected(this)
        notifyProtectionDisabled()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (ProtectionFlightRecorder.ENABLED) {
            ProtectionFlightRecorder.record(this, "service.destroy")
        }
        hideInstagramProtection()
        youtubeOverlay.hideAll()
        AccessibilityStreakPolicy.onServiceDisconnected(this)
        mainHandler.removeCallbacksAndMessages(null)
        instagramFastExitHandler.removeCallbacksAndMessages(null)
        instagramFastExitThread.quitSafely()
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
            else "Koruma devre dışı bırakıldı. Tekrar açmazsanız günlük seriniz 24 saat içinde sıfırlanır.",
            1002
        )
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted")
        if (ProtectionFlightRecorder.ENABLED) {
            ProtectionFlightRecorder.record(this, "service.interrupt")
        }
    }


}
