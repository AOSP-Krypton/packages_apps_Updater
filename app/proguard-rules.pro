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

-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite* {
    <fields>;
}

# This is generated automatically by the Android Gradle plugin.
-dontwarn android.compat.Compatibility$ChangeConfig
-dontwarn com.android.i18n.timezone.WallTime
-dontwarn com.android.i18n.timezone.ZoneInfoData
-dontwarn com.android.org.conscrypt.TrustManagerImpl
-dontwarn dalvik.system.BlockGuard$VmPolicy
-dontwarn dalvik.system.CloseGuard
-dontwarn java.beans.ConstructorProperties
-dontwarn java.beans.Transient
-dontwarn libcore.util.NativeAllocationRegistry
-dontwarn sun.misc.Cleaner