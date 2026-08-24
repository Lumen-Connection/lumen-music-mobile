# Lumen Music Mobile — regras R8.
# LIÇÃO herdada do lumen-stream-mobile: o release v0.1.0 de lá subiu quebrado
# porque o R8 removeu classes que só são alcançadas por reflexão. Testar SEMPRE
# o APK de release, não só o debug.

# kotlinx.serialization: os serializers são resolvidos por reflexão a partir das
# classes @Serializable (DTOs do sync em sync/dto).
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.lumenconnection.music.**$$serializer { *; }
-keepclassmembers class com.lumenconnection.music.** {
    *** Companion;
}
-keepclasseswithmembers class com.lumenconnection.music.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**

# Room
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-dontwarn androidx.room.paging.**

# --- As regras abaixo entram em vigor na fase 4 (extractor), mantidas desde já
# --- porque são exatamente as que o lumen-stream-mobile precisou em produção.

# NewPipe Extractor + Rhino (avaliador JS usado para decifrar assinaturas)
-keep class org.schabi.newpipe.extractor.** { *; }
-dontwarn org.schabi.newpipe.extractor.**
-keep class org.mozilla.javascript.** { *; }
-dontwarn org.mozilla.javascript.**
-keep class org.mozilla.classfile.** { *; }

# youtubedl-android (Python + ffmpeg embarcados)
-keep class com.yausername.** { *; }
-dontwarn com.yausername.**

# commons-compress: o ZipUtils do youtubedl-android extrai o Python via
# ExtraFieldUtils, cujo <clinit> morre se o R8 tornar AsiExtraField não-concreta.
-keep class org.apache.commons.compress.** { *; }
-dontwarn org.apache.commons.compress.**
