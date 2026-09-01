# Media3 reflectively instantiates the session service and its player.
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

-keep class com.mono.music.PlaybackService { *; }
