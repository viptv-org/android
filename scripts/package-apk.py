#!/usr/bin/env python3
"""Package the universal phone/TV APK with checksums, provenance and, for release tags, update.json.

Usage: package-apk.py SOURCE_APK OUTPUT_DIR
Environment: BUILD_SHA (required); VIPTV_VERSION, GITHUB_REPOSITORY and AAPT2 for release tags.
"""
from pathlib import Path
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import zipfile

root = Path(__file__).resolve().parent.parent


def version_code(version):
    match = re.fullmatch(r"(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)", version)
    if not match:
        raise SystemExit(f"Release version must be MAJOR.MINOR.PATCH: {version!r}")
    major, minor, patch = map(int, match.groups())
    if major > 2000 or minor >= 1000 or patch >= 1000:
        raise SystemExit(f"Release version out of range: {version}")
    return major * 1_000_000 + minor * 1_000 + patch


def badging(aapt2, apk):
    output = subprocess.run([aapt2, "dump", "badging", str(apk)], check=True, capture_output=True, text=True).stdout
    package = re.search(r"^package: .*?versionCode='(\d+)' versionName='([^']*)'", output, re.MULTILINE)
    sdk = re.search(r"^minSdkVersion:'(\d+)'", output, re.MULTILINE)
    if not package or not sdk:
        raise SystemExit("aapt2 badging is missing version or minSdk")
    return int(package.group(1)), package.group(2), int(sdk.group(1))


def main(source, output):
    source, output = Path(source), Path(output)
    revision = os.environ["BUILD_SHA"]
    version = os.environ.get("VIPTV_VERSION") or None
    with zipfile.ZipFile(source) as archive:
        names = archive.namelist()
        assert not any("viptv_fixture_ca" in n for n in names), "Fixture trust must not ship"
        for abi in ["arm64-v8a", "armeabi-v7a", "x86_64"]:
            assert f"lib/{abi}/libviptv_core.so" in names, f"Missing native core: {abi}"
    output.mkdir(parents=True, exist_ok=True)
    name = f"viptv-android-{version}.apk" if version else f"viptv-android-phone-tv-development-{revision[:8]}.apk"
    apk = output / name
    shutil.copy2(source, apk)
    digest = hashlib.sha256(apk.read_bytes()).hexdigest()
    (output / "SHA256SUMS").write_text(f"{digest}  {apk.name}\n")
    build = {
        "revision": revision,
        "design": (root / "DESIGN_REF").read_text().strip(),
        "core": (root / "CORE_REF").read_text().strip(),
        "torrent_runtime": (root / "TORRENT_RUNTIME_REF").read_text().strip(),
        "torrent_comparison_packaged": False,
        "native_torrent_qualified": False,
        "signing": "stable development key",
        "variant": "performance",
        "debuggable": False,
        "optimized": True,
        "platforms": ["phone", "android-tv"],
    }
    if version:
        code = version_code(version)
        actual_code, actual_name, min_sdk = badging(os.environ["AAPT2"], apk)
        if (actual_code, actual_name) != (code, version):
            raise SystemExit(f"APK is {actual_name} ({actual_code}), expected {version} ({code})")
        build.update(version=version, versionCode=code)
        repository = os.environ["GITHUB_REPOSITORY"]
        tag = f"v{version}"
        (output / "update.json").write_text(json.dumps({
            "schema": 1,
            "versionCode": code,
            "versionName": version,
            "minSdk": min_sdk,
            "revision": revision,
            "releaseUrl": f"https://github.com/{repository}/releases/tag/{tag}",
            "apk": {
                "name": apk.name,
                "url": f"https://github.com/{repository}/releases/download/{tag}/{apk.name}",
                "size": apk.stat().st_size,
                "sha256": digest,
            },
        }, indent=2) + "\n")
    (output / "build.json").write_text(json.dumps(build))


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit(__doc__)
    main(*sys.argv[1:])
