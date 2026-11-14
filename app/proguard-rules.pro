# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.petal.launcher.data.**$$serializer { *; }
-keepclassmembers class com.petal.launcher.data.** { *** Companion; }
-keepclasseswithmembers class com.petal.launcher.data.** { kotlinx.serialization.KSerializer serializer(...); }
-keepclassmembers enum com.petal.launcher.data.** { *; }
