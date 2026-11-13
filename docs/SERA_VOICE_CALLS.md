# SERA voice calls — restricted pilot
The voice-call button is implemented in the web and Android conversation headers. It is intentionally hidden until both members of a conversation are enrolled in a private pilot and a TURN relay is configured. Do not enable it for public users without real media verification.

## Transport and privacy
- WebRTC voice only, with relay-only ICE and no video or data channels.
- The call-control database stores offers and answers only while a call is live. Terminal state and expiring leases remove SDP. A database maintenance job clears expired sessions.
- The call-control API is authenticated; blocked users, outsiders and anonymous users cannot receive signaling or TURN credentials.
- The relay uses short-lived coturn-style HMAC credentials. The shared secret is never sent to clients.
- No audio recording is stored. Call history contains call metadata, not media.

## Relay provisioning
Use a dedicated TURN service supporting WebRTC, TLS and TURN REST HMAC authentication (coturn or compatible). Configure a public TLS endpoint, correct external/relay addresses, UDP/TCP relay ports, short-lived credential authentication and rate/bandwidth quotas. Deny relay connections to loopback, link-local, private networks and cloud metadata endpoints. Do not use a public anonymous TURN server.
A SERA database administrator must configure the private settings row with the relay URL (e.g. turns:relay.example.invalid:5349?transport=tcp), the shared secret using the secure dashboard, and the UUIDs of two isolated verified QA members; only then enable the pilot. Never commit the shared secret or paste it into a chat. No relay has been provisioned in this implementation.

## Acceptance before public release
1. Confirm both QA members are verified and connected.
2. Validate real calls across Android↔Android, web↔web and Android↔web, including separate mobile networks and Wi-Fi.
3. Verify inbound and outbound audio, microphone permission denial, muted audio, Android speaker route, declined/missed/cancelled calls, network loss and timeout.
4. Verify that blocked/outsider accounts cannot call, and that no direct candidates or credentials leak to other users.
5. Verify closed-app/lock-screen ringing and platform notification/foreground-service lifecycle before enabling public calls. The current pilot is foreground, within the open conversation. Android ends a call when the app goes to background.
6. Run Android compile, release lint, APK signing, emulator tests, browser typecheck/build, real-device visual acceptance and authenticated two-account voice tests.
7. Only after those gates pass, expand the pilot and publish an incremented signed APK. Do not re-publish v1.0.69.

## Bounded Android incoming-call ringing
The pilot now posts an expiring call-category notification with the platform's insistent ring flag, a local Silence action, and an Open call action. The latter revalidates the invitation with the server. Silence is device-local and does not decline a call. When an incoming ringing conversation is moved to the background, its notification is restored for the remaining ring window; the foreground ringtone is stopped. Notification sound, vibration and lock-screen display are subject to OS settings, DND and OEM behavior. No full-screen launch or background microphone access is requested. Active calls still end when the app leaves the foreground; a microphone foreground service and a separate on-device acceptance pass are required before changing that behavior.

## Active Android calls in the background (pilot implementation)
A user-initiated call now requests a non-exported microphone foreground service from the visible activity, after notification and microphone permissions. The service must acknowledge foreground status before the RTC engine opens the microphone. Its persistent, private notification can return to the call or end it. Signaling polling remains active when the activity is backgrounded and the service lease is valid. Stopping the service, removing the task, leaving the conversation or process death closes the local call; the server lease expires if signaling cannot be delivered. No service is started from an incoming push, and the service never restarts automatically. Physical Android testing (including lock screen, OEM battery behavior, permission revocation, audio routing and task removal) is required before enabling the pilot. This is not a claim of verified background calling.

## Project AURA — first calling experience pass
Android and web now dial directly from the call icon, separate recent calls, and use matching teal light/dark identity surfaces. A minimized call remains active while the same conversation stays mounted; navigating away still tears down the existing call controller. Cancellation is handled during preparation, and outgoing status says Calling rather than claiming the recipient is ringing without a delivery acknowledgment. Web overlays render at document level to avoid composer stacking. The pilot remains restricted, relay-only, and uses the existing signed APK workflow. Physical-device audio, background behavior, full audio route selection, localized call strings, and app-wide persistent call ownership remain acceptance/implementation work; do not expand public eligibility on this basis.
