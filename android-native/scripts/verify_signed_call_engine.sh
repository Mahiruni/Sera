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
  adb logcat -d -t 500 -s SeraRtc AndroidRuntime libc DEBUG || true
  exit 1
fi
echo "Signed WebRTC engine created audio offers twice without crashing."
