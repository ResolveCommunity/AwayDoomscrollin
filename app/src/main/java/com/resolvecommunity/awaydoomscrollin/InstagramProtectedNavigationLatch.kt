package com.resolvecommunity.awaydoomscrollin

internal data class InstagramProtectedNavigationRequest(
    val generation: Long,
    val tab: InstagramTab,
    val sourceWindowId: Int,
    val sourceTab: InstagramTab?,
    val expiresAtMs: Long
)

/**
 * Keeps a protected main-tab request alive until its destination curtain has drawn.
 *
 * Instagram can publish another DM tree update between mounting the curtain and the
 * next frame. That update must not release the curtain and cancel the user's tab tap.
 * Generation checks also ensure that a late callback can never dispatch an older tap.
 */
internal class InstagramProtectedNavigationLatch(
    private val holdMs: Long
) {
    private var generation = 0L
    private var active: InstagramProtectedNavigationRequest? = null
    private var dispatched = false

    fun begin(
        tab: InstagramTab,
        sourceWindowId: Int,
        sourceTab: InstagramTab?,
        nowMs: Long
    ): InstagramProtectedNavigationRequest {
        val request = InstagramProtectedNavigationRequest(
            generation = ++generation,
            tab = tab,
            sourceWindowId = sourceWindowId,
            sourceTab = sourceTab,
            expiresAtMs = nowMs + holdMs
        )
        active = request
        dispatched = false
        return request
    }

    fun isDuplicate(tab: InstagramTab, nowMs: Long): Boolean {
        expire(nowMs)
        return active?.tab == tab
    }

    fun shouldHoldBeforeDispatch(nowMs: Long, currentWindowId: Int): Boolean {
        expire(nowMs)
        val request = active ?: return false
        return !dispatched && request.sourceWindowId == currentWindowId
    }

    fun isAwaitingDispatch(nowMs: Long): Boolean {
        expire(nowMs)
        return active != null && !dispatched
    }

    fun acceptsDispatch(
        request: InstagramProtectedNavigationRequest,
        nowMs: Long,
        currentWindowId: Int,
        currentTab: InstagramTab?
    ): Boolean {
        expire(nowMs)
        if (active?.generation != request.generation || dispatched ||
            request.sourceWindowId != currentWindowId) return false
        if (request.sourceTab != null && currentTab != null &&
            currentTab != request.sourceTab && currentTab != request.tab) {
            clear(request)
            return false
        }
        return true
    }

    /**
     * Whether the guarded tap must still be sent to Instagram's tab.
     *
     * The destination tab already being selected has two meanings. If the
     * tap started on another tab, Instagram handled it natively before the
     * curtain drew: tapping again would repeat it. If the tap started on the
     * same tab, the user is on a page opened on top of that tab (e.g.
     * Notifications over Home, which keeps Home selected and shows the tab
     * bar or side rail on large screens). Only the tab tap leads back there,
     * so it must be forwarded unless the user is already on the tab's root.
     */
    fun shouldForwardTap(
        request: InstagramProtectedNavigationRequest,
        currentTab: InstagramTab?,
        onTabRoot: Boolean
    ): Boolean {
        if (currentTab != request.tab) return true
        if (request.sourceTab != null && request.sourceTab != request.tab) return false
        return !onTabRoot
    }

    fun markDispatched(request: InstagramProtectedNavigationRequest): Boolean {
        if (active?.generation != request.generation || dispatched) return false
        dispatched = true
        return true
    }

    fun isPending(tab: InstagramTab, nowMs: Long): Boolean {
        expire(nowMs)
        return active?.tab == tab
    }

    fun clear(request: InstagramProtectedNavigationRequest? = null) {
        if (request != null && active?.generation != request.generation) return
        active = null
        dispatched = false
    }

    private fun expire(nowMs: Long) {
        if (active?.let { nowMs > it.expiresAtMs } == true) clear()
    }
}
