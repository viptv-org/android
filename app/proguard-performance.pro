# Native symbols and JNA structure/proxy names are part of the FFI ABI.
-keep class com.sun.jna.** { *; }
-keep class uniffi.** { *; }
-keep class * extends com.sun.jna.* { *; }
-keepclassmembers class * extends com.sun.jna.* { public *; }
-keep,allowoptimization class org.viptv.app.NativeTorrentRuntime { *; }
-keep class androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer { public <init>(...); }
-keepclasseswithmembers,includedescriptorclasses class * { native <methods>; }
# JNA contains desktop-only AWT helpers; Android has no AWT runtime.
-dontwarn java.awt.Component
-dontwarn java.awt.GraphicsEnvironment
-dontwarn java.awt.HeadlessException
-dontwarn java.awt.Window

-keep class org.playbackgateway.runtime.NativeRuntime { *; }
