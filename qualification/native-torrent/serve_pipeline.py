#!/usr/bin/env python3
"""Run a finite isolated real authorization/owned-peer/TLS fixture environment.

Build artifacts first under the coordinated Cargo window. Start this server
before assembling the private fixture APK, whose config is generated here.
No device is connected and no application is installed by this command.
"""
import argparse
import json
import os
from pathlib import Path
import subprocess
import sys
import time

from owned_fixture import atomic_json


def wait_file(path, process, timeout=30):
    until = time.monotonic() + timeout
    while time.monotonic() < until:
        if path.exists():
            return json.loads(path.read_text())
        if process.poll() is not None:
            raise RuntimeError("owned fixture exited before readiness")
        time.sleep(0.05)
    raise RuntimeError("owned fixture readiness deadline")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    parser.add_argument("--backend-executable", type=Path, required=True)
    parser.add_argument("--control-port", type=int, default=19445)
    parser.add_argument("--peer-port", type=int, default=19446)
    parser.add_argument("--seconds", type=int, default=840)
    parser.add_argument("--bytes-per-second", type=int, default=1048576)
    args = parser.parse_args()
    os.umask(0o077)
    directory = args.directory.resolve()
    if not 1 <= args.seconds <= 840:
        raise SystemExit("fixture duration must fit within backend's finite deadline")
    for name in ("backend-ready.json", "backend-command.json", "peer.json", "pipeline-config.json"):
        if (directory / name).exists():
            raise SystemExit("use a fresh generated fixture directory")
    manifest = json.loads((directory / "manifest.json").read_text())
    atomic_json(directory / "backend-config.json", {"info_hash": manifest["info_hash"], "port": 0, "origin": f"https://127.0.0.1:{args.control_port}"})
    here = Path(__file__).resolve().parent
    processes = []
    logs = []
    try:
        log = (directory / "backend.log").open("wb")
        logs.append(log)
        backend = subprocess.Popen([str(args.backend_executable.resolve()), "--exact", "gateway::torrent_native_acceptance::actual_backend_android_native_server",
                                    "--ignored", "--nocapture", "--test-threads=1"], stdout=log, stderr=subprocess.STDOUT,
                                   env=dict(os.environ, VIPTV_ANDROID_NATIVE_CONFIG=str(directory / "backend-config.json"),
                                            VIPTV_AUTH_ORIGIN=f"https://127.0.0.1:{args.control_port}"))
        processes.append(backend)
        ready = wait_file(directory / "backend-ready.json", backend)
        for name, command in [
            ("peer", [sys.executable, str(here / "owned_fixture.py"), "serve", str(directory), "--port", str(args.peer_port), "--bytes-per-second", str(args.bytes_per_second)]),
            ("tls", [sys.executable, str(here / "https_ingress.py"), str(directory / "tls"), "--backend-port", str(ready["port"]),
                     "--port", str(args.control_port), "--owned-directory", str(directory)]),
        ]:
            log = (directory / f"{name}.log").open("wb")
            logs.append(log)
            processes.append(subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT))
        wait_file(directory / "peer.json", processes[1])
        until_certificate = time.monotonic() + 10
        while not (directory / "tls/cert.pem").is_file():
            if processes[2].poll() is not None or time.monotonic() >= until_certificate:
                raise RuntimeError("owned fixture TLS readiness deadline")
            time.sleep(0.05)
        # Both device endpoints are literal loopback after the owner's adb reverse.
        configuration = dict(manifest, **ready, origin=f"https://127.0.0.1:{args.control_port}", peer=f"127.0.0.1:{args.peer_port}")
        atomic_json(directory / "pipeline-config.json", configuration)
        print("Owned pipeline configuration generated; isolated servers running.", flush=True)
        until = time.monotonic() + args.seconds
        while time.monotonic() < until and all(process.poll() is None for process in processes):
            time.sleep(0.2)
    except KeyboardInterrupt:
        pass
    finally:
        for process in reversed(processes):
            if process.poll() is None:
                process.terminate()
        for process in processes:
            try:
                process.wait(timeout=3)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=3)
        for log in logs:
            log.close()


if __name__ == "__main__":
    main()
