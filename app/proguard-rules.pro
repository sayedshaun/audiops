# Audiops release rules.
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

# LiteRT pulls in Play AI/asset delivery, which brings WorkManager and Room 2.5. Room finds
# each generated database (`<Name>_Impl`) by reflection on the class name; without these
# rules R8 breaks that lookup and the app crashes at startup in WorkManager's initializer.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keepnames class * extends androidx.room.RoomDatabase
-keep class androidx.work.impl.WorkDatabase_Impl { *; }
-keepnames class androidx.work.impl.WorkDatabase

# Keep stack traces readable in crash reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# MediaPipe Tasks (text to speech) is reached through JNI and reads its protobuf messages by
# reflection on field names (`fileContent_` and similar). Obfuscating either breaks synthesis at
# runtime ("Field fileContent_ ... not found") while the debug build still works.
-keep class com.google.mediapipe.** { *; }
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.mediapipe.**
-dontwarn com.google.protobuf.**
