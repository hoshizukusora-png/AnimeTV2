# ============================================================
# ProGuard Rules - StarVision TV
# ============================================================

# Jaga semua class aplikasi utama
-keep class com.animatv.player.** { *; }

# Jaga model (Gson parsing)
-keep class com.animatv.player.model.** { *; }
-keepclassmembers class com.animatv.player.model.** { *; }

# Gson
-keep class com.google.gson.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# ExoPlayer
-keep class com.google.android.exoplayer2.** { *; }
-dontwarn com.google.android.exoplayer2.**

# OkHttp
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule { *; }
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** { *; }

# Lottie
-keep class com.airbnb.lottie.** { *; }
-dontwarn com.airbnb.lottie.**

# AndroidX
-keep class androidx.** { *; }
-dontwarn androidx.**

# Data Binding
-keep class androidx.databinding.** { *; }
-dontwarn androidx.databinding.**

# MultiDex - PENTING untuk Android 5
-keep class androidx.multidex.** { *; }

# Jangan obfuscate Activity, Service, Receiver
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.view.View

# Jaga Fragment
-keep public class * extends androidx.fragment.app.Fragment

# Serializable
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Parcelable
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Enum
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Jaga R class
-keepclassmembers class **.R$* {
    public static <fields>;
}

# Suppress warnings umum
-dontwarn java.lang.invoke.**
-dontwarn **$$Lambda$*
-dontwarn kotlin.**
-dontwarn kotlinx.**
