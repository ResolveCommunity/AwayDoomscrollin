# Android ProGuard/R8 Rules for AwayDoomscrollin'

# Keep core components so they aren't completely wiped out or renamed in a way that breaks Intents/Manifest declarations
-keep class com.awaydoomscrollin.app.AntiScrollService { *; }
-keep class com.awaydoomscrollin.app.MainActivity { *; }
-keep class com.awaydoomscrollin.app.AntiCheatGuiltActivity { *; }
-keep class com.awaydoomscrollin.app.ClearTaskActivity { *; }

# General ProGuard rules for Android Jetpack Compose & Kotlin
-keep class kotlin.Metadata { *; }
-keepclassmembers class ** {
    @androidx.compose.runtime.Composable *;
}
