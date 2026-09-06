# Roadmap

Ideas for where Personal Terminal can go next, grouped by theme and roughly ordered by
value-for-effort inside each group. Effort: **S** = an evening, **M** = a weekend, **L** = a multi-week feature.

**Status (0.3):** everything marked ✅ shipped in the 0.3 series – see the release notes and
[docs/AUTOMATION.md](AUTOMATION.md) for the command line / intent API. Unmarked rows are still open.

## 1. Habit loop – make the daily check-in stickier

| # | Feature | Why | Effort |
|---|---------|-----|--------|
| 1.1 ✅ | **Reminders per habit / routine** (`notify 07:30 morning`) with quiet hours and a "nudge only if not done" rule | The single biggest retention lever in habit apps; the app currently never reaches out | M |
| 1.2 ✅ | **Notes on a completion** (`why`, mood 1–5, a one-liner) shown in the habit log | Turns the log into a journal; pairs with the CLI aesthetic ("git commit -m") | S |
| 1.3 ✅ | **Negative / "avoid" habits** (no sugar, no doom-scrolling) that count *clean days* and break on a slip | Common request in Init Habits-style apps; the streak engine already models gaps | M |
| 1.4 ✅ | **Skip-with-reason** (`sick`, `travel`) that neither breaks nor extends the streak | Reduces "all-or-nothing" abandonment; complements shields | S |
| 1.5 | **Time-of-day sections** (morning / afternoon / evening / any) instead of only routines | Today screen reads like a day plan | S |
| 1.6 ✅ | **Habit templates** (`habit add --from library`) with ~30 curated presets incl. units and targets | Faster onboarding | S |
| 1.7 | **Sub-tasks / checklist habits** (pack gym bag → 4 items) | Routine = habits, habit = steps | M |

## 2. Focus timer

| # | Feature | Why | Effort |
|---|---------|-----|--------|
| 2.1 ✅ | **Session history + per-day focus minutes** on the profile heatmap (second colour channel or toggle) | Timer data is already credited to habits but never visualised on its own | S |
| 2.2 ✅ | **Stopwatch mode** (count up, stop when done) for open-ended habits like "practice guitar" | Some habits have no fixed length | S |
| 2.3 ✅ | **Custom intervals per habit** (25/5 vs 50/10) and *auto-start next phase* toggle | Power-user ergonomics | S |
| 2.4 | **Ambient sounds / tick** (rain, brown noise, mechanical-keyboard clicks) with independent volume | Popular in pomodoro apps, fits the vibe | M |
| 2.5 ✅ | **Do-Not-Disturb during focus** (request `ACCESS_NOTIFICATION_POLICY`) | Real focus protection | S |
| 2.6 | **Wear OS tile / complication** showing remaining time; start/stop from the wrist | The watch tracker audience owns smartwatches too | L |
| 2.7 | Live-Update polish: verify what ColorOS 16 needs for the island (a `ProgressStyle` with `setProgressTrackerIcon`? a `CallStyle`-like layout?) once OPPO documents it; currently the countdown lands in the shade but not the island on Find X9 Ultra | Vendor-specific; wait for documentation rather than guess | ? |

## 3. Watch tracker

| # | Feature | Why | Effort |
|---|---------|-----|--------|
| 3.1 ✅ | **Collection stats**: wear share per watch (ASCII pie / bars), days since last worn, "neglected" list, most-worn-per-month | Turns logs into insight; the data is already there | S |
| 3.2 ✅ | **Service & maintenance log** per watch (service date, cost, warranty until, strap changes) with a reminder when the service interval elapses | Watch owners track this in spreadsheets today | M |
| 3.3 ✅ | **Accuracy tracking**: log "watch shows / reference time" pairs, compute s/day drift with a sparkline; optional atomic-clock sync via NTP | Mechanical-watch enthusiasts love this (see WatchCheck / Twixt) | M |
| 3.4 ✅ | **Strap library**: straps as their own entity, log which strap was on which watch, gallery per combo | Very common collector behaviour | M |
| 3.5 | **Watch box view**: grid of thumbnails coloured by dial, sortable by last worn / brand / size | Visual browse mode for bigger collections | S |
| 3.6 ✅ | **Rotation suggester** (`watch next`): proposes tomorrow's watch weighted toward neglected pieces and the day's habits (e.g. G-Shock on workout days) | Fun, tiny algorithm, uses both halves of the app | S |
| 3.7 ✅ | **Purchase details & valuation**: price paid, date, box/papers, current estimate; collection total on the profile | Insurance / resale bookkeeping | S |
| 3.8 ✅ | **Import / export CSV** of the collection and wear log | Data portability | S |
| 3.9 | **Wrist-shot camera frame**: rule-of-thirds overlay, EXIF stamp with watch name, optional auto-crop to the dial | Better photos with zero effort | M |

## 4. Insights & gamification

| # | Feature | Why | Effort |
|---|---------|-----|--------|
| 4.1 ✅ | **Weekly review** (`report --week`): completion %, best/worst habit, focus minutes, watches worn, XP gained — as a shareable monospace card (PNG) | Reflection loop + organic sharing | M |
| 4.2 ✅ | **Achievements / badges** rendered as man-page style entries (`STREAK(7)`, `PERFECT_WEEK(1)`) | Cheap dopamine layered on the XP system | S |
| 4.3 ✅ | **Per-habit heatmap + best streak + completion trend** on the habit detail screen | Detail screen currently shows only the current streak | S |
| 4.4 ✅ | **Correlations** ("you complete *read* 40 % more on days you *meditate*") | Small statistics, big "aha" | M |
| 4.5 | **Year in review** (December): a scrolling terminal log of the year | Seasonal delight | M |

## 5. Widgets & system integration

| # | Feature | Why | Effort |
|---|---------|-----|--------|
| 5.1 ✅ | **Widget variants**: single-habit 1×1 tile, streak/heatmap 4×2, "today's watch" tile | Current widget is the checklist only | M |
| 5.2 ✅ | **Quick Settings tile** to start/pause the focus timer | One-swipe access | S |
| 5.3 ✅ | **App shortcuts** (long-press icon): add habit, start timer, log wear | Cheap wins | S |
| 5.4 | **Lock-screen / AOD** timer via the existing Live Update (already works on Pixel; ColorOS pending) | — | — |
| 5.5 ✅ | **Tasker / intent API** (`dev.personalterminal.ACTION_TOGGLE` with habit name) and an **Android Assistant / App Actions** "mark X done" | Automation crowd | S |
| 5.6 ✅ | **Health Connect** read (steps, sleep, workouts) to auto-complete matching habits | Removes manual logging for the most common habits | L |

## 6. Terminal experience

| # | Feature | Why | Effort |
|---|---------|-----|--------|
| 6.1 ✅ | **Real command line**: a prompt at the bottom of Today that accepts `done stretch`, `add water 2`, `wear speedy`, `timer 25`, `theme nord`, `help` — with tab-completion chips and history | The app's whole identity; power users would live in it | M |
| 6.2 ✅ | **Custom themes** (import an iTerm2/Windows-Terminal JSON colour scheme) + a couple more built-ins (Tokyo Night, Rosé Pine, One Dark) | Themes are the most requested cosmetic feature in this genre | S |
| 6.3 ✅ | **Font choice** (JetBrains Mono, Fira Code, IBM Plex Mono, Berkeley Mono if user-supplied) and ligature toggle | — | S |
| 6.4 ✅ | **CRT / scanline shader** done properly with `RenderEffect` (currently a simple overlay), plus an optional typing sound | Nostalgia dial | S |
| 6.5 ✅ | **Landscape / tablet layout**: tmux-style split panes (today | timeline) | Foldables and tablets | M |
| 6.6 ✅ | **Accessibility pass**: TalkBack labels for ASCII bars/checkboxes, larger-font layouts, reduced-motion toggle | Broadens the audience and is the right thing to do | S |

## 7. Data, sync & platform

| # | Feature | Why | Effort |
|---|---------|-----|--------|
| 7.1 ✅ | **Restore-from-Drive on first launch** ("found a backup from 3 days ago — restore?") | Completes the backup story for phone migrations | S |
| 7.2 ✅ | **Encrypted backups** (passphrase → AES-GCM over the zip) | Wear photos + notes are personal | S |
| 7.3 | **Conflict-free multi-device sync** (append-only log of events replayed on each device) | Real sync instead of last-writer-wins backup — big, only if a second device matters | L |
| 7.4 ✅ | **Import from other apps** (Loop Habit Tracker CSV, Habitica, Streaks) | Lowers the switching cost | M |
| 7.5 ✅ | **Room migrations test + schema CI check** and **Compose screenshot tests in CI** (the `screenshots` task already renders every screen; assert against golden PNGs) | Guard rails as the codebase grows | S |
| 7.6 | **F-Droid / Play listing**, signed release keystore in CI secrets, Play Integrity-free (no Google dependency except optional Drive) | Distribution | M |
| 7.7 | **Localisation** (string resources are already externalised; add de/es/fr/hi/ja) | — | S per language |

## Notes on the shipped items

- **2.6 Wear OS tile** is *not* shipped: it needs a separate Wear module and a paired watch to test; the
  phone-side pieces it would talk to (timer state flow, `TimerWidgetAction`, broadcast API) are in place.
- **7.5** ships as `./gradlew verifyScreenshots` (golden PNGs in `screenshots/`, pixel diff with a 1.5 % tolerance,
  diffs uploaded as a CI artifact); a Room migration test is still open.
- **5.6 Health Connect** reads steps, exercise sessions, sleep sessions and hydration; the device needs the Health
  Connect app (Android 14 has it built in).

## Suggested next three

1. **Sub-task / checklist habits (1.7)** – the last habit-loop gap.
2. **Wear OS tile (2.6)** – now that the timer has a public control surface.
3. **Multi-device sync (7.3)** – encrypted backups + Drive folder give it a base to stand on.
