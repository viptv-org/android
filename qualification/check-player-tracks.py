#!/usr/bin/env python3
"""Exercise actual Media3 track selection and remote return on dedicated TV 5576.

Precondition: native player is showing make-track-media.sh's four-minute MP4,
with no panel open. No test controller or replacement player is installed.
"""
from pathlib import Path
import subprocess
import time
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parent
adb = [str(Path.home() / "Android/Sdk/platform-tools/adb"), "-s", "emulator-5576"]
captures = root / "artifacts" / "emulator-5576"
captures.mkdir(parents=True, exist_ok=True)


def device(*values, binary=False):
    return subprocess.check_output(adb + list(values), text=not binary, timeout=20)


def tree():
    for attempt in range(3):
        try:
            device("shell", "uiautomator", "dump", "/sdcard/viptv-tracks.xml")
            break
        except subprocess.CalledProcessError:
            if attempt == 2:
                raise
            time.sleep(.3)
    raw = device("exec-out", "cat", "/sdcard/viptv-tracks.xml")
    parsed = ET.fromstring(raw)
    assert any(n.get("package") == "org.viptv.app" for n in parsed.iter("node")), "VIPTV must be foreground"
    return parsed, raw


def labels(parsed):
    return [n.get("text") or n.get("content-desc") or "" for n in parsed.iter("node")]


def key(code):
    tree()  # Refuse input to an unrelated foreground app.
    device("shell", "input", "keyevent", "KEYCODE_" + code)
    time.sleep(.15)


def snapshot(name):
    parsed, raw = tree()
    (captures / (name + ".xml")).write_text(raw)
    (captures / (name + ".png")).write_bytes(device("exec-out", "screencap", "-p", binary=True))
    return parsed


def panel():
    parsed, _ = tree()
    text = labels(parsed)
    assert "Subtitles" in text and "Off" in text and "en" in text and "es" in text, "Expected native multi-track subtitle panel"
    return parsed


def current(parsed, expected):
    # The label and Current marker belong to the same actual track-row node.
    assert any(expected in labels(n) and " · Current" in labels(n) for n in parsed.iter("node")
               if n.get("clickable") == "true"), "Expected selected native track marker"


key("MEDIA_PAUSE")
parsed, _ = tree()
assert "4:00" in labels(parsed), "Long native media must be loaded; short HLS fixture cannot qualify focus"
key("INFO")
key("DPAD_RIGHT")
key("DPAD_CENTER")
panel()
# Reset the subtitle state through the real UI so reruns are independent.
for _ in range(3):
    key("DPAD_UP")
key("DPAD_CENTER")
key("DPAD_CENTER")
current(panel(), "Off")
key("BACK")
snapshot("tracks-back-return")
key("DPAD_CENTER")  # No re-navigation: proves Back restored the invoking control.
current(panel(), "Off")
key("DPAD_DOWN")
key("DPAD_CENTER")
snapshot("tracks-selection-return")
key("DPAD_CENTER")  # Must reopen Subtitles, rather than activate Play/Pause.
current(panel(), "en")
snapshot("tracks-english-current")
key("BACK")
key("MEDIA_PLAY")
time.sleep(1)
key("INFO")
key("DPAD_RIGHT")
snapshot("tracks-english-cue")
# SubtitleLayer intentionally hides cues from accessibility. Inspect the saved
# native screenshot for the cue instead of treating its absent XML as a failure.
print("PASS: actual multi-track Subtitles Back/selection return and Current marker")
print("Inspect private tracks-english-cue.png for the rendered English cue and highlighted Subtitles control")
