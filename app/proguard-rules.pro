# Personal Terminal – R8 rules

# Keep Room entities & DAOs metadata
-keep class dev.personalterminal.data.db.** { *; }

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class dev.personalterminal.**$$serializer { *; }
-keepclassmembers class dev.personalterminal.** { *** Companion; }
-keepclasseswithmembers class dev.personalterminal.** { kotlinx.serialization.KSerializer serializer(...); }

# Google API client / Drive (reflection-based JSON models)
-keep class com.google.api.services.drive.** { *; }
-keep class com.google.api.client.** { *; }
-keepclassmembers class * { @com.google.api.client.util.Key <fields>; }
-dontwarn com.google.api.client.**
-dontwarn com.google.common.**
-dontwarn org.apache.http.**
-dontwarn javax.naming.**
-dontwarn org.ietf.jgss.**
-dontwarn org.joda.time.**
-dontwarn org.checkerframework.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**

# Glance widget receivers are referenced from the manifest
-keep class dev.personalterminal.widget.** { *; }
