package com.resolvecommunity.awaydoomscrollin

import org.junit.Assert.*
import org.junit.Test

class InstagramNotificationEntryPolicyTest {
    @Test fun loadingRowNeverPassesThrough() {
        assertEquals(InstagramNotificationTap.WAIT, InstagramNotificationEntryPolicy.routeTap(emptyList(), false))
        assertEquals(InstagramNotificationTap.WAIT, InstagramNotificationEntryPolicy.routeTap(listOf(" "), false))
    }
    @Test fun everyTapOnCommentNotificationIsBlocked() {
        repeat(100) {
            assertEquals(InstagramNotificationTap.BLOCK,
                InstagramNotificationEntryPolicy.routeTap(listOf("someone liked your comment: hi", "Reply"), false))
            assertEquals(InstagramNotificationTap.BLOCK,
                InstagramNotificationEntryPolicy.routeTap(listOf("someone replied to your comment: hi"), false))
        }
    }
    @Test fun onlyIndependentVerifiedProfileActionBypassesMediaBlock() {
        assertEquals(InstagramNotificationTap.PROFILE,
            InstagramNotificationEntryPolicy.routeTap(listOf("someone liked your comment"), true))
    }
    @Test fun connectionAndOtherExistingAllowedNotificationsStillOpen() {
        listOf("someone started following you", "someone replied to your note",
            "someone requested to follow you", "birisi seni takip etmeye başladı",
            "someone and 1 other are on Threads, an Instagram app. See what they're saying.")
            .forEach { assertEquals(InstagramNotificationTap.ALLOW, InstagramNotificationEntryPolicy.routeTap(listOf(it), false)) }
    }
    @Test fun reelLikeMentionAndTagNotificationsAreBlockedInStrict() {
        listOf("someone liked your reel.", "someone and 3 others liked your photo",
            "someone liked your story.", "someone mentioned you in a comment: @you look",
            "someone tagged you in a post", "someone commented: nice",
            "birisi reelini beğendi", "birisi ve diğerleri reel'ini beğendiler", "birisi gönderini beğendi",
            "birisi fotoğrafını beğendi", "birisi videonu beğendi", "birisi hikayeni beğendi",
            "birisi bir yorumda senden bahsetti: @sen bak", "birisi seni bir gönderide etiketledi",
            "birisi yorum yaptı: güzel")
            .forEach {
                assertTrue(it, InstagramNotificationEntryPolicy.isBlockedMediaSummary(it))
                assertEquals(it, InstagramNotificationTap.BLOCK,
                    InstagramNotificationEntryPolicy.routeTap(listOf(it), false))
            }
    }
    @Test fun anyRowWithMediaThumbnailIsBlockedEvenWithUnknownText() {
        assertEquals(InstagramNotificationTap.BLOCK, InstagramNotificationEntryPolicy.routeTap(
            listOf("someone did something new"), verifiedProfile = false, hasMediaThumbnail = true))
        assertEquals(InstagramNotificationTap.PROFILE, InstagramNotificationEntryPolicy.routeTap(
            listOf("someone liked your reel"), verifiedProfile = true, hasMediaThumbnail = true))
    }
    @Test fun quotedTextAfterTheSummaryDoesNotBlockConnectionRows() {
        assertFalse(InstagramNotificationEntryPolicy.isMediaActivitySummary(
            "someone started following you: liked your reel"))
    }
    @Test fun observedReplyAndTurkishReplyAreBlockedMediaEntries() {
        listOf("someone replied to your comment: @someone hello", "someone replied to your reply: hello",
            "birisi yorumuna yanıt verdi: @birisi merhaba", "birisi yorumuna cevap verdi: merhaba")
            .forEach { assertTrue(it, InstagramNotificationEntryPolicy.isBlockedMediaSummary(it)) }
    }
    @Test fun replyLikeAndConnectionActionsStayDistinct() {
        assertTrue(InstagramNotificationEntryPolicy.isBlockedMediaSummary("someone liked your comment"))
        listOf("someone replied to your note", "someone started following you",
            "someone sent a message: replied to your comment", "birisi mesaj gönderdi: yorumuna yanıt verdi")
            .forEach { assertFalse(it, InstagramNotificationEntryPolicy.isBlockedMediaSummary(it)) }
    }
    @Test fun observedEnglishCommentLikeIsBlockedWithoutReelGuessing() {
        assertTrue(InstagramNotificationEntryPolicy.isCommentLikeSummary("someone liked your comment: hello"))
        assertTrue(InstagramNotificationEntryPolicy.isCommentLikeSummary("someone and 3 others liked your comment."))
        assertTrue(InstagramNotificationEntryPolicy.isCommentLikeSummary("SOMEONE LIKED\nYOUR COMMENT"))
    }
    @Test fun turkishSingularAndPluralAreRecognized() {
        assertTrue(InstagramNotificationEntryPolicy.isCommentLikeSummary("birisi yorumunu beğendi: merhaba"))
        assertTrue(InstagramNotificationEntryPolicy.isCommentLikeSummary("birisi ve diğerleri yorumunu beğendiler."))
        assertTrue(InstagramNotificationEntryPolicy.isCommentLikeSummary("BİRİSİ YORUMUNU BEĞENDİ"))
    }
    @Test fun unrelatedNotificationsAndQuotedCommentTextRemainUnclassified() {
        listOf("someone started following you", "someone liked your photo", "someone requested to follow you",
            "someone mentioned you in a comment", "someone replied: liked your comment",
            "birisi seni takip etmeye başladı", "birisi fotoğrafını beğendi", "birisi yorum yaptı: yorumunu beğendi")
            .forEach { assertFalse(it, InstagramNotificationEntryPolicy.isCommentLikeSummary(it)) }
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
