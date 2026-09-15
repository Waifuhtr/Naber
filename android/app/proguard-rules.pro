# WebRTC yerel kodla konustugu icin isimler korunmali.
-keep class org.webrtc.** { *; }
-keepclassmembers class org.webrtc.** { *; }

# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**

# Firebase bildirim servisi
-keep class com.naber.app.push.NaberMessagingService { *; }

# Kotlin
-dontwarn kotlin.**
