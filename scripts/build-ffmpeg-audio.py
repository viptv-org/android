#!/usr/bin/env python3
"""Rebuild the pinned LGPL audio fallback for every shipped Android ABI (Linux)."""
from pathlib import Path
import hashlib
import json
import os
import shutil
import subprocess
import tarfile
import urllib.request

ROOT = Path(__file__).resolve().parent.parent
MODULE = ROOT / "decoder-ffmpeg"
VERSION = "6.0.1"
SOURCE_SHA = "9b16b8731d78e596b4be0d720428ca42df642bb2d78342881ff7f5bc29fc9623"
DECODERS = ["dca", "ac3", "eac3", "truehd"]
NDK = Path(os.environ.get("ANDROID_NDK_HOME", str(Path(os.environ.get("ANDROID_HOME", Path.home() / "Android/Sdk")) / "ndk/28.2.13676358")))
TOOLCHAIN = NDK / "toolchains/llvm/prebuilt/linux-x86_64/bin"
WORK = MODULE / "build/native"
WORK.mkdir(parents=True, exist_ok=True)
upstream = json.loads((MODULE / "upstream.json").read_text())
for name, expected in upstream["files"].items():
    assert hashlib.sha256((MODULE / name).read_bytes()).hexdigest() == expected, name
archive = WORK / f"ffmpeg-{VERSION}.tar.xz"
if not archive.exists():
    urllib.request.urlretrieve(f"https://ffmpeg.org/releases/ffmpeg-{VERSION}.tar.xz", archive)
assert hashlib.sha256(archive.read_bytes()).hexdigest() == SOURCE_SHA
with tarfile.open(archive) as bundle:
    bundle.extractall(WORK, filter="data")
source = WORK / f"ffmpeg-{VERSION}"
shutil.copyfile(source / "COPYING.LGPLv2.1", MODULE / "COPYING.FFmpeg.LGPLv2.1")
licenses = MODULE / "src/main/assets/licenses/ffmpeg"
licenses.mkdir(parents=True, exist_ok=True)
shutil.copyfile(MODULE / "LICENSE", licenses / "MEDIA3-LICENSE")
shutil.copyfile(MODULE / "COPYING.FFmpeg.LGPLv2.1", licenses / "FFMPEG-LGPL-2.1")
products = {}
for abi, arch, cpu, triple in [
    ("armeabi-v7a", "arm", "armv7-a", "armv7a-linux-androideabi"),
    ("arm64-v8a", "aarch64", "armv8-a", "aarch64-linux-android"),
    ("x86_64", "x86_64", "x86-64", "x86_64-linux-android"),
]:
    output = WORK / abi
    output.mkdir(exist_ok=True)
    compiler = TOOLCHAIN / f"{triple}24-clang"
    options = [str(source / "configure"), f"--prefix={output}", "--target-os=android",
        f"--arch={arch}", f"--cpu={cpu}", f"--cc={compiler}", f"--cxx={compiler}++",
        f"--ar={TOOLCHAIN / 'llvm-ar'}", f"--nm={TOOLCHAIN / 'llvm-nm'}",
        f"--ranlib={TOOLCHAIN / 'llvm-ranlib'}", f"--strip={TOOLCHAIN / 'llvm-strip'}",
        "--enable-cross-compile", "--enable-static", "--disable-shared", "--enable-pic",
        "--disable-everything", "--disable-programs", "--disable-doc", "--disable-avdevice",
        "--disable-avformat", "--disable-swscale", "--disable-postproc", "--disable-avfilter",
        "--disable-symver", "--disable-v4l2-m2m", "--disable-vulkan", "--enable-swresample",
        "--extra-cflags=-O2 -fPIC", *[f"--enable-decoder={name}" for name in DECODERS]]
    if abi == "x86_64":
        options.append("--disable-asm")
    subprocess.run(options, cwd=output, check=True)
    subprocess.run(["make", f"-j{os.environ.get('FFMPEG_BUILD_JOBS', '2')}", "install"], cwd=output, check=True)
    destination = MODULE / f"src/main/jniLibs/{abi}/libffmpegJNI.so"
    destination.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run([str(compiler) + "++", "-shared", "-fPIC", "-std=c++11", "-O2", "-static-libstdc++",
        "-Wl,-z,max-page-size=16384", "-Wl,-Bsymbolic", "-Wl,--no-undefined",
        "-I" + str(output / "include"), str(MODULE / "src/main/jni/ffmpeg_jni.cc"),
        "-Wl,--start-group", *[str(output / f"lib/lib{name}.a") for name in ["avcodec", "swresample", "avutil"]],
        "-Wl,--end-group", "-landroid", "-llog", "-lm", "-o", str(destination)], check=True)
    subprocess.run([str(TOOLCHAIN / "llvm-strip"), "--strip-unneeded", str(destination)], check=True)
    products[abi] = hashlib.sha256(destination.read_bytes()).hexdigest()
(MODULE / "native.json").write_text(json.dumps({"ffmpeg": VERSION, "sourceSha256": SOURCE_SHA,
    "decoders": DECODERS, "ndk": (NDK / "source.properties").read_text().split("Pkg.Revision = ")[1].splitlines()[0],
    "api": 24, "pageSize": 16384, "sha256": products}, indent=2) + "\n")
