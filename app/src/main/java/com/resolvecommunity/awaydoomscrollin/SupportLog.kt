package com.resolvecommunity.awaydoomscrollin

import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Events kept for the user-shared support log. Each carries only protection
 * decisions, screen kinds and numbers; never on-screen text or names.
 */
internal enum class SupportEvent(val code: String) {
    SERVICE_CONNECTED("svc.on"),
    SERVICE_INTERRUPTED("svc.int"),
    SERVICE_UNBOUND("svc.unbind"),
    SERVICE_DESTROYED("svc.off"),
    SERVICE_PREVIOUS_EXIT("svc.prev"),
    SETTINGS_CHANGED("settings"),
    DISPLAY_CHANGED("display"),
    RECENTS_OPENED("recents"),
    RECENTS_CLOSED("recents.off"),
    INSTAGRAM_SCREEN("ig.screen"),
    INSTAGRAM_CURTAIN_SHOWN("ig.curtain"),
    INSTAGRAM_CURTAIN_HIDDEN("ig.curtain.off"),
    INSTAGRAM_RESUME_CURTAIN("ig.resume"),
    INSTAGRAM_REEL_ALLOWED("ig.reel.ok"),
    INSTAGRAM_REEL_DENIED("ig.reel.no"),
    INSTAGRAM_REEL_NEXT_VIDEO("ig.reel.next"),
    INSTAGRAM_VIEWER_EXIT("ig.exit"),
    YOUTUBE_CURTAIN_SHOWN("yt.curtain"),
    YOUTUBE_CURTAIN_HIDDEN("yt.curtain.off"),
    YOUTUBE_SHORTS("yt.shorts"),
    TIKTOK("tt"),
    SLOW("slow"),
    /** A curtain made room for a window it must not cover (unknown page, pop-up, other app). */
    STEPPED_ASIDE("aside");

    companion object {
        fun fromCode(code: String): SupportEvent? = entries.firstOrNull { it.code == code }
    }
}

/**
 * One stored line: `time|code|key=value;key=value`. Only protection outcomes
 * are stored (screen kinds, decisions, numbers), never what the accessibility
 * service read from the screen: no text, labels or view identifiers. Values
 * are reduced to a small ASCII alphabet, so free text cannot be stored even
 * if a caller passed it by mistake.
 */
internal data class SupportLogEntry(
    val timeMs: Long,
    val event: SupportEvent,
    val fields: Map<String, String> = emptyMap()
) {
    fun encode(): String = buildString {
        append(timeMs)
        append('|')
        append(event.code)
        append('|')
        append(fields.entries.joinToString(";") { (key, value) -> "${cleanKey(key)}=${cleanValue(value)}" })
    }

    companion object {
        private val valuePattern = Regex("[^A-Za-z0-9_.,:-]")

        fun cleanKey(key: String): String = key.replace(Regex("[^a-z]"), "").take(16)

        fun cleanValue(value: String): String = value.replace(valuePattern, "").take(40)

        fun of(timeMs: Long, event: SupportEvent, fields: Map<String, Any?> = emptyMap()): SupportLogEntry =
            SupportLogEntry(
                timeMs, event,
                fields.entries.filter { it.value != null }
                    .associate { (key, value) -> cleanKey(key) to cleanValue(value.toString()) }
                    .filterKeys { it.isNotEmpty() })

        fun decode(line: String): SupportLogEntry? {
            // Earlier debug builds wrote a fourth part; it is ignored.
            val parts = line.split('|')
            if (parts.size !in 3..4) return null
            val time = parts[0].toLongOrNull() ?: return null
            val event = SupportEvent.fromCode(parts[1]) ?: return null
            val fields = parts[2].split(';').filter { '=' in it }.associate {
                cleanKey(it.substringBefore('=')) to cleanValue(it.substringAfter('='))
            }
            return SupportLogEntry(time, event, fields)
        }
    }
}

/** Last 24 hours, bounded in size; the oldest lines go first. */
internal class SupportLogBuffer(
    private val maxChars: Int = MAX_CHARS,
    private val maxAgeMs: Long = MAX_AGE_MS
) {
    private val lines = ArrayDeque<Pair<Long, String>>()
    private var chars = 0

    val size: Int get() = lines.size

    fun load(text: String, now: Long) {
        clear()
        text.lineSequence().mapNotNull(SupportLogEntry::decode).forEach { add(it, now) }
    }

    fun add(entry: SupportLogEntry, now: Long) {
        val line = entry.encode()
        lines.addLast(entry.timeMs to line)
        chars += line.length + 1
        prune(now)
    }

    fun prune(now: Long) {
        while (lines.isNotEmpty() &&
            (chars > maxChars || now - lines.peekFirst()!!.first > maxAgeMs)) {
            chars -= lines.removeFirst().second.length + 1
        }
    }

    fun text(): String = lines.joinToString("") { it.second + "\n" }

    fun entries(now: Long): List<SupportLogEntry> {
        prune(now)
        return lines.mapNotNull { SupportLogEntry.decode(it.second) }
    }

    fun clear() {
        lines.clear()
        chars = 0
    }

    companion object {
        const val MAX_CHARS = 120 * 1024
        const val MAX_AGE_MS = 24L * 60 * 60 * 1000
    }
}

/** Turns stored entries into sentences a user can read before sending them. */
internal object SupportLogFormatter {
    fun render(
        entries: List<SupportLogEntry>,
        english: Boolean,
        header: List<String>,
        timeZone: TimeZone = TimeZone.getDefault()
    ): String {
        val clock = SimpleDateFormat("dd.MM HH:mm:ss", Locale.US).apply { this.timeZone = timeZone }
        return buildString {
            header.forEach { appendLine(it) }
            appendLine()
            if (entries.isEmpty()) {
                appendLine(if (english) "Nothing was recorded in the last 24 hours."
                    else "Son 24 saatte kaydedilmiş bir olay yok.")
            }
            entries.forEach { entry ->
                append(clock.format(Date(entry.timeMs)))
                append("  ")
                appendLine(sentence(entry, english))
            }
        }.trimEnd() + "\n"
    }

    fun sentence(entry: SupportLogEntry, en: Boolean): String {
        val f = entry.fields
        fun screen(key: String) = instagramScreen(f[key], en)
        fun previous(extra: String = ""): String {
            if (f["from"] == null) return ""
            return (if (en) " (previous screen: " else " (önceki ekran: ") + screen("from") + extra + ")"
        }
        return when (entry.event) {
            SupportEvent.SERVICE_CONNECTED ->
                if (en) "Protection started" else "Koruma başladı"
            SupportEvent.SERVICE_INTERRUPTED ->
                if (en) "Android briefly paused protection" else "Android korumayı kısa süreliğine durdurdu"
            SupportEvent.SERVICE_UNBOUND ->
                if (en) "Accessibility permission was turned off or protection lost its connection"
                else "Erişilebilirlik izni kapatıldı ya da korumanın bağlantısı koptu"
            SupportEvent.SERVICE_DESTROYED ->
                if (en) "Protection stopped" else "Koruma durdu"
            SupportEvent.SERVICE_PREVIOUS_EXIT ->
                (if (en) "Before this, protection was stopped at ${f["at"]}: " else "Bundan önce koruma ${f["at"]} saatinde durmuştu: ") +
                    exitCause(f["reason"], en)
            SupportEvent.SETTINGS_CHANGED -> settings(f, en)
            SupportEvent.DISPLAY_CHANGED ->
                if (en) "Screen size changed to ${f["w"]}x${f["h"]} (folded, unfolded or rotated)"
                else "Ekran boyutu ${f["w"]}x${f["h"]} oldu (katlama, açma ya da döndürme)"
            SupportEvent.RECENTS_OPENED ->
                if (en) "Recent apps opened over ${appName(f["app"])}; curtains removed"
                else "${appName(f["app"])} üstünde son uygulamalar açıldı; perdeler kaldırıldı"
            SupportEvent.RECENTS_CLOSED ->
                if (f["back"] == "true") {
                    if (en) "Back in ${appName(f["app"])} from recent apps; protection restored"
                    else "Son uygulamalardan ${appName(f["app"])}'a dönüldü; koruma yeniden kuruldu"
                } else {
                    if (en) "Left recent apps for another app" else "Son uygulamalardan başka bir uygulamaya geçildi"
                }
            SupportEvent.INSTAGRAM_SCREEN ->
                "Instagram: ${screen("from")} → ${screen("to")} (${action(f["action"], en)})"
            SupportEvent.INSTAGRAM_CURTAIN_SHOWN ->
                (if (en) "Instagram curtain shown: " else "Instagram'da perde açıldı: ") + screen("screen") +
                    " (" + curtainCause(f["why"], en) + bounds(f) + ")"
            SupportEvent.INSTAGRAM_CURTAIN_HIDDEN ->
                (if (en) "Instagram curtain removed: " else "Instagram'da perde kalktı: ") + screen("screen")
            SupportEvent.INSTAGRAM_RESUME_CURTAIN ->
                (if (en) "Back in Instagram; the last screen's curtain was shown again: "
                    else "Instagram'a geri dönüldü; son ekranın perdesi yeniden açıldı: ") + screen("screen")
            SupportEvent.INSTAGRAM_REEL_ALLOWED ->
                (if (en) "Reel allowed: " else "Reel'e izin verildi: ") + ingress(f["via"], en) + previous()
            SupportEvent.INSTAGRAM_REEL_DENIED ->
                (if (en) "Reel not allowed: " else "Reel'e izin verilmedi: ") + denial(f["reason"], en) +
                    previous(details(f, en))
            SupportEvent.INSTAGRAM_REEL_NEXT_VIDEO ->
                if (en) "Moved to the next video, so the Reel was closed"
                else "Sonraki videoya geçildiği için Reel kapatıldı"
            SupportEvent.INSTAGRAM_VIEWER_EXIT ->
                (if (en) "Closed in Instagram: " else "Instagram'da kapatıldı: ") + screen("screen") +
                    " (" + exitCauseInstagram(f["why"], en) + ")" + backRefused(f, en)
            SupportEvent.YOUTUBE_CURTAIN_SHOWN ->
                (if (en) "YouTube curtain shown: " else "YouTube'da perde açıldı: ") + youtubeSurface(f["surface"], en)
            SupportEvent.YOUTUBE_CURTAIN_HIDDEN ->
                (if (en) "YouTube curtain removed: " else "YouTube'da perde kalktı: ") + youtubeSurface(f["surface"], en)
            SupportEvent.YOUTUBE_SHORTS -> youtubeShorts(f, en) + backRefused(f, en)
            SupportEvent.TIKTOK -> when (f["what"]) {
                "tab" -> if (en) "TikTok: switched back to the For You tab (other tabs are protected)"
                    else "TikTok: Sizin İçin sekmesine geri dönüldü (diğer sekmeler korunuyor)"
                else -> (if (en) "TikTok: left the feed after a swipe" else "TikTok: kaydırıldığı için akıştan çıkıldı") +
                    backRefused(f, en)
            }
            SupportEvent.SLOW ->
                if (en) "Protection responded slowly: ${f["ms"]} ms (${appName(f["app"])})"
                else "Koruma geç tepki verdi: ${f["ms"]} ms (${appName(f["app"])})"
            SupportEvent.STEPPED_ASIDE -> {
                val cause = when (f["why"]) {
                    "page" -> if (en) "a page protection does not recognize opened" else "tanınmayan bir sayfa açıldı"
                    "popup" -> if (en) "a pop-up opened" else "bir açılır pencere açıldı"
                    else -> if (en) "another app's window came to the front (such as a permission prompt)"
                        else "başka bir uygulamanın penceresi öne geçti (örneğin izin penceresi)"
                }
                if (en) "${appName(f["app"])}: $cause, so the curtain stepped aside"
                else "${appName(f["app"])}: $cause; perde kenara çekildi"
            }
        }
    }

    private fun bounds(f: Map<String, String>): String {
        val top = f["top"] ?: return ""
        val bottom = f["bottom"] ?: return ""
        return ", $top–$bottom px"
    }

    private fun backRefused(f: Map<String, String>, en: Boolean): String =
        if (f["ok"] == "false") (if (en) " (Android didn't carry out Back)" else " (Android geri komutunu uygulamadı)") else ""

    private fun curtainCause(name: String?, en: Boolean) = when (name) {
        "tab" -> if (en) "a tab was tapped" else "sekmeye dokunuldu"
        "back" -> if (en) "came back to this screen" else "bu ekrana geri dönüldü"
        "resume" -> if (en) "returned to Instagram" else "Instagram'a geri dönüldü"
        "shade" -> if (en) "the notification panel was closed" else "bildirim paneli kapatıldı"
        "restore" -> if (en) "the previous curtain was put back" else "önceki perde geri kondu"
        "profile" -> if (en) "profile page" else "profil sayfası"
        "chatmedia" -> if (en) "media shared in a chat" else "sohbette paylaşılan medya"
        "search" -> if (en) "search results" else "arama sonuçları"
        "scroll" -> if (en) "the feed was scrolled" else "akış kaydırıldı"
        else -> if (en) "screen recognized" else "ekran tanındı"
    }

    private fun exitCauseInstagram(name: String?, en: Boolean) = when (name) {
        "notification" -> if (en) "opened from a notification" else "bildirimden açılmıştı"
        "post" -> if (en) "single post outside the allowed places" else "izin verilen yerler dışında açılan gönderi"
        "story" -> if (en) "Stories are blocked in this mode" else "bu modda hikâyeler kapalı"
        "retry" -> if (en) "still open after the first Back, tried again" else "ilk denemede kapanmadı, yeniden denendi"
        "reel" -> if (en) "Reel not allowed" else "Reel'e izin verilmedi"
        "reelpreview" -> if (en) "Reel preview in a feed" else "akıştaki Reel önizlemesi"
        "reelstab" -> if (en) "main Reels tab" else "ana Reels sekmesi"
        "chatmedia" -> if (en) "opened from a chat's shared media" else "sohbette paylaşılan medyadan açılmıştı"
        "collection" -> if (en) "collection that isn't allowed" else "izin verilmeyen koleksiyon"
        else -> if (en) "screen recognized as protected" else "korunan ekran olarak tanındı"
    }

    private fun exitCause(name: String?, en: Boolean) = when (name) {
        "user" -> if (en) "the app was closed by the user (swiped away from recent apps or force stopped)"
            else "uygulama kullanıcı tarafından kapatıldı (son uygulamalardan kaydırma ya da zorla durdurma)"
        "user-stopped" -> if (en) "the app was stopped by the user" else "uygulama kullanıcı tarafından durduruldu"
        "low-memory" -> if (en) "Android closed it because memory was low" else "Android bellek yetersiz olduğu için kapattı"
        "resources" -> if (en) "Android closed it for using too many resources (battery or background limits)"
            else "Android fazla kaynak kullandığı gerekçesiyle kapattı (pil ya da arka plan sınırı)"
        "killed" -> if (en) "the system ended it (often the phone's battery or memory manager)"
            else "sistem tarafından sonlandırıldı (çoğunlukla telefonun pil ya da bellek yöneticisi)"
        "crash" -> if (en) "the app crashed" else "uygulama çöktü"
        "anr" -> if (en) "the app stopped responding" else "uygulama yanıt vermediği için kapatıldı"
        "updated" -> if (en) "the app was updated" else "uygulama güncellendi"
        "permission" -> if (en) "a permission was changed" else "bir izin değiştirildi"
        "frozen" -> if (en) "Android froze the app in the background" else "Android uygulamayı arka planda dondurdu"
        else -> (if (en) "other reason " else "başka bir neden ") + "(${name.orEmpty()})"
    }

    private fun settings(f: Map<String, String>, en: Boolean): String {
        val app = appName(f["app"])
        val on = f["on"] == "true"
        val mode = when (f["mode"]) {
            "STRICT" -> if (en) "Strict" else "Sert"
            else -> if (en) "Balanced" else "Dengeli"
        }
        // TikTok has no mode to choose.
        val withMode = if (f["app"] == "tiktok") "" else ", $mode"
        return if (en) "Settings: $app protection ${if (on) "on$withMode" else "off"}"
        else "Ayar değişti: $app koruması ${if (on) "açık$withMode" else "kapalı"}"
    }

    private fun youtubeShorts(f: Map<String, String>, en: Boolean) = when (f["what"]) {
        "next" -> if (en) "YouTube: moved to the next Short, so the Short was closed"
            else "YouTube: sonraki Short'a geçildiği için Short kapatıldı"
        "retry" -> if (en) "YouTube: the Short was still open, closed again" else "YouTube: Short hâlâ açıktı, yeniden kapatıldı"
        "blocked" -> if (f["fromtab"] == "true") {
            if (en) "YouTube: a Short opened from the Shorts tab was closed" else "YouTube: Shorts sekmesinden açılan Short kapatıldı"
        } else {
            if (en) "YouTube: Short closed (Strict mode)" else "YouTube: Short kapatıldı (Sert mod)"
        }
        "tab" -> if (en) "YouTube: a tap on the Shorts tab was blocked" else "YouTube: Shorts sekmesine dokunma engellendi"
        else -> "YouTube: Shorts"
    }

    private fun details(f: Map<String, String>, en: Boolean): String = buildString {
        f["tabbar"]?.let { append(if (en) ", bottom menu: " else ", alt menü: "); append(yesNo(it, en)) }
        f["back"]?.let { append(if (en) ", back arrow: " else ", geri oku: "); append(yesNo(it, en)) }
        f["tab"]?.let { append(if (en) ", selected tab: " else ", seçili sekme: "); append(tab(it, en)) }
    }

    private fun yesNo(value: String, en: Boolean) = when (value) {
        "true" -> if (en) "yes" else "var"
        "false" -> if (en) "no" else "yok"
        else -> value
    }

    private fun tab(name: String, en: Boolean) = when (name) {
        "HOME" -> if (en) "Home" else "Ana Sayfa"
        "REELS" -> "Reels"
        "DIRECT" -> if (en) "Messages" else "Mesajlar"
        "SEARCH" -> if (en) "Search" else "Arama"
        "PROFILE" -> if (en) "Profile" else "Profil"
        else -> name
    }

    fun instagramScreen(name: String?, en: Boolean): String = when (name) {
        "HOME_FEED" -> if (en) "Home" else "Ana Sayfa"
        "REELS" -> "Reels"
        "EXPLORE" -> if (en) "Explore" else "Keşfet"
        "NOTIFICATIONS" -> if (en) "Notifications" else "Bildirimler"
        "SEARCH_RESULTS" -> if (en) "Search results" else "Arama sonuçları"
        "DIRECT_MESSAGES" -> if (en) "Messages" else "Mesajlar"
        "DIRECT_SHARED_MEDIA" -> if (en) "Media shared in a chat" else "Sohbette paylaşılan medya"
        "PROFILE" -> if (en) "Profile" else "Profil"
        "SAVED_COLLECTION" -> if (en) "Saved" else "Kaydedilenler"
        "COMMENTS_OR_DETAIL" -> if (en) "Post or comments" else "Gönderi ya da yorumlar"
        "STORY" -> if (en) "Story" else "Hikâye"
        "CREATION" -> if (en) "Camera" else "Kamera"
        "UNKNOWN", null -> if (en) "unrecognized screen" else "tanınmayan ekran"
        else -> name
    }

    private fun action(name: String?, en: Boolean) = when (name) {
        "ALLOW" -> if (en) "allowed" else "izin verildi"
        "SHOW_CURTAIN" -> if (en) "covered" else "perdelendi"
        "EXIT" -> if (en) "closed" else "kapatıldı"
        "KEEP_CURRENT" -> if (en) "left as it was" else "değişiklik yapılmadı"
        else -> name.orEmpty()
    }

    private fun ingress(name: String?, en: Boolean) = when (name) {
        "ACTION_BAR" -> if (en) "recognized as a single Reel" else "tek Reel ekranı olarak tanındı"
        "DIRECT_INTENT" -> if (en) "tapped in a chat" else "sohbette Reel'e dokunuldu"
        "DIRECT_VIEWER_WINDOW" -> if (en) "opened from a chat" else "sohbetten açıldı"
        "PROFILE_REEL_INTENT" -> if (en) "opened from a profile" else "profilden açıldı"
        "COLLECTION_REEL_INTENT" -> if (en) "opened from Saved" else "Kaydedilenler'den açıldı"
        "NOTIFICATION_COMMENT_SHEET" -> if (en) "opened from a notification" else "bildirimden açıldı"
        "return" -> if (en) "returned to the same Reel" else "aynı Reel'e geri dönüldü"
        else -> name.orEmpty()
    }

    private fun denial(name: String?, en: Boolean) = when (name) {
        "no-ingress" -> if (en) "couldn't tell where it was opened from" else "nereden açıldığı anlaşılamadı"
        "main-reels-tab" -> if (en) "opened from the main Reels tab" else "ana Reels sekmesinden açıldı"
        "feed-origin" -> if (en) "opened from a protected feed" else "korunan bir akıştan açıldı"
        else -> name.orEmpty()
    }

    private fun youtubeSurface(name: String?, en: Boolean) = when (name) {
        "HOME" -> if (en) "Home" else "Ana Sayfa"
        "SUBSCRIPTIONS" -> if (en) "Subscriptions" else "Abonelikler"
        else -> if (en) "unknown screen" else "bilinmeyen ekran"
    }

    private fun appName(name: String?) = when (name) {
        "instagram" -> "Instagram"
        "youtube" -> "YouTube"
        "tiktok" -> "TikTok"
        else -> name.orEmpty()
    }
}
