# TermuxLite proguard & R8 optimization rules
-optimizationpasses 5
-allowaccessmodification
-mergeinterfacesaggressively

# Keep application and terminal view entrypoints
-keep class com.termux.terminal.** { *; }
-keep class com.termux.view.** { *; }
-keep class com.termux.lite.** { *; }
-keep class com.termux.app.TermuxOpenReceiver { *; }
-keep class com.termux.app.TermuxOpenReceiver$ContentProvider { *; }

-dontwarn com.termux.terminal.**
-dontwarn com.termux.view.**

# Strip unnecessary debug logs in release
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}
