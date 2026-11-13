#!/usr/bin/env bash
set -euo pipefail
# Test server lives on the CI host. Android emulator's 10.0.2.2 maps to host loopback.
: "${SERA_TEST_TURN_USERNAME:?Missing disposable username}"
: "${SERA_TEST_TURN_CREDENTIAL:?Missing disposable credential}"
adb install -r android-native/app/build/outputs/apk/debug/app-debug.apk
adb install -r android-native/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell pm grant et.sera.nativeapp android.permission.RECORD_AUDIO
adb logcat -c
set +e
result="$(timeout 160 adb shell am instrument -w -r -e relay_qa true -e turn_url 'turn:10.0.2.2:3478?transport=udp' -e turn_username "$SERA_TEST_TURN_USERNAME" -e turn_credential "$SERA_TEST_TURN_CREDENTIAL" et.sera.nativeapp.test/et.sera.nativeapp.SeraSignedCallInstrumentation 2>&1)"
status=$?
set -e
printf '%s\n' "$result"
if [[ "$status" != 0 ]] || ! grep -q 'INSTRUMENTATION_RESULT: sera_relay_recovery=passed' <<<"$result" || ! grep -q 'INSTRUMENTATION_CODE: -1' <<<"$result"; then
  adb logcat -d -b all -v threadtime > "$RUNNER_TEMP/sera-native-relay-logcat.txt" || true
  grep -iE 'SeraRtc|webrtc|jni|fatal|check failed|abort|exception|SIGILL|SIGABRT' "$RUNNER_TEMP/sera-native-relay-logcat.txt" | tail -n 200 || true
  exit 1
fi
echo 'Native relay connection and same-track ICE restart verified.'
