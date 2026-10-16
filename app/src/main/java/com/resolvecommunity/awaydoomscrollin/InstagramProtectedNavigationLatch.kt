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
