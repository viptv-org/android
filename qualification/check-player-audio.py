#!/usr/bin/env python3
"""Check real two-AAC Media3 selection and remote return on owned TV 5576.

Precondition: make-track-media.sh's four-minute MP4 is loaded in the actual app,
with no panel open. This qualifies direct native audio, not managed replacement.
"""
from pathlib import Path
import subprocess
import time
import xml.etree.ElementTree as ET

adb = [str(Path.home() / "Android/Sdk/platform-tools/adb"), "-s", "emulator-5576"]


def device(*values):
    return subprocess.check_output(adb + list(values), text=True, timeout=20)


def tree():
    for attempt in range(3):
        try:
            device("shell", "uiautomator", "dump", "/sdcard/viptv-audio.xml")
            break
        except subprocess.CalledProcessError:
            if attempt == 2:
                raise
            time.sleep(.3)
    parsed = ET.fromstring(device("exec-out", "cat", "/sdcard/viptv-audio.xml"))
    assert any(n.get("package") == "org.viptv.app" for n in parsed.iter("node")), "VIPTV must be foreground"
    return parsed


def labels(parsed):
    return [n.get("text") or n.get("content-desc") or "" for n in parsed.iter("node")]


def key(code):
    tree()
    device("shell", "input", "keyevent", "KEYCODE_" + code)
    time.sleep(.15)


def panel():
    parsed = tree()
    assert all(label in labels(parsed) for label in ("Audio", "en", "es")), "Expected the real two-AAC native inventory"
    return parsed


def current(parsed, language):
    assert any(language in labels(n) and " · Current" in labels(n) for n in parsed.iter("node")
               if n.get("clickable") == "true"), "Expected the selected native audio row"


key("MEDIA_PAUSE")
assert "4:00" in labels(tree()), "The actual four-minute native media must be loaded"
key("INFO")
key("DPAD_CENTER")
panel()
# Normalize the initial selection through the actual track panel.
key("DPAD_UP")
key("DPAD_CENTER")
key("DPAD_CENTER")
current(panel(), "en")
key("BACK")
key("DPAD_CENTER")  # No directional repair: Back must restore Audio.
current(panel(), "en")
key("DPAD_DOWN")
key("DPAD_CENTER")
key("DPAD_CENTER")  # Selection must also restore the invoking Audio control.
current(panel(), "es")
key("BACK")
key("MEDIA_PLAY")
print("PASS: actual Media3 alternate AAC selected; Back and selection returned to Audio; immediate OK reopened Audio with es Current")
