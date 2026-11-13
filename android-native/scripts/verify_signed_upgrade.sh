#!/usr/bin/env bash
# Keep state in one shell: emulator-runner executes each script line separately.
set -euo pipefail
adb install release-output/previous.apk
adb shell am start -W -n et.sera.nativeapp/.MainActivity
before="$(adb shell dumpsys package et.sera.nativeapp | grep -m1 firstInstallTime | tr -d '\r')"
test -n "$before"
adb install -r "release-output/SERA-${VERSION_NAME}.apk"
adb shell dumpsys package et.sera.nativeapp | grep "versionCode=${VERSION_CODE} "
after="$(adb shell dumpsys package et.sera.nativeapp | grep -m1 firstInstallTime | tr -d '\r')"
test "$after" = "$before"
adb shell am start -W -n et.sera.nativeapp/.MainActivity
adb shell pidof et.sera.nativeapp
adb install -r "release-output/SERA-${VERSION_NAME}-x86_64.apk"
adb shell am start -W -n et.sera.nativeapp/.MainActivity
adb shell pidof et.sera.nativeapp
test "$(adb shell dumpsys package et.sera.nativeapp | grep -m1 firstInstallTime | tr -d '\r')" = "$before"
echo "Signed universal and device-specific upgrades preserve existing installation."
