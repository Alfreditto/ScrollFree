# ScrollFree — R8/ProGuard keep rules for the release build.

# ---------------------------------------------------------------------------
# Kotlin core — keep enum/sequence/internal plumbing intact.
# The IllegalAccessError crash came from R8 rewriting access to inlined
# kotlin.* / kotlinx.coroutines symbols, so keep the essential metadata.
# ---------------------------------------------------------------------------
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions, EnclosingMethod
-keepclassmembers class kotlin.Metadata { public <methods>; }
-dontwarn kotlin.**

# ---------------------------------------------------------------------------
# kotlinx.coroutines
# ---------------------------------------------------------------------------
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-keep class kotlinx.coroutines.internal.MainDispatcherFactory { *; }
-keep class kotlinx.coroutines.CoroutineExceptionHandler { *; }
-dontwarn kotlinx.coroutines.**

# ---------------------------------------------------------------------------
# youtubedl-android / jackson (reflection based JSON + native bridges)
# ---------------------------------------------------------------------------
-keep class com.yausername.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-dontwarn com.fasterxml.jackson.**
-dontwarn com.yausername.**

# Mapper DTOs deserialized by Jackson
-keep class com.yausername.youtubedl_android.mapper.** { *; }

# ---------------------------------------------------------------------------
# Apache Commons Compress / IO
# youtubedl-android's ZipUtils uses commons-compress (ZipFile, ZipArchiveInputStream)
# and commons-io (IOUtils). R8 was merging/renaming their internals, which made
# their static initializers throw ExceptionInInitializerError at runtime.
# ---------------------------------------------------------------------------
-keep class org.apache.commons.compress.** { *; }
-keep class org.apache.commons.io.** { *; }
-keep interface org.apache.commons.compress.** { *; }
-keep enum org.apache.commons.compress.** { *; }
-keepclassmembers enum org.apache.commons.compress.** { *; }
-keepclassmembers class org.apache.commons.compress.** { *; }
-keeppackagenames org.apache.commons.compress.**
-dontwarn org.apache.commons.compress.**
-dontwarn org.apache.commons.io.**


# ---------------------------------------------------------------------------
# Media3 / ExoPlayer
# ---------------------------------------------------------------------------
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# ---------------------------------------------------------------------------
# Coil (image loading)
# ---------------------------------------------------------------------------
-keep class coil.** { *; }
-dontwarn coil.**

# ---------------------------------------------------------------------------
# Compose
# ---------------------------------------------------------------------------
-keep class androidx.compose.runtime.** { *; }
-dontwarn androidx.compose.**

# ---------------------------------------------------------------------------
# App entry points referenced from the manifest / reflection
# ---------------------------------------------------------------------------
-keep class es.tortugaselectronicas.scrollfree.ScrollFreeApplication { *; }
-keep class es.tortugaselectronicas.scrollfree.MainActivity { *; }

# Keep app data classes (used by Jackson-free JSON / reflection where relevant)
-keep class es.tortugaselectronicas.scrollfree.data.** { *; }
-keep class es.tortugaselectronicas.scrollfree.update.** { *; }
