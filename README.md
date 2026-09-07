<p align="center"><img src="docs/logo/logo_192.png" width="96" alt="Personal Terminal"></p>
<h1 align="center">Personal Terminal</h1>
<p align="center"><code>user@android $ daily</code> — a terminal-styled habit tracker for Android.</p>

<p align="center"><img src="screenshots/hero.png" alt="Today, focus timer, watch collection and profile screens" width="100%"></p>

## What it does

- **Habits** — checkbox, counter (`5/8 cups`), timer, *checklist* (`pack gym bag` → shoes, towel, bottle…) and *avoid* modes (`no sugar`: clean days grow the streak, a slip breaks it); routines with daily / weekly / custom schedules, or Today grouped by time of day; 22 templates; per-habit reminders plus an evening *check-in* ("did you do X today?" with done / skip buttons), streak-at-risk notices and quiet hours; skip-with-reason, completion notes and mood.
- **Streaks, shields & insurance** — earned streak freezes protect a chain after a missed day; skips bridge it for free; *insurance rules* auto-skip travel, sick leave or weekly rest days (`away travel 3d` … `back`).
- **Command line** — a real prompt on Today: `done stretch`, `add 2 water`, `tick gym bag towel`, `timer 25 focus`, `wear speedy`, `skip run -- sick`, `away travel 3d`, `remind read 21:00`, `watch next`, `help`.
- **Focus** — pomodoro with per-habit intervals, stopwatch mode, optional Do-Not-Disturb, session history on its own heatmap, Quick Settings tile, live countdown notification (Android 16 Live Update).
- **Insights** — GitHub-style heatmap, XP and levels, weekly review and `review --year` as shareable monospace cards, achievements as a man page, per-habit heatmaps and cross-habit correlations.
- **Widgets** — tick habits off from the home screen (today list), plus status and timer widgets; app shortcuts; Tasker / adb intent API; Health Connect auto-completion.
- **Watch tracker** — photograph today's watch, an optional daily "which watch today?" notice with a one-tap *wear it*, a *vault* grid colour-coded by days since worn, "on this day" wrist-shot memories on the timeline, collection stats (wear share, neglected, cost per wear), service log with reminders, accuracy / drift, strap library, `watch next` rotation suggester, purchase & valuation, CSV export.
- **Backup** — automatic Google Drive backups (optionally AES-256 encrypted), restore on first launch, local export/import, importers for Loop Habit Tracker and Habitica.
- **Notifications, your way** — every notification the app can send (habit reminders, check-in, streak at risk, weekly review, wear log, service due, timer alerts) has its own switch and time under settings › notifications, behind one master switch and shared quiet hours.
- **Terminal feel** — 14 themes (Dracula, Nord, Solarized, Gruvbox, Monokai, Catppuccin, Tokyo Night, One Dark, Rosé Pine, Everforest, Amber CRT, Matrix, Hacker, Synthwave '84) or an imported colour scheme, each paired with its own typeface from nine bundled monospace fonts (JetBrains Mono, Fira Code, Roboto Mono, IBM Plex Mono, Source Code Pro, Victor Mono, Space Mono, VT323) · six launcher icons · light & dark · CRT shader · tablet split panes · accessibility mode.

## Screenshots

| Today | Habits | Habit detail | Focus timer |
|:-:|:-:|:-:|:-:|
| <img src="screenshots/01-today.png" width="200" alt="Today"> | <img src="screenshots/02-habits.png" width="200" alt="Habits"> | <img src="screenshots/03-habit-detail.png" width="200" alt="Habit detail with heatmap"> | <img src="screenshots/04-timer.png" width="200" alt="Pomodoro timer"> |

| Watches | Watch detail | Timeline | Profile |
|:-:|:-:|:-:|:-:|
| <img src="screenshots/05-watches.png" width="200" alt="Watch collection"> | <img src="screenshots/06-watch-detail.png" width="200" alt="Watch detail with wrist shots"> | <img src="screenshots/07-timeline.png" width="200" alt="Calendar timeline"> | <img src="screenshots/08-profile.png" width="200" alt="Profile with contribution heatmap"> |

| Settings | Nord · light | Gruvbox | Solarized · light |
|:-:|:-:|:-:|:-:|
| <img src="screenshots/09-settings.png" width="200" alt="Settings"> | <img src="screenshots/10-theme-nord-light.png" width="200" alt="Nord light theme"> | <img src="screenshots/11-theme-gruvbox.png" width="200" alt="Gruvbox theme"> | <img src="screenshots/12-theme-solarized-light.png" width="200" alt="Solarized light theme"> |

| Weekly review | Achievements | Insights | Templates |
|:-:|:-:|:-:|:-:|
| <img src="screenshots/13-review.png" width="200" alt="Weekly review with shareable card"> | <img src="screenshots/14-achievements.png" width="200" alt="Achievements as a man page"> | <img src="screenshots/15-insights.png" width="200" alt="Correlations and per-habit heatmaps"> | <img src="screenshots/16-templates.png" width="200" alt="Habit templates"> |

| Focus sessions | Watch stats | Straps | Habit edit |
|:-:|:-:|:-:|:-:|
| <img src="screenshots/17-sessions.png" width="200" alt="Focus session history"> | <img src="screenshots/18-watch-stats.png" width="200" alt="Collection stats and watch next"> | <img src="screenshots/19-straps.png" width="200" alt="Strap library"> | <img src="screenshots/20-habit-edit.png" width="200" alt="Habit edit with reminder and avoid mode"> |

| Journal | CRT · Matrix | Today by time of day | Checklist habit |
|:-:|:-:|:-:|:-:|
| <img src="screenshots/21-journal.png" width="200" alt="Journal of notes and moods"> | <img src="screenshots/22-crt-matrix.png" width="200" alt="CRT shader with the Matrix theme"> | <img src="screenshots/23-today-sections.png" width="200" alt="Today grouped into morning, afternoon and evening"> | <img src="screenshots/24-checklist-edit.png" width="200" alt="Editing a checklist habit"> |

| Vault | `review --year` | Streak insurance | Tokyo Night |
|:-:|:-:|:-:|:-:|
| <img src="screenshots/25-watch-box.png" width="200" alt="Watch vault grid colour-coded by days since worn"> | <img src="screenshots/26-year-review.png" width="200" alt="Year in review"> | <img src="screenshots/27-streak-insurance.png" width="200" alt="Streak insurance rules"> | <img src="screenshots/28-theme-tokyo-night.png" width="200" alt="Tokyo Night theme"> |

| Amber CRT · VT323 | Rosé Pine Dawn · Victor Mono | Synthwave '84 · Space Mono | |
|:-:|:-:|:-:|:-:|
| <img src="screenshots/29-theme-amber-crt.png" width="200" alt="Amber CRT theme with scanlines"> | <img src="screenshots/30-theme-rose-pine-dawn.png" width="200" alt="Rosé Pine Dawn light theme"> | <img src="screenshots/31-theme-synthwave.png" width="200" alt="Synthwave 84 theme"> | |

Screenshots are rendered from the real screens with demo data by `./gradlew screenshots` (Robolectric, no device needed)
and double as the golden images for `./gradlew verifyScreenshots`, which CI runs on every push.
Re-run `screenshots` after an intentional UI change and commit the result.

## Install

Grab `personal-terminal-<version>.apk` from the [latest release](https://github.com/KarthikGopi1234/PersonalTerminal/releases/latest)
and verify it against `SHA256SUMS.txt`. Requires Android 8.0+; the live status-bar countdown needs Android 16.

## Build

JDK 17 · Android SDK platform 36 · build-tools 35.0.0 (`scripts/bootstrap-env.sh` installs both headlessly).

```bash
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # streak / progression / timer / insights / importer tests
./gradlew screenshots          # regenerate screenshots/ from the UI (also the goldens)
./gradlew verifyScreenshots    # golden-image test against screenshots/
```

Google Drive sync needs an OAuth *web* client id in `local.properties` as `GOOGLE_WEB_CLIENT_ID=…`
(Drive API enabled, scope `drive.file`, plus an *Android* client for the signing key). Without it the app
works normally and the Drive panel shows `not configured`.

## Release pipeline

Every push to `main` runs tests → lint → screenshot goldens → build and publishes a GitHub Release. Versions are automatic:

| | |
|---|---|
| Version | `MAJOR.MINOR.PATCH` — `app.version` in `gradle.properties` gives `MAJOR.MINOR`, the patch number is the next free `vMAJOR.MINOR.N` tag (so each successful build is 0.2.0, 0.2.1, 0.2.2 …) |
| `versionCode` | the CI run number (always increasing → every build installs as an upgrade) |
| Release / tag | named after the version: release **`0.2.1`**, tag `v0.2.1`, asset `personal-terminal-0.2.1.apk` + `SHA256SUMS.txt` |
| Notes | the commit message (subject as heading, body as notes) |

Bump `app.version` by hand only for feature milestones. Optional secrets: `GOOGLE_WEB_CLIENT_ID`,
`RELEASE_KEYSTORE_BASE64` + `RELEASE_STORE_PASSWORD` / `RELEASE_KEY_ALIAS` / `RELEASE_KEY_PASSWORD`
(without them the APK is debug-signed — still installable). The debug build is kept as a 14-day workflow artifact.

## Automation

The Today prompt, the widgets and a broadcast intent API share one shell — see
[docs/AUTOMATION.md](docs/AUTOMATION.md) for the command list and Tasker / `adb` examples.

## Roadmap

Feature ideas, grouped and sized, live in [docs/ROADMAP.md](docs/ROADMAP.md) (shipped items are ticked).

## Stack

Kotlin · Jetpack Compose (Material 3) · Room · DataStore · Glance · CameraX · WorkManager · Health Connect · Credential Manager + Drive REST v3.

## License

MIT — see [LICENSE](LICENSE). Fonts: JetBrains Mono, Fira Code, Roboto Mono, IBM Plex Mono, Source Code Pro, Victor Mono, Space Mono, VT323 (all [OFL](licenses/)). Logo assets in `docs/logo/`.
