# SERA Native Android — Phase 1
Kotlin + Jetpack Compose foundation, separate from the legacy WebView project at /android.
Package: et.sera.nativeapp. Build with Android SDK 35 and JDK 17.

## Configuration
Pass public Supabase project URL and **publishable/anon** key as Gradle properties:
`./gradlew :app:assembleDebug -PSERA_SUPABASE_URL=https://YOURPROJECT.supabase.co -PSERA_SUPABASE_ANON_KEY=YOUR_PUBLIC_KEY`
Never use the service-role key in an APK.

## Current scope
Native login via Supabase password auth and a native navigation shell. Not yet a complete app:
token is held in memory only; refresh/persistence, registration, OAuth, profile loading, data views,
push notifications and production security hardening remain to be implemented.
Do not distribute as production-ready.

## Android release configurations
The default build is lightweight and does not include WebRTC. Voice messages and file sharing remain available. Live calling shows an availability message; incoming live-call notifications are suppressed. The full transport and UI are retained under `app/src/calling/java`. To compile that edition explicitly, use `gradle :app:assembleDebug -PSERA_ENABLE_LIVE_CALLING=true` with the existing public Supabase settings. This is not a production activation: managed TURN configuration and device/relay tests are still required. Production signed releases explicitly build the lightweight edition and reject APKs over 10 MiB or containing native WebRTC libraries.
