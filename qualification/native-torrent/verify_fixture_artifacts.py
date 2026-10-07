#!/usr/bin/env python3
"""Fail closed on mixed source, policies, JNI products or private fixture configuration."""
import argparse
import hashlib
import json
from pathlib import Path
import re
from urllib.parse import urlsplit


def verify(directory, reference, configuration):
    manifest = json.loads((directory / "fixture-build.json").read_text())
    pin = reference.read_text().strip()
    if manifest.get("source_revision") != pin or manifest.get("fixture_only") is not True:
        raise ValueError("fixture source provenance mismatch")
    if manifest.get("features") != ["torrent", "test-network-policy"] or manifest.get("dht") is not False:
        raise ValueError("fixture network policy mismatch")
    files = manifest.get("files", {})
    required = {"kotlin/uniffi/playback_gateway_ffi/playback_gateway_ffi.kt", "target/release/libplayback_gateway_ffi.so"}
    if not required.issubset(files) or not any(name.startswith("jniLibs/") for name in files):
        raise ValueError("fixture products incomplete")
    for name, checksum in files.items():
        path = (directory / name).resolve()
        if directory.resolve() not in path.parents or not re.fullmatch("[0-9a-f]{64}", checksum):
            raise ValueError("fixture product identity invalid")
        if hashlib.sha256(path.read_bytes()).hexdigest() != checksum:
            raise ValueError("fixture product checksum mismatch")
    if "fun newNativeOwned(" not in (directory / next(name for name in required if name.endswith(".kt"))).read_text():
        raise ValueError("owned fixture constructor unavailable")
    if configuration.stat().st_size > 16384:
        raise ValueError("fixture configuration too large")
    config = json.loads(configuration.read_text())
    origin = urlsplit(config["origin"])
    if origin.scheme != "https" or origin.hostname != "127.0.0.1" or not origin.port or origin.username or origin.password or origin.path not in ("", "/") or origin.query or origin.fragment:
        raise ValueError("fixture control origin must be literal HTTPS loopback")
    if not re.fullmatch(r"127\.0\.0\.1:([0-9]{1,5})", config["peer"]) or int(config["peer"].rsplit(":", 1)[1]) not in range(1, 65536):
        raise ValueError("fixture peer must be literal TCP loopback")
    if not config.get("access_token") or not isinstance(config.get("sources"), list) or not config["sources"]:
        raise ValueError("fixture authorization facts unavailable")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    parser.add_argument("reference", type=Path)
    parser.add_argument("configuration", type=Path)
    args = parser.parse_args()
    try:
        verify(args.directory, args.reference, args.configuration)
    except (OSError, ValueError, KeyError, TypeError):
        raise SystemExit("Isolated native fixture verification failed.")
    print("Isolated native fixture products verified.")


if __name__ == "__main__":
    main()
