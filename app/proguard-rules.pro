# crashlytics
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception

# GSON rules https://github.com/google/gson/blob/master/examples/android-proguard-example/proguard.cfg
# https://github.com/google/gson/blob/master/examples/android-proguard-example/proguard.cfg

# Gson uses generic type information stored in a class file when working with fields. Proguard
# removes such information by default, so configure it to keep all of it.
-keepattributes Signature

# Gson specific classes
-dontwarn sun.misc.**
#-keep class com.google.gson.stream.** { *; }

# Application classes that will be serialized/deserialized with Gson.
-keep class com.github.ashutoshgngwr.noice.models.** { <fields>; }

# Cast models that are serialized/deserialized with Gson for network communication.
-keep class com.github.ashutoshgngwr.noice.cast.models.** { <fields>; }

# Prevent proguard from stripping interface information from TypeAdapter, TypeAdapterFactory,
# JsonSerializer, JsonDeserializer instances (so they can be used in @JsonAdapter)
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Prevent R8 from leaving Data object members always null
-keepclassmembers,allowobfuscation class * {
  @com.google.gson.annotations.SerializedName <fields>;
}

# Retain generic signatures of TypeToken and its subclasses with R8 version 3.0 and higher.
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken

# Preserve names of all fragment classes for analytics
-keepnames class com.github.ashutoshgngwr.noice.fragment.*

# keep parcelable and serializable classes
-keepnames class * extends android.os.Parcelable
-keepnames class * extends java.io.Serializable
