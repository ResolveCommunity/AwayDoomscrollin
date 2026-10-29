package com.resolvecommunity.awaydoomscrollin

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

/**
 * Shared Balanced/Strict protection for YouTube's algorithmic entry surfaces.
 *
 * The content curtain covers the viewport between YouTube's stable toolbar and bottom bar for a
 * protected feed. The transparent Shorts guard covers only the live bottom-navigation button
 * bounds, consuming the gesture before YouTube can start its tab transition. Missing or invalid
 * geometry removes an unlatched window; an active protected transition retains the last verified
 * viewport so transient YouTube trees cannot create an uncovered frame.
 */
internal class YouTubeProtectionOverlayController(
    private val service: AccessibilityService,
    private val onShortsBlocked: () -> Unit,
    private val onProtectedNavigation: (YouTubeProtectedSurface) -> Unit,
    private val onAllowedBackNavigation: () -> Unit,
    private val onAllowedYouNavigation: () -> Unit,
    private val onAllowedBackNavigationDown: () -> Unit = {},
    private val onAllowedLaunchNavigation: (YouTubeCurtainScreen) -> Unit = {},
    private val onCurtainVisibilityChanged: (Boolean) -> Unit = {}
) {
    companion object {
        private const val TAG = "YouTubeOverlay"
    }

    private val windowManager by lazy {
        service.getSystemService(WindowManager::class.java)
    }

    private var curtain: View? = null
    private var measuredCurtainVisible = false
    private fun notifyCurtainVisibility() {
        val visible = isCurtainVisible && !isSystemUiSuspended
        if (visible == measuredCurtainVisible) return
        measuredCurtainVisible = visible
        onCurtainVisibilityChanged(visible)
    }
    private var curtainBounds: Rect? = null
    private var lastStableCurtainBounds: Rect? = null
    private var lastDisplayBounds: Rect? = null
    private var cachedWindowBounds: Rect? = null
    private var curtainLanguageEnglish: Boolean? = null
    private var curtainSurface: YouTubeProtectedSurface? = null
    // The logical curtain is curtainBounds; the main window may be shortened
    // above a minimized player, with opaque fillers beside the player.
    private var miniplayerCutout: Rect? = null
    private var appliedMainCurtainBounds: Rect? = null
    private val curtainFillers = mutableListOf<NavigationGuard>()
    private var parkedCurtain: View? = null
    private var parkedCurtainBounds: Rect? = null
    private var parkedCurtainLanguageEnglish: Boolean? = null
    var isSystemUiSuspended: Boolean = false
        private set
    private var shortsGuard: View? = null
    private var shortsGuardBounds: Rect? = null
    private var lastStableShortsGuardBounds: Rect? = null
    private var allowedBackGuard: View? = null
    private var allowedBackGuardBounds: Rect? = null
    private var youNavigationGuard: NavigationGuard? = null
    private var lastStableYouNavigationBounds: Rect? = null
    private val stableProtectedNavigationBounds =
        mutableMapOf<YouTubeProtectedSurface, Rect>()
    private val protectedNavigationGuards =
        mutableMapOf<YouTubeProtectedSurface, NavigationGuard>()
    private val allowedLaunchGuards =
        mutableMapOf<YouTubeCurtainScreen, NavigationGuard>()

    private data class NavigationGuard(
        val view: View,
        var bounds: Rect
    )

    val hasVisibleWindows: Boolean
        get() = curtain?.visibility == View.VISIBLE ||
            shortsGuard?.visibility == View.VISIBLE ||
            allowedBackGuard?.visibility == View.VISIBLE ||
            youNavigationGuard != null ||
            protectedNavigationGuards.isNotEmpty() ||
            allowedLaunchGuards.isNotEmpty()

    val isCurtainVisible: Boolean
        get() = curtain?.visibility == View.VISIBLE

    fun afterCurtainDraw(action: () -> Unit) {
        val view = curtain ?: return
        val listener = object : android.view.ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnPreDrawListener(this)
                // Submit the opaque curtain frame before asking YouTube to animate its feed in.
                view.postOnAnimation {
                    if (curtain === view && view.visibility == View.VISIBLE && !isSystemUiSuspended) {
                        if (ProtectionFlightRecorder.ENABLED) {
                            ProtectionFlightRecorder.record(service, "youtube.curtain.drawn",
                                "surface=${curtainSurface ?: "unknown"} " +
                                    "bounds=${curtainBounds ?: "none"} source=before-action")
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

    fun setSystemUiSuspended(suspended: Boolean) {
        if (isSystemUiSuspended == suspended) return
        isSystemUiSuspended = suspended
        val visibility = if (suspended) View.INVISIBLE else View.VISIBLE
        curtain?.visibility = visibility
        notifyCurtainVisibility()
        listOfNotNull(shortsGuard, allowedBackGuard, youNavigationGuard?.view)
            .plus(curtainFillers.map { it.view })
            .plus(protectedNavigationGuards.values.map { it.view })
            .plus(allowedLaunchGuards.values.map { it.view })
            .forEach { it.visibility = visibility }
    }

    fun updateWindowBounds(bounds: Rect) {
        if (bounds.width() <= 0 || bounds.height() <= 0) return
        val previous = cachedWindowBounds
        if (previous != null && previous != bounds) {
            // Only a geometry-basis change (rotation or a major resize)
            // invalidates the mounted windows. Transition animations translate
            // the window by a few pixels and the rounding can shift the width
            // by one; those must not recycle every window per frame, which
            // flashed the curtain while returning from the camera or search.
            val widthDelta = kotlin.math.abs(previous.width() - bounds.width())
            val heightDelta = kotlin.math.abs(previous.height() - bounds.height())
            val majorChange = widthDelta > dp(24) || heightDelta > bounds.height() / 5
            if (majorChange) {
                if (ProtectionFlightRecorder.ENABLED) {
                    ProtectionFlightRecorder.record(service, "youtube.window.basis",
                        "from=$previous to=$bounds")
                }
                hideAll()
            }
        }
        cachedWindowBounds = Rect(bounds)
    }

    val currentCurtainSurface: YouTubeProtectedSurface?
        get() = curtainSurface

    /** The mounted curtain's rectangle, or null while none is mounted. */
    val currentCurtainBounds: Rect?
        get() = curtainBounds?.takeIf { curtain != null }?.let(::Rect)

    val hasStableCurtainBounds: Boolean
        get() = lastStableCurtainBounds != null

    fun rememberCurtainViewport(bounds: Rect?, overwriteExisting: Boolean = true) {
        val candidate = bounds?.takeIf {
            valid(it, minimumWidthDp = 80, minimumHeightDp = 80) && withinDisplay(it)
        }
        if (candidate != null && (overwriteExisting || lastStableCurtainBounds == null)) {
            lastStableCurtainBounds = Rect(candidate)
        }
    }

    fun showCurtain(
        bounds: Rect?,
        surface: YouTubeProtectedSurface?,
        isEnglish: Boolean,
        preserveBounds: Boolean = false
    ): Boolean {
        if (surface == null) {
            // Release the window immediately on verified allowed pages. Visibility/layout
            // changes can leave its last submitted opaque frame on screen until traversal.
            // Protected entry is independently gated on the next curtain's first draw.
            val wasMounted = curtain != null
            hideCurtain()
            if (ProtectionFlightRecorder.ENABLED && wasMounted) {
                ProtectionFlightRecorder.record(service, "youtube.curtain.reject",
                    "reason=allowed-surface")
            }
            return false
        }
        val display = currentDisplayBounds()
        val previousDisplay = lastDisplayBounds
        lastDisplayBounds = Rect(display)
        if (previousDisplay != null &&
            (previousDisplay.width() != display.width() || previousDisplay.height() != display.height())
        ) {
            // Rotation: every stored rectangle belongs to the old orientation and the
            // window manager can keep rendering the window at a rotated offset. Drop
            // the window once; the next verified frame lays it out again.
            if (ProtectionFlightRecorder.ENABLED) {
                ProtectionFlightRecorder.record(service, "youtube.curtain.rotate",
                    "from=${previousDisplay.width()}x${previousDisplay.height()} " +
                        "to=${display.width()}x${display.height()}")
            }
            hideCurtain(destroy = true)
        }
        val validRequestedBounds = bounds?.takeIf {
            valid(it, minimumWidthDp = 80, minimumHeightDp = 80) && withinDisplay(it)
        }?.let(::Rect)
        if (validRequestedBounds != null &&
            (!preserveBounds || lastStableCurtainBounds == null)
        ) {
            lastStableCurtainBounds = Rect(validRequestedBounds)
        }

        // YouTube publishes short-lived trees without results/toolbar geometry while changing
        // tabs. A protected-navigation latch must keep the already mounted curtain in place in
        // those frames; hiding it here defeats the latch and exposes the feed until a later tree
        // happens to contain usable bounds again. If this is the first protected frame after an
        // allowed surface, reuse the last verified viewport gathered with the navigation guards.
        val target = (if (preserveBounds) {
            curtainBounds?.let(::Rect)
                ?: lastStableCurtainBounds?.let(::Rect)
                ?: validRequestedBounds
        } else {
            validRequestedBounds
                ?: curtainBounds?.let(::Rect)
                ?: lastStableCurtainBounds?.let(::Rect)
        })?.takeIf(::withinDisplay) ?: conservativeCurtainBounds()
        val existing = curtain
        existing?.visibility = if (isSystemUiSuspended) View.INVISIBLE else View.VISIBLE
        if (existing != null) notifyCurtainVisibility()
        if (existing == null) {
            val parked = parkedCurtain?.takeIf { parkedCurtainLanguageEnglish == isEnglish }
            if (parkedCurtain != null && parked == null) destroyParkedCurtain()
            val content = parked ?: createCurtain(isEnglish)
            content.visibility = if (isSystemUiSuspended) View.INVISIBLE else View.VISIBLE
            return try {
                val manager = windowManager ?: return false
                val main = curtainSegments(target).first()
                val params = layoutParams(
                    main,
                    "AwayDoomscrollin YouTube feed shield",
                    touchable = true
                )
                if (parked != null) {
                    parkedCurtain = null
                    parkedCurtainBounds = null
                    parkedCurtainLanguageEnglish = null
                    try {
                        manager.updateViewLayout(parked, params)
                    } catch (error: Exception) {
                        remove(parked)
                        throw error
                    }
                } else {
                    manager.addView(content, params)
                }
                curtain = content
                curtainBounds = target
                appliedMainCurtainBounds = main
                syncCurtainFillers()
                curtainLanguageEnglish = isEnglish
                curtainSurface = surface
                notifyCurtainVisibility()
                updateYouNavigationGuard(
                    lastStableYouNavigationBounds,
                    active = true,
                    isEnglish = isEnglish,
                    preserveBounds = true
                )
                if (ProtectionFlightRecorder.ENABLED) {
                    val mode = if (parked != null) "unpark" else "add"
                    ProtectionFlightRecorder.record(service, "youtube.curtain.show",
                        "mode=$mode surface=$surface bounds=$target suspended=$isSystemUiSuspended")
                    recordCurtainDraw(content, surface, target, mode)
                }
                true
            } catch (error: Exception) {
                Log.w(TAG, "Could not show YouTube Home curtain", error)
                false
            }
        }

        if (curtainLanguageEnglish != isEnglish) {
            hideCurtain(destroy = true)
            return showCurtain(target, surface, isEnglish)
        }
        if (curtainSurface != surface) {
            // Home and Subscriptions intentionally share one immutable curtain. Some Samsung
            // compositors defer child redraws during YouTube's tab animation, so changing labels
            // here would briefly expose a blank or stale message even though protection remains.
            curtainSurface = surface
        }

        if (curtainBounds != target) {
            if (preserveBounds) {
                // YouTube temporarily drops the toolbar or results node while changing tabs.
                // Resizing an accessibility window to that intermediate geometry can make
                // Samsung's compositor discard its child layers for a frame.
                return true
            }
            return try {
                curtainBounds = target
                applyCurtainGeometry()
                if (ProtectionFlightRecorder.ENABLED) {
                    ProtectionFlightRecorder.record(service, "youtube.curtain.show",
                        "mode=layout surface=$surface bounds=$target")
                    recordCurtainDraw(existing, surface, target, "layout")
                }
                true
            } catch (error: Exception) {
                Log.w(TAG, "Could not update YouTube Home curtain", error)
                hideCurtain()
                false
            }
        }

        return true
    }

    fun hideCurtain() = hideCurtain(destroy = false)

    /**
     * Hiding parks the window instead of removing it: window-level alpha 0 and
     * FLAG_NOT_TOUCHABLE are applied by the window manager on the surface that
     * already holds the drawn curtain. Showing again is one layout update, not a
     * new window whose first frame took ~300 ms while the feed was visible.
     * Geometry-basis changes, language changes and leaving YouTube destroy it.
     */
    private fun hideCurtain(destroy: Boolean) {
        if (measuredCurtainVisible) {
            measuredCurtainVisible = false
            onCurtainVisibilityChanged(false)
        }
        hideYouNavigationGuard()
        if (destroy) destroyParkedCurtain()
        val view = curtain ?: return
        if (ProtectionFlightRecorder.ENABLED) {
            ProtectionFlightRecorder.record(service, "youtube.curtain.hide",
                "surface=${curtainSurface ?: "unknown"} bounds=$curtainBounds destroy=$destroy")
        }
        val parkBounds = appliedMainCurtainBounds
        val parkLanguage = curtainLanguageEnglish
        curtain = null
        curtainBounds = null
        appliedMainCurtainBounds = null
        curtainLanguageEnglish = null
        curtainSurface = null
        removeCurtainFillers()
        if (!destroy && parkBounds != null && parkLanguage != null && park(view, parkBounds)) {
            parkedCurtain = view
            parkedCurtainBounds = parkBounds
            parkedCurtainLanguageEnglish = parkLanguage
        } else remove(view)
    }

    private fun park(view: View, bounds: Rect): Boolean = try {
        windowManager?.updateViewLayout(view, layoutParams(
            bounds, "AwayDoomscrollin YouTube feed shield", touchable = false
        ).apply { alpha = 0f })
        true
    } catch (error: Exception) {
        Log.w(TAG, "Could not park YouTube curtain", error)
        false
    }

    private fun destroyParkedCurtain() {
        val parked = parkedCurtain ?: return
        parkedCurtain = null
        parkedCurtainBounds = null
        parkedCurtainLanguageEnglish = null
        remove(parked)
    }

    /** Updates the minimized-player hole; null restores a whole curtain. */
    fun updateMiniplayerCutout(bounds: Rect?) {
        val next = bounds?.takeIf(::withinDisplay)?.let(::Rect)
        if (next == miniplayerCutout) return
        miniplayerCutout = next
        if (curtain == null) return
        try {
            applyCurtainGeometry()
        } catch (error: Exception) {
            Log.w(TAG, "Could not apply YouTube miniplayer cutout", error)
            hideCurtain()
        }
    }

    private fun curtainSegments(target: Rect): List<Rect> =
        YouTubeFeedGeometry.curtainSegments(target, miniplayerCutout, tolerancePx = dp(24))

    private fun applyCurtainGeometry() {
        val view = curtain ?: return
        val target = curtainBounds ?: return
        val main = curtainSegments(target).first()
        if (main != appliedMainCurtainBounds) {
            windowManager?.updateViewLayout(view, layoutParams(
                main,
                "AwayDoomscrollin YouTube feed shield",
                touchable = true
            ))
            appliedMainCurtainBounds = main
        }
        syncCurtainFillers()
    }

    private fun syncCurtainFillers() {
        val target = curtainBounds
        val wanted = if (curtain != null && target != null) curtainSegments(target).drop(1)
        else emptyList()
        val manager = windowManager
        while (curtainFillers.size > wanted.size) remove(curtainFillers.removeAt(curtainFillers.lastIndex).view)
        wanted.forEachIndexed { index, bounds ->
            val existing = curtainFillers.getOrNull(index)
            if (existing == null) {
                val view = View(service).apply {
                    setBackgroundColor(Color.BLACK)
                    isClickable = true
                    isFocusable = false
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    setOnTouchListener { _, _ -> true }
                    visibility = if (isSystemUiSuspended) View.INVISIBLE else View.VISIBLE
                }
                manager?.addView(view, layoutParams(
                    bounds, "AwayDoomscrollin YouTube feed shield filler", touchable = true))
                curtainFillers += NavigationGuard(view, Rect(bounds))
            } else if (existing.bounds != bounds) {
                manager?.updateViewLayout(existing.view, layoutParams(
                    bounds, "AwayDoomscrollin YouTube feed shield filler", touchable = true))
                existing.bounds = Rect(bounds)
            }
        }
    }

    private fun removeCurtainFillers() {
        curtainFillers.forEach { remove(it.view) }
        curtainFillers.clear()
    }

    private fun recordCurtainDraw(
        view: View,
        surface: YouTubeProtectedSurface,
        bounds: Rect,
        source: String
    ) {
        val drawnBounds = Rect(bounds)
        view.postOnAnimation {
            if (curtain === view && view.visibility == View.VISIBLE && !isSystemUiSuspended) {
                ProtectionFlightRecorder.record(service, "youtube.curtain.drawn",
                    "surface=$surface bounds=$drawnBounds source=$source")
            }
        }
    }

    fun reapplyCurtainBounds() {
        val current = curtain ?: return
        val bounds = curtainBounds ?: return
        try {
            val main = curtainSegments(bounds).first()
            windowManager?.updateViewLayout(current, layoutParams(
                main,
                "AwayDoomscrollin YouTube feed shield",
                touchable = true
            ))
            appliedMainCurtainBounds = main
            syncCurtainFillers()
        } catch (error: Exception) {
            Log.w(TAG, "Could not reapply YouTube curtain bounds", error)
            hideCurtain()
        }
    }

    fun updateShortsGuard(
        bounds: Rect?,
        isEnglish: Boolean,
        preserveBounds: Boolean = false
    ) {
        val requested = bounds?.takeIf {
            valid(it, minimumWidthDp = 36, minimumHeightDp = 36)
        }?.let(::Rect)
        val target = if (preserveBounds) {
            shortsGuardBounds?.let(::Rect)
                ?: lastStableShortsGuardBounds?.let(::Rect)
                ?: requested
        } else requested
        if (target == null) {
            hideShortsGuard()
            return
        }
        if (!preserveBounds) lastStableShortsGuardBounds = Rect(target)
        val existing = shortsGuard
        if (existing == null) {
            val view = View(service).apply {
                // A fully transparent accessibility window can be skipped by Android's input
                // dispatcher on some builds. One alpha step is visually imperceptible but keeps
                // this exact tab-sized guard in the touch path.
                setBackgroundColor(Color.argb(1, 0, 0, 0))
                isClickable = true
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                contentDescription = if (isEnglish) {
                    "AwayDoomscrollin': Shorts feed blocked"
                } else {
                    "AwayDoomscrollin': Shorts akışı engellendi"
                }
                setOnClickListener { }
                val tap = BlockedTapGesture(dp(12).toFloat())
                setOnTouchListener { targetView, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> tap.begin(event.rawX, event.rawY)
                        MotionEvent.ACTION_MOVE -> tap.move(event.rawX, event.rawY)
                        MotionEvent.ACTION_UP -> if (tap.finish(event.rawX, event.rawY)) {
                            targetView.performClick()
                            onShortsBlocked()
                        }
                        MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> tap.cancel()
                    }
                    true
                }
            }
            try {
                val manager = windowManager ?: return
                manager.addView(view, layoutParams(
                    target,
                    "AwayDoomscrollin YouTube Shorts guard",
                    touchable = true
                ))
                shortsGuard = view
                shortsGuardBounds = target
            } catch (error: Exception) {
                Log.w(TAG, "Could not show YouTube Shorts guard", error)
            }
            return
        }

        if (shortsGuardBounds != target) {
            try {
                windowManager?.updateViewLayout(existing, layoutParams(
                    target,
                    "AwayDoomscrollin YouTube Shorts guard",
                    touchable = true
                ))
                shortsGuardBounds = target
            } catch (error: Exception) {
                Log.w(TAG, "Could not update YouTube Shorts guard", error)
                hideShortsGuard()
            }
        }
    }

    fun updateProtectedNavigationGuards(
        homeBounds: Rect?,
        subscriptionsBounds: Rect?,
        isEnglish: Boolean,
        active: Boolean = true,
        preserveBounds: Boolean = false,
        suspendedSurface: YouTubeProtectedSurface? = null
    ) {
        if (!active) {
            hideProtectedNavigationGuard(YouTubeProtectedSurface.HOME)
            hideProtectedNavigationGuard(YouTubeProtectedSurface.SUBSCRIPTIONS)
            return
        }
        if (suspendedSurface == YouTubeProtectedSurface.HOME) {
            hideProtectedNavigationGuard(YouTubeProtectedSurface.HOME)
        } else updateProtectedNavigationGuard(
            YouTubeProtectedSurface.HOME,
            homeBounds,
            isEnglish,
            preserveBounds
        )
        if (suspendedSurface == YouTubeProtectedSurface.SUBSCRIPTIONS) {
            hideProtectedNavigationGuard(YouTubeProtectedSurface.SUBSCRIPTIONS)
        } else updateProtectedNavigationGuard(
            YouTubeProtectedSurface.SUBSCRIPTIONS,
            subscriptionsBounds,
            isEnglish,
            preserveBounds
        )
    }

    fun updateYouNavigationGuard(
        bounds: Rect?,
        active: Boolean,
        isEnglish: Boolean,
        preserveBounds: Boolean = false
    ) {
        val requested = bounds?.takeIf {
            valid(it, minimumWidthDp = 36, minimumHeightDp = 36)
        }?.let(::Rect)
        if (requested != null && (!preserveBounds || lastStableYouNavigationBounds == null)) {
            lastStableYouNavigationBounds = Rect(requested)
        }
        if (!active) {
            hideYouNavigationGuard()
            return
        }
        val target = if (preserveBounds) {
            youNavigationGuard?.bounds?.let(::Rect)
                ?: lastStableYouNavigationBounds?.let(::Rect) ?: requested
        } else requested
        if (target == null) {
            hideYouNavigationGuard()
            return
        }
        val existing = youNavigationGuard
        if (existing == null) {
            val view = View(service).apply {
                setBackgroundColor(Color.argb(1, 0, 0, 0))
                isClickable = true
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                contentDescription = if (isEnglish) "Open You" else "You sekmesini aç"
                setOnClickListener { }
                setOnTouchListener { targetView, event ->
                    if (event.actionMasked == MotionEvent.ACTION_UP) {
                        targetView.performClick()
                        onAllowedYouNavigation()
                    }
                    true
                }
            }
            try {
                val manager = windowManager ?: return
                manager.addView(view, layoutParams(
                    target, "AwayDoomscrollin YouTube You guard", touchable = true
                ))
                youNavigationGuard = NavigationGuard(view, target)
            } catch (error: Exception) {
                Log.w(TAG, "Could not show YouTube You guard", error)
            }
        } else if (existing.bounds != target) {
            try {
                windowManager?.updateViewLayout(existing.view, layoutParams(
                    target, "AwayDoomscrollin YouTube You guard", touchable = true
                ))
                existing.bounds = target
            } catch (error: Exception) {
                Log.w(TAG, "Could not update YouTube You guard", error)
                hideYouNavigationGuard()
            }
        }
    }

    fun protectedNavigationBounds(surface: YouTubeProtectedSurface): Rect? =
        stableProtectedNavigationBounds[surface]?.let(::Rect)
            ?: protectedNavigationGuards[surface]?.bounds?.let(::Rect)

    fun updateAllowedBackGuard(bounds: Rect?, isEnglish: Boolean) {
        if (!valid(bounds, minimumWidthDp = 36, minimumHeightDp = 36)) {
            hideAllowedBackGuard()
            return
        }
        val target = Rect(bounds!!)
        val existing = allowedBackGuard
        if (existing == null) {
            val view = View(service).apply {
                setBackgroundColor(Color.argb(1, 0, 0, 0))
                isClickable = true
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                contentDescription = if (isEnglish) {
                    "AwayDoomscrollin': safely return to protected YouTube feed"
                } else {
                    "AwayDoomscrollin': korunan YouTube akışına güvenle dön"
                }
                setOnClickListener { }
                setOnTouchListener { targetView, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            onAllowedBackNavigationDown()
                        }
                        MotionEvent.ACTION_UP -> {
                            targetView.performClick()
                            onAllowedBackNavigation()
                        }
                    }
                    true
                }
            }
            try {
                val manager = windowManager ?: return
                manager.addView(view, layoutParams(
                    target,
                    "AwayDoomscrollin YouTube allowed-surface back guard",
                    touchable = true
                ))
                allowedBackGuard = view
                allowedBackGuardBounds = target
            } catch (error: Exception) {
                Log.w(TAG, "Could not show YouTube allowed-surface back guard", error)
            }
            return
        }

        existing.contentDescription = if (isEnglish) {
            "AwayDoomscrollin': safely return to protected YouTube feed"
        } else {
            "AwayDoomscrollin': korunan YouTube akışına güvenle dön"
        }
        if (allowedBackGuardBounds != target) {
            try {
                windowManager?.updateViewLayout(existing, layoutParams(
                    target,
                    "AwayDoomscrollin YouTube allowed-surface back guard",
                    touchable = true
                ))
                allowedBackGuardBounds = target
            } catch (error: Exception) {
                Log.w(TAG, "Could not update YouTube allowed-surface back guard", error)
                hideAllowedBackGuard()
            }
        }
    }

    /** Capture toolbar intent before native navigation, using only the current exact targets. */
    fun updateAllowedLaunchGuards(
        bounds: Map<YouTubeCurtainScreen, Rect>,
        isEnglish: Boolean
    ) {
        val targets = bounds.filter { (screen, target) ->
            (screen == YouTubeCurtainScreen.SEARCH ||
                screen == YouTubeCurtainScreen.NOTIFICATIONS) &&
                valid(target, minimumWidthDp = 36, minimumHeightDp = 36)
        }
        allowedLaunchGuards.keys.toList().filter { it !in targets }
            .forEach(::releaseAllowedLaunchGuard)
        for ((screen, targetBounds) in targets) {
            val target = Rect(targetBounds)
            val title = "AwayDoomscrollin YouTube ${screen.name.lowercase()} launch guard"
            val description = when (screen) {
                YouTubeCurtainScreen.SEARCH -> if (isEnglish) "Open Search" else "Aramayı aç"
                else -> if (isEnglish) "Open Notifications" else "Bildirimleri aç"
            }
            val existing = allowedLaunchGuards[screen]
            if (existing == null) {
                val view = View(service).apply {
                    setBackgroundColor(Color.argb(1, 0, 0, 0))
                    isClickable = true
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    contentDescription = description
                    visibility = if (isSystemUiSuspended) View.INVISIBLE else View.VISIBLE
                    setOnClickListener { }
                    setOnTouchListener { targetView, event ->
                        if (event.actionMasked == MotionEvent.ACTION_UP && !isSystemUiSuspended) {
                            targetView.performClick()
                            onAllowedLaunchNavigation(screen)
                        }
                        true
                    }
                }
                try {
                    val manager = windowManager ?: continue
                    manager.addView(view, layoutParams(target, title, touchable = true))
                    allowedLaunchGuards[screen] = NavigationGuard(view, target)
                } catch (error: Exception) {
                    Log.w(TAG, "Could not show YouTube allowed launch guard", error)
                }
            } else {
                existing.view.contentDescription = description
                if (existing.bounds != target) {
                    try {
                        windowManager?.updateViewLayout(
                            existing.view, layoutParams(target, title, touchable = true)
                        )
                        existing.bounds = target
                    } catch (error: Exception) {
                        Log.w(TAG, "Could not update YouTube allowed launch guard", error)
                        releaseAllowedLaunchGuard(screen)
                    }
                }
            }
        }
    }

    fun releaseAllowedLaunchGuard(screen: YouTubeCurtainScreen) {
        val guard = allowedLaunchGuards.remove(screen) ?: return
        remove(guard.view)
    }

    fun hideAll() {
        isSystemUiSuspended = false
        cachedWindowBounds = null
        lastStableCurtainBounds = null
        lastStableShortsGuardBounds = null
        lastStableYouNavigationBounds = null
        stableProtectedNavigationBounds.clear()
        miniplayerCutout = null
        hideCurtain(destroy = true)
        hideShortsGuard()
        hideAllowedBackGuard()
        hideYouNavigationGuard()
        protectedNavigationGuards.keys.toList().forEach(::hideProtectedNavigationGuard)
        allowedLaunchGuards.keys.toList().forEach(::releaseAllowedLaunchGuard)
    }

    fun releaseYouNavigationGuard() {
        hideYouNavigationGuard()
    }

    fun releaseProtectedNavigationGuard(surface: YouTubeProtectedSurface) {
        hideProtectedNavigationGuard(surface)
    }

    fun restoreProtectedNavigationGuard(
        surface: YouTubeProtectedSurface,
        isEnglish: Boolean
    ) {
        updateProtectedNavigationGuard(
            surface = surface,
            bounds = stableProtectedNavigationBounds[surface],
            isEnglish = isEnglish,
            preserveBounds = false
        )
    }

    fun releaseAllowedBackGuard() {
        hideAllowedBackGuard()
    }

    private fun hideShortsGuard() {
        val view = shortsGuard ?: return
        shortsGuard = null
        shortsGuardBounds = null
        remove(view)
    }

    private fun hideAllowedBackGuard() {
        val view = allowedBackGuard ?: return
        allowedBackGuard = null
        allowedBackGuardBounds = null
        remove(view)
    }

    private fun hideYouNavigationGuard() {
        val guard = youNavigationGuard ?: return
        youNavigationGuard = null
        remove(guard.view)
    }

    private fun updateProtectedNavigationGuard(
        surface: YouTubeProtectedSurface,
        bounds: Rect?,
        isEnglish: Boolean,
        preserveBounds: Boolean
    ) {
        val requested = bounds?.takeIf {
            valid(it, minimumWidthDp = 36, minimumHeightDp = 36)
        }?.let(::Rect)
        val target = if (preserveBounds) {
            protectedNavigationGuards[surface]?.bounds?.let(::Rect)
                ?: stableProtectedNavigationBounds[surface]?.let(::Rect)
                ?: requested
        } else requested
        if (target == null) {
            hideProtectedNavigationGuard(surface)
            return
        }
        if (!preserveBounds) stableProtectedNavigationBounds[surface] = Rect(target)
        val existing = protectedNavigationGuards[surface]
        val description = when {
            isEnglish && surface == YouTubeProtectedSurface.HOME ->
                "AwayDoomscrollin': open protected Home feed"
            isEnglish -> "AwayDoomscrollin': open protected Subscriptions feed"
            surface == YouTubeProtectedSurface.HOME ->
                "AwayDoomscrollin': korunan Ana Sayfa akışını aç"
            else -> "AwayDoomscrollin': korunan Abonelik akışını aç"
        }
        if (existing == null) {
            val view = View(service).apply {
                setBackgroundColor(Color.argb(1, 0, 0, 0))
                isClickable = true
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                contentDescription = description
                setOnClickListener { }
                setOnTouchListener { targetView, event ->
                    if (event.actionMasked == MotionEvent.ACTION_UP) {
                        targetView.performClick()
                        onProtectedNavigation(surface)
                    }
                    true
                }
            }
            try {
                val manager = windowManager ?: return
                manager.addView(view, layoutParams(
                    target,
                    "AwayDoomscrollin YouTube ${surface.name.lowercase()} guard",
                    touchable = true
                ))
                protectedNavigationGuards[surface] = NavigationGuard(view, target)
            } catch (error: Exception) {
                Log.w(TAG, "Could not show YouTube protected navigation guard", error)
            }
            return
        }

        existing.view.contentDescription = description
        if (existing.bounds != target) {
            try {
                windowManager?.updateViewLayout(existing.view, layoutParams(
                    target,
                    "AwayDoomscrollin YouTube ${surface.name.lowercase()} guard",
                    touchable = true
                ))
                existing.bounds = target
            } catch (error: Exception) {
                Log.w(TAG, "Could not update YouTube protected navigation guard", error)
                hideProtectedNavigationGuard(surface)
            }
        }
    }

    private fun hideProtectedNavigationGuard(surface: YouTubeProtectedSurface) {
        val guard = protectedNavigationGuards.remove(surface) ?: return
        remove(guard.view)
    }

    private fun remove(view: View) {
        try {
            windowManager?.removeViewImmediate(view)
        } catch (_: Exception) {
            try {
                windowManager?.removeView(view)
            } catch (_: Exception) {
                // The service/window may already be gone.
            }
        }
    }

    private data class CurtainCopy(val message: String, val detail: String)

    private fun curtainCopy(isEnglish: Boolean): CurtainCopy = if (isEnglish) {
        CurtainCopy(
            message = "YouTube feed paused",
            detail = "Search for a video or channel, or open the You tab."
        )
    } else {
        CurtainCopy(
            message = "YouTube akışı duraklatıldı",
            detail = "Bir video veya kanal arayabilir ya da You sekmesini açabilirsiniz."
        )
    }

    private fun createCurtain(isEnglish: Boolean): View {
        val copy = curtainCopy(isEnglish)
        return FrameLayout(service).apply {
            setBackgroundColor(Color.BLACK)
            isClickable = true
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            contentDescription = "${copy.message}. ${copy.detail}"
            setOnClickListener { }
            setOnTouchListener { view, event ->
                if (event.actionMasked == MotionEvent.ACTION_UP) view.performClick()
                true
            }
            addView(createCurtainBody(copy), FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
                marginStart = dp(24)
                marginEnd = dp(24)
            })
        }
    }

    private fun createCurtainBody(copy: CurtainCopy): View {
        val title = textView(
            copy.message,
            20f,
            Color.rgb(241, 245, 249),
            Typeface.BOLD
        )
        val detail = textView(
            copy.detail,
            14f,
            Color.rgb(148, 163, 184),
            Typeface.NORMAL
        ).apply {
            setLineSpacing(dp(3).toFloat(), 1f)
            setPadding(0, dp(10), 0, 0)
        }
        return LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(28), dp(28), dp(28), dp(28))
            val badge = FrameLayout(service).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.argb(32, 255, 0, 0))
                    setStroke(dp(1), Color.argb(80, 255, 0, 0))
                }
                addView(ImageView(service).apply {
                    setImageDrawable(ContextCompat.getDrawable(service, R.drawable.ic_shield_check))
                    setColorFilter(Color.rgb(255, 55, 55))
                    contentDescription = null
                }, FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER))
            }
            addView(badge, LinearLayout.LayoutParams(dp(54), dp(54)).apply {
                bottomMargin = dp(18)
            })
            addView(title)
            addView(detail)
        }
    }

    private fun textView(text: String, sizeSp: Float, color: Int, style: Int) =
        TextView(service).apply {
            this.text = text
            textSize = sizeSp
            setTextColor(color)
            gravity = Gravity.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, style)
        }

    private fun layoutParams(bounds: Rect, title: String, touchable: Boolean) =
        WindowManager.LayoutParams(
            bounds.width(),
            bounds.height(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                (if (!touchable) WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE else 0),
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = bounds.left
            y = bounds.top
            this.title = title
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                setFitInsetsTypes(0)
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

    private fun valid(bounds: Rect?, minimumWidthDp: Int, minimumHeightDp: Int): Boolean =
        bounds != null &&
            bounds.width() > dp(minimumWidthDp) &&
            bounds.height() > dp(minimumHeightDp)

    private fun currentDisplayBounds(): Rect {
        val manager = windowManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && manager != null) {
            return manager.currentWindowMetrics.bounds
        }
        val metrics = service.resources.displayMetrics
        return Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
    }

    /**
     * Rejects rectangles that cannot belong to the current display geometry.
     * Transitional accessibility trees during rotation can report stale or
     * transformed rectangles (negative offsets, cross-orientation sizes); a
     * window laid out with them renders as a misplaced black band.
     */
    private fun withinDisplay(bounds: Rect): Boolean {
        val display = currentDisplayBounds()
        val tolerance = dp(8)
        return bounds.width() <= display.width() + tolerance &&
            bounds.height() <= display.height() + tolerance &&
            bounds.left >= display.left - tolerance &&
            bounds.top >= display.top - tolerance &&
            bounds.right <= display.right + tolerance &&
            bounds.bottom <= display.bottom + tolerance
    }

    private fun conservativeCurtainBounds(): Rect {
        val metrics = service.resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val top = dp(80)
        val bottom = (height - dp(56)).coerceAtLeast(top + dp(100))
        return Rect(0, top, width, bottom)
    }

    private fun dp(value: Int): Int =
        (value * service.resources.displayMetrics.density).toInt()
}
