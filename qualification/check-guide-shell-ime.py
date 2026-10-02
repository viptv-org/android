#!/usr/bin/env python3
"""Actual TV ApplicationShell/LatinIME Guide navigation on owned emulator5576.

Synthetic HTTPS fixture only. This uses injected D-pad events, not Compose text
semantics or physical input. Captures and safe numeric results stay private.
"""
import argparse
import json
from pathlib import Path
import ssl
import subprocess
import time
import urllib.request
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--directory", type=Path, required=True)
args = parser.parse_args()
q = args.directory
q.mkdir(parents=True, exist_ok=True)
q.chmod(0o700)
adb = [str(Path.home() / "Android/Sdk/platform-tools/adb"), "-s", "emulator-5576"]
context = ssl.create_default_context(cafile=str(Path(__file__).resolve().parent / "fixtures/tls/server.crt"))


def device(*values, binary=False):
    try:
        return subprocess.check_output(adb + list(values), text=not binary, timeout=20, stderr=subprocess.PIPE)
    except subprocess.SubprocessError:
        raise RuntimeError("Owned TV operation failed; private diagnostics omitted") from None


def tree():
    for attempt in range(3):
        try:
            device("shell", "uiautomator", "dump", "/sdcard/guide-shell.xml")
            break
        except RuntimeError:
            if attempt == 2:
                raise
            time.sleep(.3)
    root = ET.fromstring(device("exec-out", "cat", "/sdcard/guide-shell.xml"))
    assert any(n.get("package") == "org.viptv.app" for n in root.iter("node")), "Owned VIPTV app must be foreground"
    return root


def labels(root):
    return [n.get("text") or n.get("content-desc") or "" for n in root.iter("node")]


def focused():
    return [value for n in tree().iter("node") if n.get("focused") == "true" for value in labels(n)]


def key(*codes):
    tree()
    for code in codes:
        device("shell", "input", "keyevent", "KEYCODE_" + code)
        time.sleep(.12)


def wait(check, timeout=30):
    until = time.monotonic() + timeout
    while time.monotonic() < until:
        if check():
            return
        time.sleep(.2)
    raise AssertionError("Native shell Guide state deadline")


def reach(label, direction, limit=20):
    for _ in range(limit):
        if label in focused():
            return
        key(direction)
    raise AssertionError("Expected native control was not remotely reachable")


def captures(name):
    (q / (name + ".png")).write_bytes(device("exec-out", "screencap", "-p", binary=True))


def admissions():
    with urllib.request.urlopen("https://127.0.0.1:9444/__requests", context=context, timeout=10) as response:
        rows = json.load(response)
    return sum(row["path"] == "/api/v2/playback" and row["method"] == "POST" for row in rows)


assert device("shell", "getprop", "ro.kernel.qemu").strip() == "1"
admissions()  # Verified synthetic TLS/journal preflight before app actions.
# Restart only the QA app, preserving its synthetic profile. No touch events
# contaminate this remote/IME flow; the fixture's normal profile gate is used.
device("shell", "am", "force-stop", "org.viptv.app")
device("shell", "am", "start", "-n", "org.viptv.app/.MainActivity", "--es", "preview-origin", "https://10.0.2.2:9444")
wait(lambda: "Home" in labels(tree()) or "Who’s watching?" in labels(tree()))
if "Who’s watching?" in labels(tree()):
    key("DPAD_CENTER")
wait(lambda: "Resume" in focused())
before = admissions()
key("DPAD_LEFT", "DPAD_DOWN", "DPAD_DOWN", "DPAD_CENTER")
wait(lambda: "ABC News Live" in focused())
key("DPAD_UP", "DPAD_RIGHT", "DPAD_RIGHT", "DPAD_RIGHT")
assert "News" in focused()
key("DPAD_CENTER")
wait(lambda: "CNN" in labels(tree()))
print("News filter reached", flush=True)
if "ABC News Live" in focused():
    key("DPAD_UP")
reach("Search channels", "DPAD_RIGHT")
key("DPAD_CENTER")
wait(lambda: "Search live TV" in labels(tree()))
print("Native Guide Search reached", flush=True)
ime = device("shell", "dumpsys", "input_method")
assert "mInputShown=true" in ime and "com.google.android.inputmethod.latin" in ime
captures("tv-native-ime-empty")
# This API36 TV LatinIME starts on q. All letters and its Check/Done action
# are activated through the real OS keyboard using ordinary D-pad keys.
key("DPAD_DOWN", "DPAD_DOWN", "DPAD_RIGHT", "DPAD_RIGHT", "DPAD_RIGHT", "DPAD_CENTER")
assert "c" in labels(tree())
key("DPAD_RIGHT", "DPAD_RIGHT", "DPAD_RIGHT", "DPAD_CENTER")
assert "cn" in labels(tree())
key("DPAD_LEFT", "DPAD_CENTER")
assert "cnb" in labels(tree())
key("DPAD_LEFT", "DPAD_LEFT", "DPAD_CENTER")
assert "cnbc" in labels(tree())
key(*(["DPAD_RIGHT"] * 6), "DPAD_DOWN")
captures("tv-native-ime-done-focused")
key("DPAD_CENTER")
wait(lambda: "Halftime Report" in labels(tree()) and "Search live TV" not in labels(tree()))
assert "CNBC" in labels(tree()) and "CNN" not in labels(tree())
assert admissions() == before
if "Search channels" in focused():
    reach("News", "DPAD_LEFT")
    key("DPAD_DOWN")
reach("Halftime Report", "DPAD_RIGHT", 4)
cell = next(n for n in tree().iter("node") if n.get("focused") == "true" and "Halftime Report" in labels(n))
bounds = cell.get("bounds")
key("DPAD_CENTER")
wait(lambda: "Watch live" in labels(tree()))
assert admissions() == before
key("BACK")
wait(lambda: "Watch live" not in labels(tree()))
restored = next(n for n in tree().iter("node") if n.get("focused") == "true" and "Halftime Report" in labels(n))
assert restored.get("bounds") == bounds and "CNBC" in labels(tree())
assert admissions() == before
captures("tv-native-future-back")
(q / "guide-shell-result.json").write_text(json.dumps({"native_ime": True, "query": "cnbc", "programme": "Halftime Report", "exact_focused_bounds": bounds, "playback_admission_delta": admissions() - before}))
print("PASS: actual shell rail, News filter, OS LatinIME D-pad query/Done, future Details/native Back exact focus, no autoplay")
