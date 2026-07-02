# ExoPlayer / Media3
-keep class androidx.media3.** { *; }
# Retrofit / Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.mydouyin.data.model.** { *; }
-keepclassmembers,allowobfuscation class * {
  @com.google.gson.annotations.SerializedName <fields>;
}
