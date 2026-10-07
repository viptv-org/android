#!/usr/bin/env python3
"""Export test-network-policy products separately from normal Android inputs."""
import argparse
import hashlib
import json
import os
import re
from pathlib import Path
import shutil
import subprocess


def run(source, arguments, env):
    subprocess.run(arguments, cwd=source, env=env, check=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--abi", choices=("arm64-v8a", "armeabi-v7a", "x86_64"), action="append")
    parser.add_argument("--host", action="store_true")
    parser.add_argument("--target-directory", type=Path, help="coordinated shared Cargo cache; products are still copied into fresh fixture output")
    args = parser.parse_args()
    source = args.source.resolve()
    output = args.output.resolve()
    if output == source or source in output.parents:
        raise SystemExit("fixture output must be outside source tree")
    if output.exists():
        raise SystemExit("fixture output must be a fresh private directory")
    if subprocess.check_output(["git", "status", "--porcelain", "--untracked-files=no"], cwd=source).strip():
        raise SystemExit("fixture source must be committed and clean")
    revision = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=source, text=True).strip()
    output.mkdir(parents=True, mode=0o700)
    target = args.target_directory.resolve() if args.target_directory else output / "target"
    env = dict(os.environ, CARGO_BUILD_JOBS="1", CARGO_TARGET_DIR=str(target))
    host_rustflags = env.get("RUSTFLAGS", "")
    features = "torrent,test-network-policy"
    # Host cdylib is also needed for UniFFI's library-mode generation.
    run(source, ["cargo", "build", "--locked", "--release", "-p", "playback-gateway-ffi", "--no-default-features", "--features", features], env)
    library = output / "target/release/libplayback_gateway_ffi.so"
    if target != output / "target":
        library.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(target / "release/libplayback_gateway_ffi.so", library)
    # Release stripping removes UniFFI's inspection metadata. Generate from the
    # same-feature unstripped library; runtime checksum checks validate the release ABI.
    run(source, ["cargo", "build", "--locked", "-p", "playback-gateway-ffi", "--lib", "--no-default-features", "--features", features], env)
    metadata_library = target / "debug/libplayback_gateway_ffi.so"
    run(source, ["cargo", "run", "--locked", "-p", "playback-gateway-ffi", "--bin", "playback-uniffi-bindgen", "--no-default-features", "--features", features + ",bindgen",
                 "--", "generate", "--library", str(metadata_library), "--language", "kotlin", "--no-format", "--out-dir", str(output / "kotlin")], env)
    bindings = output / "kotlin/uniffi/playback_gateway_ffi/playback_gateway_ffi.kt"
    if not re.search(r"\bfun\s+`?newNativeOwned`?\s*\(", bindings.read_text()):
        raise SystemExit("fixture-only constructor missing")
    env["RUSTFLAGS"] = "-C link-arg=-Wl,-z,max-page-size=16384 -C link-arg=-Wl,-z,common-page-size=16384"
    for abi in args.abi or []:
        run(source, ["cargo", "ndk", "--platform", "24", "-t", abi, "--output-dir", str(output / "jniLibs"),
                     "build", "--locked", "--release", "-p", "playback-gateway-ffi", "--no-default-features", "--features", features], env)
        for unwanted in (output / "jniLibs" / abi).glob("libcrc_fast*.so"):
            unwanted.unlink()
    if subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=source, text=True).strip() != revision or subprocess.check_output(["git", "status", "--porcelain", "--untracked-files=no"], cwd=source).strip():
        raise SystemExit("fixture source changed while building")
    files = [bindings, library] + sorted((output / "jniLibs").glob("*/libplayback_gateway_ffi.so"))
    manifest = {"fixture_only": True, "source_revision": revision, "features": ["torrent", "test-network-policy"],
                "cargo_lock_sha256": hashlib.sha256((source / "Cargo.lock").read_bytes()).hexdigest(),
                "rustc": subprocess.check_output(["rustc", "--version"], text=True).strip(),
                "rustflags": env["RUSTFLAGS"],
                "host_rustflags": host_rustflags,
                "binding_metadata_sha256": hashlib.sha256(metadata_library.read_bytes()).hexdigest(),
                "dht": False, "network": "explicit_owned_tcp_peers", "files": {str(path.relative_to(output)): hashlib.sha256(path.read_bytes()).hexdigest() for path in files}}
    (output / "fixture-build.json").write_text(json.dumps(manifest, indent=2) + "\n")
    print("Isolated owned-policy artifacts exported.")


if __name__ == "__main__":
    main()
