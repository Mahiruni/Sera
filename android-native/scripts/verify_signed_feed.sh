#!/usr/bin/env bash
set -euo pipefail
# Dedicated existing QA identity only. Never print credentials, tokens or post content.
: "${SERA_E2E_OWNER_EMAIL:?Missing dedicated QA email}"
: "${SERA_E2E_OWNER_PASSWORD:?Missing dedicated QA password}"
: "${SERA_SUPABASE_ANON_KEY:?Missing Supabase publishable key}"
refresh="$(python3 android-native/scripts/feed_qa_auth.py)"
echo "::add-mask::$refresh"
adb shell am force-stop et.sera.nativeapp
adb shell pm grant et.sera.nativeapp android.permission.POST_NOTIFICATIONS || true
adb logcat -c
set +e
result="$(timeout 180 adb shell am instrument -w -r -e feed_refresh "$refresh" et.sera.nativeapp.test/et.sera.nativeapp.SeraSignedCallInstrumentation 2>&1)"
status=$?
set -e
if [[ "$status" != 0 ]] || ! grep -q 'INSTRUMENTATION_RESULT: sera_feed_smoke=passed' <<<"$result" || ! grep -q 'INSTRUMENTATION_CODE: -1' <<<"$result"; then
    echo "::error::Signed Android did not display an authenticated live feed post."
    printf "%s\n" "$result" | grep -E "INSTRUMENTATION_CODE|AssertionError: Authenticated signed Android" | tail -n 8 || true
    adb logcat -d -s SeraFeed:D SeraPerf:I AndroidRuntime:E '*:S' | tail -n 120 || true
    exit 1
fi
echo "Signed Android rendered a real authenticated feed post."
