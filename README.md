# ForceHabits

A strict, proof-based Android habit tracker that physically cannot be dismissed until you prove you've done the habit. No snooze buttons. No "I'll do it later." Built for people who know they'll cheat any softer system — including Digital Wellbeing.

---

## What makes this different from a normal habit app

Every habit you create requires **physical proof** to mark as complete. When an alarm fires, a lock screen overlays everything on your phone and a looping alarm sound plays. You cannot dismiss it by pressing Back. You cannot dismiss it by pressing Home. The only exit is completing the proof.

For screen time limits, a math problem replaces the lock screen — silently, with no alarm sound — the moment you open a blocked app past your configured limit. Wrong answers add an increasing wait timer.

---

## Habit types and their proofs

| Habit | Proof method | How it works |
|---|---|---|
| **Exercise** | Pose sequence | Front camera + ML Kit detects body poses live. Each pose must be held for 2 seconds. Alarm won't stop until every pose in the sequence passes. |
| **Plant Care** | Photo of plant | Take a photo. ML Kit image labeling confirms it contains a plant/flower/vegetation on-device. No internet. |
| **Hydration** | Photo of drink | Photo confirmed to contain a cup/glass/bottle/water label. |
| **Reading** | 4 different page photos | OCR reads each photo. Confirms each has ≥30 real words, and all 4 are different pages — no photographing the same page twice. |
| **Screen Limit** | Math challenge | A random arithmetic problem (addition, subtraction, multiplication). Wrong answers trigger a cooldown timer that grows with each mistake. Can't muscle-memory it like a PIN. |
| **Custom** | Your choice | Pick any proof type. For photo proof, you define what the photo should contain. |

---

## Screen time enforcement

This is stronger than Digital Wellbeing because it cannot be paused, disabled, or cheated from inside the same settings menu you used to configure it.

The Accessibility Service monitors which app is in the foreground. When you open a blocked app during a restricted window, a math-challenge overlay fires within ~100ms. You can configure:

- **Night block** — block selected apps from a start time to an end time every day (handles overnight windows like 10:30 PM → 6:00 AM correctly)
- **Per-day usage limit** — block after N minutes of total combined use across selected apps, resets at midnight
- **Custom app selection** — pick exactly which apps are blocked per habit from your full installed app list. Multiple screen limit habits can target different app sets.

---

## Schedule frequencies

Habits are not just daily. You can schedule:

- **Daily** — fires every day at a fixed time
- **Every X hours** — fires every N hours starting from a base time (good for hydration)
- **Every X days** — fires every N days at a fixed time (good for watering plants)
- **Weekly** — fires on specific days of the week (pick Mon/Wed/Fri, etc.)

---

## Tech stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Camera | CameraX |
| Pose detection | ML Kit Pose Detection (on-device, no internet) |
| Image labeling | ML Kit Image Labeling (on-device) |
| OCR | ML Kit Text Recognition (on-device) |
| Database | Room |
| Dependency injection | Hilt |
| Alarm scheduling | AlarmManager (exact, survives reboots) |
| Alarm sound | ForegroundService + MediaPlayer (USAGE_ALARM, max volume) |
| Screen enforcement | AccessibilityService |
| Serialization | kotlinx.serialization |
| Navigation | Jetpack Navigation Compose |

---

## Requirements

- **Android 8.0+** (minSdk 26)
- **Tested on Android 15** (API 35)
- Physical camera required
- Permissions requested at runtime: Camera, Notifications, Exact Alarm, Overlay, Accessibility Service

---

## Setup

### 1. Clone

```bash
git clone https://github.com/YOUR_USERNAME/ForceHabits.git
cd ForceHabits
```

### 2. Open in Android Studio

Open Android Studio → **File → Open** → select the `ForceHabits` folder. Let Gradle sync finish.

### 3. No API keys needed

All ML Kit models run fully on-device. No Firebase setup, no `google-services.json`, no internet required for any proof.

### 4. Build and run

Connect your Android device (USB debugging enabled) and click **Run**, or:

```bash
./gradlew assembleDebug
```

The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

---

## First launch checklist

When you first open the app, two setup banners appear on the dashboard:

1. **Overlay permission** — tap "Grant" → find ForceHabits in the list → enable "Allow display over other apps". Required for the lock screen to appear over other apps when an alarm fires.

2. **Accessibility Service** — tap "Open Settings" → Accessibility → Installed apps → ForceHabits → **Force Habits Screen Monitor** → enable it. Required for screen time enforcement.

Neither of these can be granted automatically by the app — Android requires manual confirmation from the user.

---

## Project structure

```
app/src/main/java/com/mdskun/forcehabits/
├── HabitApplication.kt           # Hilt application, notification channels
├── MainActivity.kt               # Entry point, edge-to-edge, theme
│
├── data/
│   ├── model/
│   │   ├── Habit.kt              # Room entity, schedule logic
│   │   └── ProofConfig.kt        # Serializable proof config models
│   └── db/
│       └── HabitDatabase.kt      # Room database, DAOs
│
├── di/
│   └── AppModule.kt              # Hilt module — DB, DAO, scheduler
│
├── alarm/
│   ├── AlarmScheduler.kt         # Schedules exact alarms for all frequency types
│   ├── AlarmReceiver.kt          # BroadcastReceiver — fires alarm, reschedules next
│   └── AlarmSoundService.kt      # ForegroundService — looping alarm at max volume
│
├── lock/
│   ├── LockOverlayActivity.kt    # Full-screen lock activity, back button disabled
│   ├── LockOverlayScreen.kt      # Composable — routes habit proof vs screen-time gate
│   └── LockViewModel.kt          # Marks habit complete, stops alarm
│
├── proof/
│   ├── ProofScreen.kt            # Routes to correct proof screen by ProofType
│   ├── PoseProofScreen.kt        # ML Kit pose detection, skeleton overlay, hold timer
│   ├── PhotoLabelProofScreen.kt  # CameraX capture + ML Kit image labeling
│   ├── MultiPhotoTextProofScreen.kt  # 4-photo OCR proof for reading
│   └── SimpleProofScreens.kt    # Math challenge, manual check-in, app picker
│
├── accessibility/
│   └── HabitAccessibilityService.kt  # Monitors foreground app, enforces screen limits
│
└── ui/
    ├── AppNavigation.kt
    ├── DashboardScreen.kt        # Habit list, setup banners, streak display
    ├── DashboardViewModel.kt
    ├── AddHabitScreen.kt         # Full habit creation UI — type, proof, schedule, apps
    ├── AddHabitViewModel.kt
    ├── HabitDetailScreen.kt      # Habit info, "Complete Now" button, proof overlay
    ├── HabitDetailViewModel.kt
    └── theme/
        └── Theme.kt              # Dark-only theme, no dynamic color
```

---

## Permissions explained

| Permission | Why |
|---|---|
| `CAMERA` | Taking proof photos and pose detection |
| `SYSTEM_ALERT_WINDOW` | Drawing the lock screen over other apps |
| `FOREGROUND_SERVICE` | Keeping alarm sound alive in background |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Required service type for audio playback |
| `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` | Alarms fire at the exact time you set |
| `RECEIVE_BOOT_COMPLETED` | Re-schedule all alarms after phone reboot |
| `WAKE_LOCK` | Keeps CPU alive so alarm fires when screen is off |
| `DISABLE_KEYGUARD` | Shows lock screen over the Android lock screen |
| `POST_NOTIFICATIONS` | Alarm notification shown while alarm is playing |
| `QUERY_ALL_PACKAGES` | Required on Android 11+ to list installed apps for the screen time picker |

---

## Contributing

Pull requests welcome. For large changes, open an issue first to discuss what you'd like to change.

---

## License

MIT — see `LICENSE` file.
