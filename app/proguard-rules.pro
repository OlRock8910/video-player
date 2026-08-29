# kotlinx.serialization keeps generated serializers referenced only reflectively.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.dadsvictory.** {
    *** Companion;
}
-keepclasseswithmembers class com.dadsvictory.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.dadsvictory.**$$serializer { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
