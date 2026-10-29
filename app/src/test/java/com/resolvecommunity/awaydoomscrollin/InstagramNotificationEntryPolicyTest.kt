package com.resolvecommunity.awaydoomscrollin

import org.junit.Assert.*
import org.junit.Test

class InstagramNotificationEntryPolicyTest {
    @Test fun loadingRowNeverPassesThrough() {
        assertEquals(InstagramNotificationTap.WAIT, InstagramNotificationEntryPolicy.routeTap(emptyList(), false))
        assertEquals(InstagramNotificationTap.WAIT, InstagramNotificationEntryPolicy.routeTap(listOf(" "), false))
    }
    @Test fun onlyIndependentVerifiedProfileActionBypassesMediaBlock() {
        assertEquals(InstagramNotificationTap.PROFILE,
            InstagramNotificationEntryPolicy.routeTap(listOf("anything"), true, hasMediaThumbnail = true))
    }
    @Test fun anyRowWithMediaThumbnailIsBlockedInEveryLanguage() {
        listOf("someone liked your reel.", "birisi reelini beğendi", "quelqu’un a aimé votre reel",
            "кто-то отметил ваш Reels", "誰かがあなたのリールにいいねしました", "")
            .forEach {
                assertEquals(it, InstagramNotificationTap.BLOCK, InstagramNotificationEntryPolicy.routeTap(
                    listOf(it), verifiedProfile = false, hasMediaThumbnail = true))
            }
    }
    @Test fun rowTextNeverDecidesInAnyLanguage() {
        // The same row must get the same decision whatever the UI language:
        // only the thumbnail (structure) decides, never the localized summary.
        listOf("someone liked your comment", "birisi yorumunu beğendi", "quelqu’un a aimé votre commentaire",
            "кому-то понравился ваш комментарий", "someone started following you")
            .forEach {
                assertEquals(it, InstagramNotificationTap.ALLOW,
                    InstagramNotificationEntryPolicy.routeTap(listOf(it), false, hasMediaThumbnail = false))
            }
    }
    @Test fun stricterEntryScopeDoesNotChangeBalanced() {
        assertTrue(InstagramNotificationEntryPolicy.blocksCommentLikes(ProtectionMode.STRICT))
        assertFalse(InstagramNotificationEntryPolicy.blocksCommentLikes(ProtectionMode.BALANCED))
    }
    @Test fun rowAndThumbnailAreCoveredButExplicitAvatarRemainsUsable() {
        val row = InstagramGeometrySample(0, 852, 1080, 1166)
        val avatar = InstagramGeometrySample(60, 882, 228, 1050)
        val guards = InstagramNotificationEntryPolicy.guardRegions(row, avatar)
        assertFalse(guards.any { contains(it, 100, 900) })
        assertTrue(guards.any { contains(it, 500, 900) })
        assertTrue(guards.any { contains(it, 980, 960) })
        assertTrue(guards.any { contains(it, 20, 900) })
        assertTrue(guards.any { contains(it, 100, 1100) })
    }
    @Test fun groupNotificationWithoutIndependentProfileButtonHasNoMediaTapHole() {
        val row = InstagramGeometrySample(0, 396, 1080, 624)
        assertEquals(listOf(row), InstagramNotificationEntryPolicy.guardRegions(row, null))
    }
    @Test fun clippedAndEmptyRowsNeverGenerateOutOfBoundsGuards() {
        val row = InstagramGeometrySample(0, 2206, 1080, 2295)
        val regions = InstagramNotificationEntryPolicy.guardRegions(row, InstagramGeometrySample(60, 2236, 228, 2340))
        assertTrue(regions.all { it.left >= row.left && it.right <= row.right && it.top >= row.top && it.bottom <= row.bottom })
        assertTrue(InstagramNotificationEntryPolicy.guardRegions(InstagramGeometrySample(0, 100, 0, 100), null).isEmpty())
    }
    private fun contains(rect: InstagramGeometrySample, x: Int, y: Int): Boolean =
        x >= rect.left && x < rect.right && y >= rect.top && y < rect.bottom
}
