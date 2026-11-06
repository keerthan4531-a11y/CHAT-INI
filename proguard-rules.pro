# Keep the engine's public surface (it is consumed by the Compose UI).
-keepattributes *Annotation*, InnerClasses
-keep class com.chatapp.aiengine.** { *; }

# OkHttp / coroutines are already consumer-proguarded by their artifacts.
-dontwarn okhttp3.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
