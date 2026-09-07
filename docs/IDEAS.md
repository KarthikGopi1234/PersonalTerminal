# Ideas & polish backlog

Candidate features that would be *genuinely useful* for a one-person habit + watch terminal, and
the polish items found while auditing every screen at 0.3.2. Ordered by value-for-effort.
Effort: **S** = an evening, **M** = a weekend, **L** = multi-week.

## Features worth building

| # | Idea | Why it earns its place | Effort |
|---|------|------------------------|--------|
| A1 | **Bedtime / wake anchors** – a `sleep 23:30` / `wake 06:45` pair logged from the prompt (or Health Connect sleep). Today's *morning* section opens with "slept 7h 15m"; the review correlates sleep with completion rate. | Sleep is the single biggest predictor of whether the rest of the day's habits happen; the correlation engine already exists. | M |
| A2 | **Habit pause with a return date** (`pause run until 2026-10-01`) – different from insurance: the habit disappears from Today entirely and comes back by itself. | Injuries and busy months; avoids the temptation to archive and forget. | S |
| A3 | **Streak repair window** – if yesterday was missed, Today shows `[ yesterday ]` for 12 h so it can be ticked late without spending a shield; after that a shield is needed. | Most "broken" streaks are forgotten logs, not missed habits. | S |
| A4 | **Numeric targets that ramp** (`read 10 → 30 pages over 6 weeks`) – the target increases weekly. | Progressive overload is how habits actually grow; a static target is either too easy in week 1 or too hard in week 6. | M |
| A5 | **Weekly quota display for `x/week` habits** – "2/3 this week · 4 days left" inline, plus a "this can still be done" nudge on the last possible day. | Weekly habits are the ones that silently fail on Sunday night. | S |
| A6 | **Wear-photo contact sheet** – `watch shots speedy` renders a month of wrist shots as a monospace-captioned grid PNG for sharing. | The photo log is the most personal data in the app and there is no way to look at it in bulk. | M |
| A7 | **Water resistance / service warnings on the wear log** – a small `⚠ service overdue` / `⚠ not for swimming` badge next to the watch when logging. | The service log exists; surfacing it at the moment of choice is what makes it useful. | S |
| A8 | **Strap swap log** – `strap speedy → bond nato` records the change; the wear log inherits the strap; the strap page shows "on speedy for 23 days". | Straps are half the hobby; today the strap↔watch link is a single mutable field. | S |
| A9 | **Backup health line on the profile** – "last backup 3 h ago · 1.2 MB · 214 photos" with a `[ verify ]` that decrypts and checks the archive. | Backups fail silently; a visible heartbeat is the cheapest insurance. | S |
| A10 | **Data export as SQLite** (`export db`) alongside CSV. | One file, every table, opens in DB Browser; the app already owns the Room file. | S |
| A11 | **`stats <habit>` command** – prints the habit's 30/90/365-day rates, best weekday, and current vs best streak as a monospace block. | Keeps the terminal promise: everything the screens show should be askable. | S |
| A12 | **Voice / quick-tile "done" for the next due habit** – a QS tile that ticks the top undone habit and shows its name. | The timer tile proved the pattern; this covers the 90 % case with zero taps into the app. | S |
| A13 | **Morning briefing notification** (opt-in, one per day) – "3 habits · 2 in the morning · wear: 62MAS (14 d) · streak at risk: journal". | Replaces several nudges with one glanceable line. | M |
| A14 | **Habit dependencies** (`journal` after `meditate`) – the dependent habit is dimmed until its parent is done, then highlighted. | Habit stacking is the technique; the UI can enforce the order gently. | M |
| A15 | **Local on-device widgets for the vault** – a 2×2 widget showing today's watch photo and a `next: Cartier (14 d)` line. | The watch side has no widget yet. | M |

## Polish audit (0.3.2)

Fixed in 0.3.3:

- Buttons clipped their labels (`[ watch` / `[ man` / `[ habit`): `TermButton` now steps the font size
  down instead of clipping; long labels were shortened (`vault`, `stats`, `achievements`, `+ add`).
- Prompt headers that overflowed on a 412 dp phone (`habit add --templa…`, `history | grep foc…`,
  `vim ~/.co…`) now use short commands.

Still on the list:

- **Habit row density**: the Today row shows streak, schedule tag and best in a second line; on a
  10-habit day that is a lot of scrolling. A "compact" toggle (single line per habit, progress as a
  6-char bar) would help.
- **Empty states** are plain comments (`# no watches yet`). A three-line ASCII illustration + a single
  primary action would read better on first launch.
- **Timeline day cards** repeat the full `EEE dd MMM yyyy` title; the month is already in the header.
- **Snackbar-style feedback** for prompt commands is a one-line `Comment` that disappears on the next
  recomposition; a small history (`↑` to recall the last command, like a shell) would fit the metaphor.
- **Colour picker in habit edit** uses eight swatches without names; add the name under the selected
  swatch for accessibility mode.
- **Light themes**: `bgHighlight` on Solarized Light and Rosé Pine Dawn is close to `bgAlt`; selected
  rows need a border or a stronger tint.
- **Tablet split panes** show the same header on both sides; the right pane could drop the prompt.
- **Loading flashes**: heavy screens (year review, insights) show `# crunching…` for a frame or two;
  keep the previous result on screen while recomputing.
