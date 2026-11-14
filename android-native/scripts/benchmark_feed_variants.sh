#!/usr/bin/env bash
set -euo pipefail
# CI only. Both builds are minified, signed with the same temporary QA key,
# built from the same commit, and run on the same emulator with fresh QA sessions.
: "${RUNNER_TEMP:?}"
dir="$RUNNER_TEMP/sera-performance"
mkdir -p "$dir"
printf 'variant,trial,apk_bytes,result,feed_ms,cpu_ms,pss_kb\n' > "$dir/results.csv"
failed=0
# Alternate order to reduce systematic warm-up and network time bias.
for trial in 1 2 3; do
  if [[ "$trial" == 2 ]]; then variants=(full lite); else variants=(lite full); fi
  for variant in "${variants[@]}"; do
    apk="$dir/$variant.apk"
    adb uninstall et.sera.nativeapp.test >/dev/null 2>&1 || true
    adb uninstall et.sera.nativeapp >/dev/null 2>&1 || true
    adb install "$apk" >/dev/null
    adb install "$dir/$variant-test.apk" >/dev/null
    adb shell pm grant et.sera.nativeapp android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true
    refresh="$(python3 android-native/scripts/feed_qa_auth.py)"
    echo "::add-mask::$refresh"
    adb logcat -c
    set +e
    result="$(timeout 180 adb shell am instrument -w -r -e feed_only true -e feed_refresh "$refresh" et.sera.nativeapp.test/et.sera.nativeapp.SeraSignedCallInstrumentation 2>&1)"
    status=$?
    set -e
    unset refresh
    bytes="$(stat -c%s "$apk")"
    metric(){ sed -n "s/^INSTRUMENTATION_RESULT: $1=//p" <<<"$result" | tr -d '\r' | tail -1; }
    if [[ "$status" == 0 ]] && grep -q 'sera_feed_smoke=passed' <<<"$result" && grep -q 'INSTRUMENTATION_CODE: -1' <<<"$result"; then
      printf '%s,%s,%s,pass,%s,%s,%s\n' "$variant" "$trial" "$bytes" "$(metric sera_feed_ms)" "$(metric sera_feed_cpu_ms)" "$(metric sera_feed_pss_kb)" >> "$dir/results.csv"
    else
      printf '%s,%s,%s,fail,,,\n' "$variant" "$trial" "$bytes" >> "$dir/results.csv"
      failed=1
    fi
    # Only privacy-safe timing and feed lifecycle messages; never post contents.
    adb logcat -d -s SeraFeed:D SeraPerf:I '*:S' > "$dir/$variant-$trial.log" || true
    adb shell am force-stop et.sera.nativeapp
  done
done
cat "$dir/results.csv"
python3 - "$dir/results.csv" <<'PY'
import csv, statistics, sys
rows=list(csv.DictReader(open(sys.argv[1])))
for variant in ('lite','full'):
    samples=[r for r in rows if r['variant']==variant]
    passed=[r for r in samples if r['result']=='pass']
    print(f"{variant}: {len(passed)}/{len(samples)} successful feed renders; APK {int(samples[0]['apk_bytes'])/1048576:.2f} MiB")
    for key in ('feed_ms','cpu_ms','pss_kb'):
        values=[int(r[key]) for r in passed if r[key].isdigit()]
        if values: print(f"  median {key}: {statistics.median(values)}")
PY
exit "$failed"
