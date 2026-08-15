# Emulator packages are resolved reflectively from user-editable launch profiles,
# so component names must survive shrinking even with no static reference.
-keep class com.arcadia.shell.launcher.** { *; }

# XOrA Network Nakama DTOs are decoded through reified kotlinx.serialization — R8 must keep them.
-keep class com.arcadia.shell.xoranetwork.** { *; }
-keepclassmembers class com.arcadia.shell.xoranetwork.** { *; }
-keepattributes RuntimeVisibleAnnotations, AnnotationDefault
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Notification listener is bound by the system via the manifest component name.
-keep class com.arcadia.shell.conversations.ShellNotificationListenerService { *; }

# Libretro JNI entry points (including netplay port-2 pad).
-keep class com.arcadia.shell.libretro.LibretroNative { *; }

-keep class com.discord.socialsdk.** { *; }
-keepclassmembers class com.arcadia.shell.launcher.discord.DiscordSocialSdkBridge {
    native <methods>;
    public static void onNativeStatusChanged(int, boolean, boolean);
}
