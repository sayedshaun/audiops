# AudioOps release rules.
# sherpa-onnx and LiteRT are reached through JNI, so their classes and native
# method names must survive shrinking and obfuscation.
-keep class com.k2fsa.sherpa.onnx.** { *; }
-keep class com.google.ai.edge.litert.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# JTransforms pulls in jlargearrays, which references the JDK-internal sun.misc.Cleaner.
# That class is never reached on Android, so the reference is safe to ignore.
-dontwarn sun.misc.Cleaner

# Keep stack traces readable in crash reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
