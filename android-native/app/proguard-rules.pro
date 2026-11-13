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

# WebRTC M144 also initializes Chromium's JNI Zero bridge from JNI_OnLoad.
# Its Java classes are referenced by native names and must survive R8.
-keep class org.jni_zero.** { *; }
-keep class J.** { *; }
# Upstream WebRTC AAR has an unused generated JniZeroJni reference.
# Match flutter-webrtc's narrow missing-class rule, not a blanket suppression.
-dontwarn org.jni_zero.JniZeroJni
