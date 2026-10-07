#!/usr/bin/env python3
"""Compile actual adapters and run owned Linux FFI evidence; never claim device proof."""
import argparse
import os
from pathlib import Path
import subprocess
import tempfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("artifacts", type=Path)
    parser.add_argument("directory", type=Path)
    parser.add_argument("--core-library-directory", type=Path, required=True)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    base = Path.home() / ".gradle/caches/modules-2/files-2.1"

    def jar(group, name, version=None):
        path = base / group / name
        if version:
            path /= version
        return str(sorted(path.glob("**/*.jar"))[-1])

    stdlib = jar("org.jetbrains.kotlin", "kotlin-stdlib", "2.1.20")
    compiler = [jar("org.jetbrains.kotlin", "kotlin-compiler-embeddable"), stdlib,
                jar("org.jetbrains.intellij.deps", "trove4j"), jar("org.jetbrains.kotlinx", "kotlinx-coroutines-core-jvm"), jar("org.jetbrains", "annotations")]
    dependencies = [("org.jetbrains.kotlin", "kotlin-test"), ("org.jetbrains.kotlin", "kotlin-test-junit"),
                    ("junit", "junit"), ("org.hamcrest", "hamcrest-core"), ("org.jetbrains", "annotations"),
                    ("org.jetbrains.kotlinx", "kotlinx-coroutines-core-jvm"), ("org.jetbrains.kotlinx", "kotlinx-serialization-core-jvm"),
                    ("org.jetbrains.kotlinx", "kotlinx-serialization-json-jvm"), ("com.squareup.okio", "okio-jvm")]
    cp = [stdlib] + [jar(*item) for item in dependencies]
    cp += [jar("net.java.dev.jna", "jna", "5.17.0"), jar("com.squareup.okhttp3", "okhttp", "4.12.0"), jar("org.json", "json", "20240303")]
    cp += [str(root / "app/build/tmp/kotlin-classes/debug"), str(root / "build/tmp/kotlin-classes/debug"),
           str(Path(os.environ["ANDROID_SDK_ROOT"]) / "platforms/android-36/android.jar")]
    names = ["NativeTorrentCache.kt", "NativePlaybackTransport.kt", "NativePlaybackControl.kt", "V2PlaybackControl.kt",
             "VipTvHttpGateway.kt", "NativeTorrentCoordinator.kt", "NativeTorrentEngineAcquisition.kt", "NativeTorrentCacheAndroid.kt",
             "NativeTorrentArtifacts.kt", "NativeTorrentScopeOwner.kt"]
    sources = [str(root / "app/src/androidMain/kotlin/org/viptv/app" / name) for name in names]
    sources += [str(root / "vendor/core/generated/native-kotlin/uniffi/viptv_core/viptv_core.kt"),
                str(args.artifacts.resolve() / "kotlin/uniffi/playback_gateway_ffi/playback_gateway_ffi.kt"),
                str(Path(__file__).parent / "host/OwnedNativeHostPipelineTest.kt")]
    with tempfile.TemporaryDirectory(prefix="owned-native-host-") as output:
        subprocess.run(["java", "-Xmx1024m", "-cp", ":".join(compiler), "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
                        "-no-stdlib", "-no-reflect", "-jvm-target", "17", "-Xfriend-paths=" + str(root / "app/build/tmp/kotlin-classes/debug"),
                        "-classpath", ":".join(cp), "-d", output, *sources], check=True)
        libraries = str(args.artifacts.resolve() / "target/release") + ":" + str(args.core_library_directory.resolve())
        subprocess.run(["java", "-Djna.library.path=" + libraries, "-Downed.fixture.directory=" + str(args.directory.resolve()),
                        "-cp", output + ":" + ":".join(cp), "org.junit.runner.JUnitCore", "org.viptv.app.OwnedNativeHostPipelineTest"], check=True)


if __name__ == "__main__":
    main()
