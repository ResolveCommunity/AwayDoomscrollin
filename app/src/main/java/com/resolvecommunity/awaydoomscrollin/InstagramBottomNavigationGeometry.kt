package com.resolvecommunity.awaydoomscrollin

/** Keeps a verified Instagram bottom-navigation boundary stable during layout churn. */
internal object InstagramBottomNavigationGeometry {
    fun resolveTop(
        windowTop: Int,
        windowBottom: Int,
        retainedTop: Int?,
        detectedTop: Int?,
        fallbackInset: Int
    ): Int {
        val minimum = windowTop + (windowBottom - windowTop) / 2
        fun valid(value: Int?): Int? = value?.takeIf { it > minimum && it < windowBottom }

        // Once a curtain is correctly mounted, Instagram's bottom bar is
        // static for that surface. Its transient accessibility coordinates
        // during a fling must never resize the curtain over the navigation.
        return valid(retainedTop)
            ?: valid(detectedTop)
            ?: (windowBottom - fallbackInset).coerceIn(minimum + 1, windowBottom - 1)
    }
}
