#!/bin/bash
# Takes design screenshots of the demo build on the emulator (CI only).
set -x
APK=app/build/outputs/apk/release/app-release.apk
adb install -r "$APK"
# Let the launcher settle so its "not responding" dialog does not cover the app.
sleep 40
adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS
mkdir -p screenshots
shot() {
  adb shell am force-stop app.medassist
  adb shell am start -W -n app.medassist/.MainActivity --es demo "$1"
  sleep 5
  if [ -n "$3" ]; then adb shell input swipe 540 1900 540 500 600; sleep 2; fi
  adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null
  sleep 1
  adb exec-out screencap -p > "screenshots/$2.png"
}
for s in welcome login home records new report labs profile; do shot "$s" "$s"; done
shot report report_scrolled 1
shot home home_scrolled 1
shot profile profile_scrolled 1
adb shell cmd uimode night yes
sleep 2
shot home dark_home
shot report dark_report
adb logcat -d -s AndroidRuntime:E > screenshots/crash.txt
ls -la screenshots
