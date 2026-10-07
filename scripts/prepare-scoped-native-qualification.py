#!/usr/bin/env python3
"""Bind an explicit development qualification receipt to the actual normal inputs."""
import argparse
import hashlib
import json
import pathlib
import re
import subprocess

DECISION = "scoped_experimental_sticky_quarantine_v1"
MEASURED_TARGETS = (
    {"processAbi": "armeabi-v7a", "apiLevel": 30, "model": "AFTLAS01"},
    {"processAbi": "x86_64", "apiLevel": 36, "model": "sdk_google_atv64_x86_64"},
)


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def prepare(root, receipt_path, output):
    # A missing property deliberately emits no enabling asset.
    destination = output / "native-torrent" / "scoped-qualification.json"
    destination.unlink(missing_ok=True)
    if receipt_path is None:
        return
    receipt = json.loads(receipt_path.read_text())
    if set(receipt) != {"version", "decision", "sourceRevision", "normalApkSha256", "qualification", "platform", "cohort"}:
        raise ValueError("Invalid scoped qualification fields")
    if receipt["version"] != 1 or receipt["decision"] != DECISION:
        raise ValueError("Explicit experimental decision required")
    source_revision = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=root, text=True).strip()
    if receipt["sourceRevision"] != source_revision:
        raise ValueError("Qualification source revision differs from build")
    changed = subprocess.check_output(["git", "status", "--porcelain", "--untracked-files=normal", "--", "app", "src", "scripts", "CORE_REF", "TORRENT_REF", "DESIGN_REF"], cwd=root)
    if changed:
        raise ValueError("Qualification build inputs must be committed")
    qualification = receipt["qualification"]
    if set(qualification) != {"normalJni", "ownedMedia3", "evidenceSha256"} or qualification["normalJni"] != "pass" or qualification["ownedMedia3"] != "pass":
        raise ValueError("Actual normal JNI and owned Media3 passes required")
    platform = receipt["platform"]
    if set(platform) != {"processAbi", "apiLevel", "model"} or platform not in MEASURED_TARGETS:
        raise ValueError("Only an explicitly measured development TV cohort is admitted")
    abi = platform["processAbi"]
    cohort = receipt["cohort"]
    expected = {
        "coreRevision": (root / "CORE_REF").read_text().strip(),
        "torrentRevision": (root / "TORRENT_REF").read_text().strip(),
        "designRevision": (root / "DESIGN_REF").read_text().strip(),
        "gatewayLockSha256": sha(root / "vendor/playback-gateway/lock.json"),
        "coreLockSha256": sha(root / "vendor/core/lock.json"),
        "gatewayLibrarySha256": sha(root / f"vendor/playback-gateway/ffi/generated/android/jniLibs/{abi}/libplayback_gateway_ffi.so"),
        "coreLibrarySha256": sha(root / f"app/src/androidMain/jniLibs/{abi}/libviptv_core.so"),
    }
    if cohort != expected:
        raise ValueError("Normal artifact cohort differs from measured receipt")
    if not all(re.fullmatch(r"[a-f0-9]{64}", digest) for digest in (receipt["normalApkSha256"], qualification["evidenceSha256"])):
        raise ValueError("Sealed normal APK and evidence digests required")
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(json.dumps(receipt, sort_keys=True, separators=(",", ":")) + "\n")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--receipt", type=pathlib.Path)
    parser.add_argument("--output", type=pathlib.Path, required=True)
    args = parser.parse_args()
    try:
        prepare(pathlib.Path(__file__).resolve().parent.parent, args.receipt, args.output)
    except (ValueError, KeyError, OSError, subprocess.CalledProcessError) as error:
        raise SystemExit("Scoped native qualification refused: " + str(error))


if __name__ == "__main__":
    main()
