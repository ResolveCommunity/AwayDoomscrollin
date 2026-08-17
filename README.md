<p align="center">
  <img src="assets/logo.png" alt="AwayDoomscrollin' Logo" width="120"/>
</p>

<h1 align="center">AwayDoomscrollin'</h1>

<p align="center">
  <strong>Break the Infinite Scroll Loop — For Good.</strong><br/>
  Open-source, privacy-first Android accessibility shield for Instagram Reels, TikTok & YouTube Shorts (Beta).<br/>
  🌐 <strong><a href="https://awaydoomscrollin.com">awaydoomscrollin.com</a></strong>
</p>

<p align="center">
  <a href="https://www.gnu.org/licenses/gpl-3.0"><img src="https://img.shields.io/badge/License-GPLv3-brightgreen.svg" alt="License: GPL v3"/></a>
  <a href="https://developer.android.com"><img src="https://img.shields.io/badge/Min%20SDK-26%20(Android%208.0%2B)-blue.svg" alt="Min SDK 26"/></a>
  <img src="https://img.shields.io/badge/Status-Public%20Beta-FFB000.svg" alt="Public Beta"/>
  <img src="https://img.shields.io/badge/Telemetry-Explicit%20Opt--In-00F2FE.svg" alt="Explicit Opt-In Telemetry"/>
</p>

---

## What is it?

**AwayDoomscrollin'** is an open-source Android app that attempts to interrupt unconscious short-video scrolling when a supported feed is detected.

Unlike timer-based "screen time" apps, AwayDoomscrollin' uses an **event-driven Accessibility Service** to inspect supported-app accessibility events and return to the home screen when it recognizes a target feed. The detection engine is in beta: changes in Instagram, TikTok, YouTube, Android, or a manufacturer's accessibility implementation can cause missed detections or false positives.

---

## ✨ Features

| Feature | Description |
|---|---|
| ⚡ **Event-Driven Shield (Beta)** | Attempts to recognize target short-video feeds and return to the home screen |
| 💬 **Safe-Zone Rules (Beta)** | Designed to avoid DMs, comments, search, and long-form YouTube; compatibility can vary with third-party UI changes |
| 🛡️ **Privacy-First Core** | Screen analysis and blocking run on-device. The app still has Internet permission for explicit opt-in telemetry and automatic GitHub rule/configuration updates. |
| 🚨 **Anti-Cheat** | Prevents impulsive service deactivation from Settings |
| 🏆 **Gamification** | Daily streaks (3 / 7 / 14 / 30 days) |
| 🕒 **24-Hour Heatmap** | Visualize your hourly block patterns — know your weak hours |
| ⚡ **Peak Hour Alert** | Home screen shows today's most dangerous scroll hour |
| 📊 **Progress Dashboard** | Weekly bar chart, per-app stats, saved time tracking |

---

## 📱 Supported Platforms

| Platform | Package Name | Status |
|---|---|---|
| **Instagram Reels** | `com.instagram.android` | 🔶 Beta |
| **TikTok** | `com.zhiliaoapp.musically` | 🔶 Beta |
| **YouTube Shorts** | `com.google.android.youtube` | 🔶 Beta |

> All platform integrations are currently beta. They are functional on tested configurations but may miss a target feed or incorrectly classify a safe area after a platform, Android, or manufacturer update. Please report the app version, platform version, Android version, and device model when filing an issue.



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

> **Note:** The Gradle wrapper is included. On Linux/macOS, run `./gradlew assembleRelease`; on Windows, run `gradlew.bat assembleRelease`.

---

## 🔒 Privacy

- **On-device core**: Accessibility screen analysis and blocking work locally without a network connection.
- **Internet permission**: The Android manifest declares `android.permission.INTERNET`. AwayDoomscrollin' is not a fully offline or zero-network-permission app.
- **Opt-in telemetry to `awaydoomscrollin.com`**: Telemetry is disabled by default. No telemetry request is made and no telemetry installation UUID is created until the user explicitly enables it. The first enabled submission creates a random per-installation UUID that is not derived from hardware, account, Android ID, IMEI, MAC, or advertising data. While enabled, the app sends that UUID, device manufacturer/model, Android/SDK/app version, aggregate platform and total block counts, streak days, and XP. Turning telemetry off stops future submissions; a request already handed to the operating-system network stack may finish. The latest server snapshot expires within 90 days, and the server stores a SHA-256 installation key rather than the raw UUID.
- **Automatic GitHub rule/configuration requests**: Separately from telemetry, the app attempts to fetch non-executable JSON rule/configuration data from `raw.githubusercontent.com` when the app or accessibility service starts. A successful response is cached for six hours; a fresh cache suppresses another request during that period. **This path runs even when telemetry is off. Turning off telemetry does not disable GitHub requests, and there is currently no separate in-app switch for them.** Built-in rules remain available when the request fails or the device is offline.
- Block statistics are stored locally via `SharedPreferences`; the aggregate fields listed above are also transmitted while telemetry is enabled.
- Full source code available for audit.

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





