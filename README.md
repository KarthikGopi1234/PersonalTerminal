<p align="center">
  <img src="docs/logo/logo_192.png" width="128" alt="Personal Terminal logo">
</p>

<h1 align="center">Personal Terminal</h1>

<p align="center">
  <code>user@android $ daily</code><br>
  A terminal-themed habit tracker for Android, inspired by <em>Init Habits</em> — with a built-in
  Pomodoro timer, streak shields, a GitHub-style contribution heatmap, an interactive home-screen
  widget, automatic Google Drive backups and a wristwatch wear tracker.
</p>

---

```
[  OK  ] mounting /habits
[  OK  ] loading streak engine
[  OK  ] starting pomodoro daemon
[  OK  ] initialising watch registry
[  OK  ] personal-terminal.service started
```

## Features

### `$ today` — habit tracking
| Mode | Example | Notes |
|------|---------|-------|
| **Checkbox** | `[✓] stretch` | simple wins, one tap |
| **Counter** | `[~] drink water 5/8 cups` | `[-]`/`[+]` steppers, ASCII progress bar |
| **Timer** | `[▶] focus session 25/50m` | minutes logged automatically by the Pomodoro timer |

* **Streaks & Shields** — consecutive scheduled days are counted per habit; unscheduled days never break a chain.
  Every 10 completions earns a **shield ⛨** (max 3 held). When a streak breaks, the app offers to spend a shield
  on the missed day and the chain is repaired.
* **Schedules** — `daily`, `x/week` (quota-based) or `specific days` (Mon…Sun bitmask).
* **Routines** — group habits into `morning/`, `deep work/`, `evening/` … with reorder controls.
* **XP & Levels** — `+10` per completion, `+25` for every 7-day streak, `+20` for a perfect day.
  Levels follow `50·L·(L+1)` with terminal-flavoured titles (`guest → user → committer → … → kernel`).
* **Profile** — 52-week GitHub-style contribution heatmap, streak stats, XP ledger.
* **Timeline** — month calendar coloured by completion with the watch of the day overlaid, plus a day-by-day log.

### `$ pomodoro` — focus timer
Foreground-service Pomodoro (focus / break / long break every 4) with notification controls.
Completed focus phases add minutes to the bound timer habit; stopping early credits whole minutes.

### `$ widget` — home screen
Interactive Glance widget rendered like the app: `[✓] name`, `████░░ 66%`. Tapping a row toggles a
checkbox habit or increments a counter/timer without opening the app. Responsive 2 × 2 → 4 × 3 sizes.

### `$ watch` — wear tracker
* **Collection** — brand, model, nickname, reference, movement, case size, accent colour, notes, profile photo.
* **Camera** — in-app CameraX capture (or gallery import) for the watch profile and daily wrist shots;
  photos are down-scaled to 1600 px and stored privately.
* **Log** — which watch was worn on which day (multiple per day allowed), with note + photo.
  Shown on the Today screen, on the Timeline calendar (`⌚`), and per watch as a heatmap + weekday distribution.

### `$ backup` — Google Drive sync
* Sign in with Google (Credential Manager) and authorise the `drive.file` scope — the app can only see files it created.
* Backups are `.ptbak` zip archives (`data.json` + `media/*.jpg`) uploaded to **`Personal Terminal Backups/`** in My Drive.
* **Automatic**: periodic WorkManager job (6 h / 12 h / 24 h / 72 h) **and** a debounced upload ~10 min after any change
  (requires network, battery not low). Keeps the 10 newest backups.
* **Restore** from any remote backup, or export/import the same archive locally via the system file picker.

### Themes
Dark **and** light variants of **Dracula** (Alucard), **Nord**, **Solarized**, **Gruvbox**, **Monokai**, **Catppuccin**
(Mocha/Latte) and a **Matrix** phosphor theme. Follows system, or force light/dark. JetBrains Mono everywhere.

## Screens

```
┌ today ───────────────────────────┐  ┌ whoami ──────────────────────────┐
│ user@android $ today   Sat 05 Sep │  │ user@android $ whoami            │
│ ── status ──                      │  │ ── user ──                       │
│ progress ████████░░░░░░  57%      │  │ user@android                     │
│ 4/7 done   lvl 3 · 340 xp   ⛨ 1   │  │ committer · level 3              │
│ ── ☼ morning 2/3 ───────────────  │  │ ████████████░░░░░░░░░░░░         │
│ [✓] meditate      ⚡12  daily      │  │ ── contributions · last 52 weeks │
│ [~] drink water   5/8 cups        │  │ ▪▪▪▫▪▪▪▪▫▪▪▪▪▪▪▪▪▫▪▪▪▪▪▪▪▪▪▪▪    │
│     ██████████░░░░░░░░░░  63%     │  │ ▪▪▪▪▪▪▫▪▪▪▪▪▪▪▪▪▪▪▪▪▫▪▪▪▪▪▪▪▪    │
│ [ ] stretch       ⚡3   daily      │  └──────────────────────────────────┘
│ ── watch ──                       │
│ [✓] SKX007            photo       │  [0:today] 1:habits 2:timer 3:watch 4:profile
└───────────────────────────────────┘
```

## Building

Requirements: **JDK 17**, Android SDK with **platform 35** and **build-tools 35.0.0** (the Gradle wrapper downloads Gradle 8.9).

```bash
git clone https://github.com/<you>/personal-terminal.git
cd personal-terminal
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew assembleDebug            # → app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest        # streak / progression engine tests
./gradlew assembleRelease          # R8-minified (unsigned unless you add a signing config)
```

Or use `scripts/build.sh` which runs tests + lint + assemble in one go.

### Google Drive setup (optional, needed for sync)

1. Create a project in the [Google Cloud console](https://console.cloud.google.com/) and enable the **Google Drive API**.
2. Configure the OAuth consent screen (scope `…/auth/drive.file`).
3. Create **two** OAuth client IDs:
   * *Android* — package `dev.personalterminal` (and `dev.personalterminal.debug` for debug builds) with your signing SHA-1
     (`./gradlew signingReport`).
   * *Web application* — its client id is what the app needs.
4. Put the web client id in `local.properties` (never committed):
   ```properties
   GOOGLE_WEB_CLIENT_ID=1234567890-abcdefg.apps.googleusercontent.com
   ```
   or export it as the `GOOGLE_WEB_CLIENT_ID` environment variable (used by CI).
5. Rebuild. Settings → *google drive sync* → **sign in with google**.

Without a client id the rest of the app works normally; the Drive panel simply shows `not configured`.

## Project layout

```
app/src/main/java/dev/personalterminal/
├── data/
│   ├── db/        Room entities, DAOs, AppDatabase (schemas exported to app/schemas)
│   ├── prefs/     DataStore-backed Settings
│   ├── repo/      HabitRepository (streaks, XP, shields), WatchRepository (photos)
│   └── backup/    BackupManager – .ptbak archive writer / restorer
├── domain/        Pure logic: Schedule, Streaks, Progression (unit-tested)
├── sync/          GoogleAuth (Credential Manager + AuthorizationClient), DriveClient, DriveSync + Worker
├── timer/         PomodoroService (foreground service, StateFlow)
├── widget/        Glance home-screen widget + toggle action
└── ui/            Compose screens: today, habits, routines, timer, timeline, watch, profile, settings, boot
docs/logo/         SVG logo + rendered PNGs, generation prompt
scripts/           build.sh helper
```

## Logo

`docs/logo/logo.svg` — a terminal prompt chevron `>` sitting inside a minimalist watch-face ring, with the
block cursor `▌` doubling as the crown. The same artwork drives the adaptive launcher icon
(`ic_launcher_foreground/background/monochrome`) and the notification icon. See `docs/logo/PROMPT.md` for the
image-generation prompt used for marketing renders.

## Tech stack

Kotlin 2.0 · Jetpack Compose (Material 3, custom terminal theme) · Navigation Compose · Room + KSP · DataStore ·
WorkManager · CameraX · Coil · Glance · Credential Manager + Play Services Auth · Google Drive REST v3 ·
kotlinx.serialization · JetBrains Mono (OFL).

## License

Source code: MIT (see `LICENSE`). JetBrains Mono is bundled under the SIL Open Font License (`licenses/`).
