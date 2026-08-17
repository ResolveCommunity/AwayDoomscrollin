# Privacy Policy

**Effective Date:** August 17, 2026

## 1. Introduction
Resolve Community ("we," "our," or "us") is committed to protecting your privacy. This Privacy Policy explains our data collection and processing practices for the **AwayDoomscrollin'** Android application.

Our philosophy is simple: keep screen-content analysis on-device and disclose every network path clearly.

## 2. Accessibility Service API
AwayDoomscrollin' utilizes the Android Accessibility Service API exclusively to detect when you enter targeted short-video platforms (e.g., Instagram Reels, TikTok, YouTube Shorts). 
- **What it does:** It inspects accessibility node text, content descriptions, view identifiers, and scroll/window events in memory to recognize target feeds and safe areas. If detected, it intervenes to stop the scrolling loop.
- **What it does NOT do:** It does not record the screen, retain accessibility content, or include screen text, messages, comments, passwords, keystrokes, photos, or browsing history in network payloads.
- **Beta status and limitations:** Instagram, TikTok, and YouTube Shorts detection are beta features. Their interfaces and accessibility trees are controlled by third parties and can change without notice. Detection can therefore be missed or a safe area can be misclassified. No claim of perfect detection, perfect safe-zone classification, or compatibility with every device/OS build is made.

## 3. Data Collection, Telemetry, and Remote Rules
The core screen analysis and blocking functionality operates locally, but the app is not fully offline. Its Android manifest declares `android.permission.INTERNET` for two distinct network paths:

| Network path | Default | Destination | Trigger and frequency | User control |
|---|---|---|---|---|
| Pseudonymous telemetry | Off | `https://awaydoomscrollin.com/api/telemetry` | First explicit opt-in may attempt immediately; afterward automatic app-startup and blocking-event triggers share one persisted 24-hour attempt interval | Can be enabled or disabled during onboarding and later in About |
| Remote rule/configuration JSON | Automatic | `https://raw.githubusercontent.com/ResolveCommunity/rules/main/rules.json` | App startup or accessibility-service activation; a successful response is cached for six hours | No separate in-app switch |

### 3.1 Explicit opt-in telemetry

Telemetry is disabled by default. On a clean installation, the app does not create a telemetry installation UUID and does not make a request to `awaydoomscrollin.com` unless the user explicitly enables the telemetry switch. Enabling it may immediately attempt the first submission and creates a random per-installation UUID in `SharedPreferences`. The UUID is not derived from hardware, an account, Android ID, IMEI, a MAC address, or advertising data. Clearing app data or uninstalling the app removes it.

After explicit opt-in, automatic app-startup and blocking-event triggers share one persisted 24-hour **attempt** interval. The timestamp is reserved synchronously before networking, so concurrent events do not launch duplicate requests; a failed attempt also consumes the interval. Disabling and explicitly re-enabling telemetry may start a new immediate opt-in attempt. Turning telemetry off stops future submissions. Because networking is asynchronous, a request already handed to the operating-system network stack may finish; the app checks the preference before preparing and immediately before starting a request. The latest previously submitted server snapshot is retained for no more than 90 days and then deleted. The server stores a SHA-256 installation key rather than the raw UUID.

### 3.2 Automatic GitHub rule/configuration requests

**This path is independent of telemetry. Turning telemetry off does not stop or disable GitHub requests.** When the app or accessibility service starts, it attempts to fetch non-executable JSON rule/configuration data unless a successful result is already cached and less than six hours old. There is currently no separate in-app switch for this request. The request may therefore occur on first launch even though telemetry is still off. If the request fails or the device is offline, built-in rules remain available.

The remote JSON can contain detection keywords/view IDs and update or announcement configuration. It is parsed as data; it is not downloaded executable code. GitHub and the network provider can observe ordinary connection metadata according to their own policies.

### 3.3 Data sent while telemetry is enabled

- We do not ask for a name, email address, phone number, account identifier, precise location, contacts, photos, screen contents, messages, passwords, Android ID, IMEI, MAC address, or advertising identifier in the Android telemetry payload.
- Telemetry is **pseudonymous, not anonymous**: the random installation UUID makes submissions from the same installation linkable until app data is cleared or the app is reinstalled.
- **If telemetry is enabled, the following pseudonymous data is collected and sent to our servers:**
  - **Message Type:** The constant marker `PSEUDONYMOUS_TELEMETRY`.
  - **Random Installation Identifier:** A UUID generated by this app installation and used to keep snapshots from different installations separate.
  - **Device Information:** Manufacturer, Model, Android Version, SDK Level.
  - **App Statistics:** Total blocks, specific platform blocks (Instagram, TikTok, YouTube), Streak days, and User XP.
  - **App Version.**
  - *The telemetry payload does not contain an IP address, MAC address, location, screen content, hardware identifier, account identifier, or advertising identifier. It does contain the random installation UUID described above. As with any HTTPS request, the server, reverse proxy, and network provider can observe the source IP at the transport layer. The application telemetry database does not store that IP as a telemetry field.*

## 4. Open Source Transparency
AwayDoomscrollin' is fully open-source. The entire codebase is publicly auditable on our [GitHub Repository](https://github.com/ResolveCommunity/AwayDoomscrollin), ensuring complete transparency regarding how the app functions and handles your data.

## 5. Contact Us
For any questions regarding this Privacy Policy or the app's security, please contact us at:
**Email:** info@resolvecommunity.com

---

For the full legal Privacy Policy and Terms of Service, please visit our official website:
- **Privacy Policy:** [https://awaydoomscrollin.com/privacy](https://awaydoomscrollin.com/privacy)
- **Terms of Service:** [https://awaydoomscrollin.com/terms](https://awaydoomscrollin.com/terms)




