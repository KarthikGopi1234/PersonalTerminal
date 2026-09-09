#!/usr/bin/env bash
# Install-over-install rehearsal on a booted emulator/device.
#
#   scripts/upgrade-check.sh <previous-release.apk> <new-release.apk> [same-signing-key: true|false]
#
# 1. installs the PREVIOUS release, launches it (starter habits get seeded) and puts real data in
#    through the automation receiver (`done stretch`, `add 3 drink water`, a watch on the wrist),
# 2. installs the NEW apk *over* it (or, when the keys differ, rehearses uninstall → install),
# 3. launches the updated app, watches logcat for a crash, and asks the app for `status` / `ls`
#    again – the ticks, counters and XP from step 1 must still be there and the app must answer.
#
# Everything the script saw lands in ./upgrade-check/ (summary.md, before/after replies, logcat).
# Exit code 0 = update rehearsal passed. Used by the advisory `upgrade-check` CI job; works just as
# well against a real phone over adb (make a backup first – it force-stops and, when the keys
# differ, uninstalls the app).
set -uo pipefail

OLD=${1:?previous apk}; NEW=${2:?new apk}; SAME=${3:-true}
PKG=dev.personalterminal
OUT=upgrade-check
mkdir -p "$OUT"
SUMMARY="$OUT/summary.md"; : > "$SUMMARY"
fail=0

note() { printf '%s\n' "$*"; printf '%s\n' "$*" >> "$SUMMARY"; }
step() { printf '\n── %s\n' "$*"; printf '\n**%s**\n\n' "$*" >> "$SUMMARY"; }
bad()  { note "✗ $*"; fail=1; }
good() { note "✓ $*"; }

# Runs one in-app command line through the automation receiver and prints the app's reply
# (the receiver logs "<action> → <reply>" on the PTAutomation tag; multi-line replies keep their lines).
cmd() {
  local line=$1 i reply
  adb logcat -c >/dev/null 2>&1
  adb shell "am broadcast -n $PKG/.automation.AutomationReceiver -a $PKG.action.COMPLETE --es command '$line'" >/dev/null 2>&1
  for i in $(seq 1 40); do
    reply=$(adb logcat -d -v raw -s PTAutomation:I 2>/dev/null | sed -n '/ → /,$p' | sed '1s/^.* → //')
    if [ -n "$reply" ]; then printf '%s\n' "$reply"; return 0; fi
    sleep 0.5
  done
  printf '%s\n' "<no reply from $PKG within 20 s>"; return 1
}

version_of() { adb shell dumpsys package "$PKG" | grep -m1 -oE 'versionName=[^ ]+' | cut -d= -f2; }
code_of()    { adb shell dumpsys package "$PKG" | grep -m1 -oE 'versionCode=[0-9]+' | grep -oE '[0-9]+'; }

launch() {  # start the main activity, give it a moment, fail on crash / no foreground
  adb logcat -c >/dev/null 2>&1
  local out; out=$(adb shell am start -W -n "$PKG/.MainActivity" 2>&1); printf '%s\n' "$out" | sed 's/^/   /'
  sleep "${1:-10}"
  if adb logcat -d 2>/dev/null | grep -qE "FATAL EXCEPTION|Force finishing activity $PKG"; then
    adb logcat -d > "$OUT/crash-logcat.txt"; bad "crash after launch (see crash-logcat.txt)"; return 1
  fi
  if [ -z "$(adb shell pidof "$PKG" 2>/dev/null)" ]; then bad "$PKG is not running after launch"; return 1; fi
  if ! adb shell dumpsys activity activities 2>/dev/null | grep -E 'ResumedActivity|topResumedActivity' | grep -q "$PKG"; then
    bad "$PKG is not the foreground activity after launch"; return 1
  fi
  return 0
}

pull_db() {  # best effort: copy the database out (needs adb root – fine on google_apis emulator images)
  adb root >/dev/null 2>&1 && sleep 3 && adb wait-for-device
  adb pull "/data/data/$PKG/databases/personal_terminal.db" "$OUT/$1.db" >/dev/null 2>&1 || return 0
  # Room runs SQLite in WAL mode: the newest rows live in the -wal file until a checkpoint, pull it too.
  adb pull "/data/data/$PKG/databases/personal_terminal.db-wal" "$OUT/$1.db-wal" >/dev/null 2>&1 || true
  adb pull "/data/data/$PKG/databases/personal_terminal.db-shm" "$OUT/$1.db-shm" >/dev/null 2>&1 || true
  if command -v sqlite3 >/dev/null; then
    { echo "user_version=$(sqlite3 "$OUT/$1.db" 'PRAGMA user_version')"
      for t in habits habit_logs routines watches wear_logs xp_events; do
        printf '%s=%s\n' "$t" "$(sqlite3 "$OUT/$1.db" "SELECT COUNT(*) FROM $t" 2>/dev/null || echo '?')"
      done; } > "$OUT/$1.counts"
    sed 's/^/   /' "$OUT/$1.counts"
  fi
}

# ------------------------------------------------------------------ 1. previous release + data
step "install previous release ($(basename "$OLD"))"
adb uninstall "$PKG" >/dev/null 2>&1 || true
if ! adb install -r "$OLD"; then bad "previous release does not install"; exit 1; fi
adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true
OLD_VER=$(version_of); OLD_CODE=$(code_of)
note "previous: $OLD_VER (versionCode $OLD_CODE)"

step "first launch of $OLD_VER"
launch 12 || exit 1
good "launched $OLD_VER"

step "seed data through the automation receiver"
for i in 1 2 3; do  # starter habits are seeded asynchronously on first launch – retry briefly
  r=$(cmd "done stretch"); printf '   done stretch → %s\n' "$r"
  case "$r" in *"stretch"*) break ;; esac; sleep 4
done
printf '   add 3 drink water → %s\n' "$(cmd "add 3 drink water")"
printf '   wish Seiko SKX007 300 → %s\n' "$(cmd "wish Seiko SKX007 300")"      # 0.3.6+ (older builds answer "unknown command")
printf '   watch buy Seiko 250 → %s\n' "$(cmd "watch buy Seiko 250")"
printf '   wear Seiko → %s\n' "$(cmd "wear Seiko")"
BEFORE_STATUS=$(cmd status); BEFORE_LS=$(cmd ls)
printf '%s\n' "$BEFORE_STATUS" > "$OUT/before-status.txt"; printf '%s\n' "$BEFORE_LS" > "$OUT/before-ls.txt"
note '```'; note "status → $BEFORE_STATUS"; note "$BEFORE_LS"; note '```'
case "$BEFORE_LS" in *"[✓] stretch"*) good "seeded: stretch ticked" ;; *) bad "could not seed data in $OLD_VER (is the automation receiver answering?)" ;; esac
adb shell am force-stop "$PKG"
pull_db before

# ------------------------------------------------------------------ 2. the update
if [ "$SAME" = "true" ]; then
  step "install $(basename "$NEW") OVER $OLD_VER (in-place update)"
  if out=$(adb install -r "$NEW" 2>&1); then good "in-place install accepted: $out"
  else bad "in-place install REJECTED: $out"; adb logcat -d > "$OUT/install-logcat.txt"; exit 1; fi
  FRESH=false
else
  step "keys differ – rehearsing uninstall → install (what the user has to do once)"
  note "⚠ previous release is signed with a different key; data cannot carry over in place (backup → restore is the documented path)"
  adb uninstall "$PKG" >/dev/null 2>&1 || true
  if out=$(adb install "$NEW" 2>&1); then good "fresh install accepted: $out"; else bad "fresh install rejected: $out"; exit 1; fi
  FRESH=true
fi
NEW_VER=$(version_of); NEW_CODE=$(code_of)
note "now: $NEW_VER (versionCode $NEW_CODE)"
[ "$NEW_CODE" -gt "$OLD_CODE" ] 2>/dev/null && good "versionCode increased $OLD_CODE → $NEW_CODE" || bad "versionCode did not increase ($OLD_CODE → $NEW_CODE)"

# ------------------------------------------------------------------ 3. does it still work, is the data still there
step "first launch of $NEW_VER after the update"
launch 15 && good "launched $NEW_VER without crashing"
sleep 5   # let the migration + first-launch housekeeping settle
pull_db after

step "data after the update"
AFTER_STATUS=$(cmd status); AFTER_LS=$(cmd ls)
printf '%s\n' "$AFTER_STATUS" > "$OUT/after-status.txt"; printf '%s\n' "$AFTER_LS" > "$OUT/after-ls.txt"
note '```'; note "status → $AFTER_STATUS"; note "$AFTER_LS"; note '```'
case "$AFTER_STATUS" in *"<no reply"*) bad "app does not answer commands after the update" ;; *) good "app answers commands after the update" ;; esac
if [ "$FRESH" = "false" ]; then
  case "$AFTER_LS" in *"[✓] stretch"*) good "tick survived the update" ;; *) bad "tick LOST in the update" ;; esac
  case "$AFTER_LS" in *"drink water 3/8"*) good "counter value survived the update" ;; *) bad "counter value LOST in the update" ;; esac
  bx=$(printf '%s' "$BEFORE_STATUS" | grep -oE '[0-9]+ xp'); ax=$(printf '%s' "$AFTER_STATUS" | grep -oE '[0-9]+ xp')
  [ -n "$bx" ] && [ "$bx" = "$ax" ] && good "xp unchanged ($ax)" || bad "xp changed across the update ($bx → $ax)"
  if [ -f "$OUT/before.counts" ] && [ -f "$OUT/after.counts" ]; then
    for t in habits habit_logs routines watches wear_logs; do
      b=$(grep "^$t=" "$OUT/before.counts" | cut -d= -f2); a=$(grep "^$t=" "$OUT/after.counts" | cut -d= -f2)
      [ "$a" = "$b" ] && good "table $t: $b rows before, $a after" || bad "table $t: $b rows before, $a after"
    done
    note "schema: user_version $(grep -m1 user_version "$OUT/before.counts" | cut -d= -f2) → $(grep -m1 user_version "$OUT/after.counts" | cut -d= -f2)"
  fi
  printf '   wear log after update → %s\n' "$(cmd "watch")"
else
  case "$AFTER_LS" in *"stretch"*) good "starter habits seeded on the fresh install" ;; *) bad "fresh install did not seed starter habits" ;; esac
fi

step "second cold start of $NEW_VER (migration must not run twice / app must reopen)"
adb shell am force-stop "$PKG"
launch 8 && good "reopened fine"

adb logcat -d > "$OUT/logcat.txt" 2>/dev/null || true
step "result"
if [ "$fail" = 0 ]; then note "**PASS** – $OLD_VER → $NEW_VER update rehearsal succeeded"; else note "**FAIL** – see the ✗ lines above and the logs in $OUT/"; fi
if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then { echo "## Install-over-install rehearsal"; cat "$SUMMARY"; } >> "$GITHUB_STEP_SUMMARY"; fi
exit $fail
