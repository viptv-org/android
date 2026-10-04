#!/usr/bin/env python3
"""Actual backend/gateway HLS audio replacement on owned silent TV emulator.

Requires the real-stack serve-only runner, native HTTPS ingress and privately
seeded fixture session. UI and HLS evidence are distinct; no login claim.
"""
import argparse
import json
from pathlib import Path
import re
import subprocess
import time
import urllib.parse
import urllib.request
import ssl
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--directory", type=Path, required=True)
args = parser.parse_args()
q = args.directory
adb = [str(Path.home() / "Android/Sdk/platform-tools/adb"), "-s", "emulator-5576"]


def device(*values, binary=False):
    try:
        return subprocess.check_output(adb + list(values), text=not binary, timeout=20, stderr=subprocess.PIPE)
    except subprocess.SubprocessError:
        raise RuntimeError("Owned native UI operation failed; private diagnostics omitted") from None


def labels(root):
    return [n.get("text") or n.get("content-desc") or "" for n in root.iter("node")]


def tree():
    device("shell", "uiautomator", "dump", "/sdcard/native-managed.xml")
    root = ET.fromstring(device("exec-out", "cat", "/sdcard/native-managed.xml"))
    assert any(n.get("package") == "org.viptv.app" for n in root.iter("node")), "Owned VIPTV app must be foreground"
    return root


def key(code):
    tree()
    device("shell", "input", "keyevent", "KEYCODE_" + code)
    time.sleep(.1)


def tap(label):
    root = tree()
    parents = {child: parent for parent in root.iter() for child in parent}
    for node in root.iter("node"):
        if label not in (node.get("text"), node.get("content-desc")):
            continue
        while node.get("clickable") != "true" and node in parents:
            node = parents[node]
        if node.get("clickable") == "true":
            left, top, right, bottom = map(int, re.findall(r"\d+", node.get("bounds")))
            device("shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2))
            return
    raise AssertionError("Expected fixture UI action unavailable")


def wait(check, timeout=60):
    until = time.monotonic() + timeout
    while time.monotonic() < until:
        if check():
            return
        time.sleep(.2)
    raise AssertionError("Actual native managed fixture state deadline")


def delivery():
    return json.loads((q / "native-last-delivery.json").read_text())


def journal():
    return json.loads((q / "native-requests.json").read_text())


def selected(root, language):
    return any(language in labels(n) and " · Current" in labels(n) for n in root.iter("node") if n.get("clickable") == "true")


def position():
    times = [value for value in labels(tree()) if re.fullmatch(r"\d+:\d\d", value)]
    assert len(times) >= 2, "Actual player must publish position and duration"
    minutes, seconds = map(int, times[0].split(":"))
    return minutes * 60 + seconds


def probe_audio(value):
    uri = urllib.parse.urlsplit(value["delivery"]["url"])
    local = urllib.parse.urlunsplit((uri.scheme, "127.0.0.1:9445", uri.path, uri.query, ""))
    context = ssl.create_default_context(cafile=str(Path(__file__).resolve().parent / "fixtures/tls/server.crt"))
    with urllib.request.urlopen(local, context=context, timeout=10) as response:
        playlist = response.read().decode()
    segment = next(line for line in playlist.splitlines() if line and not line.startswith("#"))
    segment_uri = urllib.parse.urljoin(local, segment)
    with urllib.request.urlopen(segment_uri, context=context, timeout=10) as response:
        segment_file = q / "native-inspected-segment.ts"
        segment_file.write_bytes(response.read())
    with (q / "native-ffprobe-error.log").open("wb") as log:
        result = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "stream=codec_type,codec_name:stream_tags=language", "-of", "json", str(segment_file)],
            stdout=subprocess.PIPE, stderr=log, timeout=20)
    assert result.returncode == 0, "Actual delivered HLS inspection failed; private diagnostics omitted"
    streams = json.loads(result.stdout)["streams"]
    return [(s.get("codec_name"), s.get("tags", {}).get("language")) for s in streams if s["codec_type"] == "audio"]


wait(lambda: "Home" in labels(tree()), 20)
tap("Details")
wait(lambda: "Choose source" in labels(tree()))
tap("Choose source")
wait(lambda: "Generated torrent addon" in labels(tree()))
tap("Generated torrent addon")
wait(lambda: "Exit player" in labels(tree()) and "Pause" in labels(tree()), 75)
key("MEDIA_PAUSE")
initial = delivery()
(q / "native-initial-delivery.json").write_text(json.dumps(initial))
(q / "native-initial-delivery.json").chmod(0o600)
assert initial["delivery"]["kind"] == "gateway" and initial["delivery"]["format"] == "hls"
before = probe_audio(initial)
assert before and all(codec == "aac" and language in {"eng", "en"} for codec, language in before), "Initial actual HLS must contain English AAC"
(q / "native-before.png").write_bytes(device("exec-out", "screencap", "-p", binary=True))
key("INFO")
key("DPAD_CENTER")
assert selected(tree(), "English"), "Actual managed menu must show initial English Current"
key("DPAD_DOWN")
key("DPAD_CENTER")
wait(lambda: any(r.get("audio_track") == 2 and r.get("status") in {200, 202} for r in journal() if r["kind"] == "playback create"))
wait(lambda: delivery()["id"] != initial["id"] and delivery()["status"] == "ready" and any(t.get("input_index") == 2 and t.get("selected") for t in delivery()["delivery"].get("audio_tracks", [])))
changed = delivery()
assert changed["delivery"]["selected_audio"]["input_index"] == 2 and changed["delivery"]["selected_audio"]["language"] == "spa", "Actual ready delivery must identify Spanish input two"
after = probe_audio(changed)
assert after and all(codec == "aac" and language == "und" for codec, language in after), "Actual replacement HLS must follow this runtime's neutral AAC output tag"
(q / "native-replacement-ready").write_text("input two replacement is ready")
# Resolve the real output observation before replacing it again with seek.
# Raw proc arguments remain private and never enter public output.
wait(lambda: (q / "native-audio-map.json").exists(), 90)
mapping = json.loads((q / "native-audio-map.json").read_text())
assert mapping["selector"] == 2 and mapping["matching_output_processes"] == 1 and mapping["ready_delivery_output_matched"] is True
key("DPAD_CENTER")  # No focus repair: selection must return to Audio.
assert selected(tree(), "Spanish")
key("BACK")
key("DPAD_CENTER")  # No directional repair: Back must return to Audio.
assert selected(tree(), "Spanish")
key("BACK")
key("MEDIA_FAST_FORWARD")
wait(lambda: any(r.get("position", 0) >= 50 and r.get("status") in {200, 202} for r in journal() if r["kind"] == "playback create"))
wait(lambda: delivery()["status"] == "ready")
key("MEDIA_PLAY")
if "Exit player" not in labels(tree()):
    key("INFO")  # Show timed controls; this does not change Audio focus.
wait(lambda: "Pause" in labels(tree()) and position() >= 50)
first_position = position()
time.sleep(2)
if "Exit player" not in labels(tree()):
    key("INFO")
wait(lambda: position() > first_position)
sought_position = position()
(q / "native-after-seek.png").write_bytes(device("exec-out", "screencap", "-p", binary=True))
if "Exit player" not in labels(tree()):
    key("INFO")
key("DPAD_RIGHT")
key("DPAD_RIGHT")
key("DPAD_CENTER")
wait(lambda: sum(r["kind"] == "playback release" and r.get("status") == 200 for r in journal()) >= 3)
result = {"initial_hls_audio": before, "replacement_hls_audio": after, "selected_input": 2, "selected_input_language": "spa",
    "actual_ffmpeg_selector": "0:" + str(mapping["selector"]), "output_job_match": mapping["ready_delivery_output_matched"], "seek_position_before": first_position,
    "seek_position_after": sought_position, "replacement_id_changed": changed["id"] != initial["id"],
    "creates": [{"position":r.get("position"), "audio_track":r.get("audio_track"), "status":r.get("status")} for r in journal() if r["kind"] == "playback create"],
    "releases": sum(r["kind"] == "playback release" and r.get("status") == 200 for r in journal()), "media_requests":sum(r["kind"] == "gateway media" for r in journal())}
(q / "native-result.json").write_text(json.dumps(result, indent=2))
print("PASS: actual backend/gateway native HLS input selection changed to Spanish; neutral AAC output, new lease, Audio return, managed seek and explicit release")
