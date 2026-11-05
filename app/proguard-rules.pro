# Keep download model classes (manually serialized, no reflection, but keep for clarity)
-keep class com.motrixlite.downloader.data.model.** { *; }
# OkHttp / Okio optional dependencies
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
# Keep line numbers for readable crash reports
-keepattributes SourceFile,LineNumberTable