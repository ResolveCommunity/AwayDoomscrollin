package com.resolvecommunity.awaydoomscrollin

/** Shared explanations keep setup and the app's protection guide in sync. */
internal object ProtectionCopy {
    /** What both modes share, then what each mode adds; setup shows them as separate blocks. */
    data class ModeScope(val both: String, val balanced: String, val strict: String)

    fun instagramScope(isEn: Boolean) = if (isEn) ModeScope(
        both = "Home and Explore are covered and the Reels feed tab is blocked. Messages and account search remain available; the suggested grid under keyword search results is covered like Explore.",
        balanced = "Profiles, profile posts, Stories and Highlights stay open, as do your Saved, Liked and Reposted lists. You can watch one selected Reel and use its comments; moving to another video closes the viewer.",
        strict = "Profile posts are covered. Reels (including those shared in messages), Stories and Highlights are blocked, and Saved, Liked and Reposted lists are closed."
    ) else ModeScope(
        both = "Ana Sayfa ve Keşfet perdelenir; Reels akış sekmesi engellenir. Mesajlar ve hesap arama açık kalır; kelime aramasının altındaki önerilen içerik ızgarası Keşfet gibi perdelenir.",
        balanced = "Profiller, profil gönderileri, hikâyeler ve öne çıkanlar açıktır; Kaydedilenler, Beğeniler ve Yeniden paylaşılanlar listeleriniz de açıktır. Seçtiğiniz tek bir Reel'i izleyebilir ve yorumlarını kullanabilirsiniz; başka videoya geçtiğinizde video ekranından geri dönülür.",
        strict = "Profil gönderileri perdelenir. Mesajlarda paylaşılanlar dahil Reels, hikâyeler ve öne çıkanlar engellenir; Kaydedilenler, Beğeniler ve Yeniden paylaşılanlar listeleri kapanır."
    )

    fun youtubeScope(isEn: Boolean) = if (isEn) ModeScope(
        both = "Home and Subscriptions are covered and the Shorts feed tab is blocked. Search results, channels, playlists, the You tab and long videos remain available.",
        balanced = "You can watch one specific Short opened from search, a channel or a link; moving to another video closes the viewer. Reading and scrolling comments do not count as changing videos.",
        strict = "All Shorts are blocked, including those opened from search, channels or links."
    ) else ModeScope(
        both = "Ana Sayfa ve Abonelikler akışları perdelenir; Shorts akış sekmesi engellenir. Arama sonuçları, kanallar, oynatma listeleri, You sekmesi ve uzun videolar açık kalır.",
        balanced = "Arama, kanal veya bağlantıdan açtığınız tek bir Short'u izleyebilirsiniz; başka videoya geçtiğinizde video ekranından geri dönülür. Yorumları okumak ve kaydırmak video geçişi sayılmaz.",
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
        "Protection interrupts scrolling through TikTok's video feeds rather than blocking the entire app. TikTok currently uses one protection setting; Strict and Balanced modes are available for Instagram and YouTube."
    } else {
        "Koruma, TikTok'u tamamen kapatmak yerine video akışında kaydırmayı durdurur. TikTok'ta şu an tek koruma seçeneği vardır; Sert ve Dengeli modlar Instagram ve YouTube için kullanılabilir."
    }

    fun overview(isEn: Boolean) = if (isEn) {
        "Instagram and YouTube use curtains to hide protected feeds. In both apps, Strict blocks short videos; Balanced allows one selected video but interrupts the move to the next. Instagram Stories and Highlights are available only in Balanced. Messaging, search and YouTube long videos remain available. TikTok uses a separate feed-scrolling protection. Detection can be delayed or affected by changes to other apps, Android or your device's software."
    } else {
        "Instagram ve YouTube'da korunan akışlar perdeyle gizlenir. İkisinde de Sert mod kısa videoları engeller; Dengeli mod seçilen tek videoya izin verir, sonraki videoya geçişi durdurur. Instagram hikâyeleri ve öne çıkanları yalnızca Dengeli modda açıktır. Mesajlaşma, arama ve YouTube'daki uzun videolar kullanılabilir. TikTok'ta ayrı bir akış kaydırma koruması vardır. Diğer uygulamalardaki, Android'deki veya telefon yazılımındaki değişiklikler algılamayı geciktirebilir ya da etkileyebilir."
    }
}
