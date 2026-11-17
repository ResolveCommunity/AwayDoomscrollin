# AwayDoomscrollin' v1.5.0

Version name: 1.5.0 · Version code: 12

## English

### Added

- Languages: the app is now available in French, Italian, Spanish, Russian
  and Korean, besides English and Turkish. You can choose the language in the
  app.
- Instagram, Open in Messages (optional, off by default, in the Apps tab
  under Instagram): when Instagram opens and lands on Home, the app taps
  Messages for you, so you start in your messages instead of the feed.
  Moving around inside Instagram, notifications and links are left alone.
- "Protection is not running" notification: if the phone stops protection
  in the background, this notification now appears about a minute later, so
  Reels no longer come back unnoticed. Tapping it opens Accessibility
  settings, where turning AwayDoomscrollin' off and on brings protection
  back. Protection is also checked again after the phone restarts or the app
  updates.
- Xiaomi, Redmi and POCO: on a Xiaomi test phone, clearing Recent apps
  stopped protection, and it came back by itself only when Autostart was on.
  On these phones, setup now strongly recommends both Autostart and running in
  the background, with a button for each, Home shows a small reminder under
  Recent blocks, and the "Protection is not running" notification leads to
  Autostart.
- TikTok: the regional TikTok app used in Korea, Japan and Southeast Asia is
  now recognized and protected like TikTok. TikTok Lite is not supported
  yet.
- Send feedback: a bug report can include the troubleshooting log with one
  tick (recommended), and the screen says where our reply will arrive.
- Troubleshooting log: when the phone stops the app, the log now says why
  (for example, Recent apps were cleared).
- After an update, a short summary of what changed is shown once.

### Fixed

- A lifted curtain no longer stays on screen until you touch or scroll.
- Home no longer says protection is on when the phone has stopped it but left
  its switch on; it now says to turn the switch off and on again.
- Instagram, Explore search: the cover stays below the keyboard and leaves
  the search page sooner. Thanks to Nelson Joppi (njoppi2) for this
  contribution.
- Instagram: going back from search to Explore now covers the grid right
  away.
- Instagram, Balanced: switching to Home no longer covers your Stories for a
  moment.
- Instagram: if you scrolled Home until Instagram hid its top bar, the
  curtain could keep Stories and the top bar covered. Tapping Home now brings
  them back, and the first posts no longer show while the feed refreshes.
- YouTube, Balanced: a Short opened from a channel or a shelf no longer
  closes at once.
- YouTube: opening a notification that shows a pop-up no longer flashes the
  Home curtain.
- YouTube: scrolling a channel's posts or their comments no longer covers
  the channel with the Home curtain.
- Achievement notices: the tier message now reads naturally in every
  language.

### Improved

- Faster curtains: on our test phones the curtain now lifts within about 0.1
  to 0.3 seconds when you open Messages, a profile or your notifications (up
  to 1 to 3 seconds before), and covers the feed sooner when you go back from
  notifications or search. Protection also does much less work in the
  background, so it responds sooner on slower phones.
- Notifications: status notifications vibrate only when the phone stops
  protection. "Protection active" and the notice after you turn protection
  off yourself are silent, and the vibration follows Android's notification
  settings, so you can turn it off there.
- Troubleshooting log: on a new installation it is now off until you turn it
  on in About. If it was on before this update, it stays on.

Detection depends on accessibility information from other apps. Changes to
those apps, Android or device software can still affect protection.

## Türkçe

### Eklenenler

- Diller: Uygulama artık İngilizce ve Türkçenin yanında Fransızca,
  İtalyanca, İspanyolca, Rusça ve Korece de kullanılabiliyor. Dili
  uygulamanın içinden seçebilirsiniz.
- Instagram, Mesajlar'da aç (isteğe bağlı, varsayılan olarak kapalı,
  Uygulamalar sekmesinde Instagram'ın altında): Instagram açılıp Ana
  Sayfa'da başladığında uygulama sizin yerinize Mesajlar'a dokunur; akış
  yerine mesajlarınızla başlarsınız. Instagram içinde gezinmek, bildirimler
  ve bağlantılar etkilenmez.
- "Koruma çalışmıyor" bildirimi: Telefon korumayı arka planda durdurursa bu
  bildirim artık yaklaşık bir dakika sonra geliyor; Reels fark edilmeden
  geri gelmiyor. Bildirime dokununca Erişilebilirlik ayarları açılıyor;
  AwayDoomscrollin' orada kapatılıp açılınca koruma geri geliyor. Telefon
  yeniden başlayınca ya da uygulama güncellenince koruma tekrar
  denetleniyor.
- Xiaomi, Redmi ve POCO: Bir Xiaomi test telefonunda Son uygulamalar'ı
  temizlemek korumayı durdurdu; koruma yalnızca Otomatik başlatma açıkken
  kendiliğinden geri geldi. Bu telefonlarda kurulum artık Otomatik
  başlatmayı ve arka planda çalışmayı şiddetle tavsiye ediyor ve ikisi için
  de bir düğme sunuyor; Ana Sayfa, Son engellemeler'in altında küçük bir
  hatırlatma gösteriyor; "Koruma çalışmıyor" bildirimi de Otomatik
  başlatmaya götürüyor.
- TikTok: Kore, Japonya ve Güneydoğu Asya'da kullanılan bölgesel TikTok
  uygulaması artık tanınıyor ve TikTok gibi korunuyor. TikTok Lite henüz
  desteklenmiyor.
- Geri bildirim gönder: Hata bildirimine sorun giderme kaydı tek bir
  işaretle eklenebiliyor (önerilir); ekran yanıtımızın nereye geleceğini de
  söylüyor.
- Sorun giderme kaydı: Telefon uygulamayı durdurduğunda kayıt artık nedenini
  de yazıyor (örneğin Son uygulamalar temizlendi).
- Güncellemeden sonra neler değiştiğini anlatan kısa bir özet bir kez
  gösteriliyor.

### Düzeltilenler

- Kalkan perde artık siz dokunana ya da kaydırana kadar ekranda kalmıyor.
- Telefon korumayı durdurup anahtarını açık bıraktığında Ana Sayfa artık
  korumayı açık göstermiyor; anahtarı kapatıp yeniden açmayı söylüyor.
- Instagram, Keşfet araması: Perde klavyenin altında kalıyor ve arama
  sayfasından daha çabuk kalkıyor. Bu katkı için Nelson Joppi'ye (njoppi2)
  teşekkür ederiz.
- Instagram: Aramadan Keşfet'e geri dönünce ızgara artık hemen kapanıyor.
- Instagram, Dengeli: Ana Sayfa'ya geçerken hikâyeleriniz artık bir an bile
  kapanmıyor.
- Instagram: Ana Sayfa'yı Instagram üst çubuğu gizleyene kadar
  kaydırdıysanız perde hikâyeleri ve üst çubuğu kapalı tutabiliyordu. Artık
  Ana Sayfa'ya dokununca ikisi de geri geliyor; akış yenilenirken ilk
  gönderiler de görünmüyor.
- YouTube, Dengeli: Bir kanaldan ya da raftan açılan Short artık hemen
  kapanmıyor.
- YouTube: Açılır pencere gösteren bir bildirimi açmak artık Ana Sayfa
  perdesini bir anlığına göstermiyor.
- YouTube: Bir kanalın gönderilerinde ya da yorumlarında kaydırmak artık
  kanalı Ana Sayfa perdesiyle kapatmıyor.
- Başarım bildirimleri: Kademe mesajı artık her dilde doğal okunuyor.

### İyileştirmeler

- Daha hızlı perdeler: Test telefonlarımızda Mesajlar'ı, bir profili ya da
  bildirimlerinizi açtığınızda perde artık 0,1 ile 0,3 saniye içinde
  kalkıyor (önceden 1 ile 3 saniyeyi bulabiliyordu); bildirimlerden ya da
  aramadan geri döndüğünüzde akışı daha erken kapatıyor. Koruma arka planda
  da çok daha az iş yapıyor, bu yüzden yavaş telefonlarda daha çabuk tepki
  veriyor.
- Bildirimler: Durum bildirimleri yalnızca telefon korumayı durdurduğunda
  titreşiyor. "Koruma etkin" bildirimi ve korumayı kendiniz kapattığınızda
  gelen bildirim sessiz; titreşim Android'in bildirim ayarlarına uyuyor,
  oradan kapatabilirsiniz.
- Sorun giderme kaydı: Yeni kurulumda artık siz Hakkında bölümünden açana
  kadar kapalı. Bu güncellemeden önce açıksa açık kalıyor.

Algılama, diğer uygulamaların sunduğu erişilebilirlik bilgilerine bağlıdır.
Uygulama, Android veya telefon yazılımındaki değişiklikler korumayı
etkileyebilir.
