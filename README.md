<p align="center"><img src="docs/logo/logo_192.png" width="96" alt="Personal Terminal"></p>
<h1 align="center">Personal Terminal</h1>
<p align="center"><code>user@android $ daily</code> — a terminal-styled habit tracker for Android.</p>

<p align="center"><img src="screenshots/hero.png" alt="Today, focus timer, watch collection and profile screens" width="100%"></p>

## What it does

- **Habits** — checkbox, counter (`5/8 cups`) and timer modes; routines with daily / weekly / custom schedules.
- **Streaks & shields** — earned streak freezes protect a chain after a missed day.
- **Pomodoro** — foreground timer with a live countdown notification (Android 16 Live Update / status-bar chip).
- **Stats** — GitHub-style heatmap, XP and levels.
- **Widget** — check off habits from the home screen.
- **Watch tracker** — photograph today's watch (camera, photos or files), keep a collection, see it on the timeline.
- **Backup** — automatic Google Drive backups, plus local export/import.
- **Themes** — Dracula, Nord, Solarized, Gruvbox · light & dark · JetBrains Mono everywhere.

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

Screenshots are rendered from the real screens with demo data by `./gradlew screenshots` (Robolectric, no device needed);
re-run it after UI changes and commit the result.

## Install

Grab `personal-terminal-<version>.apk` from the [latest release](https://github.com/KarthikGopi1234/PersonalTerminal/releases/latest)
and verify it against `SHA256SUMS.txt`. Requires Android 8.0+; the live status-bar countdown needs Android 16.

## Build

JDK 17 · Android SDK platform 36 · build-tools 35.0.0 (`scripts/bootstrap-env.sh` installs both headlessly).

```bash
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # streak / progression / timer tests
./gradlew screenshots          # regenerate screenshots/ from the UI
```

Google Drive sync needs an OAuth *web* client id in `local.properties` as `GOOGLE_WEB_CLIENT_ID=…`
(Drive API enabled, scope `drive.file`, plus an *Android* client for the signing key). Without it the app
works normally and the Drive panel shows `not configured`.

## Release pipeline

Every push to `main` runs tests → lint → build and publishes a GitHub Release. Versions are automatic:

| | |
|---|---|
| Version | `MAJOR.MINOR.PATCH` — `app.version` in `gradle.properties` gives `MAJOR.MINOR`, the patch number is the next free `vMAJOR.MINOR.N` tag (so each successful build is 0.2.0, 0.2.1, 0.2.2 …) |
| `versionCode` | the CI run number (always increasing → every build installs as an upgrade) |
| Release / tag | named after the version: release **`0.2.1`**, tag `v0.2.1`, asset `personal-terminal-0.2.1.apk` + `SHA256SUMS.txt` |
| Notes | the commit message (subject as heading, body as notes) |

Bump `app.version` by hand only for feature milestones. Optional secrets: `GOOGLE_WEB_CLIENT_ID`,
`RELEASE_KEYSTORE_BASE64` + `RELEASE_STORE_PASSWORD` / `RELEASE_KEY_ALIAS` / `RELEASE_KEY_PASSWORD`
(without them the APK is debug-signed — still installable). The debug build is kept as a 14-day workflow artifact.

## Roadmap

Feature ideas, grouped and sized, live in [docs/ROADMAP.md](docs/ROADMAP.md).

## Stack

Kotlin · Jetpack Compose (Material 3) · Room · DataStore · Glance · CameraX · WorkManager · Credential Manager + Drive REST v3.

## License

MIT — see [LICENSE](LICENSE). Font: JetBrains Mono ([OFL](licenses/JetBrainsMono-OFL.txt)). Logo assets in `docs/logo/`.
