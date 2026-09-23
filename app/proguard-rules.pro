# R8 rules for the release build. kotlinx-serialization, Ktor and supabase-kt ship their
# own consumer rules; only add here what a release build or walkthrough actually demands.

# Keep line numbers in stack traces (retrace them with build/outputs/mapping/*/mapping.txt),
# but don't leak the original file names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
