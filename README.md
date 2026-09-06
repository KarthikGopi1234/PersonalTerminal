<p align="center"><img src="docs/logo/logo_192.png" width="96" alt="Personal Terminal"></p>
<h1 align="center">Personal Terminal</h1>
<p align="center"><code>user@android $ daily</code> — a terminal-styled habit tracker for Android.</p>

## What it does

- **Habits** — checkbox, counter (`5/8 cups`) and timer modes; routines with daily / weekly / custom schedules.
- **Streaks & shields** — earned streak freezes protect a chain after a missed day.
- **Pomodoro** — foreground timer with a live countdown notification (Android 16 Live Update / status-bar chip).
- **Stats** — GitHub-style heatmap, XP and levels.
- **Widget** — check off habits from the home screen.
- **Watch tracker** — photograph today's watch (camera, photos or files), keep a collection, see it on the timeline.
- **Backup** — automatic Google Drive backups, plus local export/import.
- **Themes** — Dracula, Nord, Solarized, Gruvbox · light & dark · JetBrains Mono everywhere.

## Install

Grab `personal-terminal-<version>.apk` from the [latest release](https://github.com/KarthikGopi1234/PersonalTerminal/releases/latest)
and verify it against `SHA256SUMS.txt`. Requires Android 8.0+; the live status-bar countdown needs Android 16.

## Build

JDK 17 · Android SDK platform 36 · build-tools 35.0.0 (`scripts/bootstrap-env.sh` installs both headlessly).

```bash
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # streak / progression tests
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

## Stack

Kotlin · Jetpack Compose (Material 3) · Room · DataStore · Glance · CameraX · WorkManager · Credential Manager + Drive REST v3.

## License

MIT — see [LICENSE](LICENSE). Font: JetBrains Mono ([OFL](licenses/JetBrainsMono-OFL.txt)). Logo assets in `docs/logo/`.
