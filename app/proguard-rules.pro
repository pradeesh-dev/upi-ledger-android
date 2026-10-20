# Room entities are accessed via generated code; keep their shape.
-keep class com.newdreamdata.upiledger.data.** { *; }
# ML Kit
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**
# Kotlin coroutines debug metadata
-dontwarn kotlinx.coroutines.**
