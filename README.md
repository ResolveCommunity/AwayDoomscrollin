# 🛡️ AwayDoomscrollin' — Break the Infinite Scroll Loop

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-brightgreen.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Android Min SDK](https://img.shields.io/badge/Min%20SDK-26%20%28Android%208.0%2B%29-blue.svg)](https://developer.android.com)
[![Status](https://img.shields.io/badge/Status-100%25%20Offline%20%26%20Privacy--First-00F2FE.svg)](#privacy--security)
[![Samsung One UI](https://img.shields.io/badge/Samsung%20One%20UI-100%25%20Verified-00FF87.svg)](#device-compatibility)

**AwayDoomscrollin'** is an open-source, privacy-first Android application designed to help users break mindless doomscrolling addiction on short-video platforms (**Instagram Reels**, **TikTok**, and **YouTube Shorts**).

Unlike restrictive "digital wellbeing" apps that offer bargaining limits (e.g. "15 mins/day"), AwayDoomscrollin' uses an event-driven **Accessibility Service Shield** that intervenes the exact moment an unconscious vertical scroll gesture occurs.

---

## ✨ Key Features

- **⚡ Instant Anti-Scroll Intervention:** Automatically detects vertical swipe gestures on short-video feeds and returns you to the home screen.
- **💬 Smart Safe Zone:** DM messaging and comment reading sheets are recognized as safe zones — scrolling in DMs and reading comments is never blocked.
- **🔒 100% Offline & Zero Internet Permissions:** Requests zero network permissions (`android.permission.INTERNET` is omitted). Runs entirely locally on your device.
- **🚨 Anti-Cheat Protection:** Prevents accidental or impulsive service deactivation from Android Settings.
- **🏆 Gamification & XP System:** Earn XP and build daily streak milestones (3, 7, 14, 30 days) as you reclaim your time.
- **📱 Cyberpunk / Neon Jetpack Compose UI:** Premium dark-mode UI with live shield activity logs and habit statistics.

---

## 📱 Supported Platforms

| Platform | Package Name | Status | Safe Zone Behavior |
|---|---|---|---|
| **Instagram Reels** | `com.instagram.android` | ✅ Full Support | DMs & Comments fully accessible |
| **TikTok** | `com.zhiliaoapp.musically` | 🔶 Beta | Messaging accessible |
| **YouTube Shorts** | `com.google.android.youtube` | 🔶 Beta | Long videos allowed; Shorts blocked |

---

## 🛠️ Architecture & File Structure

Built with **Kotlin** and **Jetpack Compose (Material3)** following modern Android architecture guidelines:

```
app/src/main/
├── AndroidManifest.xml              # Permissions, Activities, Accessibility Service
├── java/com/awaydoomscrollin/app/
│   ├── MainActivity.kt              # Jetpack Compose UI Dashboard & Feedback Engine
│   ├── AntiScrollService.kt         # Accessibility Engine & Anti-Scroll Shield
│   ├── AntiCheatGuiltActivity.kt    # Anti-Cheat popup dialog
│   └── ClearTaskActivity.kt         # Recents task clearing helper
└── res/
    ├── xml/accessibility_service_config.xml   # Accessibility flags & package targets
    └── drawable/                     # Platform brand icons
```

---

## 🚀 Building from Source

### Prerequisites
- Android Studio Jellyfish / Ladybug or newer
- Android SDK 34 (Build Tools 34.0.0)
- JDK 17 / Kotlin 1.9+

### Build Commands
```bash
# Clone the repository
git clone https://github.com/awaydoomscrollin/AwayDoomscrolling.git
cd AwayDoomscrolling

# Build Debug APK
./gradlew assembleDebug

# Build Unsigned Release APK
./gradlew assembleRelease
```

---

## 🤖 F-Droid & Google Play Metadata

This repository contains full [Fastlane Supply](https://docs.fastlane.tools/actions/supply/) structure under `fastlane/metadata/android/` for F-Droid automated builds.

---

## 📄 License

Distributed under the **GNU General Public License v3.0 (GPLv3)**. See `LICENSE` for details.
