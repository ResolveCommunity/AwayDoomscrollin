package com.resolvecommunity.awaydoomscrollin

/** First two Direct-details tabs contain browsable media; the third is links. */
internal object InstagramDirectDetailsPolicy {
    fun shouldProtect(selectedTabIndex: Int?, previouslyProtected: Boolean): Boolean = when (selectedTabIndex) {
        0, 1 -> true
        2 -> false
        else -> previouslyProtected
    }

    fun curtainTop(pagerTop: Int, tabBottom: Int, edgeBleed: Int): Int =
        minOf(pagerTop, (tabBottom - edgeBleed.coerceAtLeast(0)).coerceAtLeast(0))

    fun selectedTabIndex(visibleTabCount: Int, selectedIndex: Int): Int? =
        selectedIndex.takeIf { visibleTabCount >= 1 && it in 0 until visibleTabCount }
}
