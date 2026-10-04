# kotlinx.serialization: keep generated serializers for content models and navigation keys.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.tamilscripture.** {
    *** Companion;
}
-keepclasseswithmembers class com.tamilscripture.** {
    kotlinx.serialization.KSerializer serializer(...);
}
