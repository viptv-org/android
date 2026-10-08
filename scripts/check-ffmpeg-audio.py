#!/usr/bin/env python3
"""Verify source pin, native decoder integrity, ABI/page alignment and optional APK."""
from pathlib import Path
import hashlib
import json
import struct
import sys
import re
import zipfile

root = Path(__file__).resolve().parent.parent
module = root / "decoder-ffmpeg"
upstream = json.loads((module / "upstream.json").read_text())
native = json.loads((module / "native.json").read_text())
media3 = re.search(r'^media3\s*=\s*"([^"]+)"', (root / "gradle/libs.versions.toml").read_text(), re.MULTILINE)
assert media3 and upstream["tag"] == media3.group(1), "Rebuild the audio extension when upgrading Media3"
for name, expected in upstream["files"].items():
    assert hashlib.sha256((module / name).read_bytes()).hexdigest() == expected, name
for abi, machine in [("armeabi-v7a", 40), ("arm64-v8a", 183), ("x86_64", 62)]:
    data = (module / f"src/main/jniLibs/{abi}/libffmpegJNI.so").read_bytes()
    assert hashlib.sha256(data).hexdigest() == native["sha256"][abi], f"Native audio checksum: {abi}"
    assert data[:4] == b"\x7fELF" and struct.unpack_from("<H", data, 18)[0] == machine, abi
    is64 = data[4] == 2
    phoff = struct.unpack_from("<Q" if is64 else "<I", data, 32 if is64 else 28)[0]
    phsize, phcount = struct.unpack_from("<HH", data, 54 if is64 else 42)
    for index in range(phcount):
        offset = phoff + index * phsize
        if struct.unpack_from("<I", data, offset)[0] == 1:
            alignment = struct.unpack_from("<Q" if is64 else "<I", data, offset + (48 if is64 else 28))[0]
            assert alignment >= 16384, f"Native audio page alignment: {abi}"
    assert b"libc++_shared.so\0" not in data, f"Unexpected shared C++ runtime: {abi}"
if len(sys.argv) == 2:
    with zipfile.ZipFile(sys.argv[1]) as apk:
        for abi, expected in native["sha256"].items():
            assert hashlib.sha256(apk.read(f"lib/{abi}/libffmpegJNI.so")).hexdigest() == expected, abi
        for name in ["MEDIA3-LICENSE", "FFMPEG-LGPL-2.1", "NOTICE.txt"]:
            assert apk.read(f"assets/licenses/ffmpeg/{name}"), name
print("PASS: pinned Media3 audio decoder, three ABIs, 16 KB ELF alignment" + (", APK libraries/notices" if len(sys.argv) == 2 else ""))
