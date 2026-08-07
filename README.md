<p align="center">
  <img src="assets/logo.png" alt="AwayDoomscrollin' Logo" width="120"/>
</p>

<h1 align="center">AwayDoomscrollin'</h1>

<p align="center">
  <strong>Break the Infinite Scroll Loop — For Good.</strong><br/>
  Open-source, offline-first Android accessibility shield against Instagram Reels, TikTok & YouTube Shorts.<br/>
  🌐 <strong><a href="https://awaydoomscrollin.com">awaydoomscrollin.com</a></strong>
</p>

<p align="center">
  <a href="https://www.gnu.org/licenses/gpl-3.0"><img src="https://img.shields.io/badge/License-GPLv3-brightgreen.svg" alt="License: GPL v3"/></a>
  <a href="https://developer.android.com"><img src="https://img.shields.io/badge/Min%20SDK-26%20(Android%208.0%2B)-blue.svg" alt="Min SDK 26"/></a>
  <img src="https://img.shields.io/badge/Status-100%25%20Offline%20%26%20Privacy--First-00F2FE.svg" alt="Offline & Privacy-First"/>
  <img src="https://img.shields.io/badge/Samsung%20One%20UI-100%25%20Verified-00FF87.svg" alt="Samsung One UI Verified"/>
</p>

---

## What is it?

**AwayDoomscrollin'** is an open-source Android app that physically interrupts unconscious short-video scrolling the moment it happens — not 15 minutes later.

Unlike standard "screen time" apps that negotiate with you, AwayDoomscrollin' uses an **event-driven Accessibility Service** that fires the instant a vertical swipe gesture is detected on a short-video feed. No timer. No limit. Instant exit.

---

## ✨ Features

| Feature | Description |
|---|---|
| ⚡ **Instant Shield** | Detects scroll gesture → returns to home screen immediately |
| 💬 **Smart Safe Zones** | DMs, comments, and long-form YouTube are never blocked |
| 🔒 **100% Offline** | Zero internet permissions — all logic runs on-device |
| 🚨 **Anti-Cheat** | Prevents impulsive service deactivation from Settings |
| 🏆 **Gamification** | XP system, daily streaks (3 / 7 / 14 / 30 days) |
| 🕒 **24-Hour Heatmap** | Visualize your hourly block patterns — know your weak hours |
| ⚡ **Peak Hour Alert** | Home screen shows today's most dangerous scroll hour |
| 📊 **Progress Dashboard** | Weekly bar chart, per-app stats, saved time tracking |

---

## 📱 Supported Platforms

| Platform | Package Name | Status |
|---|---|---|
| **Instagram Reels** | `com.instagram.android` | ✅ Full Support |
| **TikTok** | `com.zhiliaoapp.musically` | 🔶 Beta |
| **YouTube Shorts** | `com.google.android.youtube` | 🔶 Beta |

> Beta platforms: functional but may occasionally miss detection. Open an issue if you encounter problems.



---

## 🚀 Building from Source

### Requirements
- Android Studio Jellyfish or newer
- Android SDK 34, Build Tools 34.0.0
- JDK 17 / Kotlin 1.9+

### Steps

```bash
# 1. Clone the repo
git clone https://github.com/resolvecommunity/AwayDoomscrollin.git
cd AwayDoomscrollin

# 2. Open in Android Studio
# File → Open → select the project folder

# 3. Build & Run
# Run → Run 'app'  (Shift+F10)
```

> **Note:** This project does not include a `gradlew` wrapper. Build via Android Studio or set up your own Gradle installation.

---

## 🔒 Privacy

- **No internet permission** declared in `AndroidManifest.xml`
- All block data stored locally via `SharedPreferences`
- Optional telemetry (disabled by default) sends only anonymous block counts — no device IDs, no user data
- Full source code available for audit

---

## 🤝 Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

---

## 🛡️ Security

See [SECURITY.md](SECURITY.md) for reporting vulnerabilities.

---

## 📄 License

Distributed under the **GNU General Public License v3.0**.  
See [LICENSE](LICENSE) for full details.

---

<p align="center">
  Made with ❤️ by <a href="https://github.com/resolvecommunity">Resolve Community</a>
</p>
