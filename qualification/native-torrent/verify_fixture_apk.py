#!/usr/bin/env python3
"""Verify actual isolated APK identity, private products, policy and alignment."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import struct
import subprocess
import zipfile


def verify(apk, artifacts, root, aapt):
    stamp = json.loads((artifacts / "fixture-build.json").read_text())
    lock = json.loads((root / "vendor/playback-gateway/lock.json").read_text())
    badging = subprocess.check_output([str(aapt), "dump", "badging", str(apk)], text=True)
    assert "package: name='org.viptv.app.nativefixture'" in badging
    assert re.search(r"^(?:minSdkVersion|sdkVersion):'24'$", badging, re.MULTILINE)
    manifest = subprocess.check_output([str(aapt), "dump", "xmltree", str(apk), "--file", "AndroidManifest.xml"], text=True)
    activity = re.search(r"E: activity .*?A: [^\n]*android:name[^\n]*OwnedNativeFixtureActivity[^\n]*\n(?:(?!E:).)*", manifest, re.DOTALL)
    assert activity and "android:exported" in activity.group() and "=false" in activity.group()
    policy = subprocess.check_output([str(aapt), "dump", "xmltree", str(apk), "--file", "res/xml/network_security_config.xml"], text=True)
    assert re.findall(r"A: cleartextTrafficPermitted=(true|false)", policy) == ["false", "true"]
    assert re.findall(r"T: '([^']+)'", policy) == ["127.0.0.1"]
    assert "E: debug-overrides " in policy and "E: base-config " in policy
    with apk.open("rb") as raw, zipfile.ZipFile(apk) as archive:
        assert "assets/native-fixture/config.json" in archive.namelist()
        assert any("viptv_fixture_ca" in name for name in archive.namelist())
        dex = b"".join(archive.read(name) for name in archive.namelist() if re.fullmatch(r"classes[0-9]*\.dex", name))
        assert b"newNativeOwned" in dex and b"OwnedNativeFixtureActivity" in dex
        hashes = {}
        for abi in ("armeabi-v7a", "arm64-v8a", "x86_64"):
            for library in ("libplayback_gateway_ffi.so", "libviptv_core.so", "libjnidispatch.so"):
                name = f"lib/{abi}/{library}"
                entry = archive.getinfo(name)
                assert entry.compress_type == zipfile.ZIP_STORED
                raw.seek(entry.header_offset)
                filename, extra = struct.unpack_from("<HH", raw.read(30), 26)
                assert (entry.header_offset + 30 + filename + extra) % 16384 == 0
                digest = hashlib.sha256(archive.read(name)).hexdigest()
                hashes[name] = digest
                if library == "libplayback_gateway_ffi.so":
                    assert digest == stamp["files"][f"jniLibs/{abi}/{library}"]
                    assert digest != lock["files"][f"ffi/generated/android/jniLibs/{abi}/{library}"]
        for path, digest in lock["files"].items():
            if path in ("LICENSE", "PROVENANCE.md") or path.startswith("THIRD_PARTY/"):
                assert hashlib.sha256(archive.read("assets/playback-gateway/" + path)).hexdigest() == digest
    return hashes


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("artifacts", type=Path)
    parser.add_argument("--aapt", type=Path)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    sdk = Path(os.environ.get("ANDROID_SDK_ROOT", os.environ.get("ANDROID_HOME", "")))
    aapt = args.aapt or sorted((sdk / "build-tools").glob("*/aapt2"))[-1]
    try:
        hashes = verify(args.apk, args.artifacts, root, aapt)
    except (AssertionError, OSError, ValueError, KeyError):
        raise SystemExit("Isolated owned APK verification failed.")
    print(json.dumps({"fixture_apk_sha256": hashlib.sha256(args.apk.read_bytes()).hexdigest(), "libraries": hashes}, indent=2))


if __name__ == "__main__":
    main()
