# 100 Hours

A minimal Android app for one goal: **study 100 hours in 7 days.**

- Tap **Start week**. The 7 days begin, and the timer starts counting down from your goal (default 100h, editable).
- **Start / Pause** whenever you sit down or take a break. The big `HH:MM:SS` countdown shows study time left.
- Below the countdown: time left in the week, and how many hours per day you need to finish.
- The timer keeps running when the app is closed or the phone is locked, with an ongoing notification that has a **Pause** button.
- Per-day bars show how much you studied each day.
- The menu (⋮) has **Undo last session**, **Adjust time** (add/remove minutes if you forgot to start/stop), the theme (System / Light / Dark), and **Start over**.
- After 7 days, time stops counting. A result screen shows your total, then you can start a new week.

## Install

Every push builds an APK with GitHub Actions. Open the repo's **Releases** page on your phone, download the newest `100-hours-*.apk`, and install it. Newer builds install over older ones and keep your data.

## Emulator check

Every push also installs the APK on a full-speed Android 14 emulator on GitHub's machines (they support
hardware virtualization; cloud sandboxes without it can only run old Android images in slow software mode)
and uses it like a person: start the week, leave the app, pause from the notification, let Android kill the
app's process, and check the timer survives. Screenshots and the device log are saved as the run's
`emulator-screenshots` artifact. The script, `scripts/emulator-smoke-test.sh`, works against any emulator
or phone connected with adb.

## Build locally

Open the project in Android Studio, or run `./gradlew assembleRelease` (needs the Android SDK and JDK 17+).
`./gradlew testDebugUnitTest` runs the tests: timer maths (`StudyStateTest`) and the app itself on the JVM
with Robolectric (`AppFlowTest`: start/pause, the background notification and its Pause button, adjust/undo,
week end). `./gradlew recordRoborazziDebug` also saves screenshots of each screen to `app/build/outputs/roborazzi/`.
