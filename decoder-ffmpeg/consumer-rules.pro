# FFmpeg JNI_OnLoad finds this class and callback by their exact Java names.
# The callback has no Java callers and must survive optimized APK packaging.
-keep,includedescriptorclasses class androidx.media3.decoder.ffmpeg.** { *; }
