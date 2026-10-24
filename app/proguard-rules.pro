# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ART rejects the R8-optimized Compose entry point when the W10M surface is first composed.
# Keep only this large entry point unoptimized; R8 can still shrink and obfuscate the rest.
-keepclassmembers class com.flivoro.tile8auncher.ui.phone.PhoneLauncherKt {
    public static void PhoneLauncherSurface(...);
}
