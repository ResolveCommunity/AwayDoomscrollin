# Android ProGuard/R8 Rules for AwayDoomscrollin'

# Keep core components so they aren't completely wiped out or renamed in a way that breaks Intents/Manifest declarations
-keep class com.resolvecommunity.awaydoomscrollin.MainActivity { *; }
-keep class com.resolvecommunity.awaydoomscrollin.AntiScrollService { *; }

# General ProGuard rules for Android Jetpack Compose & Kotlin
-keep class kotlin.Metadata { *; }
-keepclassmembers class ** {
    @androidx.compose.runtime.Composable *;
}

# Strip debug and verbose logging from release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}
