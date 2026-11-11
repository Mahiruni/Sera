# SERA.et Android v1.0.1

Android WebView wrapper for the SERA production-origin fallback at https://sera.hisabtech.com.

## Build
Run GitHub Actions **SERA Android APK** on main or push changes under android/. Download the **SERA-v1.0.1-debug-apk** artifact from the successful workflow run. The artifact contains app-debug.apk and is installable for testing.

The debug APK is signed by Android's generated debug key. It is **not** a Play Store release. Do not distribute as a production signed release. Configure a secure release signing key and test login, OAuth redirects, uploads, Android back navigation, notifications, and real-device compatibility before a public release.

This initial wrapper does not implement native push notifications or a native camera capture flow. Standard HTML file picker uploads are supported. External authentication flows may require additional verified redirect handling.

## Build trigger
This documentation update requests the GitHub Actions APK build on the main branch. Confirm the workflow run succeeds before distributing an APK.
