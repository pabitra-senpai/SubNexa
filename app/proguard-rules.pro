# SubNexa release (R8) rules.
# Compose, AndroidX, DataStore and Media3 ship their own consumer rules,
# so only project-specific rules are needed here.

# Keep readable stack traces in crash reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Media3 ExoPlayer optional extensions that are not bundled.
-dontwarn org.checkerframework.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
