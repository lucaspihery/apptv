# Les règles de JNA / UniFFI sont fournies par core:native (consumer-rules.pro).

# Destinations de navigation typées (kotlinx.serialization) : garder leurs sérialiseurs.
-keep @kotlinx.serialization.Serializable class io.github.lucaspihery.apptv.** { *; }
-keepclassmembers class io.github.lucaspihery.apptv.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
