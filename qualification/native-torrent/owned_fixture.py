#!/usr/bin/env python3
"""Owned BEP3/BEP9 fixture. Every listener binds literal IPv4 loopback.

Generated media, metainfo, controls and counters belong in an ignored private
directory. The peer serves only the exact generated info dictionary and bytes;
it does not discover peers, announce, resolve hosts, or contact a tracker.
"""
import argparse
import hashlib
import json
import math
import os
from pathlib import Path
import select
import socket
import struct
import subprocess
import threading
import time


def encode(value):
    if isinstance(value, int):
        return b"i" + str(value).encode() + b"e"
    if isinstance(value, bytes):
        return str(len(value)).encode() + b":" + value
    if isinstance(value, list):
        return b"l" + b"".join(map(encode, value)) + b"e"
    if isinstance(value, dict):
        return b"d" + b"".join(encode(key) + encode(value[key]) for key in sorted(value)) + b"e"
    raise ValueError("unsupported fixture bencode type")


def decode(data, offset=0):
    kind = data[offset:offset + 1]
    if kind == b"i":
        end = data.index(b"e", offset)
        return int(data[offset + 1:end]), end + 1
    if kind in (b"l", b"d"):
        result = [] if kind == b"l" else {}
        offset += 1
        while data[offset:offset + 1] != b"e":
            item, offset = decode(data, offset)
            if kind == b"l":
                result.append(item)
            else:
                value, offset = decode(data, offset)
                result[item] = value
        return result, offset + 1
    end = data.index(b":", offset)
    size = int(data[offset:end])
    end += 1
    if size < 0 or end + size > len(data):
        raise ValueError("invalid fixture bencode length")
    return data[end:end + size], end + size


def atomic_json(path, value):
    temporary = path.with_suffix(".tmp")
    temporary.write_text(json.dumps(value, sort_keys=True) + "\n")
    temporary.replace(path)


def generate(directory, seconds):
    directory.mkdir(parents=True, exist_ok=True)
    os.chmod(directory, 0o700)
    payload = directory / "owned-episodes"
    payload.mkdir(exist_ok=True)
    (payload / "00-readme.txt").write_text("Owned synthetic multi-file fixture; explicit episode selection required.\n")
    for language in ("English", "Spanish"):
        (directory / f"{language}.srt").write_text(f"1\n00:00:00,250 --> 00:00:20,000\nOwned {language} cue\n")
    for episode in (1, 2):
        subprocess.run([
            "ffmpeg", "-nostdin", "-y", "-v", "error", "-f", "lavfi", "-i", "testsrc2=size=640x360:rate=24",
            "-f", "lavfi", "-i", f"sine=frequency={440 * episode}:sample_rate=48000",
            "-f", "lavfi", "-i", "sine=frequency=1320:sample_rate=48000",
            "-i", str(directory / "English.srt"), "-i", str(directory / "Spanish.srt"),
            "-map", "0:v", "-map", "1:a", "-map", "2:a", "-map", "3:s", "-map", "4:s",
            "-t", str(seconds), "-c:v", "libx264", "-threads", "2", "-preset", "ultrafast", "-pix_fmt", "yuv420p",
            "-c:a", "aac", "-c:s", "mov_text", "-metadata:s:a:0", "language=eng", "-metadata:s:a:1", "language=spa",
            "-metadata:s:s:0", "language=eng", "-metadata:s:s:1", "language=spa", "-disposition:s:0", "0",
            "-disposition:s:1", "0", "-movflags", "+faststart", str(payload / f"0{episode}-episode.mp4"),
        ], check=True)
    files = sorted(payload.iterdir())
    content = b"".join(path.read_bytes() for path in files)
    piece_length = 16384
    info = {b"name": payload.name.encode(), b"piece length": piece_length,
            b"files": [{b"length": path.stat().st_size, b"path": [path.name.encode()]} for path in files],
            b"pieces": b"".join(hashlib.sha1(content[start:start + piece_length]).digest() for start in range(0, len(content), piece_length))}
    raw_info = encode(info)
    (directory / "owned.torrent").write_bytes(encode({b"info": info}))
    manifest = {"info_hash": hashlib.sha1(raw_info).hexdigest(), "piece_length": piece_length,
                "payload_bytes": len(content), "metainfo_sha256": hashlib.sha256((directory / "owned.torrent").read_bytes()).hexdigest(),
                "files": [{"index": index, "name": path.name, "bytes": path.stat().st_size,
                           "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
                           "prefix_sha256": hashlib.sha256(path.read_bytes()[:64]).hexdigest()} for index, path in enumerate(files)]}
    atomic_json(directory / "manifest.json", manifest)
    atomic_json(directory / "hold-pieces.json", [])
    atomic_json(directory / "hold-metadata.json", False)
    return manifest


class OwnedPeer:
    def __init__(self, directory, port=0, bytes_per_second=0):
        self.directory = directory
        metainfo, _ = decode((directory / "owned.torrent").read_bytes())
        self.info = metainfo[b"info"]
        self.metadata = encode(self.info)
        self.hash = hashlib.sha1(self.metadata).digest()
        root = directory / self.info[b"name"].decode()
        self.content = b"".join(root.joinpath(*(part.decode() for part in item[b"path"])).read_bytes() for item in self.info[b"files"])
        if b"".join(hashlib.sha1(self.content[i:i + self.info[b"piece length"]]).digest() for i in range(0, len(self.content), self.info[b"piece length"])) != self.info[b"pieces"]:
            raise ValueError("fixture payload does not match metainfo")
        self.rate = bytes_per_second
        self.stopped = threading.Event()
        self.lock = threading.Lock()
        self.clients = set()
        self.counters = {"connections": 0, "requests": 0, "served_bytes": 0, "held_requests": 0}
        self.listener = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.listener.bind(("127.0.0.1", port))
        self.listener.listen(16)
        self.listener.settimeout(0.2)
        self.port = self.listener.getsockname()[1]

    def metrics(self):
        with self.lock:
            return dict(self.counters, active_connections=len(self.clients))

    def stop(self):
        self.stopped.set()
        self.listener.close()
        with self.lock:
            clients = list(self.clients)
        for client in clients:
            try:
                client.shutdown(socket.SHUT_RDWR)
            except OSError:
                pass
            client.close()

    def run(self):
        workers = []
        try:
            while not self.stopped.is_set():
                try:
                    client, _ = self.listener.accept()
                except socket.timeout:
                    continue
                except OSError:
                    break
                with self.lock:
                    self.clients.add(client)
                    self.counters["connections"] += 1
                worker = threading.Thread(target=self.serve, args=(client,), daemon=True)
                worker.start()
                workers.append(worker)
                workers = [thread for thread in workers if thread.is_alive()]
        finally:
            self.stop()
            for worker in workers:
                worker.join(2)

    @staticmethod
    def receive(client, size):
        result = bytearray()
        while len(result) < size:
            block = client.recv(size - len(result))
            if not block:
                raise EOFError()
            result.extend(block)
        return bytes(result)

    @staticmethod
    def send(client, value):
        client.sendall(struct.pack("!I", len(value)) + value)

    def held(self, index):
        try:
            return index in json.loads((self.directory / "hold-pieces.json").read_text())
        except (OSError, ValueError):
            return True

    def metadata_held(self):
        try:
            return json.loads((self.directory / "hold-metadata.json").read_text()) is True
        except FileNotFoundError:
            return False
        except (OSError, ValueError):
            return True

    def metadata_piece(self, client, extension, piece):
        start = piece * 16384
        if start < 0 or start >= len(self.metadata):
            raise ValueError("invalid metadata piece")
        self.send(client, bytes([20, extension]) + encode({b"msg_type": 1, b"piece": piece, b"total_size": len(self.metadata)}) + self.metadata[start:start + 16384])

    def serve(self, client):
        try:
            client.settimeout(2)
            handshake = self.receive(client, 68)
            if handshake[:20] != b"\x13BitTorrent protocol" or handshake[28:48] != self.hash:
                return
            client.sendall(b"\x13BitTorrent protocol" + b"\x00\x00\x00\x00\x00\x10\x00\x00" + self.hash + b"-OWNED1-000000000000")
            count = len(self.info[b"pieces"]) // 20
            bitfield = bytearray(math.ceil(count / 8))
            for index in range(count):
                bitfield[index // 8] |= 1 << (7 - index % 8)
            self.send(client, b"\x05" + bitfield)
            self.send(client, b"\x01")
            self.send(client, b"\x14\x00" + encode({b"m": {b"ut_metadata": 1}, b"metadata_size": len(self.metadata)}))
            extension = 1
            pending = []
            pending_metadata = []
            while not self.stopped.is_set():
                # Held requests remain pending without blocking cancellation/keepalive reads.
                for request in pending[:]:
                    if not self.held(request[0]):
                        self.piece(client, request)
                        pending.remove(request)
                if not self.metadata_held():
                    for piece in pending_metadata:
                        self.metadata_piece(client, extension, piece)
                    pending_metadata.clear()
                if not select.select([client], [], [], 0.1)[0]:
                    continue
                client.settimeout(2)
                header = self.receive(client, 4)
                length = struct.unpack("!I", header)[0]
                if length > 65536:
                    return
                if not length:
                    continue
                client.settimeout(2)
                message = self.receive(client, length)
                if message[:2] == b"\x14\x00":
                    value, _ = decode(message[2:])
                    extension = value.get(b"m", {}).get(b"ut_metadata", 1)
                elif message[:2] == b"\x14\x01":
                    value, _ = decode(message[2:])
                    if value.get(b"msg_type") == 0:
                        piece = value[b"piece"]
                        if self.metadata_held():
                            if piece not in pending_metadata and len(pending_metadata) < 256:
                                pending_metadata.append(piece)
                        else:
                            self.metadata_piece(client, extension, piece)
                elif message[:1] == b"\x06" and len(message) == 13:
                    request = struct.unpack("!III", message[1:])
                    with self.lock:
                        self.counters["requests"] += 1
                    if self.held(request[0]):
                        if len(pending) >= 256:
                            return
                        pending.append(request)
                        with self.lock:
                            self.counters["held_requests"] += 1
                    else:
                        self.piece(client, request)
                elif message[:1] == b"\x08" and len(message) == 13:
                    request = struct.unpack("!III", message[1:])
                    if request in pending:
                        pending.remove(request)
        except (OSError, EOFError, ValueError, KeyError, IndexError):
            pass
        finally:
            with self.lock:
                self.clients.discard(client)
            client.close()

    def piece(self, client, request):
        index, begin, length = request
        piece_length = self.info[b"piece length"]
        start = index * piece_length + begin
        if length < 1 or length > 16384 or begin + length > piece_length or start + length > len(self.content):
            raise ValueError("invalid owned piece request")
        if self.rate and self.stopped.wait(length / self.rate):
            return
        self.send(client, b"\x07" + struct.pack("!II", index, begin) + self.content[start:start + length])
        with self.lock:
            self.counters["served_bytes"] += length


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("generate", "serve"))
    parser.add_argument("directory", type=Path)
    parser.add_argument("--seconds", type=int, default=60)
    parser.add_argument("--port", type=int, default=0)
    parser.add_argument("--bytes-per-second", type=int, default=0)
    args = parser.parse_args()
    if args.command == "generate":
        generate(args.directory, args.seconds)
        print("Owned media and canonical metainfo generated.")
    else:
        peer = OwnedPeer(args.directory, args.port, args.bytes_per_second)
        atomic_json(args.directory / "peer.json", {"peer": f"127.0.0.1:{peer.port}", "dht": False, "info_hash": peer.hash.hex()})
        worker = threading.Thread(target=peer.run, daemon=True)
        worker.start()
        try:
            while worker.is_alive():
                atomic_json(args.directory / "peer-metrics.json", peer.metrics())
                worker.join(0.2)
        except KeyboardInterrupt:
            peer.stop()
            worker.join(2)


if __name__ == "__main__":
    main()
