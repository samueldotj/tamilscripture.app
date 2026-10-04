# UniFFI calls into libts_mobile.so through JNA, which reflects on these classes.
-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.** { public *; }
-keep class uniffi.ts_mobile.** { *; }
-dontwarn java.awt.**
