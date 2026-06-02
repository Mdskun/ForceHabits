<div align="center">

# 🔒 ForceHabits

### *Your phone doesn't let you off the hook.*

<p align="center">
  A strict, proof-enforced Android habit tracker that physically locks your phone until you prove you did the habit — using your camera, ML Kit pose detection, and a math challenge you can't muscle-memory past.
</p>

<br/>

[![Version](https://img.shields.io/badge/version-1.0-FF6B35?style=for-the-badge&logo=android)](https://github.com/mdskun/ForceHabits/releases)
[![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-✓-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow?style=for-the-badge)](LICENSE)

<br/>

> **Built for people who know they'll cheat any softer system — including Digital Wellbeing.**

<br/>

</div>

---

## 📋 Table of Contents

- [Overview](#-overview)
- [Features](#-features)
- [How It Works](#-how-it-works)
- [Screenshots](#-screenshots--demo)
- [Tech Stack](#-tech-stack)
- [Requirements](#-requirements)
- [Installation & Setup](#-installation--setup)
- [Configuration](#-configuration)
- [Project Structure](#-project-structure)
- [Architecture](#-architecture)
- [Changelog](#-changelog)
- [Contributing](#-contributing)
- [License](#-license)
- [Author](#-author)

---

## 🧠 Overview

### The Problem

Every habit app fails for the same reason: **you can dismiss it.**

Snooze the alarm. Tap "skip today." Turn off Digital Wellbeing when you're tempted. Pause the grayscale mode for 30 minutes. We've all done it. The apps that are supposed to help us build discipline are ultimately optional — and our brains know it.

### The Solution

**ForceHabits** removes the exit. When your alarm fires, a full-screen lock overlay appears over everything on your phone. The back button does nothing. The home button does nothing. The alarm loops at maximum volume.

The **only** way out is **physical proof** that you did the habit.

### Who It's For

- 🎯 People who've failed with every other habit app
- 🧠 Anyone who manipulates their own productivity tools when tempted
- 🏋️ People who need external accountability but don't have a partner
- 📵 Anyone whose screen time is genuinely harming them and knows it

---

## ✨ Features

<table>
<tr>
<td width="50%">

### 🤸 Pose-Enforced Exercise
Not a tap, not a checkbox. The front camera runs **ML Kit Pose Detection** live. You must physically perform each pose and hold it for 2 full seconds. The alarm does not stop until every pose in your sequence passes — in order.

</td>
<td width="50%">

### 📷 Computer Vision Photo Proofs
For plant care, hydration, and custom habits — take a photo. **ML Kit Image Labeling** analyzes it on-device (zero internet) and confirms it shows the required object. A photo of your hand won't pass for a glass of water.

</td>
</tr>
<tr>
<td width="50%">

### 📚 Anti-Cheat Reading Verification
4 photos of different pages. **ML Kit OCR** confirms each has ≥30 real words of text AND all 4 are different pages. Photographing the same page four times won't work.

</td>
<td width="50%">

### 🧮 Math-Challenge Screen Lock
For screen time, a random arithmetic problem fires silently the moment you open a blocked app. Wrong answers trigger an escalating cooldown — 3s, 5s, 10s, 15s. A new problem generates after 3 failed attempts. Can't memorize a PIN if the PIN changes every time.

</td>
</tr>
<tr>
<td width="50%">

### 📵 Unbypassable Screen Time
The **Accessibility Service** monitors foreground apps at the OS level. Within ~100ms of opening a blocked app past your limit, the math overlay fires. No "Pause for 30 minutes." No settings shortcut. No pause button inside the challenge.

</td>
<td width="50%">

### ⚙️ Per-App, Per-Habit Customization
Pick exactly which apps each Screen Limit habit blocks from your full installed app list. Set a night block window (handles overnight: 10:30 PM → 6:00 AM). Set a daily usage limit in minutes. Multiple screen limit habits can target different app sets simultaneously.

</td>
</tr>
<tr>
<td width="50%">

### 🔔 Flexible Scheduling
Every habit supports four schedule types: **Daily** at a fixed time, **Every X Hours** from a start time (great for hydration), **Every X Days** (great for plant care), or **Weekly** on specific days. Alarms survive reboots.

</td>
<td width="50%">

### ✅ Custom Habit Builder
Not everything fits a category. Pick any proof type for a custom habit. For photo proof, define the ML Kit labels yourself — "Coffee, Cup, Mug" if you're proving you made your morning coffee. Set how many photos are required.

</td>
</tr>
</table>

---

## 🎬 How It Works

### User Flow

```
Create Habit → Set Schedule → Configure Proof → Alarm Fires → Lock Screen → Submit Proof → Unlocked ✓
```

### Regular Habit (Exercise / Plant / Hydration / Reading / Custom)

```
1. Alarm fires at the scheduled time
2. AlarmSoundService starts — loops alarm at max volume via ForegroundService
3. LockOverlayActivity launches over everything (back button disabled)
4. User must complete the configured proof:
   └── POSE_SEQUENCE   → camera runs ML Kit live, each pose held 2s
   └── PHOTO_LABEL     → camera captures photo, ML Kit labels it on-device
   └── MULTI_PHOTO_TEXT→ 4 photos, OCR confirms text + unique pages
   └── NONE            → manual tap confirm
5. Proof passes → habit logged → streak incremented → alarm stopped → unlocked
```

### Screen Time Habit

```
1. Accessibility Service monitors foreground app (no alarm sound)
2. User opens a blocked app during the restricted window
3. Math challenge overlay fires within ~100ms
4. User must solve a random arithmetic problem
5. Wrong answers → escalating cooldown timer
6. Correct answer → overlay dismissed, usage logged
7. Resets at midnight
```

### Internal Architecture

```
Alarm fires
    └── AlarmReceiver (BroadcastReceiver)
            ├── Starts AlarmSoundService (ForegroundService, MediaPlayer, USAGE_ALARM)
            ├── Launches LockOverlayActivity (FLAG_SHOW_WHEN_LOCKED)
            └── Reschedules next occurrence (AlarmScheduler)

LockOverlayActivity
    └── LockOverlayScreen (Compose)
            ├── habitId == -99  → ScreenTimeLockGate → MathChallengeScreen
            └── habitId != -99  → ProofScreen
                    ├── POSE_SEQUENCE    → PoseProofScreen (CameraX + ML Kit)
                    ├── PHOTO_LABEL      → PhotoLabelProofScreen (CameraX + ML Kit)
                    ├── MULTI_PHOTO_TEXT → MultiPhotoTextProofScreen (CameraX + OCR)
                    └── NONE             → NormalHabitProofScreen
```

---

## 📱 Screenshots / Demo

<p align="center">
  <em>Screenshots coming soon — PRs with screenshots welcome!</em>
</p>

<p align="center">
  <table align="center">
    <tr>
      <td align="center"><b>Dashboard</b></td>
      <td align="center"><b>Add Habit</b></td>
      <td align="center"><b>Pose Proof</b></td>
      <td align="center"><b>Math Lock</b></td>
    </tr>
    <tr>
      <td align="center"><img src="docs/screenshots/dashboard.png" width="180" alt="Dashboard — placeholder"/></td>
      <td align="center"><img src="docs/screenshots/add_habit.png" width="180" alt="Add Habit — placeholder"/></td>
      <td align="center"><img src="docs/screenshots/pose_proof.png" width="180" alt="Pose Proof — placeholder"/></td>
      <td align="center"><img src="docs/screenshots/math_lock.png" width="180" alt="Math Lock — placeholder"/></td>
    </tr>
  </table>
</p>

> 📸 To add screenshots: create a `docs/screenshots/` folder, add your images, and they'll render automatically.

---

## ⚙️ Configuration

### First Launch — Two Permissions Required Manually

Android requires these to be granted manually. The app shows setup banners on the dashboard until both are done.

| Permission | Where to grant | Why it can't be auto-granted |
|---|---|---|
| **Display over other apps** (Overlay) | Settings → Apps → ForceHabits → Display over other apps | Android security restriction |
| **Accessibility Service** | Settings → Accessibility → Installed apps → ForceHabits → Enable | Android security restriction |

### Screen Time Habit Configuration

| Setting | Description | Default |
|---|---|---|
| **Blocked apps** | Apps selected from your installed app list | None (must select manually) |
| **Night block** | From time → until time (handles overnight windows) | 10:30 PM → 6:00 AM |
| **Daily usage limit** | Total minutes across blocked apps before challenge fires | 35 min |
| **Night block enabled** | Toggle — times are saved even when off | On |

### Alarm Behavior

| Scenario | Behavior |
|---|---|
| Phone off at alarm time | Fires on next boot (RECEIVE_BOOT_COMPLETED) |
| Phone rebooted | All alarms rescheduled from database automatically |
| Daily habit | Reschedules next day automatically after firing |
| Weekly habit | Schedules one alarm per enabled weekday |

---

## 🛠 Tech Stack

| Technology | Version | Purpose |
|---|---|---|
| **Kotlin** | 2.0.21 | Primary language |
| **Jetpack Compose** | BOM 2024.11.00 | Declarative UI |
| **Material 3** | via Compose BOM | Design system — dark-only, no dynamic color |
| **CameraX** | 1.4.1 | Camera preview, image capture, image analysis |
| **ML Kit Pose Detection** | 17.0.0 (stable) | Real-time body pose landmark detection |
| **ML Kit Image Labeling** | 17.0.9 | On-device image content classification |
| **ML Kit Text Recognition** | 16.0.1 | OCR for reading proof verification |
| **Room** | 2.6.1 | Local database — habits, logs, streaks |
| **Hilt** | 2.52 | Dependency injection |
| **AlarmManager** | Android SDK | Exact alarm scheduling, survives reboots |
| **AccessibilityService** | Android SDK | Foreground app monitoring for screen limits |
| **ForegroundService** | Android SDK | Keeps alarm sound alive when screen is off |
| **kotlinx.serialization** | 1.7.3 | JSON encoding/decoding for proof configs |
| **Navigation Compose** | 2.8.5 | In-app navigation |
| **WorkManager** | 2.10.0 | Background scheduling infrastructure |
| **Accompanist Permissions** | 0.36.0 | Compose-friendly runtime permission handling |
| **DataStore Preferences** | 1.1.1 | Key-value preferences storage |

> All ML Kit models run **fully on-device**. No Firebase, no API keys, no internet connection needed for any proof verification.

---

## 📋 Requirements

| Requirement | Details |
|---|---|
| **Android version** | 8.0 (API 26) minimum · Tested on Android 15 (API 35) |
| **Hardware** | Front-facing camera required for pose detection |
| **Rear camera** | Required for photo and reading proofs |
| **Internet** | Not required — all ML Kit inference is on-device |
| **Android Studio** | Hedgehog (2023.1.1) or newer |
| **JDK** | 21 |
| **Gradle** | 8.7.0 |

---

## 🚀 Installation & Setup

### Option A — Download APK directly

<p align="center">
  <a href="https://github.com/mdskun/ForceHabits/releases/latest">
    <img src="https://img.shields.io/badge/⬇️%20Download%20APK-Latest%20Release-FF6B35?style=for-the-badge" alt="Download APK"/>
  </a>
</p>

Enable "Install from unknown sources" on your device, then open the downloaded APK.

---

### Option B — Build from source

**1. Clone the repository**

```bash
git clone https://github.com/mdskun/ForceHabits.git
cd ForceHabits
```

**2. Open in Android Studio**

```
File → Open → select the ForceHabits folder → wait for Gradle sync
```

**3. No setup needed**

No API keys. No `google-services.json`. No Firebase. No environment variables. Just build and run.

**4. Run on device**

Connect your Android device with USB debugging enabled, then:

```bash
./gradlew installDebug
```

Or press the **▶ Run** button in Android Studio.

**5. Build release APK**

```bash
./gradlew assembleRelease
# Output: app/build/outputs/apk/release/app-release-unsigned.apk
```

---

### First Run Checklist

After installing, open the app. You'll see two setup banners:

```
[ 🪟 Allow overlay permission ]  ← tap Grant → enable in system settings
[ ♿ Enable Accessibility Service ]  ← tap Open Settings → find ForceHabits → enable
```

Once both banners disappear, the app is fully functional.

---

## 📁 Project Structure

```
ForceHabits/
├── app/
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── java/com/mdskun/forcehabits/
│           │
│           ├── HabitApplication.kt          # Hilt entry point, notification channels
│           ├── MainActivity.kt              # Edge-to-edge setup, theme, overlay permission
│           │
│           ├── data/
│           │   ├── model/
│           │   │   ├── Habit.kt             # Room entity · enums · schedule display logic
│           │   │   └── ProofConfig.kt       # Serializable configs for all proof types
│           │   └── db/
│           │       └── HabitDatabase.kt     # Room DB · DAOs · TypeConverters
│           │
│           ├── di/
│           │   └── AppModule.kt             # Hilt module — provides DB, DAO, AlarmScheduler
│           │
│           ├── alarm/
│           │   ├── AlarmScheduler.kt        # Exact alarms for Daily/X-Hours/X-Days/Weekly
│           │   ├── AlarmReceiver.kt         # Fires sound service + lock overlay on trigger
│           │   └── AlarmSoundService.kt     # ForegroundService · looping alarm · max volume
│           │
│           ├── lock/
│           │   ├── LockOverlayActivity.kt   # Full-screen lock · back button disabled
│           │   ├── LockOverlayScreen.kt     # Routes: habit proof vs screen-time math gate
│           │   └── LockViewModel.kt         # Marks complete · stops alarm · updates streak
│           │
│           ├── proof/
│           │   ├── ProofScreen.kt           # Top-level router by ProofType
│           │   ├── PoseProofScreen.kt       # CameraX + ML Kit live pose detection + skeleton
│           │   ├── PhotoLabelProofScreen.kt # CameraX capture + ML Kit image labeling
│           │   ├── MultiPhotoTextProofScreen.kt  # 4-photo OCR · anti-duplicate detection
│           │   └── SimpleProofScreens.kt    # Math challenge · manual check-in · app picker
│           │
│           ├── accessibility/
│           │   └── HabitAccessibilityService.kt  # Foreground app monitoring · usage tracking
│           │
│           └── ui/
│               ├── AppNavigation.kt         # Compose navigation graph
│               ├── DashboardScreen.kt       # Habit list · streak display · setup banners
│               ├── DashboardViewModel.kt
│               ├── AddHabitScreen.kt        # Full creation UI — type · proof · schedule · apps
│               ├── AddHabitViewModel.kt     # Saves habit · schedules alarm · configures service
│               ├── HabitDetailScreen.kt     # Detail view · proof overlay · history
│               ├── HabitDetailViewModel.kt
│               └── theme/
│                   └── Theme.kt             # Dark-only · custom color scheme · no dynamic color
│
├── gradle/
│   └── libs.versions.toml                  # Version catalog for all dependencies
├── .gitignore
├── README.md
└── LICENSE
```

---

## 🏗 Architecture

ForceHabits follows **MVVM (Model-View-ViewModel)** with a clean unidirectional data flow.

```
┌──────────────────────────────────────────────────────┐
│                        UI Layer                       │
│   Compose Screens  ←→  ViewModels (Hilt, StateFlow)  │
└──────────────────────────┬───────────────────────────┘
                           │
┌──────────────────────────▼───────────────────────────┐
│                     Domain Layer                      │
│   AlarmScheduler · ProofValidation · AccessService   │
└──────────────────────────┬───────────────────────────┘
                           │
┌──────────────────────────▼───────────────────────────┐
│                      Data Layer                       │
│        Room (HabitDao) · SharedPreferences            │
└──────────────────────────────────────────────────────┘
```

**Key design decisions:**

- **No internet dependency** — all ML inference happens on-device via ML Kit's bundled models
- **ForegroundService for alarm** — ensures alarm continues even when the phone screen is off or the app is in the background
- **AccessibilityService for screen limits** — the only Android API that can monitor foreground apps without root; fires overlays at the OS level before the app fully renders
- **Per-habit blocked app lists** — stored as `blocked_apps_habit_{id}` keys so multiple screen limit habits can target different app sets simultaneously
- **Immutable `Set<String>` for app selection** — avoids Compose recomposition bugs that occur with `MutableSet`

---

## 📝 Changelog

### v1.0 — Initial Release

**Habit Engine**
- 6 habit types: Exercise, Plant Care, Hydration, Reading, Screen Limit, Custom
- 4 schedule frequencies: Daily, Every X Hours, Every X Days, Weekly
- Alarms survive device reboots via `RECEIVE_BOOT_COMPLETED`
- Streak tracking and completion history

**Proof System**
- ML Kit pose detection with skeleton overlay (shoulder-width normalised, resolution-independent)
- ML Kit image labeling for plant and hydration photo proofs
- 4-page OCR reading proof with anti-duplicate detection
- Randomized arithmetic math challenge for screen time
- Custom photo labels for Custom habit type
- Increasing cooldown timer on wrong math answers

**Screen Time Enforcement**
- Accessibility Service monitors foreground app at OS level
- Per-app selection from full installed app list
- Night block with overnight window support
- Per-day usage limit in minutes with midnight reset
- Per-habit app lists (multiple habits can target different apps)

**UX**
- Full-screen lock overlay with back button disabled
- Edge-to-edge dark theme with no dynamic color
- Setup banners for required manual permissions
- Camera error reporting in pose proof screen

---

## 🤝 Contributing

Contributions are what make open source great. Any contribution is **genuinely welcome**.

### How to contribute

```bash
# 1. Fork the repository
# 2. Create your feature branch
git checkout -b feature/your-feature-name

# 3. Make your changes, then commit
git commit -m "feat: add your feature description"

# 4. Push to your fork
git push origin feature/your-feature-name

# 5. Open a Pull Request
```

### What's needed

- 📸 **Screenshots** — the README placeholder images need real ones
- 🌙 **New habit types** — e.g. meditation timer, journal entry proof
- 🌐 **Translations** — string resources for other languages
- 🐛 **Bug reports** — open an issue with device/Android version + steps to reproduce
- 🎨 **UI improvements** — the design is functional, not final

### Guidelines

- Follow the existing code style (Kotlin conventions, Compose patterns)
- Keep proof logic in the `proof/` package
- Keep scheduling logic in the `alarm/` package
- New habit types need a proof validator, a DB entry, and a schedule config

---

## 📄 License

```
MIT License — Copyright (c) 2026 Manthan D Soni

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction...
```

See the full [LICENSE](LICENSE) file.

---

## 👤 Author

<p align="center">

**Manthan D Soni**

[![GitHub](https://img.shields.io/badge/GitHub-mdskun-181717?style=for-the-badge&logo=github)](https://github.com/mdskun)

</p>

---

<p align="center">

Built out of personal frustration with every other habit app. <br/>
If it's helped you stay accountable too, give it a ⭐ — it means a lot.

<br/><br/>

<sub>ForceHabits · MIT License · Made with Kotlin and Jetpack Compose</sub>

</p>
