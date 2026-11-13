# WebRTC native JNI resolves Java names and callbacks at runtime.
# Preserve the complete bridge in signed/minified builds, not the rest of Sera.
-keep class org.webrtc.** { *; }
-keepclassmembers class * implements org.webrtc.PeerConnection$Observer { *; }
-keepclassmembers class * implements org.webrtc.SdpObserver { *; }
# Keep the native call engine accessible to signed-release instrumentation.
-keep class et.sera.nativeapp.SeraNativeRtc { *; }

# AndroidX Test invokes this class from the signed target APK. R8 cannot see
# those external references when shrinking the production app.
-keep class androidx.tracing.** { *; }
