# Personal use, debug-focused. Minification is disabled by default.
# Keep annotations used by Room/Hilt tooling if minification is enabled later.
-keepattributes *Annotation*, Signature, RuntimeVisible*Annotations

# kotlinx.serialization
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
