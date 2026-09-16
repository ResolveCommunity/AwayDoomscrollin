package com.resolvecommunity.awaydoomscrollin

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.os.Build
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
        { _, _, _ -> }
) {
    companion object {
        private const val TAG = "InstagramOverlay"
    }

    private val windowManager by lazy {
        service.getSystemService(WindowManager::class.java)
    }
    private var overlay: View? = null
    private var currentBounds: Rect? = null
    private var currentCopyKey: String? = null
    private var shieldContent: View? = null
    private val entryWindows = mutableListOf<Pair<View, Rect>>()
    private var transientTouchGuard: View? = null
    private var transientTouchGuardBounds: Rect? = null
    private var conversationEntryGuard: View? = null
    private var conversationEntryGuardBounds: Rect? = null
    private val navigationWindows = mutableMapOf<InstagramTab, Pair<View, Rect>>()

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
                    var scrollIssued = false
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
                                    scrollIssued = false
                                }
                                MotionEvent.ACTION_MOVE -> if (!scrollIssued) {
                                    val direction = InstagramGuardGesture.verticalScrollDirection(
                                        event.rawX - startX,
                                        event.rawY - startY,
                                        dp(12).toFloat())
                                    if (direction != null) {
                                        scrollIssued = true
                                        Log.d(TAG, "IG_ENTRY_SWIPE direction=$direction")
                                        onEntryGuardVerticalSwipe(startX, startY, direction)
                                    }
                                }
                                MotionEvent.ACTION_UP -> {
                                    if (!scrollIssued) {
                                        Log.d(TAG, "IG_ENTRY_TOUCH consumed")
                                        onBlockedTouch()
                                        target.performClick()
                                    }
                                }
                                MotionEvent.ACTION_CANCEL -> scrollIssued = true
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
        get() = overlay?.visibility == View.VISIBLE || entryWindows.isNotEmpty() ||
            transientTouchGuard?.visibility == View.VISIBLE ||
            conversationEntryGuard?.visibility == View.VISIBLE || navigationWindows.isNotEmpty()

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
            return false
        }

        val copyKey = "${screen.name}:$isEnglish"
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
                InstagramProtectionMetrics.onCurtainShown(service)
                Log.d(TAG, "Overlay successfully shown: bounds=$bounds screen=$screen")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add overlay with bounds $bounds", e)
                false
            }
        }

        val copyChanged = currentCopyKey != copyKey
        shieldContent?.visibility = if (screen == InstagramScreen.REELS) View.GONE else View.VISIBLE
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
            return try {
                val wm = windowManager ?: return false
                wm.updateViewLayout(existing, layoutParams(bounds, touchable = true))
                currentBounds = Rect(bounds)
                existing.visibility = View.VISIBLE
                InstagramProtectionMetrics.onCurtainShown(service)
                if (boundsChanged) Log.d(TAG, "Overlay layout updated: bounds=$bounds")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update overlay layout", e)
                hideAll()
                false
            }
        }

        existing.visibility = View.VISIBLE
        InstagramProtectionMetrics.onCurtainShown(service)
        return true
    }

    fun hide() {
        // Samsung can keep a GONE accessibility surface in the outgoing app
        // transition for several frames. Removing the curtain window avoids a
        // stale Home/Profile curtain flashing over allowed DM, Search, Story,
        // notification and profile-sheet surfaces. Entry guards are independent
        // windows and intentionally remain mounted.
        removeContentWindow()
    }

    private fun removeContentWindow() {
        val view = overlay ?: return
        InstagramProtectionMetrics.onCurtainHidden(service)
        overlay = null
        currentBounds = null
        currentCopyKey = null
        messageTextView = null
        detailTextView = null
        shieldContent = null
        try {
            windowManager?.removeViewImmediate(view)
            Log.d(TAG, "Overlay removed (hidden)")
        } catch (e: Exception) {
            try {
                windowManager?.removeView(view)
            } catch (_: Exception) {}
        }
    }

    fun hideAll() {
        // The language flag is unused when every guard window is cleared.
        updateEntryGuards(emptyList(), isEnglish = false)
        updateNavigationGuards(emptyMap())
        hideTransientTouchGuard()
        hideConversationEntryGuard()
        removeContentWindow()
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
                        if (!profileGesture) onBlockedTouch()
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (profileGesture && !swipeIssued &&
                            currentCopyKey?.startsWith("PROFILE:") == true &&
                            InstagramProfileSwipe.isUp(event.rawX - startX, event.rawY - startY, dp(12).toFloat())) {
                            swipeIssued = true
                            onProfileSwipeUp()
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

        content.addView(ImageView(service).apply {
            setImageDrawable(ContextCompat.getDrawable(service, R.drawable.ic_shield_check))
            setColorFilter(Color.WHITE)
            contentDescription = null
        }, LinearLayout.LayoutParams(dp(44), dp(44)).apply {
            bottomMargin = dp(20)
        })

        val message = textView(copy.message, 21f, Color.WHITE, Typeface.BOLD)
        messageTextView = message
        content.addView(message)

        val detail = textView(copy.detail, 14f, Color.rgb(180, 180, 180), Typeface.NORMAL).apply {
            setPadding(0, dp(12), 0, 0)
        }
        detailTextView = detail
        content.addView(detail)

        container.addView(content, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER
            marginStart = dp(22)
            marginEnd = dp(22)
        })

        return container
    }

    private fun copyFor(screen: InstagramScreen, isEnglish: Boolean): OverlayCopy {
        if (isEnglish) {
            val detail = when (screen) {
                InstagramScreen.HOME_FEED -> "Home feed is unavailable. Stories and Messages remain available."
                InstagramScreen.EXPLORE -> "Explore is unavailable. Search remains available."
                InstagramScreen.PROFILE -> "Posts are unavailable. Profile information and Stories remain available."
                InstagramScreen.DIRECT_SHARED_MEDIA -> "The shared media gallery is unavailable. Messages remain available."
                InstagramScreen.REELS -> ""
                InstagramScreen.STORY -> "Returning from Stories…"
                InstagramScreen.COMMENTS_OR_DETAIL -> "Returning from the post viewer…"
                else -> "This content is unavailable."
            }
            return OverlayCopy("Blocked by AwayDoomscrollin'", detail)
        }

        val detail = when (screen) {
            InstagramScreen.HOME_FEED -> "Ana akış kapalıdır. hikâyeler ve Direkt Mesajlar kullanılabilir."
            InstagramScreen.EXPLORE -> "Keşfet kapalıdır. Arama kullanılabilir."
            InstagramScreen.PROFILE -> "Gönderiler kapalıdır. Profil bilgileri ve hikâyeler kullanılabilir."
            InstagramScreen.DIRECT_SHARED_MEDIA -> "Paylaşılan medya galerisi kapalıdır. Direkt Mesajlar kullanılabilir."
            InstagramScreen.REELS -> ""
            InstagramScreen.STORY -> "Hikâyeden geri dönülüyor…"
            InstagramScreen.COMMENTS_OR_DETAIL -> "Gönderiden geri dönülüyor…"
            else -> "Bu içerik kullanılamaz."
        }
        return OverlayCopy("AwayDoomscrollin' tarafından engellendi", detail)
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
            (if (!touchable) WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE else 0),
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = bounds.left
        y = bounds.top
        title = windowTitle
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
