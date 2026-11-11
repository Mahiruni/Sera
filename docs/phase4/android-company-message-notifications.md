# SERA company-message push delivery integration checklist

Implemented on the Phase 4 branch:
- The existing Firebase HTTP v1 sender requests Android high priority, channel `sera_messages`, and the system's default notification sound.
- The native app declares Android 13+ `POST_NOTIFICATIONS`.
- A user-configurable high-importance Android notification channel is registered on startup.

Required before claiming that company messages ring while another app is open:
1. Configure the native Firebase Android app using its own actual Firebase application ID, API key, and sender/project identifiers. Do not invent credentials or commit secrets.
2. Install and configure Firebase Messaging in the native app, register FCM tokens per authenticated SERA account through the existing `/api/network/push/devices` flow, handle refresh and logout unregistration.
3. Request runtime notification permission contextually after sign-in on Android 13+, with an explicit user action and an explanation.
4. On receipt of a company message, send push from the trusted server only after successful database persistence, with the recipient's user ID and company identity resolved server-side.
5. Open the correct authorized company conversation on notification tap. Never include private CVs or full message text in push payloads; use a short generic preview if notification privacy settings require it.
6. Test background, force-stopped, doze, locked-screen, silent mode, Do Not Disturb, permission denied, disabled channels, and delivery after a network reconnect.
7. Notification push acceptance by Firebase does **not** mean a message was delivered or read by the recipient app. Those receipts must come from authenticated device/reader acknowledgement.
8. Keep personal and company messages isolated; a company's team only sees messages authorized through membership roles.

Release blocker: Firebase receiver and token registration are not yet implemented in the native app. A sound-enabled channel alone cannot receive a push notification.
