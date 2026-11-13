# SERA Messaging 360 — verification checklist

Use two dedicated test accounts A and B on separate Android devices, plus an authenticated web session for B. Do not use production member conversations or record private content in logs. Verify production deployment SHA and the installed APK version before starting. Record device/OS, network, build SHA, scenario result and redacted timestamps.

| Scenario | Expected |
|---|---|
| A sends three messages quickly with keyboard open | Immediate optimistic bubbles; no duplicate IDs; keyboard remains usable |
| A sends offline, reconnects | Encrypted queue survives interruption; server receives each client ID once |
| B foreground in same conversation | No duplicate system alert; incoming timeline updates |
| B foreground in another screen | In-app banner opens correct conversation |
| B background or process killed (not force-stopped) | Account-bound grouped notification and correct launch destination |
| B denies OS notification permission | Inbox remains usable and authoritative unread count updates |
| B taps direct reply and explicit read | Authorized action; reply idempotent; read only explicit target |
| B hides read receipts on web | New private reads clear own unread count; A sees no read receipt |
| B toggles sharing back on | Old private read remains private; new read can be shared |
| B opts out of previews | Generic push and notification; verify public lock-screen content |
| B opts in | Authorized sender and preview with generic fallback on expired session |
| B mutes and enables quiet hours | No new push alerts while preference applies; message persists |
| A blocks B | No new authorized messaging or previews across block |
| Rotate FCM token and switch account | Current token registered to correct account; no prior-account alerts |
| Simulate deleted FCM backlog | Refresh history and unread totals without replaying stale alerts |
| A sends bold, URL, mention-like text, Amharic and emoji | Render safely, preserve original stored content; no executable links |
| A shares image/PDF/voice | Recipient opens authorized attachment; no public storage URL leakage |

Performance targets (measure, do not assume): p95 local send-to-bubble under 100 ms on representative hardware; no network calls on UI thread; report p50/p95 send-to-server acknowledgement and incoming visibility separately; inspect frame timing while scrolling 100+ messages. Run signed upgrade and APK size checks. Treat FCM acceptance as distinct from recipient delivery.

Production readiness: Vercel must run the validated recovery and preview endpoints, scheduler HTTP authentication must pass, transactional enqueue rollout must be explicitly enabled only after worker verification. Actual device tests remain pending until performed. Android force-stop and OEM battery restrictions can delay or prevent FCM delivery; report these separately.
