package com.resolvecommunity.awaydoomscrollin

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat

/**
 * Covers only Instagram's feed viewport. The Instagram action bar and bottom
 * navigation remain available, so the user can still move to DMs or a profile.
 *
 * TYPE_ACCESSIBILITY_OVERLAY is provided by the enabled accessibility service;
 * it does not require the broad SYSTEM_ALERT_WINDOW permission.
 */
internal class InstagramFeedOverlayController(
    private val service: AccessibilityService,
    private val onBlockedTouch: () -> Unit,
    private val onProfileSwipeUp: () -> Unit = {},
    private val onProtectedNavigation: (InstagramTab) -> Unit = {},
    private val onEntryGuardVerticalSwipe: (Float, Float, InstagramGuardScrollDirection) -> Unit =
        { _, _, _ -> },
    private val onEntryGuardGesture: ((Float, Float, Float, Float, Long) -> Unit)? = null,
    private val onStoryEntrySwipe: (Float, Float, InstagramStoryGuardSwipe) -> Unit = { _, _, _ -> },
    private val onProfileSwipeDown: (Float, Float) -> Unit = { _, _ -> },
    // Where a guarded control has a harmless long-press action (a chat message's
    // react/reply menu), a held press is replayed underneath instead of blocked.
    private val entryGuardReplaysLongPress: () -> Boolean = { false }
) {
    companion object {
        private const val TAG = "InstagramOverlay"

        /**
         * The resume gate re-applies identical bounds to snap WindowManager
         * transforms back into application coordinates. Those re-hover frames
         * target one animation, not one per queued rescan, so repeat applies
         * closer together than this are duplicates of the same frame.
         */
        private const val FORCED_LAYOUT_MIN_INTERVAL_MS = 96L
    }

    private val windowManager by lazy {
        service.getSystemService(WindowManager::class.java)
    }
    private var overlay: View? = null
    var isNotificationShadeSuspended: Boolean = false
        private set

    fun suspendForNotificationShade() {
        isNotificationShadeSuspended = true
        removeGuardWindows()
        // Keep measured geometry attached without intercepting SystemUI.
        overlay?.visibility = View.INVISIBLE
    }
    private var currentBounds: Rect? = null
    private var currentCopyKey: String? = null
    private var shieldContent: View? = null
    private var curtainWindowMounted = false
    private var lastForcedLayoutAtMs = 0L
    private val entryWindows = mutableListOf<Pair<View, Rect>>()
    private var transientTouchGuard: View? = null
    private var transientTouchGuardBounds: Rect? = null
    private var conversationEntryGuard: View? = null
    private var conversationEntryGuardBounds: Rect? = null
    private val navigationWindows = mutableMapOf<InstagramTab, Pair<View, Rect>>()
    private var notificationBackWindow: Pair<View, Rect>? = null
    private var profileReturnWindow: Pair<View, Rect>? = null
    private var profileChromeWindow: Pair<View, Rect>? = null
    private var searchReturnWindow: Pair<View, Rect>? = null
    private var reelsTabGuardWindow: Pair<View, Rect>? = null
    private var reelsSlipCover: Pair<View, Rect>? = null
    private var strictProfilePieceWindows = mutableListOf<Pair<View, Rect>>()
    private var notificationLaunchWindow: Pair<View, Rect>? = null
    private var notificationIngressWindow: Pair<View, Rect>? = null
    private val storyEntryWindows = mutableListOf<Pair<View, Rect>>()
    private var accountSearchWindow: Pair<View, Rect>? = null
    private var profileIngressWindow: Pair<View, Rect>? = null

    fun updateAccountSearchGuard(bounds: Rect?, onTap: (Float, Float) -> Unit,
        onScroll: (Boolean) -> Unit) {
        if (bounds != null && accountSearchWindow?.second == bounds) return
        accountSearchWindow = updateNotificationWindow(accountSearchWindow, bounds, "account search")
        accountSearchWindow?.first?.setOnTouchListener(object : View.OnTouchListener {
            private val gesture = InstagramStoryGuardGesture(dp(12).toFloat())
            override fun onTouch(view: View, event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> gesture.begin(event.rawX, event.rawY)
                    MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                        gesture.update(event.rawX, event.rawY)?.let {
                            if (it == InstagramStoryGuardSwipe.PROFILE_FORWARD ||
                                it == InstagramStoryGuardSwipe.PROFILE_BACKWARD) {
                                onScroll(it == InstagramStoryGuardSwipe.PROFILE_FORWARD)
                            }
                        }
                        if (event.actionMasked == MotionEvent.ACTION_UP) {
                            if (gesture.isTap) onTap(event.rawX, event.rawY)
                            gesture.cancel()
                        }
                    }
                    MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> gesture.cancel()
                }
                return true
            }
        })
    }

    fun updateProfileIngressGuard(bounds: Rect?): Boolean {
        profileIngressWindow = updateNotificationWindow(profileIngressWindow, bounds, "profile loading")
        return profileIngressWindow != null
    }

    fun afterProfileIngressDraw(action: () -> Unit) {
        val view = profileIngressWindow?.first ?: return
        afterWindowDraw(view, { profileIngressWindow?.first === view }, action)
    }

    fun afterStoryEntryGuardsDraw(targets: List<Rect>, action: () -> Unit) {
        val snapshot = storyEntryWindows.toList()
        if (snapshot.map { it.second } != targets) return
        if (snapshot.isEmpty()) { action(); return }
        var remaining = snapshot.size
        snapshot.forEach { (view, _) ->
            afterWindowDraw(view, { storyEntryWindows == snapshot }, {
                remaining--
                if (remaining == 0) action()
            })
        }
    }

    fun updateStoryEntryGuards(targets: List<Rect>) {
        val changed = storyEntryWindows.map { it.second } != targets
        targets.forEachIndexed { index, bounds ->
            val previous = storyEntryWindows.getOrNull(index)
            val replacement = updateNotificationWindow(previous, bounds, "story entry",
                replaceTouchListener = false)
            if (replacement != null) {
                if (previous == null) {
                    val gesture = InstagramStoryGuardGesture(dp(12).toFloat())
                    var startX = 0f
                    var startY = 0f
                    replacement.first.setOnTouchListener { _, event ->
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                startX = event.rawX
                                startY = event.rawY
                                gesture.begin(startX, startY)
                            }
                            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                                gesture.update(event.rawX, event.rawY)?.let {
                                    onStoryEntrySwipe(startX, startY, it)
                                }
                                if (event.actionMasked == MotionEvent.ACTION_UP) {
                                    if (gesture.isTap) onBlockedTouch()
                                    gesture.cancel()
                                }
                            }
                            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> gesture.cancel()
                        }
                        true
                    }
                }
                if (index < storyEntryWindows.size) storyEntryWindows[index] = replacement
                else storyEntryWindows.add(replacement)
            }
        }
        while (storyEntryWindows.size > targets.size) {
            updateNotificationWindow(storyEntryWindows.removeAt(storyEntryWindows.lastIndex), null, "story entry")
        }
        if (changed) Log.d(TAG, "IG_STORY_ENTRY_GUARDS requested=${targets.size} " +
            "visible=${storyEntryWindows.count { it.first.visibility == View.VISIBLE }} bounds=$targets")
    }

    fun updateNotificationLaunchGuard(bounds: Rect?, onLaunch: () -> Unit = {}) {
        notificationLaunchWindow = updateNotificationWindow(notificationLaunchWindow, bounds,
            "notification launch", onLaunch)
    }

    fun updateNotificationIngressGuard(bounds: Rect?, onTap: (Float, Float) -> Unit = { _, _ -> }): Boolean {
        if (bounds != null && notificationIngressWindow?.second == bounds) return true
        notificationIngressWindow = updateNotificationWindow(notificationIngressWindow, bounds,
            "notification loading")
        notificationIngressWindow?.first?.apply {
            var startX = 0f
            var startY = 0f
            var scrolled = false
            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = event.rawX
                        startY = event.rawY
                        scrolled = false
                    }
                    MotionEvent.ACTION_MOVE -> if (!scrolled) {
                        val direction = InstagramGuardGesture.verticalScrollDirection(
                            event.rawX - startX, event.rawY - startY, dp(12).toFloat())
                        if (direction != null) {
                            scrolled = true
                            onEntryGuardVerticalSwipe(startX, startY, direction)
                        }
                    }
                    MotionEvent.ACTION_UP -> if (!scrolled) onTap(event.rawX, event.rawY)
                    MotionEvent.ACTION_CANCEL -> scrolled = true
                }
                true
            }
        }
        return notificationIngressWindow != null
    }

    private fun updateNotificationWindow(existing: Pair<View, Rect>?, bounds: Rect?,
        title: String, onTap: () -> Unit = {}, replaceTouchListener: Boolean = true,
        opaqueBlack: Boolean = false): Pair<View, Rect>? {
        if (bounds == null || bounds.isEmpty) {
            existing?.first?.let {
                it.visibility = View.GONE
                try { windowManager?.removeViewImmediate(it) } catch (_: Exception) {}
            }
            return null
        }
        val view = existing?.first ?: View(service).apply {
            setBackgroundColor(if (opaqueBlack) Color.BLACK else Color.TRANSPARENT)
            isClickable = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val keyboardSafe = title == "account search" || title == "profile loading"
        if (keyboardSafe) {
            view.tag = Rect(bounds)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                view.setOnApplyWindowInsetsListener { inputView, insets ->
                    val requested = inputView.tag as? Rect
                    if (requested != null) {
                        val keyboardTop = if (insets.isVisible(android.view.WindowInsets.Type.ime())) {
                            val screenBottom = windowManager?.currentWindowMetrics?.bounds?.bottom
                                ?: service.resources.displayMetrics.heightPixels
                            screenBottom - insets.getInsets(android.view.WindowInsets.Type.ime()).bottom
                        } else null
                        val clipped = InstagramSearchGuardGeometry.clip(InstagramGeometrySample(
                            requested.left, requested.top, requested.right, requested.bottom), keyboardTop)
                        inputView.visibility = if (clipped == null) View.GONE else View.VISIBLE
                        if (clipped != null) {
                            try {
                                windowManager?.updateViewLayout(inputView, layoutParams(Rect(
                                    clipped.left, clipped.top, clipped.right, clipped.bottom), title))
                            } catch (_: Exception) { /* Detached views have no input region. */ }
                        }
                    }
                    insets
                }
            }
        }
        if (existing == null || replaceTouchListener) {
            view.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_UP) onTap()
                true
            }
        }
        return try {
            val manager = windowManager ?: return null
            if (existing == null) manager.addView(view, layoutParams(bounds, title))
            else if (existing.second != bounds) manager.updateViewLayout(view, layoutParams(bounds, title))
            if (keyboardSafe) view.requestApplyInsets()
            view to Rect(bounds)
        } catch (e: Exception) {
            view.visibility = View.GONE
            try { windowManager?.removeViewImmediate(view) } catch (_: Exception) {}
            Log.w(TAG, "Notification window update failed: $title", e)
            null
        }
    }

    private fun afterWindowDraw(view: View, stillCurrent: () -> Boolean, action: () -> Unit) {
        val listener = object : android.view.ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnPreDrawListener(this)
                view.postOnAnimation { if (stillCurrent() && view.visibility == View.VISIBLE) action() }
                return true
            }
        }
        view.viewTreeObserver.addOnPreDrawListener(listener)
        view.invalidate()
    }

    fun afterNotificationIngressDraw(action: () -> Unit) {
        val view = notificationIngressWindow?.first ?: return
        afterWindowDraw(view, { notificationIngressWindow?.first === view }, action)
    }

    fun afterEntryGuardsDraw(targets: List<Rect>, action: () -> Unit) {
        val snapshot = entryWindows.toList()
        if (snapshot.map { it.second } != targets || snapshot.any { it.first.visibility != View.VISIBLE }) return
        if (snapshot.isEmpty()) { action(); return }
        var remaining = snapshot.size
        snapshot.forEach { (view, _) ->
            afterWindowDraw(view, { entryWindows == snapshot }, {
                remaining--
                if (remaining == 0) action()
            })
        }
    }

    fun updateNotificationBackGuard(bounds: Rect?, onBack: () -> Unit = {}) {
        if (bounds == null || bounds.isEmpty) {
            notificationBackWindow?.first?.let { view ->
                view.visibility = View.GONE
                try { windowManager?.removeViewImmediate(view) } catch (_: Exception) {}
            }
            notificationBackWindow = null
            return
        }
        try {
            val existing = notificationBackWindow
            val view = existing?.first ?: View(service).apply {
                setBackgroundColor(Color.TRANSPARENT)
                isClickable = true
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            view.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_UP) onBack()
                true
            }
            if (existing == null) {
                val manager = windowManager ?: return
                manager.addView(view, layoutParams(bounds, "AwayDoomscrollin Instagram notification return"))
            } else if (existing.second != bounds) {
                windowManager?.updateViewLayout(view, layoutParams(bounds, "AwayDoomscrollin Instagram notification return"))
            }
            notificationBackWindow = view to Rect(bounds)
        } catch (e: Exception) {
            Log.w(TAG, "Notification Back guard update failed", e)
            updateNotificationBackGuard(null)
        }
    }

    /**
     * Covers only Instagram's native Back control while an allowed Strict-profile
     * layer is open. This lets the service draw the profile curtain before asking
     * Instagram to run its return animation, without blocking the allowed layer.
     */
    fun updateProfileReturnGuard(bounds: Rect?, onBack: () -> Unit = {}) {
        if (bounds == null || bounds.isEmpty) {
            profileReturnWindow?.first?.let { view ->
                view.visibility = View.GONE
                try { windowManager?.removeViewImmediate(view) } catch (_: Exception) {}
            }
            profileReturnWindow = null
            return
        }
        try {
            val existing = profileReturnWindow
            val view = existing?.first ?: View(service).apply {
                setBackgroundColor(Color.TRANSPARENT)
                isClickable = true
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            view.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_UP) onBack()
                true
            }
            if (existing == null) {
                val manager = windowManager ?: return
                manager.addView(view, layoutParams(bounds,
                    "AwayDoomscrollin Instagram profile return"))
            } else if (existing.second != bounds) {
                windowManager?.updateViewLayout(view, layoutParams(bounds,
                    "AwayDoomscrollin Instagram profile return"))
            }
            profileReturnWindow = view to Rect(bounds)
        } catch (e: Exception) {
            Log.w(TAG, "Profile return guard update failed", e)
            updateProfileReturnGuard(null)
        }
    }

    /** Strict profiles expose only Instagram's native Back control. */
    fun updateProfileChromeGuard(bounds: Rect?) {
        profileChromeWindow = updateNotificationWindow(profileChromeWindow, bounds,
            "strict profile chrome", onBlockedTouch)
    }

    /**
     * Touch-first return from search results: a transparent guard over the
     * Back control lets the service draw the Explore curtain before Instagram
     * runs its own back navigation, so the returning grid is never exposed.
     */
    fun updateSearchReturnGuard(bounds: Rect?, onBack: () -> Unit) {
        searchReturnWindow = updateNotificationWindow(searchReturnWindow, bounds,
            "search return", onTap = onBack)
    }

    /**
     * Persistent guard for the blocked bottom Reels tab. Unlike the generic
     * entry-guard list it is never cleared by unrelated surface updates, so a
     * transition that rebuilds other guards cannot open a tap window over the
     * tab. The service refreshes its bounds from every verified frame and
     * passes null when the tab bar is not visible.
     */
    fun updateReelsTabGuard(bounds: Rect?) {
        reelsTabGuardWindow = updateNotificationWindow(reelsTabGuardWindow, bounds,
            "reels tab guard", onBlockedTouch)
    }

    /**
     * One-shot opaque cover for a tab-slip Reels feed exit. The feed has no
     * action-bar Back control, so a tap that slipped past the tab guard and
     * reached the feed must not show even its first frames; the cover is
     * removed as soon as a non-Reels surface is verified (or on any guard
     * sweep). Verified single viewers keep the plain transparent exit.
     */
    fun showReelsSlipCover(bounds: Rect?) {
        reelsSlipCover = updateNotificationWindow(reelsSlipCover, bounds,
            "reels slip cover", onBlockedTouch, opaqueBlack = true)
    }

    fun hideReelsSlipCover() {
        reelsSlipCover = updateNotificationWindow(reelsSlipCover, null,
            "reels slip cover", onBlockedTouch)
    }

    /**
     * Secondary opaque segments of the split Strict-profile curtain. The
     * primary overlay keeps the first piece (with the protection message);
     * these cover every protected band between the exposed information rows so
     * highlights and grid stay untouchable while the numbers and buttons stay
     * native.
     */
    fun updateStrictProfilePieceGuards(pieces: List<Rect>) {
        val targets = pieces.map { Rect(it) }
        val changed = strictProfilePieceWindows.map { it.second } != targets
        targets.forEachIndexed { index, bounds ->
            val existing = strictProfilePieceWindows.getOrNull(index)
            val replacement = updateNotificationWindow(existing, bounds,
                "strict profile piece", replaceTouchListener = false, opaqueBlack = true)
            if (replacement != null) {
                if (index < strictProfilePieceWindows.size) {
                    strictProfilePieceWindows[index] = replacement
                } else {
                    strictProfilePieceWindows.add(replacement)
                }
            }
        }
        while (strictProfilePieceWindows.size > targets.size) {
            updateNotificationWindow(strictProfilePieceWindows.removeAt(
                strictProfilePieceWindows.lastIndex), null, "strict profile piece")
        }
        if (changed) Log.d(TAG, "IG_STRICT_PIECES requested=${targets.size} " +
            "visible=${strictProfilePieceWindows.count { it.first.visibility == View.VISIBLE }} " +
            "bounds=$targets")
    }

    fun afterCurtainDraw(action: () -> Unit) {
        val view = overlay ?: return
        val listener = object : android.view.ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnPreDrawListener(this)
                view.postOnAnimation {
                    if (overlay === view && view.visibility == View.VISIBLE) {
                        if (ProtectionFlightRecorder.ENABLED) {
                            ProtectionFlightRecorder.record(service, "instagram.curtain.drawn",
                                "screen=${currentCopyKey?.substringBefore(':') ?: "unknown"} " +
                                    "bounds=${currentBounds ?: "none"} source=before-action")
                        }
                        action()
                    }
                }
                return true
            }
        }
        view.viewTreeObserver.addOnPreDrawListener(listener)
        view.invalidate()
    }

    /**
     * Home and Explore are protected destinations. These transparent proxies
     * receive the tap before Instagram starts its tab animation; the service
     * mounts the destination curtain and then invokes the real tab action.
     */
    fun updateNavigationGuards(targets: Map<InstagramTab, Rect>) {
        targets.forEach { (tab, bounds) ->
            if (bounds.width() <= 0 || bounds.height() <= 0) return@forEach
            try {
                val existing = navigationWindows[tab]
                if (existing == null) {
                    val view = View(service).apply {
                        setBackgroundColor(Color.TRANSPARENT)
                        isClickable = true
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                        setOnClickListener { }
                        setOnTouchListener { target, event ->
                            if (event.actionMasked == MotionEvent.ACTION_UP) {
                                target.performClick()
                                Log.d(TAG, "IG_NAVIGATION_TOUCH tab=$tab")
                                onProtectedNavigation(tab)
                            }
                            true
                        }
                    }
                    val manager = windowManager ?: return@forEach
                    manager.addView(view, layoutParams(
                        bounds, "AwayDoomscrollin Instagram protected navigation"))
                    navigationWindows[tab] = view to Rect(bounds)
                } else {
                    val (view, previous) = existing
                    if (previous != bounds) {
                        windowManager?.updateViewLayout(view, layoutParams(
                            bounds, "AwayDoomscrollin Instagram protected navigation"))
                        navigationWindows[tab] = view to Rect(bounds)
                    }
                    view.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                navigationWindows.remove(tab)?.first?.visibility = View.GONE
                Log.w(TAG, "Navigation guard update failed for $tab", e)
            }
        }
        navigationWindows.keys.filterNot(targets::containsKey).toList().forEach { tab ->
            val view = navigationWindows.remove(tab)?.first ?: return@forEach
            view.visibility = View.GONE
            try { windowManager?.removeViewImmediate(view) } catch (e: Exception) {
                Log.w(TAG, "Navigation guard removal failed for $tab", e)
            }
        }
    }

    // These windows consume the entire gesture. They NEVER replay a tap or navigate.
    fun updateEntryGuards(targets: List<Rect>, isEnglish: Boolean, forceRelayout: Boolean = false) {
        val changed = entryWindows.map { it.second } != targets
        targets.forEachIndexed { index, bounds ->
            try {
                if (index < entryWindows.size) {
                    val (view, previous) = entryWindows[index]
                    if (previous != bounds || forceRelayout) {
                        windowManager?.updateViewLayout(view, layoutParams(bounds, "AwayDoomscrollin Instagram entry guard"))
                        entryWindows[index] = view to Rect(bounds)
                    }
                    view.visibility = View.VISIBLE
                } else {
                    var startX = 0f
                    var startY = 0f
                    var gestureStartedAt = 0L
                    var scrollIssued = false
                    val tap = BlockedTapGesture(dp(12).toFloat())
                    val view = View(service).apply {
                        setBackgroundColor(Color.TRANSPARENT)
                        isClickable = true
                        contentDescription = if (isEnglish) "AwayDoomscrollin': Reels blocked"
                            else "AwayDoomscrollin': Reels engellendi"
                        setOnClickListener { }
                        setOnTouchListener { target, event ->
                            when (event.actionMasked) {
                                MotionEvent.ACTION_DOWN -> {
                                    startX = event.rawX
                                    startY = event.rawY
                                    gestureStartedAt = SystemClock.uptimeMillis()
                                    scrollIssued = false
                                    tap.begin(startX, startY)
                                }
                                MotionEvent.ACTION_MOVE -> if (!scrollIssued) {
                                    tap.move(event.rawX, event.rawY)
                                    val direction = InstagramGuardGesture.verticalScrollDirection(
                                        event.rawX - startX,
                                        event.rawY - startY,
                                        dp(12).toFloat())
                                    if (direction != null) {
                                        scrollIssued = true
                                        if (onEntryGuardGesture == null) {
                                            Log.d(TAG, "IG_ENTRY_SWIPE direction=$direction")
                                            onEntryGuardVerticalSwipe(startX, startY, direction)
                                        }
                                    }
                                }
                                MotionEvent.ACTION_UP -> {
                                    val endX = event.rawX
                                    val endY = event.rawY
                                    if (scrollIssued) {
                                        onEntryGuardGesture?.invoke(
                                            startX, startY, endX, endY,
                                            (SystemClock.uptimeMillis() - gestureStartedAt)
                                                .coerceIn(80L, 1_000L))
                                    } else if (tap.finish(endX, endY)) {
                                        val held = SystemClock.uptimeMillis() - gestureStartedAt
                                        val replay = onEntryGuardGesture
                                        if (replay != null &&
                                            held >= ViewConfiguration.getLongPressTimeout() &&
                                            entryGuardReplaysLongPress()) {
                                            Log.d(TAG, "IG_ENTRY_LONG_PRESS replayed")
                                            replay(startX, startY, startX, startY,
                                                held.coerceIn(600L, 1_000L))
                                        } else {
                                            Log.d(TAG, "IG_ENTRY_TOUCH consumed")
                                            onBlockedTouch()
                                            target.performClick()
                                        }
                                    }
                                }
                                MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> {
                                    tap.cancel()
                                    scrollIssued = true
                                }
                            }
                            true
                        }
                    }
                    val manager = windowManager ?: return@forEachIndexed
                    manager.addView(view, layoutParams(bounds, "AwayDoomscrollin Instagram entry guard"))
                    entryWindows.add(view to Rect(bounds))
                }
            } catch (e: Exception) {
                // A stale region must not keep intercepting a different message/control.
                entryWindows.getOrNull(index)?.first?.visibility = View.GONE
                Log.w(TAG, "Entry guard update failed", e)
            }
        }
        // Replacement regions are ready before retiring surplus windows.
        while (entryWindows.size > targets.size) {
            val (view, _) = entryWindows.removeAt(entryWindows.lastIndex)
            view.visibility = View.GONE
            try { windowManager?.removeViewImmediate(view) } catch (e: Exception) {
                Log.w(TAG, "Entry guard removal failed", e)
            }
        }
        if (changed) Log.d(TAG, "IG_ENTRY_GUARDS requested=${targets.size} " +
            "visible=${entryWindows.count { it.first.visibility == View.VISIBLE }} " +
            "bounds=${targets.take(4).joinToString()}")
    }

    val hasVisibleWindows: Boolean
        get() = (isNotificationShadeSuspended && overlay != null) ||
            overlay?.visibility == View.VISIBLE || entryWindows.isNotEmpty() ||
            transientTouchGuard?.visibility == View.VISIBLE ||
            conversationEntryGuard?.visibility == View.VISIBLE || navigationWindows.isNotEmpty() ||
            notificationBackWindow?.first?.visibility == View.VISIBLE ||
            profileReturnWindow?.first?.visibility == View.VISIBLE ||
            profileChromeWindow?.first?.visibility == View.VISIBLE ||
            notificationLaunchWindow != null || notificationIngressWindow != null || storyEntryWindows.isNotEmpty() ||
            accountSearchWindow != null || profileIngressWindow != null ||
            searchReturnWindow?.first?.visibility == View.VISIBLE ||
            reelsTabGuardWindow?.first?.visibility == View.VISIBLE ||
            reelsSlipCover?.first?.visibility == View.VISIBLE ||
            strictProfilePieceWindows.isNotEmpty()

    /**
     * Pre-arms the DM message viewport while Instagram replaces the inbox with
     * a conversation window. This window exists before any Reel card geometry
     * is published, so a rapid second tap cannot start a gesture in Instagram.
     */
    fun showConversationEntryGuard(bounds: Rect): Boolean {
        if (bounds.width() <= 0 || bounds.height() <= 0) return false
        val existing = conversationEntryGuard
        if (existing == null) {
            var startX = 0f
            var startY = 0f
            var scrollIssued = false
            val view = View(service).apply {
                setBackgroundColor(Color.TRANSPARENT)
                isClickable = true
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                setOnClickListener { }
                setOnTouchListener { target, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            startX = event.rawX
                            startY = event.rawY
                            scrollIssued = false
                        }
                        MotionEvent.ACTION_MOVE -> if (!scrollIssued) {
                            val direction = InstagramGuardGesture.verticalScrollDirection(
                                event.rawX - startX,
                                event.rawY - startY,
                                dp(12).toFloat())
                            if (direction != null) {
                                scrollIssued = true
                                Log.d(TAG, "IG_CONVERSATION_ENTRY_SWIPE direction=$direction")
                                onEntryGuardVerticalSwipe(startX, startY, direction)
                            }
                        }
                        MotionEvent.ACTION_UP -> if (!scrollIssued) {
                            Log.d(TAG, "IG_CONVERSATION_ENTRY_TOUCH consumed")
                            target.performClick()
                        }
                        MotionEvent.ACTION_CANCEL -> scrollIssued = true
                    }
                    true
                }
            }
            return try {
                val manager = windowManager ?: return false
                manager.addView(view, layoutParams(
                    bounds, "AwayDoomscrollin Instagram conversation entry guard"))
                conversationEntryGuard = view
                conversationEntryGuardBounds = Rect(bounds)
                true
            } catch (e: Exception) {
                Log.w(TAG, "Conversation entry guard add failed", e)
                false
            }
        }
        return try {
            if (conversationEntryGuardBounds != bounds) {
                windowManager?.updateViewLayout(existing, layoutParams(
                    bounds, "AwayDoomscrollin Instagram conversation entry guard"))
                conversationEntryGuardBounds = Rect(bounds)
            }
            existing.visibility = View.VISIBLE
            true
        } catch (e: Exception) {
            Log.w(TAG, "Conversation entry guard update failed", e)
            hideConversationEntryGuard()
            false
        }
    }

    fun hideConversationEntryGuard() {
        val view = conversationEntryGuard ?: return
        conversationEntryGuard = null
        conversationEntryGuardBounds = null
        try {
            windowManager?.removeViewImmediate(view)
        } catch (e: Exception) {
            try { windowManager?.removeView(view) } catch (_: Exception) {}
        }
    }

    fun showTransientTouchGuard(bounds: Rect, isEnglish: Boolean): Boolean {
        if (bounds.width() <= 0 || bounds.height() <= 0) return false
        val existing = transientTouchGuard
        if (existing == null) {
            val view = View(service).apply {
                setBackgroundColor(Color.TRANSPARENT)
                isClickable = true
                contentDescription = if (isEnglish) "AwayDoomscrollin': protected content transition"
                    else "AwayDoomscrollin': korunan içerik geçişi"
                setOnClickListener { }
                setOnTouchListener { target, event ->
                    if (event.actionMasked == MotionEvent.ACTION_UP) target.performClick()
                    true
                }
            }
            return try {
                val manager = windowManager ?: return false
                manager.addView(view, layoutParams(bounds,
                    "AwayDoomscrollin Instagram transition touch guard"))
                transientTouchGuard = view
                transientTouchGuardBounds = Rect(bounds)
                true
            } catch (e: Exception) {
                Log.w(TAG, "Transient touch guard add failed", e)
                false
            }
        }
        return try {
            if (transientTouchGuardBounds != bounds) {
                windowManager?.updateViewLayout(existing, layoutParams(bounds,
                    "AwayDoomscrollin Instagram transition touch guard"))
                transientTouchGuardBounds = Rect(bounds)
            }
            existing.visibility = View.VISIBLE
            true
        } catch (e: Exception) {
            Log.w(TAG, "Transient touch guard update failed", e)
            hideTransientTouchGuard()
            false
        }
    }

    fun hideTransientTouchGuard() {
        val view = transientTouchGuard ?: return
        transientTouchGuard = null
        transientTouchGuardBounds = null
        try {
            windowManager?.removeViewImmediate(view)
        } catch (e: Exception) {
            try { windowManager?.removeView(view) } catch (_: Exception) {}
        }
    }

    fun visibleProfileBounds(): Rect? = currentBounds?.takeIf {
        overlay?.visibility == View.VISIBLE && currentCopyKey?.startsWith("PROFILE:") == true
    }?.let(::Rect)

    fun visibleHomeBounds(): Rect? = currentBounds?.takeIf {
        overlay?.visibility == View.VISIBLE && currentCopyKey?.startsWith("HOME_FEED:") == true
    }?.let(::Rect)

    fun visibleCurtain(): Pair<InstagramScreen, Rect>? {
        if (overlay?.visibility != View.VISIBLE) return null
        return retainedCurtain()
    }

    fun retainedCurtain(): Pair<InstagramScreen, Rect>? {
        val screenName = currentCopyKey?.substringBefore(':') ?: return null
        val screen = runCatching { InstagramScreen.valueOf(screenName) }.getOrNull() ?: return null
        val bounds = currentBounds ?: return null
        return screen to Rect(bounds)
    }

    private var messageTextView: TextView? = null
    private var detailTextView: TextView? = null

    fun show(
        bounds: Rect?,
        screen: InstagramScreen,
        isEnglish: Boolean,
        forceRelayout: Boolean = false
    ): Boolean {
        // Visible curtains belong to feeds, never to media-exit transitions.
        if (!InstagramSurfacePolicy.needsCurtain(screen)) return false
        if (bounds == null || bounds.width() <= 0 || bounds.height() <= dp(60)) {
            Log.w(TAG, "Overlay show rejected bounds: $bounds")
            if (ProtectionFlightRecorder.ENABLED) {
                ProtectionFlightRecorder.record(service, "instagram.curtain.reject",
                    "screen=$screen bounds=$bounds")
            }
            return false
        }

        val copyKey = "${screen.name}:$isEnglish"
        isNotificationShadeSuspended = false
        // The split Strict-profile lower piece belongs to the profile surface
        // only. A direct tab or destination switch to any other protected feed
        // must retire it before the new curtain's first frame, otherwise the
        // opaque lower window would outlive the profile and cover the bottom
        // navigation bar.
        if (screen != InstagramScreen.PROFILE && strictProfilePieceWindows.isNotEmpty()) {
            updateStrictProfilePieceGuards(emptyList())
        }
        if (overlay == null) restoreParkedContentWindow()
        val existing = overlay
        if (existing == null) {
            val view = createOverlay(screen, isEnglish)
            return try {
                val wm = windowManager ?: run {
                    Log.e(TAG, "WindowManager is null")
                    return false
                }
                wm.addView(view, layoutParams(bounds, touchable = true))
                overlay = view
                currentBounds = Rect(bounds)
                currentCopyKey = copyKey
                curtainWindowMounted = true
                InstagramProtectionMetrics.onCurtainShown(service)
                Log.d(TAG, "Overlay successfully shown: bounds=$bounds screen=$screen")
                if (ProtectionFlightRecorder.ENABLED) {
                    ProtectionFlightRecorder.record(service, "instagram.curtain.show",
                        "mode=add screen=$screen bounds=$bounds")
                    recordCurtainDraw(view, screen, bounds, "add")
                }
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add overlay with bounds $bounds", e)
                false
            }
        }

        val copyChanged = currentCopyKey != copyKey
        // Reels strips and thin split pieces render without the message text:
        // letterboxing a full copy into a short opaque band clips illegibly.
        shieldContent?.visibility =
            if (screen == InstagramScreen.REELS ||
                (currentBounds?.height() ?: bounds.height()) < dp(140)) View.GONE
            else View.VISIBLE
        if (copyChanged) {
            val copy = copyFor(screen, isEnglish)
            messageTextView?.text = copy.message
            detailTextView?.text = copy.detail
            existing.contentDescription = "${copy.message}. ${copy.detail}"
            currentCopyKey = copyKey
            Log.d(TAG, "Overlay copy updated: screen=$screen")
        }

        val previous = currentBounds
        val boundsChanged = previous != bounds

        if (boundsChanged || forceRelayout) {
            // Identical-bounds re-applies originate from the resume gate and
            // transition scanners. Only the first per ~one animation window
            // needs a WindowManager transaction; later repeats during the same
            // settle are duplicate writes of the unchanged LayoutParams.
            val forcedDuplicate = !boundsChanged && forceRelayout &&
                android.os.SystemClock.uptimeMillis() - lastForcedLayoutAtMs <
                    FORCED_LAYOUT_MIN_INTERVAL_MS
            if (forcedDuplicate) {
                return true
            }
            return try {
                val wm = windowManager ?: return false
                wm.updateViewLayout(existing, layoutParams(bounds, touchable = true))
                if (!boundsChanged) lastForcedLayoutAtMs = android.os.SystemClock.uptimeMillis()
                else lastForcedLayoutAtMs = 0L
                currentBounds = Rect(bounds)
                existing.visibility = View.VISIBLE
                if (!curtainWindowMounted) {
                    curtainWindowMounted = true
                    InstagramProtectionMetrics.onCurtainShown(service)
                }
                if (boundsChanged) Log.d(TAG, "Overlay layout updated: bounds=$bounds")
                if (ProtectionFlightRecorder.ENABLED) {
                    ProtectionFlightRecorder.record(service, "instagram.curtain.show",
                        "mode=layout screen=$screen bounds=$bounds changed=$boundsChanged forced=$forceRelayout")
                    recordCurtainDraw(existing, screen, bounds, "layout")
                }
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update overlay layout", e)
                hideAll()
                false
            }
        }

        existing.visibility = View.VISIBLE
        if (!curtainWindowMounted) {
            curtainWindowMounted = true
            InstagramProtectionMetrics.onCurtainShown(service)
        }
        if (ProtectionFlightRecorder.ENABLED) {
            ProtectionFlightRecorder.record(service, "instagram.curtain.show",
                "mode=reuse screen=$screen bounds=$bounds")
        }
        return true
    }

    fun hide() {
        if (service.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0 &&
            overlay != null) {
            // Record only code locations, never account names or screen text. This
            // distinguishes a slow mount from competing owners removing a curtain.
            val callers = Throwable().stackTrace.drop(1).take(3)
                .joinToString(" <- ") { "${it.methodName}:${it.lineNumber}" }
            Log.d(TAG, "IG_CURTAIN_RELEASE owner=$callers screen=${currentCopyKey?.substringBefore(':')}")
        }
        // Samsung can keep a GONE accessibility surface in the outgoing app
        // transition for several frames, so the view is never merely hidden.
        // The window is parked instead: window alpha 0 and FLAG_NOT_TOUCHABLE
        // are applied by the window manager to the surface itself, so no stale
        // curtain can flash over allowed DM, Search, Story, notification and
        // profile-sheet surfaces, while the next protected frame reuses the
        // drawn surface instead of waiting ~200 ms for a new window's first
        // frame. Entry guards are independent windows and remain mounted.
        removeContentWindow(park = true)
    }

    private var parkedOverlay: View? = null
    private var parkedCopyKey: String? = null
    private var parkedMessageTextView: TextView? = null
    private var parkedDetailTextView: TextView? = null
    private var parkedShieldContent: View? = null

    private fun restoreParkedContentWindow() {
        val view = parkedOverlay ?: return
        parkedOverlay = null
        overlay = view
        currentCopyKey = parkedCopyKey
        messageTextView = parkedMessageTextView
        detailTextView = parkedDetailTextView
        shieldContent = parkedShieldContent
        // Null bounds force the layout update that restores alpha and touch.
        currentBounds = null
        curtainWindowMounted = false
        parkedCopyKey = null
        parkedMessageTextView = null
        parkedDetailTextView = null
        parkedShieldContent = null
    }

    private fun destroyParkedContentWindow() {
        val view = parkedOverlay ?: return
        parkedOverlay = null
        parkedCopyKey = null
        parkedMessageTextView = null
        parkedDetailTextView = null
        parkedShieldContent = null
        try {
            windowManager?.removeViewImmediate(view)
        } catch (_: Exception) {
            try { windowManager?.removeView(view) } catch (_: Exception) {}
        }
    }

    private fun removeContentWindow(park: Boolean = false) {
        if (!park) destroyParkedContentWindow()
        val view = overlay ?: return
        val parkBounds = currentBounds?.let(::Rect)
        val parkCopyKey = currentCopyKey
        val parkMessage = messageTextView
        val parkDetail = detailTextView
        val parkShield = shieldContent
        if (ProtectionFlightRecorder.ENABLED) {
            ProtectionFlightRecorder.record(service, "instagram.curtain.hide",
                "screen=${currentCopyKey?.substringBefore(':') ?: "unknown"} bounds=$currentBounds")
        }
        InstagramProtectionMetrics.onCurtainHidden(service)
        overlay = null
        currentBounds = null
        currentCopyKey = null
        curtainWindowMounted = false
        lastForcedLayoutAtMs = 0L
        messageTextView = null
        detailTextView = null
        shieldContent = null
        // The split Strict-profile lower pieces only complement the primary
        // curtain; they must never outlive it.
        updateStrictProfilePieceGuards(emptyList())
        if (park && parkBounds != null && !parkBounds.isEmpty) {
            val parked = try {
                windowManager?.updateViewLayout(view,
                    layoutParams(parkBounds, touchable = false).apply { alpha = 0f })
                true
            } catch (e: Exception) {
                Log.w(TAG, "Could not park overlay", e)
                false
            }
            if (parked) {
                parkedOverlay = view
                parkedCopyKey = parkCopyKey
                parkedMessageTextView = parkMessage
                parkedDetailTextView = parkDetail
                parkedShieldContent = parkShield
                return
            }
        }
        try {
            windowManager?.removeViewImmediate(view)
            Log.d(TAG, "Overlay removed (hidden)")
        } catch (e: Exception) {
            try {
                windowManager?.removeView(view)
            } catch (_: Exception) {}
        }
    }

    private fun recordCurtainDraw(view: View, screen: InstagramScreen, bounds: Rect, source: String) {
        val drawnBounds = Rect(bounds)
        view.postOnAnimation {
            if (overlay === view && view.visibility == View.VISIBLE) {
                ProtectionFlightRecorder.record(service, "instagram.curtain.drawn",
                    "screen=$screen bounds=$drawnBounds source=$source")
            }
        }
    }

    fun hideAll() {
        isNotificationShadeSuspended = false
        removeGuardWindows()
        removeContentWindow(park = false)
    }

    private fun removeGuardWindows() {
        updateAccountSearchGuard(null, { _, _ -> }, {})
        updateProfileIngressGuard(null)
        updateStoryEntryGuards(emptyList())
        updateNotificationLaunchGuard(null)
        updateNotificationIngressGuard(null)
        updateNotificationBackGuard(null)
        updateProfileReturnGuard(null)
        updateProfileChromeGuard(null)
        updateSearchReturnGuard(null) {}
        updateReelsTabGuard(null)
        hideReelsSlipCover()
        updateStrictProfilePieceGuards(emptyList())
        // The language flag is unused when every guard window is cleared.
        updateEntryGuards(emptyList(), isEnglish = false)
        updateNavigationGuards(emptyMap())
        hideTransientTouchGuard()
        hideConversationEntryGuard()
    }

    private fun createOverlay(screen: InstagramScreen, isEnglish: Boolean): View {
        val copy = copyFor(screen, isEnglish)
        var startX = 0f
        var startY = 0f
        var swipeIssued = false
        var profileGesture = false

        val container = FrameLayout(service).apply {
            isClickable = true
            isFocusable = false
            fitsSystemWindows = false
            // Accessibility overlay windows can still receive Samsung's stable
            // navigation-bar inset even when LayoutParams fit no inset types. If it
            // reaches the view hierarchy, the bottom of a pushed profile curtain is
            // left transparent and the final media row remains visible. This window
            // is already positioned to the exact protected rectangle, so none of the
            // system insets belong inside its local coordinate space.
            setOnApplyWindowInsetsListener { _, insets ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    WindowInsets.CONSUMED
                } else {
                    @Suppress("DEPRECATION")
                    insets.consumeSystemWindowInsets()
                }
            }
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            contentDescription = "${copy.message}. ${copy.detail}"
            setBackgroundColor(Color.BLACK)
            setOnClickListener { }
            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = event.rawX
                        startY = event.rawY
                        swipeIssued = false
                        profileGesture = currentCopyKey?.startsWith("PROFILE:") == true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (profileGesture && !swipeIssued &&
                            currentCopyKey?.startsWith("PROFILE:") == true) {
                            val direction = InstagramGuardGesture.verticalScrollDirection(
                                event.rawX - startX, event.rawY - startY, dp(12).toFloat())
                            if (direction != null) {
                                swipeIssued = true
                                if (direction == InstagramGuardScrollDirection.FORWARD) onProfileSwipeUp()
                                else onProfileSwipeDown(startX, startY)
                            }
                        }
                    }
                    MotionEvent.ACTION_CANCEL -> { profileGesture = false }
                    MotionEvent.ACTION_UP -> view.performClick()
                }
                true
            }
        }

        val content = LinearLayout(service).apply {
            visibility = if (screen == InstagramScreen.REELS) View.GONE else View.VISIBLE
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(28), dp(28), dp(28), dp(28))
        }

        shieldContent = content

        val iconBadge = FrameLayout(service).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(32, 0, 242, 254)) // 12.5% Neon Cyan glow
                setStroke(dp(1), Color.argb(64, 0, 242, 254)) // 25% Neon Cyan border
            }
            val iconView = ImageView(service).apply {
                setImageDrawable(ContextCompat.getDrawable(service, R.drawable.ic_shield_check))
                setColorFilter(Color.rgb(0, 242, 254)) // Neon Cyan #00F2FE
                contentDescription = null
            }
            addView(iconView, FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER))
        }
        content.addView(iconBadge, LinearLayout.LayoutParams(dp(54), dp(54)).apply {
            bottomMargin = dp(18)
        })

        val message = textView(copy.message, 20f, Color.rgb(241, 245, 249), Typeface.BOLD)
        messageTextView = message
        content.addView(message)

        val detail = textView(copy.detail, 14f, Color.rgb(148, 163, 184), Typeface.NORMAL).apply {
            setLineSpacing(dp(3).toFloat(), 1f)
            setPadding(0, dp(10), 0, 0)
        }
        detailTextView = detail
        content.addView(detail)

        container.addView(content, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER
            marginStart = dp(24)
            marginEnd = dp(24)
        })

        return container
    }

    private fun copyFor(screen: InstagramScreen, isEnglish: Boolean): OverlayCopy {
        if (isEnglish) {
            val message = when (screen) {
                InstagramScreen.EXPLORE -> "Explore is protected"
                InstagramScreen.PROFILE -> "Profile is protected"
                InstagramScreen.DIRECT_SHARED_MEDIA -> "Shared media is protected"
                else -> "Feed is protected"
            }
            val detail = when (screen) {
                InstagramScreen.HOME_FEED -> "Home feed is paused. Messages remain available."
                InstagramScreen.EXPLORE -> "The suggested feed is paused. You can still search for accounts."
                InstagramScreen.PROFILE -> "Posts are hidden. Profile information remains available."
                InstagramScreen.DIRECT_SHARED_MEDIA -> "Shared media gallery is paused. Messages remain available."
                InstagramScreen.REELS -> ""
                InstagramScreen.STORY -> "Returning from Stories…"
                InstagramScreen.COMMENTS_OR_DETAIL -> "Returning from the post viewer…"
                else -> "This content is paused."
            }
            return OverlayCopy(message, detail)
        }

        val message = when (screen) {
            InstagramScreen.EXPLORE -> "Keşfet korunuyor"
            InstagramScreen.PROFILE -> "Profil korunuyor"
            InstagramScreen.DIRECT_SHARED_MEDIA -> "Medya galerisi korunuyor"
            else -> "Akış korunuyor"
        }
        val detail = when (screen) {
            InstagramScreen.HOME_FEED -> "Ana Sayfa akışı kapalı. Mesajlar açık kalır."
            InstagramScreen.EXPLORE -> "Önerilen akış kapalı. Hesap aramaya devam edebilirsiniz."
            InstagramScreen.PROFILE -> "Gönderiler gizli. Profil bilgileri açık kalır."
            InstagramScreen.DIRECT_SHARED_MEDIA -> "Paylaşılan medya galerisi kapalı. Mesajlar açık kalır."
            InstagramScreen.REELS -> ""
            InstagramScreen.STORY -> "Hikâyeden geri dönülüyor…"
            InstagramScreen.COMMENTS_OR_DETAIL -> "Gönderiden geri dönülüyor…"
            else -> "Bu içerik kapalı."
        }
        return OverlayCopy(message, detail)
    }

    private fun textView(text: String, sizeSp: Float, color: Int, style: Int) =
        TextView(service).apply {
            this.text = text
            textSize = sizeSp
            setTextColor(color)
            gravity = Gravity.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, style)
        }

    private fun layoutParams(
        bounds: Rect,
        windowTitle: String = "AwayDoomscrollin Instagram content shield",
        touchable: Boolean = true
    ) = WindowManager.LayoutParams(
        bounds.width(),
        bounds.height(),
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            (if (windowTitle == "account search" || windowTitle == "profile loading")
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM else 0) or
            (if (!touchable) WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE else 0),
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = bounds.left
        y = bounds.top
        title = windowTitle
        // Window moves otherwise animate independently of the immediate resize,
        // exposing content along the curtain edge during Home/Explore transitions.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            setCanPlayMoveAnimation(false)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            setFitInsetsTypes(0)
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    private fun dp(value: Int): Int =
        (value * service.resources.displayMetrics.density).toInt()

    private data class OverlayCopy(
        val message: String,
        val detail: String
    )
}
