# Keep the app's own classes as written (serialization, reflection-free but safest).
-dontobfuscate
-keep class app.medassist.** { *; }
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
# kotlinx.serialization
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * { kotlinx.serialization.KSerializer serializer(...); }
# OkHttp optional dependencies
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
