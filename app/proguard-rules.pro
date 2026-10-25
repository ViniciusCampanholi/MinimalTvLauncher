# Keep HiddenApiBypass and reflection targets intact
-keep class org.lsposed.hiddenapibypass.** { *; }
-dontwarn org.lsposed.hiddenapibypass.**
-keep class com.minimal.tvlauncher.bluetooth.** { *; }
-keep class com.minimal.tvlauncher.youtube.** { *; }
