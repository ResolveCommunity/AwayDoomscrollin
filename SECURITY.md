# Security Policy

## Supported Versions

| Version | Supported |
|---|---|
| Latest (main branch) | ✅ |
| Older releases | ❌ |

## Reporting a Vulnerability

**Please do not open public issues for security vulnerabilities.**

Report security issues privately via:
- **Email (Security & Support):** support@awaydoomscrollin.com
- **General Inquiries:** info@resolvecommunity.com
- **GitLab:** Use the [confidential issue](https://gitlab.com/resolve-community/AwayDoomscrollin/-/issues/new?issue%5Bconfidential%5D=true) feature

Please include:
- Description of the vulnerability
- Steps to reproduce
- Potential impact
- Suggested fix (if any)

We will respond within **72 hours** and aim to release a fix within **14 days** for critical issues.

## Scope

This app:
- Performs screen-content analysis and blocking locally on the device
- Declares Android `INTERNET` permission for two separate paths: opt-in telemetry and automatic GitHub rule/configuration requests
- Keeps telemetry disabled by default and sends no request to `awaydoomscrollin.com` until the user explicitly opts in
- Generates a random per-installation UUID for telemetry that is not derived from Android ID, IMEI, MAC address, hardware, account, or advertising data
- Sends that UUID, the disclosed device/app fields, and aggregate blocking, streak, and XP statistics to `awaydoomscrollin.com` while telemetry is enabled
- Stops future submissions when the user opts out; the latest server snapshot expires within 90 days
- Automatically fetches non-executable JSON rule/configuration updates from GitHub at app or accessibility-service startup; successful fetches are cached for six hours
- Treats GitHub rule fetching as independent of telemetry: **disabling telemetry does not disable this request**, and no separate rule-fetch switch currently exists
- Stores blocking statistics locally via `SharedPreferences` in addition to the disclosed aggregate telemetry submissions

## Beta Compatibility Scope

Instagram Reels, TikTok, and YouTube Shorts detection are beta features. Third-party UI/accessibility-tree changes and manufacturer-specific Android behavior can cause missed detections or false positives. Reports about those compatibility failures are in scope as functional bugs, but the project does not claim perfect detection or universal device compatibility.

Out of scope: vulnerabilities in Android OS, third-party libraries, or user device configurations.


