#!/usr/bin/env python3
"""Bounded physical qualification on the one privately configured Fire TV."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import shlex
import subprocess
import time

ROOT = Path(__file__).resolve().parents[2]
NORMAL = "org.viptv.app"
FIXTURE = NORMAL + ".nativefixture"
CASES = (
    "normal_install", "normal_jni_load", "runtime_loopback_policy",
    "fixture_instrumentation", "manual_observation_window", "exact_file_decoded_video", "audible_audio",
    "alternate_audio_subtitle_cues", "seek_missing_pieces", "physical_remote_focus",
    "pause_resume", "startup_cancel", "replacement", "expiry_revocation",
    "background_resume", "profile_signout_cleanup", "sustained_owned_resources",
    "final_normal_signed_in_origin_unchanged", "final_playback_stopped",
)
HUMAN_CASES = {"audible_audio", "physical_remote_focus", "final_normal_signed_in_origin_unchanged"}
OUTSIDE_SCOPE = ("arm64_jni", "x86_64_jni", "public_peers", "blocked_os_io", "hdr", "drm", "pip")
KEYS = {"KEYCODE_DPAD_UP", "KEYCODE_DPAD_DOWN", "KEYCODE_DPAD_LEFT", "KEYCODE_DPAD_RIGHT",
        "KEYCODE_DPAD_CENTER", "KEYCODE_BACK", "KEYCODE_MEDIA_PLAY_PAUSE"}
RESULTS = ("owned-native-evidence.json", "owned-expiry-evidence.json",
           "owned-ordinary-evidence.json", "owned-controller-evidence.json", "owned-manual-evidence.json")


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def timestamp():
    return datetime.now(timezone.utc).isoformat()


def private_directory(path, root=ROOT):
    path = Path(os.path.abspath(path))
    allowed = root / ".scratch" / "native-torrent"
    require(path.is_relative_to(allowed) and path != allowed, "Use a dedicated ignored native-torrent evidence directory")
    for parent in [path, *path.parents]:
        if parent == root.parent:
            break
        require(not parent.is_symlink(), "Private qualification paths must not contain symlinks")
    path.mkdir(parents=True, exist_ok=True, mode=0o700)
    os.chmod(path, 0o700)
    return path


def write_private(path, data):
    require(not any(parent.is_symlink() for parent in [path, *path.parents]), "Refuse a symlinked evidence path")
    temp = path.with_name(path.name + ".pending")
    for candidate in [path, temp]:
        require(not candidate.is_symlink(), "Refuse a symlinked evidence file")
        require(not candidate.exists() or candidate.stat().st_nlink == 1, "Refuse hard-linked evidence")
    fd = os.open(temp, os.O_WRONLY | os.O_CREAT | os.O_TRUNC | getattr(os, "O_NOFOLLOW", 0), 0o600)
    with os.fdopen(fd, "wb") as out:
        out.write(data.encode() if isinstance(data, str) else data)
    temp.replace(path)
    os.chmod(path, 0o600)


def configured_values(root=ROOT.parent):
    values = {}
    for line in (root / ".env").read_text().splitlines():
        if "=" not in line or line.lstrip().startswith("#"):
            continue
        key, value = line.split("=", 1)
        if key in {"ANDROID_ADB_BIN", "VIPTV_TAILSCALE_TV_ADB_SERIAL", "ANDROID_HOME", "ANDROID_SDK_ROOT"}:
            parts = shlex.split(value)
            require(len(parts) == 1, "Private tool/device value must be one literal value")
            values[key] = parts[0]
    serial = values.get("VIPTV_TAILSCALE_TV_ADB_SERIAL", "")
    require(serial and not serial.startswith("emulator-") and not re.search(r"\s", serial), "Only the configured physical TV is allowed")
    require(values.get("ANDROID_ADB_BIN"), "Use the configured ADB executable")
    return values


def checksum(path, expected):
    require(re.fullmatch(r"[0-9a-f]{64}", expected or ""), "Supply the recorded SHA-256")
    require(path.is_file() and not path.is_symlink(), "Use a regular sealed APK")
    actual = hashlib.sha256(path.read_bytes()).hexdigest()
    require(actual == expected, "APK differs from its recorded hash; no device mutation allowed")
    return actual


def instrumentation_passed(output, count=None, allowed_skips=()):
    completed = 0
    current_test = None
    for line in output.splitlines():
        test = re.match(r"INSTRUMENTATION_STATUS: test=(.+)$", line)
        if test:
            current_test = test.group(1)
        code = re.match(r"INSTRUMENTATION_STATUS_CODE:\s*(-?\d+)$", line)
        if code:
            value = int(code.group(1))
            if value == 0:
                completed += 1
            elif value < 0 and not (value in {-3, -4} and current_test in allowed_skips):
                return False
    match = re.search(r"OK\s*\((\d+) tests?\)", output)
    return bool(match and completed > 0 and int(match.group(1)) > 0 and
                (count is None or (int(match.group(1)) == count and completed == count)) and
                "INSTRUMENTATION_CODE: -1" in output and
                not re.search(r"FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed|shortMsg=", output))


class Qualification:
    def __init__(self, directory, values=None):
        self.directory = private_directory(directory)
        self.values = values if values is not None else configured_values()
        self.adb = [self.values["ANDROID_ADB_BIN"], "-s", self.values["VIPTV_TAILSCALE_TV_ADB_SERIAL"]]

    def command(self, label, args, timeout=30):
        require(re.fullmatch(r"[a-z0-9_-]+", label), "Use a safe log label")
        try:
            result = subprocess.run([*self.adb, *args], capture_output=True, timeout=timeout)
        except subprocess.TimeoutExpired:
            write_private(self.directory / (label + ".log"), "Configured device command timed out.\n")
            raise RuntimeError("Configured device unavailable; no connection or device configuration is changed") from None
        write_private(self.directory / (label + ".log"), result.stdout + result.stderr)
        require(result.returncode == 0, "Configured device command failed; details remain in private evidence")
        return result.stdout

    def record(self, case, verdict, basis, evidence=None, observer=None):
        require(case in CASES and verdict in {"PASS", "FAIL", "NOT-RUN"}, "Unknown qualification case or verdict")
        if verdict == "PASS":
            require(basis in {"instrumentation", "measured", "human"}, "Passing observations require an explicit evidence basis")
            require(case not in HUMAN_CASES or (basis == "human" and observer == "human"), "Physical sound/remote/final session require actual human confirmation")
            require(evidence, "Passing observations require a private evidence file")
        digest = None
        if evidence:
            file = self.directory / evidence
            require(file.is_relative_to(self.directory) and ".." not in Path(evidence).parts and
                    not any(parent.is_symlink() for parent in [file, *file.parents]), "Evidence must be inside the private run directory")
            require(file.is_file(), "Evidence file is missing")
            digest = hashlib.sha256(file.read_bytes()).hexdigest()
        cases = self.directory / "cases"
        cases.mkdir(exist_ok=True, mode=0o700)
        write_private(cases / (case + ".json"), json.dumps({"case": case, "verdict": verdict,
                      "basis": basis, "observer": observer, "evidence": evidence,
                      "evidence_sha256": digest, "recorded_at": timestamp()}, indent=2) + "\n")

    def device(self):
        require(self.command("device-state", ["get-state"]).strip() == b"device", "Configured TV is unavailable")
        facts = {}
        for name, key in [("model", "ro.product.model"), ("sdk", "ro.build.version.sdk"), ("abi", "ro.product.cpu.abi")]:
            facts[name] = self.command("device-" + name, ["shell", "getprop", key]).decode().strip()
        require(facts == {"model": "AFTLAS01", "sdk": "30", "abi": "armeabi-v7a"}, "Device differs from the authorized API30 ARMv7 Fire TV")
        write_private(self.directory / "device.json", json.dumps(facts, indent=2) + "\n")
        return facts

    def install(self, apk, expected, package):
        checksum(apk, expected)
        self.device()
        sdk = self.values.get("ANDROID_HOME") or self.values.get("ANDROID_SDK_ROOT")
        tools = sorted((Path(sdk) / "build-tools").glob("*/aapt2")) if sdk else []
        require(tools, "Configured aapt2 is required to verify APK identity before installation")
        badging = subprocess.run([str(tools[-1]), "dump", "badging", str(apk)], capture_output=True, text=True)
        require(badging.returncode == 0 and re.search(r"^package: name='" + re.escape(package) + "'", badging.stdout, re.MULTILINE), "APK package differs from the selected normal/isolated target")
        output = self.command("install-" + package.replace(".", "-"), ["install", "-r", str(apk)], 180)
        require(b"Success" in output, "Same-signing update was refused; app data must remain intact")
        write_private(self.directory / ("apk-" + package + ".json"), json.dumps({"package": package, "sha256": expected,
                      "bytes": apk.stat().st_size, "installed_at": timestamp()}, indent=2) + "\n")
        if package == NORMAL:
            self.record("normal_install", "PASS", "measured", "install-" + package.replace(".", "-") + ".log")

    def probe(self):
        self.device()
        output = self.command("normal-jni-instrumentation", ["shell", "am", "instrument", "-w", "-e", "class",
                              "org.viptv.app.NativeTorrentArtifactLoadTest", "-e", "nativeTorrentExpectedAbi", "armeabi-v7a",
                              NORMAL + ".test/androidx.test.runner.AndroidJUnitRunner"], 120).decode(errors="replace")
        passed = instrumentation_passed(output, 2)
        for case in ["normal_jni_load", "runtime_loopback_policy"]:
            self.record(case, "PASS" if passed else "FAIL", "instrumentation", "normal-jni-instrumentation.log")
        require(passed, "Actual normal JNI/policy tests did not pass; retain failure evidence")

    def fixture(self, selector, hold_seconds=0):
        require(selector in {"org.viptv.app.OwnedNativePipelineTest", "org.viptv.app.OwnedNativePipelineTest#manualOwnedNativeObservation"}, "Use only the full owned pipeline class or dedicated manual observation")
        require(hold_seconds == 0 or 180 <= hold_seconds <= 300, "Observation interval must be bounded to 180–300 seconds")
        require(bool(hold_seconds) == selector.endswith("#manualOwnedNativeObservation"), "Manual observation requires its dedicated selector and bounded interval")
        execution = self.directory / "fixture-execution.json"
        require(not execution.exists(), "Use a fresh finite backend/configuration/APK and new run directory for each fixture selector")
        self.device()
        existing = self.command("reverse-before", ["reverse", "--list"]).decode().splitlines()
        matches_by_port = {}
        for port in [19445, 19446]:
            local = "tcp:" + str(port)
            matches = [line.split() for line in existing if local in line.split()]
            require(not matches or all(fields[-2:] == [local, local] for fields in matches), "Fixture port conflicts with an existing reverse; no mapping replaced")
            matches_by_port[port] = matches
        saved = self.directory / "created-reverses.json"
        prior = json.loads(saved.read_text()) if saved.exists() else []
        require(all(port in [19445, 19446] for port in prior), "Unexpected prior owned reverse entry")
        created = []
        for port in [19445, 19446]:
            local = "tcp:" + str(port)
            if not matches_by_port[port]:
                self.command("reverse-" + str(port), ["reverse", local, local])
                created.append(port)
                write_private(saved, json.dumps(sorted(set(prior + created))))
        command = ["shell", "am", "instrument", "-w", "-e", "class", selector]
        if hold_seconds:
            command += ["-e", "ownedNativeManualSeconds", str(hold_seconds)]
        command += [FIXTURE + ".test/androidx.test.runner.AndroidJUnitRunner"]
        write_private(execution, json.dumps({"selector": selector, "hold_seconds": hold_seconds, "started_at": timestamp()}, indent=2) + "\n")
        label = "owned-manual-instrumentation" if hold_seconds else "owned-instrumentation"
        output = self.command(label, command, max(300, hold_seconds + 120)).decode(errors="replace")
        skips = () if hold_seconds else ("manualOwnedNativeObservation",)
        passed = instrumentation_passed(output, allowed_skips=skips)
        case = "manual_observation_window" if hold_seconds else "fixture_instrumentation"
        self.record(case, "PASS" if passed else "FAIL", "instrumentation", label + ".log")
        for name in RESULTS:
            try:
                data = self.command(name.removesuffix(".json"), ["shell", "run-as", FIXTURE, "cat", "no_backup/" + name])
                json.loads(data)
                write_private(self.directory / name, data)
            except (RuntimeError, ValueError):
                # Missing individual facts never become a passing acceptance case.
                pass
        require(passed, "Owned instrumentation did not pass; retain individual failure/not-run facts")

    def foreground(self):
        state = self.command("foreground", ["shell", "dumpsys", "activity", "activities"]).decode(errors="replace")
        resumed = [line for line in state.splitlines() if "mResumedActivity" in line or "topResumedActivity" in line]
        require(any(re.search(r"\b" + re.escape(FIXTURE) + r"/", line) for line in resumed), "Isolated fixture must be foreground; no input sent")

    def key(self, key):
        require(key in KEYS, "Only the bounded fixture remote keys are allowed")
        self.device()
        self.foreground()
        self.command("remote-" + key.lower(), ["shell", "input", "keyevent", key])

    def capture(self, name):
        require(re.fullmatch(r"[a-z0-9_-]+", name), "Use a simple private capture name")
        self.device()
        self.foreground()
        data = self.command("capture-" + name, ["exec-out", "screencap", "-p"])
        require(data.startswith(b"\x89PNG"), "Device screenshot is unavailable")
        write_private(self.directory / (name + ".png"), data)
        temporary = "/data/local/tmp/viptv-native-physical.xml"
        try:
            self.command("tree-" + name, ["shell", "uiautomator", "dump", temporary])
            tree = self.command("tree-read-" + name, ["exec-out", "cat", temporary])
            write_private(self.directory / (name + ".xml"), tree)
        finally:
            self.command("tree-cleanup-" + name, ["shell", "rm", "-f", temporary])

    def sample(self, seconds, interval):
        require(60 <= seconds <= 900 and 5 <= interval <= 60, "Use a bounded 60–900 second owned-resource interval")
        self.device()
        samples = []
        start = time.monotonic()
        while True:
            self.foreground()
            index = len(samples)
            memory = self.command("memory-" + str(index), ["shell", "dumpsys", "meminfo", FIXTURE]).decode(errors="replace")
            pss = re.search(r"TOTAL PSS:\s*(\d+)", memory) or re.search(r"^\s*TOTAL\s+(\d+)", memory, re.MULTILINE)
            disk = self.command("disk-" + str(index), ["shell", "run-as", FIXTURE, "du", "-sk", "no_backup"]).decode()
            size = re.match(r"\s*(\d+)\b", disk)
            pid = self.command("pid-" + str(index), ["shell", "pidof", FIXTURE]).decode().strip()
            require(re.fullmatch(r"[1-9][0-9]*", pid), "Owned fixture process is unavailable or ambiguous")
            status = self.command("threads-" + str(index), ["shell", "run-as", FIXTURE, "cat", "/proc/" + pid + "/status"]).decode()
            threads = re.search(r"^Threads:\s*(\d+)$", status, re.MULTILINE)
            descriptors = self.command("descriptors-" + str(index), ["shell", "run-as", FIXTURE, "ls", "-1", "/proc/" + pid + "/fd"]).decode().splitlines()
            require(pss and size and threads and descriptors and all(re.fullmatch(r"\d+", item.strip()) for item in descriptors), "Device resource counters unavailable; no sustained-resource verdict inferred")
            samples.append({"elapsed_seconds": round(time.monotonic() - start, 3), "pss_kib": int(pss.group(1)),
                            "private_storage_kib": int(size.group(1)), "process_threads": int(threads.group(1)),
                            "open_file_descriptors": len(descriptors)})
            write_private(self.directory / "owned-resources.json", json.dumps(samples, indent=2) + "\n")
            if time.monotonic() - start >= seconds:
                break
            time.sleep(max(0, min(interval, seconds - (time.monotonic() - start))))
        # Counters alone do not prove continuous playback or budget reclamation.

    def stop(self):
        self.device()
        self.command("fixture-stop", ["shell", "am", "force-stop", FIXTURE])
        created = self.directory / "created-reverses.json"
        if created.exists():
            for port in json.loads(created.read_text()):
                require(port in [19445, 19446], "Unexpected owned reverse entry")
                self.command("reverse-remove-" + str(port), ["reverse", "--remove", "tcp:" + str(port)])
        self.record("final_playback_stopped", "PASS", "measured", "fixture-stop.log")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--directory", type=Path, required=True)
    sub = parser.add_subparsers(dest="action", required=True)
    sub.add_parser("initialize")
    sub.add_parser("device")
    install = sub.add_parser("install")
    install.add_argument("--apk", type=Path, required=True)
    install.add_argument("--sha256", required=True)
    install.add_argument("--package", choices=[NORMAL, NORMAL + ".test", FIXTURE, FIXTURE + ".test"], required=True)
    sub.add_parser("normal-probe")
    fixture = sub.add_parser("fixture")
    fixture.add_argument("--selector", default="org.viptv.app.OwnedNativePipelineTest")
    fixture.add_argument("--hold-seconds", type=int, default=0)
    key = sub.add_parser("key")
    key.add_argument("value", choices=sorted(KEYS))
    capture = sub.add_parser("capture")
    capture.add_argument("name")
    resources = sub.add_parser("resources")
    resources.add_argument("--seconds", type=int, default=300)
    resources.add_argument("--interval", type=int, default=10)
    record = sub.add_parser("record")
    record.add_argument("--case", choices=CASES, required=True)
    record.add_argument("--verdict", choices=["PASS", "FAIL", "NOT-RUN"], required=True)
    record.add_argument("--basis", choices=["instrumentation", "measured", "human", "unavailable"], required=True)
    record.add_argument("--observer", choices=["human"])
    record.add_argument("--evidence")
    sub.add_parser("stop")
    sub.add_parser("report")
    args = parser.parse_args()
    q = Qualification(args.directory)
    fingerprint = hashlib.sha256(q.values["VIPTV_TAILSCALE_TV_ADB_SERIAL"].encode()).hexdigest()
    if args.action != "initialize":
        session = q.directory / "session.json"
        require(session.is_file() and not session.is_symlink(), "Initialize a fresh private run before qualification")
        require(json.loads(session.read_text())["configured_device_fingerprint"] == fingerprint, "Configured device changed; preserve this run and use a new directory")
    if args.action == "initialize":
        require(not (q.directory / "session.json").exists(), "Use a fresh run directory; existing evidence is preserved")
        pins = {name: (ROOT / name).read_text().strip() for name in ["CORE_REF", "DESIGN_REF", "TORRENT_REF"]}
        revisions = {}
        for repository in ["android", "backend", "core", "playback-gateway"]:
            result = subprocess.run(["git", "-C", str(ROOT.parent / repository), "rev-parse", "HEAD"], capture_output=True, text=True)
            require(result.returncode == 0 and re.fullmatch(r"[0-9a-f]{40}\n?", result.stdout), "Owning repository revision is unavailable")
            revisions[repository] = result.stdout.strip()
        write_private(q.directory / "session.json", json.dumps({"created_at": timestamp(), "pins": pins, "repository_revisions_at_preparation": revisions,
                      "configured_device_fingerprint": fingerprint,
                      "device": {"model": "AFTLAS01", "sdk": 30, "abi": "armeabi-v7a"}, "native_capability_qualified": False}, indent=2) + "\n")
    elif args.action == "device": q.device()
    elif args.action == "install": q.install(args.apk, args.sha256, args.package)
    elif args.action == "normal-probe": q.probe()
    elif args.action == "fixture": q.fixture(args.selector, args.hold_seconds)
    elif args.action == "key": q.key(args.value)
    elif args.action == "capture": q.capture(args.name)
    elif args.action == "resources": q.sample(args.seconds, args.interval)
    elif args.action == "record": q.record(args.case, args.verdict, args.basis, args.evidence, args.observer)
    elif args.action == "stop": q.stop()
    else:
        cases = {}
        for case in CASES:
            path = q.directory / "cases" / (case + ".json")
            cases[case] = json.loads(path.read_text()) if path.exists() else {"verdict": "NOT-RUN"}
        write_private(q.directory / "report.json", json.dumps({"cases": cases,
                      "outside_scope": {case: "NOT-RUN" for case in OUTSIDE_SCOPE},
                      "native_capability_qualified": False}, indent=2) + "\n")
    print("Physical qualification action completed; evidence remains private. No capability activation is performed.")


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, OSError, ValueError) as error:
        # Never include subprocess arguments, addresses or private configuration.
        print("Physical qualification stopped: " + (str(error) if isinstance(error, RuntimeError) else type(error).__name__))
        raise SystemExit(1)
