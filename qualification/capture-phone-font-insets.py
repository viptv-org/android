#!/usr/bin/env python3
"""Capture a verified synthetic phone route and font/IME facts, without navigation.

Only the owned emulator5574 at the isolated HTTPS fixture origin is accepted.
Pixel inspection remains separate from these native hierarchy/state assertions.
"""
import argparse
import json
from pathlib import Path
import re
import ssl
import subprocess
import urllib.request
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--directory", type=Path, required=True)
parser.add_argument("--state", choices=["home", "title", "search-insets"], required=True)
parser.add_argument("--font-scale", choices=["1.0", "1.3"], required=True)
args = parser.parse_args()
adb = [str(Path.home() / "Android/Sdk/platform-tools/adb"), "-s", "emulator-5574"]


def device(*values, binary=False):
    try:
        return subprocess.check_output(adb + list(values), text=not binary, timeout=20, stderr=subprocess.PIPE)
    except subprocess.SubprocessError:
        raise RuntimeError("Owned phone capture failed; private diagnostics omitted") from None


assert device("shell", "getprop", "ro.kernel.qemu").strip() == "1"
preferences = ET.fromstring(device("exec-out", "run-as", "org.viptv.app", "cat", "shared_prefs/viptv.settings.xml"))
assert next(n.text for n in preferences if n.get("name") == "origin") == "https://10.0.2.2:9443"
assert device("shell", "settings", "get", "system", "font_scale").strip() == args.font_scale
assert "780x1688" in device("shell", "wm", "size") and "320" in device("shell", "wm", "density")
context = ssl.create_default_context(cafile=str(Path(__file__).resolve().parent / "fixtures/tls/server.crt"))
with urllib.request.urlopen("https://127.0.0.1:9443/__requests", context=context, timeout=10) as response:
    assert response.status == 200
for attempt in range(3):
    try:
        device("shell", "uiautomator", "dump", "/sdcard/phone-font.xml")
        break
    except RuntimeError:
        if attempt == 2:
            raise
raw = device("exec-out", "cat", "/sdcard/phone-font.xml")
root = ET.fromstring(raw)
assert any(n.get("package") == "org.viptv.app" for n in root.iter("node"))
labels = [n.get("text") or n.get("content-desc") for n in root.iter("node")]
required = {"home": ["Home", "Search", "Play"], "title": ["Choose source", "Play", "More info"],
            "search-insets": ["Search", "Search movies and series"]}[args.state]
assert all(label in labels for label in required), "Expected phone route must be loaded before capture"
ime_visible = "mInputShown=true" in device("shell", "dumpsys", "input_method")
field_bounds = None
if args.state == "search-insets":
    assert ime_visible, "Actual OS IME must be shown for the inset capture"
    field = next(n for n in root.iter("node") if n.get("class") == "android.widget.EditText" and n.get("focused") == "true")
    field_bounds = list(map(int, re.findall(r"\d+", field.get("bounds"))))
    assert 0 <= field_bounds[0] < field_bounds[2] <= 780 and 0 <= field_bounds[1] < field_bounds[3] < 844
q = args.directory
q.mkdir(parents=True, exist_ok=True)
q.chmod(0o700)
name = f"phone-{args.state}-font-{args.font_scale}"
(q / (name + ".xml")).write_text(raw)
(q / (name + ".png")).write_bytes(device("exec-out", "screencap", "-p", binary=True))
(q / (name + ".json")).write_text(json.dumps({"font_scale": float(args.font_scale), "state": args.state,
    "viewport_dp": [390, 844], "ime_shown": ime_visible, "focused_field_bounds_px": field_bounds}))
print("PASS: owned synthetic phone route/font/IME capture; pixel inspection remains separate")
