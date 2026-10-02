# Flutter's embedding and plugins are kept by their own consumer rules; keep our channel host.
-keep class dz.wave.app.MainActivity { *; }
# flutter_secure_storage uses the Android Keystore through reflection-free APIs, but its
# Tink dependency references optional annotations.
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
# The Flutter embedding references Play Core (deferred components) optionally; the app does not
# ship it, and R8 would otherwise stop on the missing classes.
-dontwarn com.google.android.play.core.**
