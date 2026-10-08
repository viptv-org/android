#!/usr/bin/env python3
"""Check real APK ABI contents, checksums, ZIP alignment and native notices."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import struct
import subprocess
import zipfile

ABIS = ["armeabi-v7a", "arm64-v8a", "x86_64"]


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("--aapt", help="Configured aapt2 executable for actual APK minSdk inspection")
    args = parser.parse_args()
    project = Path(__file__).resolve().parent.parent
    lock = json.loads((project / "vendor/playback-gateway/lock.json").read_text())
    aapt = args.aapt or shutil.which("aapt2")
    if not aapt:
        sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
        candidates = list((Path(sdk) / "build-tools").glob("*/aapt2*")) if sdk else []
        candidates = [path for path in candidates if path.name in {"aapt2", "aapt2.exe"} and path.is_file()]
        candidates.sort(key=lambda path: [int(part) for part in re.findall(r"\d+", path.parent.name)])
        aapt = str(candidates[-1]) if candidates else None
    require(aapt, "Configure the installed Android SDK/aapt2 for actual APK minSdk verification")
    badging = subprocess.check_output([aapt, "dump", "badging", str(args.apk)], text=True)
    require(re.search(r"^(?:minSdkVersion|sdkVersion):'24'$", badging, re.MULTILINE), "Native APK must retain minSdk 24")
    manifest = subprocess.check_output([aapt, "dump", "xmltree", str(args.apk), "--file", "AndroidManifest.xml"], text=True)
    resources = subprocess.check_output([aapt, "dump", "resources", str(args.apk)], text=True)
    policy_resource = re.search(
        r"resource (0x[0-9a-f]+) xml/network_security_config\b\s+\(\) \(file\) (res/[^\s]+\.xml) type=XML",
        resources,
    )
    require(policy_resource, "APK must contain its default network security policy resource")
    # Release resource optimization can rename the packaged XML file.
    policy = subprocess.check_output([aapt, "dump", "xmltree", str(args.apk), "--file", policy_resource.group(2)], text=True)
    manifest_id = re.search(r"android:networkSecurityConfig[^\n]*=@(0x[0-9a-f]+)", manifest)
    require(manifest_id and policy_resource.group(1) == manifest_id.group(1), "APK must use the checked network security policy")
    require(re.search(r"android:usesCleartextTraffic[^\n]*=true", manifest), "APK must permit HTTP media")
    require("OwnedNativeFixtureActivity" not in manifest, "Owned fixture Activity must not ship in normal manifest")
    require(policy.count("E: base-config ") == 1 and policy.count("E: domain-config ") == 0, "Unexpected APK network policy scopes")
    require(re.findall(r"A: cleartextTrafficPermitted=(true|false)", policy) == ["true"], "APK must support HTTP media sources")
    require(policy.count("E: certificates ") == 1 and re.findall(r'A: src="([^"\n]+)"', policy) == ["system"], "Normal APK must trust only system CAs")
    with args.apk.open("rb") as raw, zipfile.ZipFile(args.apk) as archive:
        names = archive.namelist()
        require(not any("viptv_fixture_ca" in name for name in names), "Fixture CA must not ship")
        require(not any("native-fixture/" in name for name in names), "Owned fixture configuration must not ship")
        require("assets/native-torrent/scoped-qualification.json" not in names, "Native runtime must not require a scoped enablement receipt")
        dex = b"".join(archive.read(name) for name in names if re.fullmatch(r"classes[0-9]*\.dex", name))
        require(b"newNativeOwned" not in dex and b"OwnedNativeFixtureActivity" not in dex, "Owned fixture code must not ship")
        require(b"Lorg/viptv/app/NativeTorrentRuntime;" in dex and b"Lorg/viptv/app/NativeTorrentQualification;" not in dex, "Normal native capability must use runtime prerequisites")
        for abi in ABIS:
            for library in ["libviptv_core.so", "libjnidispatch.so", "libplayback_gateway_ffi.so"]:
                name = f"lib/{abi}/{library}"
                entry = archive.getinfo(name)
                require(entry.compress_type == zipfile.ZIP_STORED, "JNI libraries must be uncompressed")
                raw.seek(entry.header_offset)
                header = raw.read(30)
                filename, extra = struct.unpack_from("<HH", header, 26)
                offset = entry.header_offset + 30 + filename + extra
                require(offset % 16384 == 0, f"Native APK ZIP alignment is below 16 KiB: {abi}/{library}")
                if library == "libplayback_gateway_ffi.so":
                    path = f"ffi/generated/android/jniLibs/{abi}/{library}"
                    require(hashlib.sha256(archive.read(name)).hexdigest() == lock["files"][path], "Native APK bytes mismatch the artifact pin")
        for path, expected in lock["files"].items():
            if path in ["LICENSE", "PROVENANCE.md"] or path.startswith("THIRD_PARTY/"):
                require(hashlib.sha256(archive.read("assets/playback-gateway/" + path)).hexdigest() == expected, "Native notice missing or altered in APK")
    print("Verified real APK minSdk 24, three-ABI native/core/JNA contents, pinned checksums, notices, system-CA/HTTP-media policy and 16 KiB ZIP alignment; ABI/device loading is a separate check.")


if __name__ == "__main__":
    main()
