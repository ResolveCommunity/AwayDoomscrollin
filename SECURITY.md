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
- Enables anonymous telemetry by default; users can disable it in the app
- Sends the disclosed device/app fields and aggregate blocking, streak, and XP statistics to `awaydoomscrollin.com` while telemetry is enabled
- Automatically fetches rule/configuration updates from GitHub at app or accessibility-service startup; successful fetches are cached for six hours and this path is independent of the telemetry switch
- Stores blocking statistics locally via `SharedPreferences` in addition to the disclosed aggregate telemetry submissions

Out of scope: vulnerabilities in Android OS, third-party libraries, or user device configurations.


