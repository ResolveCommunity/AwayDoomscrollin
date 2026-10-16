package com.resolvecommunity.awaydoomscrollin

/** Keeps a verified Instagram bottom boundary stable during layout churn. */
internal object InstagramBottomNavigationGeometry {
    /** A visible navigation bar is authoritative; a prior curtain is not chrome. */
    fun alignToVisibleNavigation(
        minimumTop: Int,
        windowBottom: Int,
        currentBottom: Int,
        visibleNavigationTop: Int?
    ): Int = visibleNavigationTop?.takeIf { it > minimumTop && it < windowBottom }
        ?: currentBottom

    // A retained boundary protects against small transient accessibility
    // samples during a fling. It must not survive a task/Recents return where
    // Instagram publishes a wholly different navigation geometry.
    private const val MAX_RETAINED_DRIFT_PX = 240

    fun resolveTop(
        windowTop: Int,
        windowBottom: Int,
        retainedTop: Int?,
        detectedTop: Int?,
        fallbackInset: Int,
        contentBottom: Int? = null
    ): Int {
        val minimum = windowTop + (windowBottom - windowTop) / 2
        fun valid(value: Int?): Int? = value?.takeIf { it > minimum && it < windowBottom }
        val fallbackTop = (windowBottom - fallbackInset)
            .coerceIn(minimum + 1, windowBottom - 1)
        val retained = valid(retainedTop)
        val detected = valid(detectedTop)
        val content = contentBottom?.takeIf { it > minimum && it <= windowBottom }

        // Pushed profile screens do not render Instagram's main bottom-navigation
        // bar. In that state the fixed fallback inset leaves the last grid row
        // exposed. Prefer the observed app-content edge, and let it replace an
        // already-mounted boundary when that boundary came from the same fallback.
        if (detected == null && content != null &&
            (retained == null || retained == fallbackTop)) {
            return content
        }

        // Once a curtain is correctly mounted, Instagram's bottom bar is
        // static for that surface. Its small transient accessibility
        // coordinates during a fling must never resize the curtain over the
        // navigation. A large jump, however, indicates a task/Recents return
        // or a recreated layout; prefer the fresh measurement in that case.
        val retainedIsStale = retained != null && detected != null &&
            kotlin.math.abs(retained - detected) > MAX_RETAINED_DRIFT_PX
        return if (retainedIsStale) detected!! else retained
            ?: detected
            ?: content
            ?: fallbackTop
    }
}
