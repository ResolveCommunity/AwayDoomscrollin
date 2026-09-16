package com.resolvecommunity.awaydoomscrollin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Signal values come from real uiautomator dumps of TikTok (Turkish locale, 2026-09):
 * bottom tabs expose localized descriptions with a selected state, resource ids are
 * obfuscated ("olw", "htb"), and the video action rail uses running descriptions.
 */
class TikTokSurfacePolicyTest {

    // ---- resolveSelectedTab (bottom bar) ----

    @Test
    fun `real bottom tab labels resolve`() {
        assertEquals(TikTokSurfacePolicy.TAB_FEED, TikTokSurfacePolicy.resolveSelectedTab(null, "Ana sayfa", "Ana sayfa"))
        assertEquals(TikTokSurfacePolicy.TAB_FEED, TikTokSurfacePolicy.resolveSelectedTab(null, "Arkadaşlar", null))
        assertEquals(TikTokSurfacePolicy.TAB_INBOX, TikTokSurfacePolicy.resolveSelectedTab("olx", "Gelen kutusu", "Gelen kutusu"))
        assertEquals(TikTokSurfacePolicy.TAB_PROFILE, TikTokSurfacePolicy.resolveSelectedTab("oly", "Profil", "Profil"))
    }

    @Test
    fun `obfuscated ids and captions never resolve as bottom tabs`() {
        assertNull(TikTokSurfacePolicy.resolveSelectedTab("olw", null, null))
        assertNull(TikTokSurfacePolicy.resolveSelectedTab(null, "amazing video #funny", null))
        assertNull(TikTokSurfacePolicy.resolveSelectedTab(null, null, null))
    }

    // ---- resolveSelectedTopTab (feed header) ----

    @Test
    fun `Takipte and Topluluk top tabs are blocked`() {
        assertEquals(TikTokSurfacePolicy.TOP_TAB_BLOCKED, TikTokSurfacePolicy.resolveSelectedTopTab("Takipte", "Takipte"))
        assertEquals(TikTokSurfacePolicy.TOP_TAB_BLOCKED, TikTokSurfacePolicy.resolveSelectedTopTab("Topluluk", "Topluluk"))
        assertEquals(TikTokSurfacePolicy.TOP_TAB_BLOCKED, TikTokSurfacePolicy.resolveSelectedTopTab("Following", null))
        assertEquals(TikTokSurfacePolicy.TOP_TAB_BLOCKED, TikTokSurfacePolicy.resolveSelectedTopTab("Community", null))
    }

    @Test
    fun `Sizin Icin and For You top tabs are home`() {
        assertEquals(TikTokSurfacePolicy.TOP_TAB_HOME, TikTokSurfacePolicy.resolveSelectedTopTab("Sizin İçin", "Sizin İçin"))
        assertEquals(TikTokSurfacePolicy.TOP_TAB_HOME, TikTokSurfacePolicy.resolveSelectedTopTab(null, "For You"))
    }

    @Test
    fun `unknown selected header text is not a top tab`() {
        assertNull(TikTokSurfacePolicy.resolveSelectedTopTab("LIVE", null))
        assertNull(TikTokSurfacePolicy.resolveSelectedTopTab("Rabia Tunçbilek", null))
    }

    @Test
    fun `top tab label helpers work on raw labels`() {
        assertTrue(TikTokSurfacePolicy.isBlockedTopTabLabel("Takipte"))
        assertTrue(TikTokSurfacePolicy.isBlockedTopTabLabel("Topluluk"))
        assertFalse(TikTokSurfacePolicy.isBlockedTopTabLabel("Sizin İçin"))
        assertTrue(TikTokSurfacePolicy.isHomeTopTabLabel("Sizin İçin"))
        assertFalse(TikTokSurfacePolicy.isHomeTopTabLabel("Takipte"))
        assertFalse(TikTokSurfacePolicy.isHomeTopTabLabel(null))
    }

    // ---- classifyComposer ----

    @Test
    fun `real watch page comment bar classifies as comment composer`() {
        assertEquals(
            TikTokComposer.COMMENT,
            TikTokSurfacePolicy.classifyComposer("Yorum ekleyin...", null, null, "ejc")
        )
        assertEquals(
            TikTokComposer.COMMENT,
            TikTokSurfacePolicy.classifyComposer("Add a comment...", null, null, null)
        )
    }

    @Test
    fun `message and search fields classify`() {
        assertEquals(
            TikTokComposer.MESSAGE,
            TikTokSurfacePolicy.classifyComposer("Mesaj gönder", null, null, null)
        )
        assertEquals(
            TikTokComposer.SEARCH,
            TikTokSurfacePolicy.classifyComposer("Ara", null, null, "htb")
        )
        assertEquals(
            TikTokComposer.SEARCH,
            TikTokSurfacePolicy.classifyComposer(null, null, null, "et_search")
        )
        assertEquals(
            TikTokComposer.NONE,
            TikTokSurfacePolicy.classifyComposer(null, "ahmet", null, "htb")
        )
    }

    // ---- railActionCategory (real running descriptions) ----

    @Test
    fun `real rail descriptions map to categories`() {
        assertEquals("like", TikTokSurfacePolicy.railActionCategory("Video beğenin. 55,2 B beğeni"))
        assertEquals("like", TikTokSurfacePolicy.railActionCategory("Beğen"))
        assertEquals("comment", TikTokSurfacePolicy.railActionCategory("Yorum okuyun veya ekleyin. 402 yorum"))
        assertEquals("share", TikTokSurfacePolicy.railActionCategory("Videoyu paylaş. 3.255 paylaşım"))
        assertEquals(
            "favorite",
            TikTokSurfacePolicy.railActionCategory("Bu videoyu Favorilere ekleyin veya Favorilerden çıkarın. 5.078")
        )
    }

    @Test
    fun `non rail descriptions stay null`() {
        assertNull(TikTokSurfacePolicy.railActionCategory("Ses: ariigilmore - orijinal ses, ari"))
        assertNull(TikTokSurfacePolicy.railActionCategory("blmyoru adlı kullanıcının videosu, #keşfet"))
        assertNull(TikTokSurfacePolicy.railActionCategory("Ara"))
        assertNull(TikTokSurfacePolicy.railActionCategory("\$ek3r2.0 Takip Edin"))
        assertNull(TikTokSurfacePolicy.railActionCategory(null))
    }

    // ---- classify ----

    private fun signals(
        selectedTab: String? = null,
        selectedTopTab: String? = null,
        composer: TikTokComposer = TikTokComposer.NONE,
        hasProfileSignals: Boolean = false,
        hasFeedPager: Boolean = false,
        railCategories: Set<String> = emptySet()
    ) = TikTokUiSignals(
        selectedTab,
        selectedTopTab,
        composer,
        hasProfileSignals,
        hasFeedPager,
        railCategories
    )

    // ---- isProfileLabel (real profile header strings) ----

    @Test
    fun `follower count lines identify profile surfaces`() {
        assertTrue(TikTokSurfacePolicy.isProfileLabel("0 takip ediliyor · 1.5M takipçi"))
        assertTrue(TikTokSurfacePolicy.isProfileLabel("1,3 B takipçi · 256,4 B Beğeni"))
        assertTrue(TikTokSurfacePolicy.isProfileLabel("500 Followers"))
        assertFalse(TikTokSurfacePolicy.isProfileLabel("Takip Et"))
        assertFalse(TikTokSurfacePolicy.isProfileLabel("Mesaj..."))
        assertFalse(TikTokSurfacePolicy.isProfileLabel("amazing video #funny"))
        assertFalse(TikTokSurfacePolicy.isProfileLabel(null))
    }

    @Test
    fun `profile surfaces stay safe even when feed signals bleed behind them`() {
        assertEquals(
            TikTokSurface.SAFE,
            TikTokSurfacePolicy.classify(
                signals(
                    selectedTab = TikTokSurfacePolicy.TAB_FEED,
                    hasProfileSignals = true,
                    railCategories = setOf("like", "comment", "share", "favorite")
                )
            )
        )
        assertEquals(
            TikTokSurface.SAFE,
            TikTokSurfacePolicy.classify(signals(hasProfileSignals = true))
        )
    }

    @Test
    fun `main feed via selected bottom tab`() {
        assertEquals(
            TikTokSurface.FEED,
            TikTokSurfacePolicy.classify(signals(selectedTab = TikTokSurfacePolicy.TAB_FEED))
        )
    }

    @Test
    fun `inbox profile and shop tabs are safe`() {
        assertEquals(TikTokSurface.SAFE, TikTokSurfacePolicy.classify(signals(selectedTab = TikTokSurfacePolicy.TAB_INBOX)))
        assertEquals(TikTokSurface.SAFE, TikTokSurfacePolicy.classify(signals(selectedTab = TikTokSurfacePolicy.TAB_PROFILE)))
        assertEquals(TikTokSurface.SAFE, TikTokSurfacePolicy.classify(signals(selectedTab = TikTokSurfacePolicy.TAB_SHOP)))
    }

    @Test
    fun `search and message composers are safe`() {
        assertEquals(TikTokSurface.SAFE, TikTokSurfacePolicy.classify(signals(composer = TikTokComposer.SEARCH)))
        assertEquals(TikTokSurface.SAFE, TikTokSurfacePolicy.classify(signals(composer = TikTokComposer.MESSAGE)))
    }

    @Test
    fun `watch page with full rail is feed despite the persistent comment bar`() {
        assertEquals(
            TikTokSurface.FEED,
            TikTokSurfacePolicy.classify(
                signals(
                    composer = TikTokComposer.COMMENT,
                    railCategories = setOf("like", "comment", "share", "favorite")
                )
            )
        )
    }

    @Test
    fun `a lone comment composer without a strong rail is safe`() {
        assertEquals(
            TikTokSurface.SAFE,
            TikTokSurfacePolicy.classify(
                signals(composer = TikTokComposer.COMMENT, railCategories = setOf("like"))
            )
        )
    }

    @Test
    fun `selected blocked top tab is feed`() {
        assertEquals(
            TikTokSurface.FEED,
            TikTokSurfacePolicy.classify(signals(selectedTopTab = TikTokSurfacePolicy.TOP_TAB_BLOCKED))
        )
    }

    @Test
    fun `selected home top tab alone stays unknown`() {
        assertEquals(
            TikTokSurface.UNKNOWN,
            TikTokSurfacePolicy.classify(signals(selectedTopTab = TikTokSurfacePolicy.TOP_TAB_HOME))
        )
    }

    @Test
    fun `weaker rail evidence still identifies the feed`() {
        assertEquals(
            TikTokSurface.FEED,
            TikTokSurfacePolicy.classify(signals(railCategories = setOf("like", "comment")))
        )
        assertEquals(
            TikTokSurface.FEED,
            TikTokSurfacePolicy.classify(signals(hasFeedPager = true, railCategories = setOf("share")))
        )
    }

    @Test
    fun `a bare pager id or single rail marker is not the feed`() {
        assertEquals(TikTokSurface.UNKNOWN, TikTokSurfacePolicy.classify(signals(hasFeedPager = true)))
        assertEquals(TikTokSurface.UNKNOWN, TikTokSurfacePolicy.classify(signals(railCategories = setOf("like"))))
    }
}
