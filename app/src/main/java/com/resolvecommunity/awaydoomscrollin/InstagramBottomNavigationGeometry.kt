package com.resolvecommunity.awaydoomscrollin

/** Keeps a verified Instagram bottom boundary stable during layout churn. */
internal object InstagramBottomNavigationGeometry {
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
        // static for that surface. Its transient accessibility coordinates
        // during a fling must never resize the curtain over the navigation.
        return retained
            ?: detected
            ?: content
            ?: fallbackTop
    }
}
