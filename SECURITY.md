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
- Requests **zero internet permissions** — no network traffic in production builds
- Stores all data locally via `SharedPreferences` on the device
- Optional telemetry (disabled by default) sends only anonymous aggregate counts

Out of scope: vulnerabilities in Android OS, third-party libraries, or user device configurations.
