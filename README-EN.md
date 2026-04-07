# Xinyi Relay

<div align="center">
    <a href="https://play.google.com/store/apps/details?id=io.github.magisk317.relay">
        <img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" alt="Get it on Google Play" height="80"/>
    </a>
    <a href="https://github.com/magisk317/xinyi-relay/releases">
        <img src="https://raw.githubusercontent.com/machiav3lli/oandbackupx/master/badge_github.png" alt="Get it on GitHub" height="80"/>
    </a>
</div>

<div align="center">

[![Commits](https://img.shields.io/github/commit-activity/y/magisk317/xinyi-relay?style=flat-square)](https://github.com/magisk317/xinyi-relay/graphs/commit-activity) [![Last Commit](https://img.shields.io/github/last-commit/magisk317/xinyi-relay?style=flat-square)](https://github.com/magisk317/xinyi-relay/commits) [![Contributors](https://img.shields.io/github/contributors/magisk317/xinyi-relay?style=flat-square)](https://github.com/magisk317/xinyi-relay/graphs/contributors) [![CI](https://img.shields.io/github/actions/workflow/status/magisk317/xinyi-relay/ci.yml?branch=beta&style=flat-square&label=Build&logo=github-actions&logoColor=white)](https://github.com/magisk317/xinyi-relay/actions/workflows/ci.yml) [![Latest Release](https://img.shields.io/github/v/release/magisk317/xinyi-relay?include_prereleases&style=flat-square&logo=github)](https://github.com/magisk317/xinyi-relay/releases) [![Release Date](https://img.shields.io/github/release-date/magisk317/xinyi-relay?style=flat-square)](https://github.com/magisk317/xinyi-relay/releases) [![Downloads](https://img.shields.io/github/downloads/magisk317/xinyi-relay/total?style=flat-square&color=blue)](https://github.com/magisk317/xinyi-relay/releases) [![License](https://img.shields.io/github/license/magisk317/xinyi-relay?style=flat-square)](LICENSE)

[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.20-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org) [![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-BOM_2026.03.01-4285F4?style=flat-square&logo=android&logoColor=white)](https://developer.android.com/jetpack/compose) [![Gradle](https://img.shields.io/badge/Gradle-9.5.0--nightly-02303A?style=flat-square&logo=gradle&logoColor=white)](https://gradle.org) [![AGP](https://img.shields.io/badge/AGP-9.1.0-3DDC84?style=flat-square&logo=gradle&logoColor=white)](https://developer.android.com/studio/releases/gradle-plugin) [![Min SDK](https://img.shields.io/badge/Min_SDK-26-brightgreen?style=flat-square&logo=android)](https://developer.android.com/about/versions) [![Target SDK](https://img.shields.io/badge/Target_SDK-37-blue?style=flat-square&logo=android)](https://developer.android.com/about/versions) [![Xposed API](https://img.shields.io/badge/Xposed_API-101-orange?style=flat-square)](https://github.com/rovo89/XposedBridge) [![Telegram](https://img.shields.io/badge/Telegram-Group-2CA5E0?style=flat-square&logo=telegram&logoColor=white)](https://t.me/+NR2QaQ4dlEgxYmNl)

</div>

Xinyi Relay is a relay and verification-code autofill project for Xposed/LSPosed, with unified handling for SMS, app notifications, and incoming call events.

The project now ships in two parts:

- Android App: local event capture, verification parsing, autofill, and Xposed hooks
- Backend: device binding, config snapshots, record upload, and the Web / Desktop remote console

The old embedded WebUI has been retired from the Android runtime path. The current official architecture is `Android Agent + Backend + Web / Desktop`.

[中文版本](./README.md)

# Screenshots
<img src="./art/common/01.png" width="720"/>

# Communication & Feedback
- [Telegram Group](https://t.me/+NR2QaQ4dlEgxYmNl)

## Android App

The Android app is the on-device module for Xposed/LSPosed. It is responsible for local event capture, verification parsing, autofill, and runtime hooks.

### Install & Use
1. Root your device and install LSPosed/Xposed.
2. Install Xinyi Relay and pick the matching APK:
   - GitHub Releases: `legacy` and `api101`
   - Google Play: `api101` only
3. Enable the module and reboot.
4. Configure sender channels, routing rules, filters, and verification-code autofill policies.

### Compatibility
- **Minimum Android 8.0 (API 26), target SDK 37.**
- **Designed for AOSP-like systems; heavily customized ROMs may have compatibility issues.**
- **Codebase: 100% Kotlin + Jetpack Compose + Room + Coroutines.**

### Core Features
- SMS relay: relay verification SMS and plain SMS with rules
- App notification relay: bind apps to sender channels
- Call-event relay: capture and relay incoming-call metadata
- Global and app-level filtering: keywords, sources, priorities, and forward filters
- Verification code parsing, copy, and autofill
- Records and backup: export/import config and history

## Backend

The Backend is the self-hosted remote control plane for Xinyi Relay, with a local-first deployment model and optional public access.

### Stack
- Go API
- PostgreSQL
- Caddy
- Web console
- Tauri desktop shell

### Default Deployment
- Docker Compose pulls `ghcr.io/magisk317/xinyi-relay-backend:beta` by default
- Android Agent, Web, and Desktop share the same backend API
- Local HTTPS uses Caddy `tls internal`, with user CA installation available for Android Agent

### Entry Docs
- [Backend Guide](backend/README.md)
- [Backend API Overview](backend/API_OVERVIEW.md)
- [Remote Architecture](docs/REMOTE_ARCHITECTURE.md)

Feedback and suggestions are welcome.

# Release Metadata
- Fastlane metadata location: `fastlane/metadata/android`
- Sync Fastlane changelogs/screenshots before release: `scripts/sync_fastlane_metadata.sh`
- Validate release metadata and tag consistency: `scripts/check_release_guard.sh`
- Fastlane changelog files `changelogs/{versionCode}.txt` are synchronized from `distribution/whatsnew`.

# Repository Notes
- The repository root is the primary build entry for day-to-day development.
- `smscode-core` is treated as an embedded shared-library submodule, not a parallel primary root project.
- Runtime layering and module-boundary guidance lives in [docs/REFACTORING.md](docs/REFACTORING.md).

# Documentation
- [Release Logs](docs/CHANGELOG.md)
- [Architecture & Runtime Refactoring](docs/REFACTORING.md)
- [Remote Architecture](docs/REMOTE_ARCHITECTURE.md)
- [Backend Guide](backend/README.md)
- [Backend API Overview](backend/API_OVERVIEW.md)
- [Privacy Policy](docs/PRIVACY.md)
- [Donations](docs/DONATIONS.md)

# Thanks To
- [Original Project (tianma8023/XposedSmsCode)](https://github.com/tianma8023/XposedSmsCode)
- [Xposed](https://github.com/rovo89/Xposed)
- [NekoSMS](https://github.com/apsun/NekoSMS)
- [Material Dialogs](https://github.com/afollestad/material-dialogs)
- [EventBus](https://github.com/greenrobot/EventBus)
- [Room](https://developer.android.com/training/data-storage/room)
- [Kotlin Serialization](https://github.com/Kotlin/kotlinx.serialization)
- [Kotlin Coroutines](https://github.com/Kotlin/kotlinx.coroutines)
- [Material Design 3](https://m3.material.io/)
- [Jetpack Compose](https://developer.android.com/jetpack/compose)

# License
All code is licensed under [GPLv3](https://www.gnu.org/licenses/gpl-3.0.txt).

# Donation
If this project helps you, consider supporting development. Your support directly helps maintenance and iteration.

Donation list and details: [Donations](docs/DONATIONS.md).

| Alipay Receipt | WeChat Appreciation | WeChat Collect |
| :---: | :---: | :---: |
| ![Alipay](./art/sponsorship/alipay.png) | ![WeChat Appreciation](./art/sponsorship/wx.png) | ![WeChat Collect](./art/sponsorship/wx_collect.png) |

# Star History
![Star History Chart](https://api.star-history.com/svg?repos=magisk317/xinyi-relay&type=Date)
