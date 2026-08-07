# AwayDoomscrollin' — GEMINI.md

Bu dosya, Antigravity (AI asistanı) ve gelecekteki geliştiriciler için projenin tam bağlamını, mimarisini ve kurallarını belgeler.

---

## 📱 Proje Özeti

**AwayDoomscrollin'**, kullanıcıların Instagram Reels, TikTok ve YouTube Shorts gibi kısa video platformlarındaki sonsuz kaydırma (doomscrolling) alışkanlığını kırmalarına yardımcı olan bir Android uygulamasıdır.

- **Dil:** Kotlin
- **UI Framework:** Jetpack Compose (Material3)
- **Minimum SDK:** 26 (Android 8.0)
- **Target SDK:** 34
- **Paket Adı:** `com.example.antidoomscrolling`
- **Tasarım Teması:** Siber/Neon Cyberpunk (Koyu arka plan, neon cyan/yeşil/kırmızı tonları)

---

## 🏗️ Mimari

### Dosya Yapısı

```
app/src/main/
├── AndroidManifest.xml              # İzinler, Aktiviteler, Servis tanımı
├── java/com/example/antidoomscrolling/
│   ├── MainActivity.kt              # Tüm Compose UI (3000+ satır)
│   ├── AntiScrollService.kt         # Erişilebilirlik Servisi - engelleme motoru
│   ├── AntiCheatGuiltActivity.kt    # Hile yakalandığında gösterilen suçluluk ekranı
│   └── ClearTaskActivity.kt        # Görev temizleme yardımcısı
└── res/
    ├── xml/accessibility_service_config.xml   # Servis izin yapılandırması
    ├── drawable/                     # Uygulama ikonları (ic_instagram, ic_tiktok, ic_youtube)
    └── raw/                          # Demo Reels videoları (reels_video_1..5)
```

### Ana Bileşenler

| Bileşen | Dosya | Açıklama |
|---|---|---|
| `AntiScrollService` | `AntiScrollService.kt` | Erişilebilirlik servisi; kaydırmaları algılar, uygulamaları zorla durdurur |
| `MainActivity` | `MainActivity.kt` | Tüm ekranlar ve Compose UI |
| `AntiCheatGuiltActivity` | `AntiCheatGuiltActivity.kt` | Hile algılandığında gösterilen suçluluk popup'ı |

---

## 🧠 Engelleme Mantığı (AntiScrollService)

### Nasıl Çalışır?

1. Android **Erişilebilirlik Servisi** olarak arka planda çalışır
2. `TYPE_VIEW_SCROLLED` ve `TYPE_WINDOW_STATE_CHANGED` erişilebilirlik olaylarını dinler
3. Hedef uygulamada kaydırma tespit edildiğinde:
   - `performGlobalAction(GLOBAL_ACTION_HOME)` → Ana ekrana fırlatır
   - `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` → Uygulamanın ayar sayfasını açar
   - Oradaki "Durmaya Zorla" butonuna otomatik tıklar
4. Anti-Cheat sistemi: Kullanıcı erişilebilirlik servisini kapatmaya çalışırsa `AntiCheatGuiltActivity` açılır

### Desteklenen Uygulamalar

| Uygulama | Paket Adı | SharedPrefs Anahtarı | Durum |
|---|---|---|---|
| Instagram | `com.instagram.android` | `is_instagram_enabled` | ✅ Tam Destekli |
| TikTok | `com.zhiliaoapp.musically` | `is_tiktok_enabled` | 🔶 BETA |
| YouTube Shorts | `com.google.android.youtube` | `is_youtube_enabled` | 🔶 BETA |

### Cihaz Uyumluluğu

- **Samsung Galaxy (One UI):** %100 test edildi, tam uyumlu
- **Diğer Android cihazlar:** Test edilmedi, hata raporu bekleniyor

---

## 💾 Veri Kalıcılığı (SharedPreferences)

**Dosya Adı:** `"away_doomscroll_prefs"`

| Anahtar | Tip | Açıklama |
|---|---|---|
| `total_blocks` | Int | Toplam engelleme sayısı |
| `user_xp` | Long | Kullanıcının XP puanı |
| `streak_days` | Int | Günlük seri (streak) günü |
| `last_active_day` | String | Son aktif gün (yyyy-MM-dd) |
| `is_instagram_enabled` | Boolean | Instagram koruma aktif mi |
| `is_tiktok_enabled` | Boolean | TikTok koruma aktif mi |
| `is_youtube_enabled` | Boolean | YouTube Shorts koruma aktif mi |
| `blocks_instagram` | Int | Toplam Instagram engelleme |
| `blocks_tiktok` | Int | Toplam TikTok engelleme |
| `blocks_youtube` | Int | Toplam YouTube engelleme |
| `blocks_{yyyy-MM-dd}` | Int | O güne ait toplam engelleme |
| `blocks_{yyyy-MM-dd}_instagram` | Int | O güne ait Instagram engelleme |
| `blocks_{yyyy-MM-dd}_tiktok` | Int | O güne ait TikTok engelleme |
| `blocks_{yyyy-MM-dd}_youtube` | Int | O güne ait YouTube engelleme |
| `blocks_{yyyy-MM-dd}_hour_{HH}` | Int | O güne ve saate ait engelleme (Heatmap için) |
| `instagram_daily_time_ms` | Long | Instagram'da geçirilen günlük süre (ms) |
| `onboarding_completed` | Boolean | Kurulum tamamlandı mı |

---

## 🎨 UI Yapısı (MainActivity.kt)

### Ekran Hiyerarşisi

```
MainActivity
└── ZenTheme (Koyu/Cyberpunk renk şeması)
    ├── OnboardingScreen (ilk kurulum, 4 adım)
    │   ├── OnboardingStepOne   - Geliştirici mektubu
    │   ├── OnboardingStepTwo   - Kalkan nasıl çalışır (video demo)
    │   ├── OnboardingStepThree - Ne yapabilir, ne yapamaz
    │   └── OnboardingStepFour  - İzin kurulumu
    └── MainNavigationDashboard (4 sekmeli ana menü)
        ├── HomeScreen          - Kalkan durumu, kurtarılan zaman, ⚡ Kritik Saat kartı
        ├── ModesAndAppsScreen  - Hedef uygulama toggleları
        ├── ProgressStatusScreen - Haftalık grafik, 🕒 24-saat heatmap, rozetler, istatistikler
        └── AboutScreen         - Hakkında, uyumluluk, geri bildirim
```

### Renk Paleti (ZenTheme)

| Token | Hex | Kullanım |
|---|---|---|
| `background` | `#070A12` | Sayfa arka planı |
| `surface` | `#0F1523` | Kart arka planı |
| `primary` | `#00F2FE` | Neon Cyan - Ana vurgu |
| `secondary` | `#00FF87` | Neon Yeşil - Başarı |
| `error` | `#FF0055` | Neon Kırmızı - Uyarı |
| `outline` | `#1E2A40` | Çerçeve |
| Instagram | `#E1306C` | Instagram brand rengi |
| TikTok | `#00F2FE` | TikTok brand rengi |
| YouTube | `#FF0000` | YouTube brand rengi |

---

## ⚡ Accessibility Service Config

**Dosya:** `res/xml/accessibility_service_config.xml`

```xml
android:accessibilityEventTypes="typeViewScrolled|typeWindowStateChanged|typeWindowContentChanged|typeViewClicked"
android:packageNames="com.instagram.android,com.android.settings,com.zhiliaoapp.musically,com.google.android.youtube"
android:accessibilityFlags="flagDefault|flagRetrieveInteractiveWindows|flagIncludeNotImportantViews|flagRequestFilterKeyEvents|flagReportViewIds"
```

> ⚠️ Yeni bir uygulama eklendiğinde hem bu config dosyasına hem de `AntiScrollService.kt` içine eklenmesi gerekir.

---

## 🚀 Derleme ve Cihaza Yükleme

```powershell
# ADB ile bağlı cihazı kontrol et
& "C:\Users\Desktop\AppData\Local\Android\Sdk\platform-tools\adb.exe" devices

# Android Studio üzerinden: Run > Run 'app' (Shift+F10)
# veya Build > Build APK
```

> Erişilebilirlik servisi yapılandırması (`accessibility_service_config.xml`) değiştirildiğinde, uygulama yüklendikten sonra **Ayarlar > Erişilebilirlik** menüsünden servis kapatılıp tekrar açılmalıdır.

---

## 🔧 Bilinen Sorunlar ve Notlar

- **TikTok (BETA):** TikTok kendi özel render motorunu kullandığı için standart `TYPE_VIEW_SCROLLED` olayı göndermeyebilir. Mevcut yaklaşım logcat üzerinden test edilmeye devam etmektedir.
- **YouTube Shorts (BETA):** `isYoutubeShortsScreen()` fonksiyonu ViewID (`reel`, `shorts`) ve seçili sekme (`isSelected`) kontrolü ile Shorts ekranını ayırt eder. YouTube ana sayfasında kaydırma **engellenmez**.
- **Instagram:** Tam stabil. `isDangerousScreen()` ve `isSafeScreen()` fonksiyonları DM, yorum alanları ve güvenli bölgeleri doğru şekilde ayırt eder.
- **Anti-Cheat:** Kullanıcı uygulamayı Erişilebilirlik listesinden kapatmaya çalışırsa `isSwitchClickedInDetailPage()` veya `isSamsungTurnOffDialog()` ile yakalanır ve `AntiCheatGuiltActivity` açılır.

---

## 📋 Kural ve Kurallar (AI Kodlama Rehberi)

1. **Stil tutarlılığı:** Tüm yeni UI bileşenleri ZenTheme renkleri kullanmalı; `MaterialTheme.colorScheme.*` referans almalı.
2. **SharedPrefs anahtarları:** Yeni anahtar eklenirse bu belgeyi güncelle.
3. **Yeni uygulama desteği:** `accessibility_service_config.xml` `packageNames` listesine eklenmeli; `AntiScrollService.kt` içine yeni bir `if (packageName == "...")` bloğu yazılmalı.
4. **BETA uygulamalar:** TikTok ve YouTube `CyberTargetAppCard` bileşeninde `isBeta = true` ile işaretlidir. Stabil olduğunda `isBeta = false` yapılabilir.
5. **Derleme:** Gradlew projede `./gradlew.bat` dosyası yoktur. Android Studio üzerinden derleme yapılır. ADB için tam yol: `C:\Users\Desktop\AppData\Local\Android\Sdk\platform-tools\adb.exe`
7. **Heatmap Mimarisi:** `DoomscrollHourlyHeatmap` composable'ı `ProgressStatusScreen` içinde yer alır. Veriler `blocks_{yyyy-MM-dd}_hour_{HH}` anahtarlarından okunur. Gerçek veri yoksa otomatik demo mock data gösterilir. `HomeScreen`'deki ⚡ Kritik Saat kartı günün en yoğun saatini hesaplar ve bağlamsal uyarı mesajı gösterir.
6. **Test cihazı:** Samsung Galaxy (RFCW30GNQXK) — One UI yüklü. Diğer cihazlar test edilmemiştir.
