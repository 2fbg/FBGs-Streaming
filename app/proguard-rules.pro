# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep line number and source file information for debugging stack traces
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# Room resolves database implementations by their generated class name.
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Room inspects entity metadata when validating database schemas.
-keep @androidx.room.Entity class * { *; }

# Retrofit discovers endpoints from their HTTP annotations.
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# Preserve models that opt into Moshi's generated adapters.
-keep @com.squareup.moshi.JsonClass class * { *; }
