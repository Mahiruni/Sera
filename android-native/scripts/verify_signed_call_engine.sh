#!/usr/bin/env bash
set -euo pipefail
adb install -r "$RUNNER_TEMP/sera-call-smoke.apk"
adb shell pm grant et.sera.nativeapp android.permission.RECORD_AUDIO
adb logcat -c
# Require explicit success from the actual minified signed target APK.
set +e
result="$(timeout 90 adb shell am instrument -w -r et.sera.nativeapp.test/et.sera.nativeapp.SeraSignedCallInstrumentation 2>&1)"
status=$?
set -e
printf '%s\n' "$result"
if [[ "$status" != 0 ]] || ! grep -q 'INSTRUMENTATION_RESULT: sera_call_smoke=passed' <<<"$result" || ! grep -q 'INSTRUMENTATION_CODE: -1' <<<"$result"; then
  # Include WebRTC and JNI Zero tags: the native assertion often precedes the tombstone.
  adb logcat -d -b all -v threadtime > "$RUNNER_TEMP/sera-call-smoke-logcat.txt" || true
  grep -iE 'SeraRtc|webrtc|jni|fatal|check failed|abort|exception|SIGILL|SIGABRT' "$RUNNER_TEMP/sera-call-smoke-logcat.txt" | tail -n 240 || true
  adb logcat -d -b crash -v threadtime || true
  exit 1
fi
echo "Signed WebRTC engine created audio offers twice without crashing."
