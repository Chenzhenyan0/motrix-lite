# Keep download model classes (manually serialized, no reflection, but keep for clarity)
-keep class com.motrixlite.downloader.data.model.** { *; }
# Gopeed gomobile AAR is loaded via reflection (actual class: com.gopeed.libgopeed.Libgopeed)
-keep class com.gopeed.** { *; }
-keep class libgopeed.** { *; }
-keep class go.** { *; }
-keepclasseswithmembernames class * { native <methods>; }
-dontwarn com.gopeed.**
-dontwarn go.**
# OkHttp / Okio optional dependencies
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
# Keep line numbers for readable crash reports
-keepattributes SourceFile,LineNumberTable