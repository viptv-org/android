#!/usr/bin/env python3
"""Bracket one operator-selected TV journey; raw captures stay in ignored artifacts."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import time
import xml.etree.ElementTree as ET


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--label", required=True, help="Name the actual starting screen and journey")
    parser.add_argument("--directory", type=Path, required=True)
    parser.add_argument("--source-checkout", type=Path, help="Checkout associated with the artifact being tested")
    parser.add_argument("--seconds", type=float, default=8)
    parser.add_argument("--keys", nargs="*", choices=["UP", "DOWN", "LEFT", "RIGHT", "BACK", "CENTER"], default=[])
    parser.add_argument("--interval", type=float, default=.35)
    parser.add_argument("--restart", action="store_true", help="Force-stop and start VIPTV, preserving app data; coordinate shared device use first")
    parser.add_argument("--wait-home", action="store_true", help="After restart, require visible Home controls and saved-row heading; coarse UI probe")
    args = parser.parse_args()
    if not re.fullmatch(r"[a-zA-Z0-9_-]+", args.label):
        parser.error("Use a simple label")
    if args.seconds <= 0 or args.interval <= 0:
        parser.error("Durations must be positive")
    if args.wait_home and not args.restart:
        parser.error("--wait-home requires --restart")
    root = Path(__file__).resolve().parents[1]
    directory = args.directory.resolve()
    # Logs can contain private screen/device facts; never write them into tracked docs.
    artifacts = root / "qualification/artifacts"
    if not directory.is_relative_to(artifacts):
        parser.error("Directory must be inside qualification/artifacts")
    directory.mkdir(parents=True, exist_ok=True)
    adb = os.environ.get("ANDROID_ADB_BIN") or shutil.which("adb")
    if not adb:
        parser.error("Set ANDROID_ADB_BIN or put adb on PATH")
    package = "org.viptv.app"

    def run(*values):
        return subprocess.check_output([adb, "-s", args.serial, *values], text=True, timeout=30)

    def require_foreground():
        window = run("shell", "dumpsys", "window")
        focused = [line for line in window.splitlines() if "mCurrentFocus=" in line or "mFocusedApp=" in line]
        if not any(package + "/" in line for line in focused):
            raise RuntimeError("VIPTV must be foreground before sampling; no input sent")

    require_foreground()
    installed = run("shell", "pm", "path", package).strip().splitlines()
    base = next(line.removeprefix("package:") for line in installed if line.endswith("/base.apk"))
    apk_hash = run("shell", "sha256sum", base).split()[0]
    if not re.fullmatch(r"[0-9a-f]{64}", apk_hash):
        raise RuntimeError("Could not verify installed APK")
    source = args.source_checkout.resolve() if args.source_checkout else root
    revision = subprocess.check_output(["git", "-C", str(source), "rev-parse", "HEAD"], text=True).strip()
    local_apk = source / "app/build/outputs/apk/debug/app-debug.apk"
    local_hash = hashlib.sha256(local_apk.read_bytes()).hexdigest() if local_apk.exists() else None
    run("shell", "dumpsys", "gfxinfo", package, "reset")
    launch = None
    home_visible_by_ms = None
    if args.restart:
        run("shell", "am", "force-stop", package)
        launch_started = time.monotonic()
        launch = run("shell", "am", "start", "-W", "-n", package + "/.MainActivity")
        (directory / (args.label + "-launch.txt")).write_text(launch)
        if args.wait_home:
            deadline = time.monotonic() + 30
            while time.monotonic() < deadline:
                run("shell", "rm", "-f", "/sdcard/viptv-perf-home.xml")
                status = run("shell", "uiautomator", "dump", "/sdcard/viptv-perf-home.xml")
                if "UI hierchary dumped" not in status and "UI hierarchy dumped" not in status:
                    time.sleep(.2)
                    continue
                tree = run("exec-out", "cat", "/sdcard/viptv-perf-home.xml")
                nodes = list(ET.fromstring(tree).iter("node"))
                texts = {node.get("text") for node in nodes}
                if "Continue watching" in texts and {"Play", "Resume"}.intersection(texts):
                    home_visible_by_ms = round((time.monotonic() - launch_started) * 1000)
                    (directory / (args.label + "-home-ready.xml")).write_text(tree)
                    break
            if home_visible_by_ms is None:
                raise RuntimeError("Home readiness probe timed out; no navigation sent")
    started = time.monotonic()
    for key in args.keys:
        require_foreground()
        run("shell", "input", "keyevent", "KEYCODE_" + ("DPAD_" + key if key != "BACK" else key))
        time.sleep(args.interval)
    time.sleep(args.seconds)
    raw = run("shell", "dumpsys", "gfxinfo", package, "framestats")
    (directory / (args.label + "-gfxinfo.txt")).write_text(raw)
    memory = run("shell", "dumpsys", "meminfo", package)
    (directory / (args.label + "-meminfo.txt")).write_text(memory)
    metrics = {}
    for key, pattern in {
        "frames": r"Total frames rendered: (\d+)",
        "janky_frames": r"Janky frames: (\d+)",
        "jank_percent": r"Janky frames: \d+ \(([\d.]+)%\)",
        "p50_ms": r"50th percentile: (\d+)ms",
        "p90_ms": r"90th percentile: (\d+)ms",
        "p95_ms": r"95th percentile: (\d+)ms",
        "p99_ms": r"99th percentile: (\d+)ms",
        "slow_ui_thread": r"Number Slow UI thread: (\d+)",
        "slow_draw_commands": r"Number Slow issue draw commands: (\d+)",
        "slow_bitmap_uploads": r"Number Slow bitmap uploads: (\d+)",
    }.items():
        match = re.search(pattern, raw)
        metrics[key] = float(match[1]) if match else None
    pss = re.search(r"TOTAL PSS:\s+(\d+)", memory)
    metrics["pss_kib"] = int(pss[1]) if pss else None
    if not metrics["frames"]:
        for key in ("jank_percent", "p50_ms", "p90_ms", "p95_ms", "p99_ms"):
            metrics[key] = None
    result = {"label": args.label, "utc": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
              "checkout_revision": revision, "installed_apk_sha256": apk_hash,
              "matches_local_debug_apk": apk_hash == local_hash,
              "elapsed_seconds": round(time.monotonic() - started, 2),
              "restart": args.restart, "keys": args.keys, "metrics": metrics,
              "home_visible_by_ms": home_visible_by_ms,
              "limits": "Debug/emulator diagnostic; checkout revision is not installed APK provenance. Launch timing is first display, not fully loaded Home. Home visible-by includes UIAutomation overhead and verifies saved-row controls, not every shelf or artwork. Zero frames means no rendering sample."}
    (directory / (args.label + ".json")).write_text(json.dumps(result, indent=2) + "\n")
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
