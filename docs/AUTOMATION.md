# Automation & command line

Personal Terminal exposes one shell in three places: the prompt on the **Today** screen, the
`AutomationReceiver` broadcast API (Tasker, MacroDroid, Automate, `adb`) and the widget /
notification actions. Everything below works without opening the app.

## The prompt (Today screen)

```
done <habit>              toggle / complete            undo <habit>         un-complete
add <n> <habit>           counter +n / timer +n min    set <n> <habit>      absolute value
skip <habit> -- reason    skip today (bridges streak)  unskip <habit>
slip <habit>              log a slip on an avoid-habit note <habit> -- text  completion note
mood <1-5> [habit]        mood for a habit             timer [min] [habit]  start a focus session
timer stop|pause|resume   control the timer            stopwatch [habit]    count-up session
wear <watch>              log today's watch            watch next           rotation suggestion
shield <habit>            repair the latest gap        habit add [template] new habit / from template
ls · status · review · man · insights · timeline · journal · settings
theme <name> · dark · light · backup · help
```

Habits and watches are matched by id, exact name, unique prefix, substring or initials
(`dw` → "drink water"). A bare habit name is treated as `done <habit>`. Anything after ` -- `
(or ` # `) is a note / reason.

## Broadcast API (Tasker etc.)

Send an **explicit broadcast** to package `dev.personalterminal`, receiver
`dev.personalterminal.automation.AutomationReceiver` (Tasker: *Send Intent* → Target: Broadcast
Receiver, Package `dev.personalterminal`, Class `dev.personalterminal.automation.AutomationReceiver`).

| Action | Extras |
|---|---|
| `dev.personalterminal.action.COMPLETE` | `habit` (string) or `id` (long) |
| `dev.personalterminal.action.INCREMENT` | `habit`, `amount` (int, default 1) |
| `dev.personalterminal.action.SET` | `habit`, `value` (int) |
| `dev.personalterminal.action.SKIP` | `habit`, `reason` (string) |
| `dev.personalterminal.action.WEAR` | `watch` (string: nickname / model / id) |
| `dev.personalterminal.action.TIMER_START` | optional `habit`, `minutes` (int) |
| `dev.personalterminal.action.TIMER_STOP` | – |
| `dev.personalterminal.action.BACKUP` | – (requests a Drive backup) |

Every action also accepts `command` (string) with any prompt line, e.g. `--es command "done stretch -- felt great"`.

```bash
adb shell am broadcast -n dev.personalterminal/.automation.AutomationReceiver \
  -a dev.personalterminal.action.COMPLETE --es habit "stretch"
adb shell am broadcast -n dev.personalterminal/.automation.AutomationReceiver \
  -a dev.personalterminal.action.TIMER_START --es habit "focus session" --ei minutes 50
adb shell am broadcast -n dev.personalterminal/.automation.AutomationReceiver \
  -a dev.personalterminal.action.WEAR --es command "wear speedy -- office day"
```

Results are logged under the tag `PTAutomation`; the widget refreshes after every action.

## Other entry points

- **Widgets** – *today* (tap a row to tick / +1 / +5 min, header opens the app), *status*
  (level, XP, streak, shields), *timer* (start / pause / stop).
- **Quick Settings tile** – "Focus timer": tap to start with the global pomodoro lengths, tap
  again to stop.
- **App shortcuts** – long-press the icon: `timer`, `wear`, `habit add`, `review`.
- **Reminder notifications** – "done" / "+1" action button.
- **Deep links** – `MainActivity` extra `route` with any in-app route (`timer`, `review`,
  `habit/42`, `wear/log`).
- **Health Connect** – link a counter/timer habit to steps, exercise minutes, sleep hours or
  hydration in `habit edit`; the value is pulled hourly and on app start.
