#!/usr/bin/env bash
set -euo pipefail
adb install -r "$RUNNER_TEMP/sera-call-smoke.apk"
adb logcat -c
# The smoke runs in the already-installed, signed and minified x86_64 release.
result="$(adb shell am instrument -w -r -e class et.sera.nativeapp.SeraNativeRtcSmokeTest et.sera.nativeapp.test/androidx.test.runner.AndroidJUnitRunner)"
printf '%s\n' "$result"
if ! grep -q 'OK (1 test)' <<<"$result" || grep -Eq 'FAILURES|INSTRUMENTATION_FAILED|Process crashed' <<<"$result"; then
  adb logcat -d -t 500 -s SeraRtc AndroidRuntime libc DEBUG || true
  exit 1
fi
echo "Signed WebRTC engine created audio offers twice without crashing."
