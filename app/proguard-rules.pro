# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /Users/gfan/dev/sdk_current/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:
-keepattributes LineNumberTable,SourceFile
-keep class com.tutpro.baresip.plus.Camera2 { *; }
-dontobfuscate
-dontoptimize

# Prevent R8 from inlining, merging, or optimizing Android framework and internal classes
-keep class android.** { *; }
-keepclassmembers class android.** { *; }
-keep class com.android.internal.** { *; }
-keepclassmembers class com.android.internal.** { *; }
-dontwarn com.android.internal.**

# Keep androidx activity and activity result classes intact
-keep class androidx.activity.** { *; }
-keepclassmembers class androidx.activity.** { *; }
