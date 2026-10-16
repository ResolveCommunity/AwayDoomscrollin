package com.resolvecommunity.awaydoomscrollin

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtectionCopyTest {
    @Test fun youtubeExplainsFeedsModesAndLongVideosInBothLanguages() {
        val en = ProtectionCopy.youtube(true)
        val tr = ProtectionCopy.youtube(false)
        listOf("Home", "Subscriptions", "Strict", "Balanced", "long videos", "comments").forEach {
            assertTrue(en.contains(it))
        }
        listOf("Ana Sayfa", "Abonelikler", "You sekmesi", "Sert", "Dengeli", "uzun videolar", "Yorumları").forEach {
            assertTrue(tr.contains(it))
        }
        assertFalse(en.contains("Shorts screen only"))
        assertFalse(tr.contains("Yalnızca Shorts"))
        assertFalse(tr.contains("Siz sekmesi"))
    }

    @Test fun instagramExplainsStoriesAndProfileGridWithoutBlockingMessages() {
        val en = ProtectionCopy.instagramScope(true)
        val tr = ProtectionCopy.instagramScope(false)
        assertTrue(en.both.contains("Messages and account search remain available"))
        assertTrue(en.balanced.contains("Profiles, profile posts, Stories and Highlights stay open"))
        assertTrue(en.strict.contains("Profile posts are covered"))
        assertTrue(tr.both.contains("Mesajlar ve hesap arama açık kalır"))
        assertTrue(tr.balanced.contains("Profiller, profil gönderileri, hikâyeler ve öne çıkanlar açıktır"))
        assertTrue(tr.strict.contains("Profil gönderileri perdelenir"))
    }

    @Test fun instagramSaysSearchGridAndSavedListsDependOnMode() {
        // The keyword-search grid is covered in both modes; Saved/Liked/Reposted
        // lists are open in Balanced and closed in Strict.
        val en = ProtectionCopy.instagramScope(true)
        val tr = ProtectionCopy.instagramScope(false)
        assertTrue(en.both.contains("covered like Explore"))
        assertTrue(en.balanced.contains("Saved, Liked and Reposted lists"))
        assertTrue(en.strict.contains("Saved, Liked and Reposted lists are closed"))
        assertTrue(tr.both.contains("Keşfet gibi perdelenir"))
        assertTrue(tr.balanced.contains("Kaydedilenler"))
        assertTrue(tr.strict.contains("Yeniden paylaşılanlar listeleri kapanır"))
    }

    @Test fun youtubeListsChannelsAndPlaylistsAsOpen() {
        assertTrue(ProtectionCopy.youtubeScope(true).both.contains("channels, playlists"))
        assertTrue(ProtectionCopy.youtubeScope(false).both.contains("kanallar, oynatma listeleri"))
    }

    @Test fun joinedTextKeepsEachModeInItsOwnParagraph() {
        val tr = ProtectionCopy.instagram(false).split("\n\n")
        assertTrue(tr.size == 3)
        assertTrue(tr[0].startsWith("İki modda da: "))
        assertTrue(tr[1].startsWith("Dengeli: "))
        assertTrue(tr[2].startsWith("Sert: "))
        assertTrue(ProtectionCopy.youtube(true).split("\n\n")[2].startsWith("Strict: "))
    }

    @Test fun tiktokDoesNotPromiseModesThatItDoesNotHave() {
        assertTrue(ProtectionCopy.tiktok(true).contains("one protection setting"))
        assertTrue(ProtectionCopy.tiktok(false).contains("tek koruma seçeneği"))
    }
}
