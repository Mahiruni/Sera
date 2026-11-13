# Sera Connect: physical-device acceptance

This is a release acceptance protocol, not a record of completed tests. Run with two verified test accounts, two Android phones, the same published signed version, and permission from both participants. No production TURN secrets, call recordings, contact details or SDP in evidence.

## Setup

- Upgrade using Sera's in-app updater; verify version and retain both existing accounts.
- Enable microphone and notifications on both phones. Enable Wi-Fi and mobile data.
- Use a quiet room and headphones if practical. One phone should be on Wi-Fi, the other on mobile data.
- Record device model, Android version, Sera version, carrier/network type and approximate test time. Use anonymized participant A/B.
- Start a call from A to B, answer on B, and exchange ordinary sentences for at least 30 seconds. Each person must confirm the other's speech is understandable.

## Acceptance matrix

| Scenario | Action | Pass evidence |
|---|---|---|
| Baseline | Call both directions, talk for 30 seconds | Both hear intelligible speech, timer advances, history appears after ending |
| Wi-Fi to cellular | While talking, disable Wi-Fi on A with mobile data enabled | Same call ID/history; audio returns within the 45-second recovery deadline, no redial |
| Cellular to Wi-Fi | Re-enable Wi-Fi on A | Same call, speech resumes, timer does not reset |
| Remote handover | Repeat on B | Same call, both hear each other again |
| Brief offline | Disable both connections for 5–10 seconds on A; re-enable | Reconnecting shown as applicable, audio recovers or failure is explicitly reported |
| Prolonged outage | Disable both connections beyond recovery deadline | Ends safely, no indefinite microphone capture, no false Connected state |
| Multitasking | Minimize, browse feed/profile, open another chat, return using call bar | Audio continues, same timer; mute/end work; second concurrent call cannot replace first |
| Background | Press Home, then return through ongoing notification | Ongoing notification remains; speech continues subject to OS restrictions |
| Locked incoming | Lock B, call from A, use Answer notification | B resumes authenticated app and connects after any required device unlock/permission; no microphone before consent |
| Decline/Silence | Repeat using decline and silence | Decline ends invitation; silence suppresses local alert without accepting |
| Stale answer | Wait beyond invitation expiry, try old action | No microphone capture or new call |
| Audio route | Switch supported earpiece/speaker/headset; mute/unmute | Correct routing, no one-way audio, mic mute affects only sender |
| Interruption | Receive ordinary cellular call while Sera is active | No competing microphone capture; Sera responds safely to audio focus change |
| Weak mobile data | Test on naturally poor connection or controlled network shaping | Record subjective clarity, gaps, time to recover; no claims below usable bandwidth |
| Cross-platform | Android to web and web to Android; repeat handover | Same call and persistent history; both sides recover if supported |
| Call cleanup | End from each side and reopen chat | Both leave active call, no lingering foreground service or ringing notification |

## Evidence and release decision

For each scenario record PASS / FAIL / NOT RUN, time to regain intelligible audio, whether the timer reset, and a short description of symptoms. If failed, record only redacted app logs and the approximate timestamp. Do not record conversation audio unless both participants explicitly consent.

Automated browser impairment and native emulator relay tests complement this matrix, but cannot replace physical microphone, cellular handover, device lock-screen, Bluetooth or battery tests. A device-dependent failure must be reproduced and fixed before claiming full device compatibility.
