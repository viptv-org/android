#!/usr/bin/env python3
"""Public Android UI/remote operations on dedicated VIPTV test emulators only."""
import argparse
import os
import shutil
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser()
parser.add_argument("--serial", choices=["emulator-5570", "emulator-5572", "emulator-5574", "emulator-5576"], default="emulator-5570")
parser.add_argument("action", choices=["snapshot", "tap", "key", "swipe", "texts", "wait"])
parser.add_argument("value", nargs="*", default=[])
args = parser.parse_args()
root = Path(__file__).resolve().parent
adb_path = os.environ.get("ADB") or shutil.which("adb") or str(Path(os.environ.get("ANDROID_HOME", str(Path.home() / "Android/Sdk"))) / "platform-tools/adb")
adb = [adb_path, "-s", args.serial]
def run(*values, binary=False):
    return subprocess.check_output(adb + list(values), text=not binary)
def tree():
    for attempt in range(3):
        try:
            output = run("shell", "uiautomator", "dump", "/sdcard/viptv-qa.xml")
            if "UI hierchary dumped" in output or "UI hierarchy dumped" in output:
                break
        except subprocess.CalledProcessError:
            pass
        time.sleep(.3)
    else:
        raise RuntimeError("Android could not capture a fresh UI tree; no input was sent")
    raw = run("exec-out", "cat", "/sdcard/viptv-qa.xml")
    parsed = ET.fromstring(raw)
    if not any(node.get("package") == "org.viptv.app" for node in parsed.iter("node")):
        raise RuntimeError("VIPTV is not the foreground app; no input was sent")
    return parsed, raw
def bounds(node):
    numbers = list(map(int, re.findall(r"\d+", node.get("bounds", ""))))
    return numbers
if args.action == "key":
    tree()
    for _ in range(int(args.value[1]) if len(args.value) > 1 else 1):
        run("shell", "input", "keyevent", args.value[0])
        time.sleep(.12)
elif args.action == "swipe":
    tree()
    run("shell", "input", "swipe", *args.value)
elif args.action == "wait":
    deadline = time.monotonic() + 15
    while time.monotonic() < deadline:
        parsed, _ = tree()
        if any(args.value[0] in (n.get("text", "") + n.get("content-desc", "")) for n in parsed.iter("node")):
            break
        time.sleep(.2)
    else:
        raise RuntimeError("Expected UI text did not appear")
else:
    parsed, raw = tree()
    if args.action == "tap":
        label = args.value[0]
        parents = {child: parent for parent in parsed.iter() for child in parent}
        found = [node for node in parsed.iter("node") if node.get("text") == label or node.get("content-desc") == label]
        if not found:
            raise RuntimeError("No matching visible control: " + label)
        node = found[0]
        while node.get("clickable") != "true" and node in parents:
            node = parents[node]
        if node.get("clickable") != "true":
            raise RuntimeError("The matching label has no clickable ancestor")
        left, top, right, bottom = bounds(node)
        run("shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2))
    elif args.action == "snapshot":
        name = args.value[0]
        if not re.fullmatch(r"[A-Za-z0-9_-]+", name):
            raise RuntimeError("Use a simple capture name")
        folder = root / "artifacts" / args.serial
        folder.mkdir(parents=True, exist_ok=True)
        (folder / (name + ".xml")).write_text(raw)
        (folder / (name + ".png")).write_bytes(run("exec-out", "screencap", "-p", binary=True))
        print(folder / (name + ".png"))
    else:
        for node in parsed.iter("node"):
            label = node.get("text") or node.get("content-desc")
            if label:
                print(label, node.get("bounds"), "focused" if node.get("focused") == "true" else "")
