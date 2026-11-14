# StreamLab release rules. Libraries (Media3, Room, WorkManager, Cast, OkHttp) ship their own.

# Named in the manifest metadata and created by reflection by the Cast framework.
-keep class app.cleanroom.streamlab.cast.StreamLabCastOptionsProvider { *; }

# Crash reports stay readable for the developer (mapping file kept out of the APK).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Google code scanner (QR "Scanner le code d'un ami"): ML Kit finds its parts by name through
# manifest-declared registrars; renaming them crashed the scanner in the shrunk build.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_code_scanner.** { *; }
-keep class * implements com.google.firebase.components.ComponentRegistrar { *; }
