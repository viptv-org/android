#!/usr/bin/env python3
"""Provision only the owned emulator's private synthetic integration session.

This is setup for actual backend/gateway media qualification, not login/pairing
UI acceptance. Config and credential backups must remain private and ignored.
"""
import argparse
import json
from pathlib import Path
import subprocess
import time
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--serial", choices=["emulator-5576"], required=True)
parser.add_argument("--config", type=Path, required=True)
args = parser.parse_args()
config = json.loads(args.config.read_text())
session = config["session"]
assert set(session) == {"sessionId", "accountId", "profileId", "accessToken", "refreshToken", "expiresIn"}, "Fixture session shape required"
assert config["origin"] == "https://10.0.2.2:9445", "Only the isolated native ingress origin is supported"
adb = [str(Path.home() / "Android/Sdk/platform-tools/adb"), "-s", args.serial]


def device(*values, data=None):
    try:
        return subprocess.check_output(adb + list(values), input=data, timeout=20, stderr=subprocess.PIPE)
    except subprocess.SubprocessError:
        raise RuntimeError("Owned native fixture provisioning failed; credentials are omitted") from None


assert device("shell", "getprop", "ro.kernel.qemu").strip() == b"1", "An emulator is required"
device("shell", "am", "force-stop", "org.viptv.app")
device("shell", "run-as org.viptv.app sh -c 'mkdir -p shared_prefs; test -f shared_prefs/viptv.auth.xml || echo \"<map />\" > shared_prefs/viptv.auth.xml'")
raw = device("exec-out", "run-as", "org.viptv.app", "cat", "shared_prefs/viptv.auth.xml")
backup = args.config.parent / "native-auth-before.xml"
if not backup.exists():
    backup.write_bytes(raw)
    backup.chmod(0o600)
# Changing the debug origin legitimately clears the old account. Complete that
# transition before provisioning the new fixture's private session.
device("shell", "am", "start", "-n", "org.viptv.app/.MainActivity", "--es", "preview-origin", config["origin"])
time.sleep(1)
device("shell", "am", "force-stop", "org.viptv.app")
raw = device("exec-out", "run-as", "org.viptv.app", "cat", "shared_prefs/viptv.auth.xml")
tree = ET.fromstring(raw)
for child in list(tree):
    if child.get("name") in {"core.session", "access", "refresh"}:
        tree.remove(child)
for key, value in {"core.session": json.dumps(session, separators=(",", ":")), "access": session["accessToken"], "refresh": session["refreshToken"]}.items():
    ET.SubElement(tree, "string", name=key).text = value
output = ET.tostring(tree, encoding="utf-8", xml_declaration=True)
device("shell", "run-as org.viptv.app sh -c 'cat > shared_prefs/viptv.auth.xml'", data=output)
device("shell", "am", "start", "-n", "org.viptv.app/.MainActivity", "--es", "preview-origin", config["origin"])
print("Synthetic session provisioned in owned app-private storage; real login/pairing UI is not claimed")
