#!/usr/bin/env bash
# End-to-end check of 100 Hours on a running Android emulator (or a phone over adb).
# Uses the app like a person would: taps buttons, leaves the app, pauses from the
# notification, lets Android kill the app's process, and checks the timer survives.
# Saves a screenshot of each step plus the device log.
#
#   scripts/emulator-smoke-test.sh <apk file or folder> <output folder>
#
# Env: ADB (default: adb), SLOW (multiplies waits, for slow software-only emulators).
set -uo pipefail

APK=$1; [ -d "$APK" ] && APK=$(ls "$APK"/*.apk | head -1)
OUT=$2; mkdir -p "$OUT"
ADB=${ADB:-adb}
SLOW=${SLOW:-1}
PKG=com.hundredhours.app
HERE=$(cd "$(dirname "$0")" && pwd)
FAILED=0

log() { echo "[$(date +%H:%M:%S)] $*"; }
pass() { log "✅ $*"; }
fail() { log "❌ $*"; FAILED=1; }
wait_s() { sleep $(( $1 * SLOW )); }
shot() { $ADB exec-out screencap -p > "$OUT/$1.png" && log "📸 $1.png"; }
dump() { $ADB shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1; $ADB pull /sdcard/ui.xml "$OUT/ui.xml" > /dev/null 2>&1; }
screen_text() { dump; python3 "$HERE/ui_find.py" "$OUT/ui.xml" --texts; }
find_xy() { dump; python3 "$HERE/ui_find.py" "$OUT/ui.xml" "$1"; }

# Wait (up to ~20s x SLOW) for something to be on screen.
see() {
  for _ in $(seq 1 10); do
    [ -n "$(find_xy "$1")" ] && { pass "sees '$1'"; return 0; }
    wait_s 2
  done
  fail "expected '$1' on screen. Screen shows: $(screen_text)"; return 1
}
tap() {
  for _ in $(seq 1 10); do
    xy=$(find_xy "$1")
    [ -n "$xy" ] && { $ADB shell input tap $xy; log "👆 tapped '$1'"; return 0; }
    wait_s 2
  done
  fail "could not find '$1' to tap. Screen shows: $(screen_text)"; return 1
}
countdown() { screen_text | grep -oE '[0-9]{2,3}:[0-9]{2}:[0-9]{2}' | head -1; }
to_sec() { IFS=: read -r h m s <<< "$1"; echo $(( 10#$h * 3600 + 10#$m * 60 + 10#$s )); }
fgs_running() { $ADB shell dumpsys activity services "$PKG" | grep -q "isForeground=true"; }
has_notification() { $ADB shell dumpsys notification --noredact | grep -q "pkg=$PKG"; }
open_app() { $ADB shell am start -W -n "$PKG/.MainActivity" > /dev/null; }
open_shade() { $ADB shell cmd statusbar expand-notifications > /dev/null 2>&1 || $ADB shell service call statusbar 1 > /dev/null; }
close_shade() { $ADB shell cmd statusbar collapse > /dev/null 2>&1 || $ADB shell service call statusbar 2 > /dev/null; }

API=$($ADB shell getprop ro.build.version.sdk | tr -d '\r')
log "Device: Android $($ADB shell getprop ro.build.version.release | tr -d '\r') (API $API), APK: $(basename "$APK")"
$ADB logcat -c > /dev/null 2>&1 || true   # start the device log fresh (not every image allows it)

# 1. Fresh install, open the app.
$ADB uninstall "$PKG" > /dev/null 2>&1
$ADB install "$APK" > /dev/null || { fail "install failed"; exit 1; }
[ "$API" -ge 33 ] && $ADB shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS
open_app
see "Start week" && shot 01_setup

# 2. Start the week: timer runs, background service + notification appear.
tap "Start week"
[ -n "$(find_xy Allow)" ] && tap "Allow"   # notification permission, if it was not pre-granted
see "Pause"
see "left of 100h goal"
see "Day 1 of 7"
shot 02_running
if fgs_running; then pass "timer service runs as a foreground service"; else fail "timer service is not running in the foreground"; fi
if has_notification; then pass "notification is posted"; else fail "no notification posted"; fi

# 3. The countdown actually ticks down.
a=$(countdown); wait_s 4; b=$(countdown)
if [ -n "$a" ] && [ -n "$b" ] && [ "$(to_sec "$b")" -lt "$(to_sec "$a")" ]; then
  pass "countdown ticks down: $a -> $b"
else
  fail "countdown did not tick: '$a' -> '$b'"
fi

# 4. Leave the app: the notification keeps the countdown; pause from there.
$ADB shell input keyevent KEYCODE_HOME; wait_s 2
open_shade; wait_s 3
see "Studying"
shot 03_notification
if [ -z "$(find_xy Pause)" ]; then   # notification collapsed: expand it to show its button
  xy=$(find_xy id:expand_button); [ -n "$xy" ] && $ADB shell input tap $xy && wait_s 2
fi
tap "Pause"
wait_s 3
close_shade
if fgs_running; then fail "service still running after Pause"; else pass "Pause in the notification stopped the timer"; fi
if has_notification; then fail "notification still showing after Pause"; else pass "notification removed after Pause"; fi
open_app
see "Start" && shot 04_paused_from_notification

# 5. Resume, then Android kills the app's process (as it does to free memory).
tap "Start"
see "Pause"
before=$(countdown)
$ADB shell input keyevent KEYCODE_HOME; wait_s 2
if $ADB root > /dev/null 2>&1 && $ADB wait-for-device && sleep 2 && [ -n "$($ADB shell pidof "$PKG" | tr -d '\r')" ]; then
  $ADB shell kill -9 "$($ADB shell pidof "$PKG" | tr -d '\r')"
  log "💀 killed the app's process"
  back=0
  for _ in $(seq 1 15); do wait_s 4; fgs_running && has_notification && { back=1; break; }; done
  if [ $back = 1 ]; then pass "Android restarted the timer and its notification after the kill"; else fail "timer notification did not come back after the process was killed"; fi
  open_app
  see "Pause"
  after=$(countdown)
  if [ -n "$before" ] && [ -n "$after" ] && [ "$(to_sec "$after")" -lt "$(to_sec "$before")" ]; then
    pass "time kept counting through the kill: $before -> $after"
  else
    fail "countdown did not continue after the kill: '$before' -> '$after'"
  fi
  shot 05_after_process_killed
else
  log "⏭️  skipped process-kill check (needs adb root, e.g. a google_apis emulator image)"
fi

# 6. No crashes anywhere.
$ADB logcat -d > "$OUT/logcat.txt"
if grep -A30 "FATAL EXCEPTION" "$OUT/logcat.txt" | grep -q "$PKG"; then
  fail "the app crashed:"; grep -A30 "FATAL EXCEPTION" "$OUT/logcat.txt" | head -40
else
  pass "no crashes in the device log"
fi

[ $FAILED = 0 ] && log "🎉 all checks passed" || log "some checks failed"
exit $FAILED
