package com.resolvecommunity.awaydoomscrollin

/** Shared explanations keep setup and the app's protection guide in sync. */
internal object ProtectionCopy {
    /** What both modes share, then what each mode adds; setup shows them as separate blocks. */
    data class ModeScope(val both: String, val balanced: String, val strict: String)

    fun instagramScope(isEn: Boolean) = if (isEn) ModeScope(
        both = "Home and Explore are covered and the Reels tab is blocked. Messages and account search remain available; the suggested grid under keyword search results is covered like Explore.",
        balanced = "Profiles, profile posts, Stories and Highlights stay open, as do your Saved, Liked and Reposted lists. You can watch one Reel you open and use its comments; swiping to the next one takes you back.",
        strict = "Profile posts are covered. Reels (including those shared in messages), Stories and Highlights are blocked, and Saved, Liked and Reposted lists are closed."
    ) else ModeScope(
        both = "Ana Sayfa ve Keşfet perdelenir; Reels sekmesi engellenir. Mesajlar ve hesap arama açık kalır; kelimeyle aramada sonuçların altındaki önerilen içerikler de Keşfet gibi perdelenir.",
        balanced = "Profiller, profil gönderileri, hikâyeler ve öne çıkanlar açıktır; Kaydedilenler, Beğeniler ve Yeniden paylaşılanlar listeleriniz de açıktır. Açtığınız tek bir Reel'i izleyebilir ve yorumlarını kullanabilirsiniz; sonraki videoya geçtiğinizde geri dönülür.",
        strict = "Profil gönderileri perdelenir. Mesajlarda paylaşılanlar dahil Reels, hikâyeler ve öne çıkanlar engellenir; Kaydedilenler, Beğeniler ve Yeniden paylaşılanlar listeleri kapanır."
    )

    fun youtubeScope(isEn: Boolean) = if (isEn) ModeScope(
        both = "Home and Subscriptions are covered and the Shorts tab is blocked. Search results, channels, playlists, the You tab and long videos remain available.",
        balanced = "You can watch one Short opened from search, a channel or a link; swiping to the next one takes you back. Reading and scrolling comments do not count as changing videos.",
        strict = "All Shorts are blocked, including those opened from search, channels or links."
    ) else ModeScope(
        both = "Ana Sayfa ve Abonelikler akışları perdelenir; Shorts sekmesi engellenir. Arama sonuçları, kanallar, oynatma listeleri, You sekmesi ve uzun videolar açık kalır.",
        balanced = "Arama, kanal veya bağlantıdan açtığınız tek bir Short'u izleyebilirsiniz; sonraki videoya geçtiğinizde geri dönülür. Yorumları okumak ve kaydırmak video geçişi sayılmaz.",
        strict = "Arama, kanal veya bağlantıdan açılanlar dahil tüm Shorts videoları engellenir."
    )

    /** Plain-text form for dialogs and bullet lists, one paragraph per part. */
    fun joined(scope: ModeScope, isEn: Boolean): String =
        (if (isEn) "Both modes: " else "İki modda da: ") + scope.both + "\n\n" +
            (if (isEn) "Balanced: " else "Dengeli: ") + scope.balanced + "\n\n" +
            (if (isEn) "Strict: " else "Sert: ") + scope.strict

    fun instagram(isEn: Boolean) = joined(instagramScope(isEn), isEn)

    fun youtube(isEn: Boolean) = joined(youtubeScope(isEn), isEn)

    fun tiktok(isEn: Boolean) = if (isEn) {
        "Opening and browsing TikTok is allowed. The first swipe to the next video in a video feed takes you back, and on Home every top tab other than For You switches back to For You. TikTok has one protection setting, without Strict and Balanced modes."
    } else {
        "TikTok'u açmak ve gezinmek serbesttir. Bir video akışında sonraki videoya ilk kaydırmada geri dönülür; Ana sayfada Sizin İçin dışındaki her üst sekme Sizin İçin'e geri döner. TikTok'ta tek koruma ayarı vardır; Sert ve Dengeli modlar yoktur."
    }

    /** TikTok has one protection setting: what is stopped and what stays open. */
    data class SingleScope(val stopped: String, val open: String)

    fun tiktokScope(isEn: Boolean) = if (isEn) SingleScope(
        stopped = "The first swipe to the next video in a video feed takes you back, including a video opened from search or a profile. On Home, choosing any top tab other than For You (such as Following or Community) switches back to For You.",
        open = "Opening and browsing TikTok, messages, profiles, search and comments."
    ) else SingleScope(
        stopped = "Bir video akışında sonraki videoya ilk kaydırmada geri dönülür; aramadan veya profilden açılan videolar da buna dahildir. Ana sayfada Sizin İçin dışındaki bir üst sekme (Takipte, Topluluk gibi) seçilirse Sizin İçin'e geri dönülür.",
        open = "TikTok'u açmak ve gezinmek, mesajlar, profiller, arama ve yorumlar."
    )

    /**
     * One line under the mode picker, in setup and in the Apps tab: only what
     * the selected mode changes. The full scope lives in the scope dialog.
     */
    fun modeSummary(app: ProtectedApp, mode: ProtectionMode, isEn: Boolean): String = when (app) {
        ProtectedApp.INSTAGRAM -> when (mode) {
            ProtectionMode.BALANCED -> if (isEn)
                "Profiles and Stories stay open; you can watch one Reel you open."
                else "Profiller ve hikâyeler açık; açtığınız tek bir Reel'i izleyebilirsiniz."
            ProtectionMode.STRICT -> if (isEn)
                "Profile posts, Stories and all Reels are closed."
                else "Profil gönderileri, hikâyeler ve tüm Reels kapalı."
        }
        ProtectedApp.YOUTUBE -> when (mode) {
            ProtectionMode.BALANCED -> if (isEn)
                "You can watch one Short opened from search, a channel or a link."
                else "Arama, kanal veya bağlantıdan açtığınız tek bir Short'u izleyebilirsiniz."
            ProtectionMode.STRICT -> if (isEn)
                "All Shorts are closed, wherever they are opened from."
                else "Nereden açılırsa açılsın tüm Shorts kapalı."
        }
        ProtectedApp.TIKTOK -> ""
    }

    /** Applies to every app; shown once under the per-app scope. */
    fun detectionNote(isEn: Boolean) = if (isEn) {
        "Screen information is processed on your device and is never saved or sent. Detection can be delayed or affected by changes to these apps, Android or your phone's software."
    } else {
        "Ekran bilgileri cihazınızda işlenir; kaydedilmez ve gönderilmez. Bu uygulamalardaki, Android'deki veya telefon yazılımındaki değişiklikler algılamayı geciktirebilir ya da etkileyebilir."
    }
}
