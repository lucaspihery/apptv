# JNA et les bindings UniFFI utilisent la réflexion : ne pas les renommer ni les retirer.
-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.** { public *; }
-keep class io.github.lucaspihery.apptv.core.ffi.** { *; }
-dontwarn java.awt.**
