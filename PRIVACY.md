# Privacy Policy

**Effective Date:** August 8, 2026

## 1. Introduction
Resolve Community ("we," "our," or "us") is committed to protecting your privacy. This Privacy Policy explains our data collection and processing practices for the **AwayDoomscrollin'** Android application.

Our philosophy is simple: keep screen-content analysis on-device and disclose every network path clearly.

## 2. Accessibility Service API
AwayDoomscrollin' utilizes the Android Accessibility Service API exclusively to detect when you enter targeted short-video platforms (e.g., Instagram Reels, TikTok, YouTube Shorts). 
- **What it does:** It inspects accessibility node text, content descriptions, view identifiers, and scroll/window events in memory to recognize target feeds and safe areas. If detected, it intervenes to stop the scrolling loop.
- **What it does NOT do:** It does not record the screen, retain accessibility content, or include screen text, messages, comments, passwords, keystrokes, photos, or browsing history in network payloads.

## 3. Data Collection, Telemetry, and Remote Rules
The core screen analysis and blocking functionality of AwayDoomscrollin' operates locally. The app requests the `INTERNET` permission for the following two network paths:
- **Default-on telemetry:** Anonymous telemetry is enabled by default and can be disabled at any time in the app settings. The app attempts a telemetry submission at startup subject to a 24-hour throttle, and after blocking events.
- **Automatic remote rules:** Independently of the telemetry switch, the app contacts GitHub when the app or accessibility service starts to fetch updated rule/configuration data. Successful fetches are cached for six hours. There is currently no separate in-app switch for this request.
- We do not collect, transmit, monetize, or share any Personally Identifiable Information (PII).
- **If telemetry is enabled, the following anonymous data is collected and sent to our servers:**
  - **Device Information:** Manufacturer, Model, Android Version, SDK Level.
  - **App Statistics:** Total blocks, specific platform blocks (Instagram, TikTok, YouTube), Streak days, and User XP.
  - **App Version.**
  - *The app's telemetry payload does not contain an IP address, MAC address, location, screen content, or a persistent unique identifier. As with any HTTPS request, the receiving server can observe the source IP at the transport layer.*

## 4. Open Source Transparency
AwayDoomscrollin' is fully open-source. The entire codebase is publicly auditable on our [GitHub Repository](https://github.com/ResolveCommunity/AwayDoomscrollin), ensuring complete transparency regarding how the app functions and handles your data.

## 5. Contact Us
For any questions regarding this Privacy Policy or the app's security, please contact us at:
**Email:** info@resolvecommunity.com

---

For the full legal Privacy Policy and Terms of Service, please visit our official website:
- **Privacy Policy:** [https://awaydoomscrollin.com/privacy](https://awaydoomscrollin.com/privacy)
- **Terms of Service:** [https://awaydoomscrollin.com/terms](https://awaydoomscrollin.com/terms)




