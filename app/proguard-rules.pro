# R8 rules for the release build.
#
# Most libraries here (Room, Compose, ML Kit, WorkManager, ktor) ship their own consumer rules, so
# this file only covers what R8 cannot see: things reached by reflection, by name, or through
# generated serializers. Everything listed below has a concrete reason — do not add blanket keeps
# for whole packages, since the point of the pass is to strip the ~10,000 unused icon classes that
# material-icons-extended contributes.

# --- Kotlin metadata ---------------------------------------------------------
# Serialization, Room's generated code and enum lookups all read these attributes at runtime.
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault

# --- kotlinx.serialization ---------------------------------------------------
# Serializers are generated as synthetic `$$serializer` classes and reached through a static
# `serializer()` on the companion. Neither is called from code R8 can trace.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.example.financemanager.**$$serializer { *; }
-keepclassmembers class com.example.financemanager.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

# Navigation3 serializes these NavKey objects to save and restore the back stack. They are
# `data object`s referenced only through the entry provider, so R8 sees no constructor call.
-keep class com.example.financemanager.Dashboard { *; }
-keep class com.example.financemanager.QuickEntry { *; }
-keep class com.example.financemanager.Budget { *; }
-keep class com.example.financemanager.Goals { *; }
-keep class com.example.financemanager.Insights { *; }
-keep class com.example.financemanager.CustomInsight { *; }
-keep class com.example.financemanager.Settings { *; }
-keep class com.example.financemanager.TransactionLogs { *; }
-keep class com.example.financemanager.AccountTransactions { *; }
-keep class com.example.financemanager.Subscriptions { *; }
-keep class com.example.financemanager.CategoryDetails { *; }
-keep class com.example.financemanager.Debt { *; }
-keep class com.example.financemanager.NetWorth { *; }
-keep class com.example.financemanager.Search { *; }
-keep class com.example.financemanager.SmsTransactions { *; }

# The Supabase DTOs are serialized by name against a remote schema; renaming a field would
# silently break sync rather than fail the build.
-keep class com.example.financemanager.data.remote.** { *; }

# --- Enums -------------------------------------------------------------------
# Room persists these as their names, and SupabaseDto.toEntity calls valueOf() on the way back in.
# Obfuscating the constants would turn every stored value into a parse failure.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keep enum com.example.financemanager.data.** { *; }

# --- Room --------------------------------------------------------------------
# Entities are constructed by generated code, but the type converters are looked up by type.
-keep class com.example.financemanager.data.Converters { *; }
-keep class com.example.financemanager.data.FinanceDatabase { *; }

# --- WorkManager -------------------------------------------------------------
# The default WorkerFactory instantiates workers reflectively from the class name stored in its
# own database, so a renamed worker breaks every already-scheduled job on an updated install.
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# --- Misc --------------------------------------------------------------------
# Keep line numbers so release crash reports stay readable, but hide the original file name.
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile
