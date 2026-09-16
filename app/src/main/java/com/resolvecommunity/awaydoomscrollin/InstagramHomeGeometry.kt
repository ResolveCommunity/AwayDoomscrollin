package com.resolvecommunity.awaydoomscrollin

internal object InstagramHomeGeometry {
    fun top(windowTop: Int, storiesBottom: Int?, verifiedHeaderBottom: Int?): Int =
        storiesBottom?.takeIf { it > windowTop }
            ?: verifiedHeaderBottom?.takeIf { it > windowTop }
            ?: windowTop
}
