# Contributing to AwayDoomscrollin'

Thank you for your interest in contributing! Here's how to get started.

---

## 🐛 Reporting Bugs

1. Search [existing issues](https://gitlab.com/resolve-community/AwayDoomscrollin/-/issues) first.
2. If not found, open a new issue with:
   - Android version and device model
   - Steps to reproduce
   - Expected vs actual behavior
   - Logcat output (if possible)

## 💡 Suggesting Features

Open an issue with the `enhancement` label and describe:
- The problem you're solving
- Your proposed solution
- Any alternative approaches you considered

## 🔧 Pull Requests

1. Fork the repository
2. Create a feature branch: `git checkout -b feat/your-feature`
3. Follow the existing code style (Kotlin + Jetpack Compose)
4. Keep UI changes consistent with the Cyberpunk/Neon ZenTheme color system
5. Test on a real device (Samsung One UI preferred)
6. Submit your pull request with a clear description

## 📋 Code Style

- Kotlin idiomatic code, no Java interop unless necessary
- All new UI composables should use the existing color tokens (`#00F2FE`, `#00FF87`, `#FF0055`, `#0F1523`)
- New SharedPreferences keys must be documented in a comment near their usage

## ⚖️ License

By contributing, you agree that your contributions will be licensed under the **GPLv3** license.
