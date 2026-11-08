# Tesseract (JNI calls back into these classes by name)
-keep class com.googlecode.tesseract.android.** { *; }
-keep class com.googlecode.leptonica.android.** { *; }

# kotlinx.serialization: keep generated serializers of our @Serializable classes
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-keepclassmembers @kotlinx.serialization.Serializable class dev.ticker.converter.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class dev.ticker.converter.**$$serializer { *; }

# Retrofit interfaces with suspend functions (Retrofit 3 ships most rules itself)
-keep,allowobfuscation interface dev.ticker.converter.data.remote.*Api

# Release builds log nothing: OCR text and network errors stay out of logcat.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}
