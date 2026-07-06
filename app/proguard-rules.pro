# Keep annotation/signature metadata used by provider SDKs and Kotlin libraries.
-keepattributes Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations

# ML Kit barcode scanning loads native code and internal models at runtime.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_barcode.** { *; }

# Keep crypto provider registrations stable for signing and address derivation.
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# Keep TON Kotlin models stable across optimization.
-keep class org.ton.** { *; }
-dontwarn org.ton.**
