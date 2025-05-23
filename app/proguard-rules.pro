-dontobfuscate
-keepattributes Signature, LineNumber

# Firebase message is missing its analytics connector but we deliberately don't want them
-dontwarn com.google.firebase.**

# Some internal APIs are "referenced" but not really used
-dontwarn sun.nio.**

# WebRTC doesn't bundle its own ProGuard rules :-(
-keep class org.webrtc.** {
    @org.webrtc.CalledByNative *;
    @org.webrtc.CalledByNativeUnchecked *;
}